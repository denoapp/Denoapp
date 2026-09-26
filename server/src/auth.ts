/**
 * Firebase ID token verification for the media endpoints.
 *
 * Why this exists: the presign routes hand out write access to the bucket. Left
 * unauthenticated, anyone on the internet could fill the bucket and read every
 * object, so every media route requires a valid Firebase ID token.
 *
 * Google publishes the Secure Token signing keys as a JWKS, which WebCrypto can
 * consume directly. That avoids both an x509 parser and the Firebase Admin SDK,
 * so the credential path stays dependency-free.
 */

import { MediaError } from "./b2.ts";

const JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const ISSUER_PREFIX = "https://securetoken.google.com/";
const JWKS_TTL_MS = 60 * 60 * 1000;
/** Tolerance for clock skew between this host and Google's token issuer. */
const CLOCK_SKEW_SECONDS = 60;
const ALLOWED_ALGORITHM = "RS256";

interface JsonWebKeySet {
  keys?: Array<{
    kid?: string;
    kty?: string;
    alg?: string;
    use?: string;
    n?: string;
    e?: string;
  }>;
}

interface TokenHeader {
  alg?: string;
  kid?: string;
  typ?: string;
}

interface TokenPayload {
  iss?: string;
  aud?: string | string[];
  sub?: string;
  exp?: number;
  iat?: number;
}

export interface AuthenticatedUser {
  uid: string;
}

const encoder = new TextEncoder();

function decodeBase64Url(input: string): Uint8Array<ArrayBuffer> {
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  let binary: string;
  try {
    binary = atob(padded);
  } catch {
    throw new MediaError("Malformed bearer token.", 401);
  }
  const out = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

function decodeJsonSegment(segment: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(new TextDecoder().decode(decodeBase64Url(segment)));
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new MediaError("Malformed bearer token.", 401);
  }
}

function readBearerToken(authorization: string | null | undefined): string {
  if (typeof authorization !== "string") {
    throw new MediaError("Authorization header missing.", 401);
  }
  const match = /^Bearer ([A-Za-z0-9._~-]+)$/.exec(authorization.trim());
  if (match === null || match[1] === undefined) {
    throw new MediaError("Authorization header must be 'Bearer <Firebase ID token>'.", 401);
  }
  return match[1];
}

export interface FirebaseVerifierOptions {
  projectId: string;
  fetchImpl?: typeof fetch;
  now?: () => number;
}

export class FirebaseTokenVerifier {
  readonly #projectId: string;
  readonly #fetch: typeof fetch;
  readonly #now: () => number;
  #keys: Map<string, CryptoKey> | null = null;
  #fetchedAt = 0;
  #inFlight: Promise<void> | null = null;

  constructor(options: FirebaseVerifierOptions) {
    this.#projectId = options.projectId;
    this.#fetch = options.fetchImpl ?? fetch;
    this.#now = options.now ?? Date.now;
  }

  async verify(authorization: string | null | undefined): Promise<AuthenticatedUser> {
    const token = readBearerToken(authorization);
    const parts = token.split(".");
    if (parts.length !== 3) throw new MediaError("Malformed bearer token.", 401);
    const [encodedHeader, encodedPayload, encodedSignature] = parts as [string, string, string];

    const header = decodeJsonSegment(encodedHeader) as TokenHeader;
    if (header.alg !== ALLOWED_ALGORITHM) {
      // "alg: none" and HMAC confusion are the classic JWT attacks; refusing
      // anything but RS256 closes both.
      throw new MediaError("Unsupported token algorithm.", 401);
    }
    if (typeof header.kid !== "string" || header.kid === "") {
      throw new MediaError("Token is missing a key id.", 401);
    }

    const key = await this.#resolveKey(header.kid);
    const verified = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      decodeBase64Url(encodedSignature),
      encoder.encode(`${encodedHeader}.${encodedPayload}`),
    ).catch(() => false);
    if (!verified) {
      throw new MediaError("Token signature is not valid.", 401);
    }

    return { uid: this.#validateClaims(decodeJsonSegment(encodedPayload) as TokenPayload) };
  }

  async #resolveKey(kid: string): Promise<CryptoKey> {
    if (this.#keys === null || this.#now() - this.#fetchedAt > JWKS_TTL_MS) {
      await this.#refreshKeys();
    }
    let keys = this.#keys;
    if (keys === null || !keys.has(kid)) {
      // A rotated key id we have not seen: force one refresh, then give up.
      await this.#refreshKeys(true);
      keys = this.#keys;
    }
    if (keys === null) throw new MediaError("Token signing keys are unavailable.", 503);
    const key = keys.get(kid);
    if (key === undefined) throw new MediaError("Token was signed by an unknown key.", 401);
    return key;
  }

  #refreshKeys(force = false): Promise<void> {
    if (this.#inFlight !== null && !force) return this.#inFlight;
    const refresh = async (): Promise<void> => {
      let response: Response;
      try {
        response = await this.#fetch(JWKS_URL, { headers: { accept: "application/json" } });
      } catch {
        throw new MediaError("Could not reach the token key service.", 503);
      }
      if (!response.ok) {
        throw new MediaError("Could not reach the token key service.", 503);
      }
      const body = (await response.json()) as JsonWebKeySet;
      const keys = new Map<string, CryptoKey>();
      for (const jwk of body.keys ?? []) {
        if (
          jwk.kid === undefined || jwk.kty !== "RSA" || jwk.n === undefined || jwk.e === undefined
        ) {
          continue;
        }
        if (jwk.use !== undefined && jwk.use !== "sig") continue;
        if (jwk.alg !== undefined && jwk.alg !== ALLOWED_ALGORITHM) continue;
        const imported = await crypto.subtle.importKey(
          "jwk",
          { kty: "RSA", n: jwk.n, e: jwk.e, alg: ALLOWED_ALGORITHM },
          { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
          false,
          ["verify"],
        ).catch(() => null);
        if (imported !== null) keys.set(jwk.kid, imported);
      }
      if (keys.size === 0) {
        throw new MediaError("Token key service returned no usable keys.", 503);
      }
      this.#keys = keys;
      this.#fetchedAt = this.#now();
    };
    const task: Promise<void> = refresh()
      .catch((error: unknown) => {
        throw error instanceof MediaError
          ? error
          : new MediaError("Could not load token keys.", 503);
      })
      .finally(() => {
        // Only clear the slot if it still refers to this task, so a forced
        // refresh cannot cancel bookkeeping for a newer one.
        if (this.#inFlight === task) this.#inFlight = null;
      });
    this.#inFlight = task;
    return task;
  }

  #validateClaims(payload: TokenPayload): string {
    const expectedIssuer = `${ISSUER_PREFIX}${this.#projectId}`;
    if (payload.iss !== expectedIssuer) {
      throw new MediaError("Token was issued for a different project.", 401);
    }
    const audience = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
    if (!audience.includes(this.#projectId)) {
      throw new MediaError("Token audience does not match this service.", 401);
    }
    const nowSeconds = Math.floor(this.#now() / 1000);
    if (typeof payload.exp !== "number" || payload.exp + CLOCK_SKEW_SECONDS <= nowSeconds) {
      throw new MediaError("Token has expired.", 401);
    }
    if (typeof payload.iat === "number" && payload.iat - CLOCK_SKEW_SECONDS > nowSeconds) {
      throw new MediaError("Token is not valid yet.", 401);
    }
    if (typeof payload.sub !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(payload.sub)) {
      throw new MediaError("Token is missing a usable subject.", 401);
    }
    return payload.sub;
  }
}
