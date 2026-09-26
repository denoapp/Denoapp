/**
 * DeenoLink B2 media service.
 *
 * A small HTTP surface that mints short-lived, per-object presigned URLs for the
 * private Backblaze B2 bucket. No B2 credential is ever returned to a client
 * and none is ever present in the Android artifact.
 *
 * Routes:
 *   GET    /health                 configuration + liveness, no secrets
 *   POST   /v1/media/upload-url    mint a presigned PUT for a server-made key
 *   POST   /v1/media/download-url  mint a presigned GET for an existing key
 *   GET    /v1/media/head?key=     object metadata, signed request to B2
 *   DELETE /v1/media?key=          delete, owner only
 *
 * Every /v1 route requires "Authorization: Bearer <Firebase ID token>".
 */

import { FirebaseTokenVerifier } from "./auth.ts";
import {
  assertReadableKey,
  B2Client,
  buildObjectKey,
  MediaError,
  normalizeContentType,
} from "./b2.ts";
import {
  ConfigError,
  loadSettings,
  publicConfigView,
  type ServerSettings,
  systemEnv,
} from "./config.ts";

const MAX_REQUEST_BYTES = 16 * 1024;

function jsonResponse(status: number, body: unknown, extraHeaders: HeadersInit = {}): Response {
  return new Response(JSON.stringify(body, null, 2), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      ...extraHeaders,
    },
  });
}

function applyCors(request: Request, settings: ServerSettings): Record<string, string> {
  const origin = request.headers.get("origin");
  // A native Android client sends no Origin, so CORS is only relevant for
  // browser-based tooling and is restricted to an explicit allowlist.
  if (origin !== null && settings.corsAllowedOrigins.includes(origin)) {
    return {
      "access-control-allow-origin": origin,
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
      "access-control-max-age": "600",
      vary: "Origin",
    };
  }
  return {};
}

async function readJsonBody(request: Request): Promise<Record<string, unknown>> {
  const declared = request.headers.get("content-length");
  if (declared !== null && /^\d+$/.test(declared) && Number(declared) > MAX_REQUEST_BYTES) {
    throw new MediaError("Request body is too large.", 413);
  }
  if (request.body === null) {
    throw new MediaError("A JSON request body is required.", 400);
  }

  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    if (value === undefined) continue;
    total += value.byteLength;
    if (total > MAX_REQUEST_BYTES) {
      await reader.cancel();
      throw new MediaError("Request body is too large.", 413);
    }
    chunks.push(value);
  }

  const raw = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    raw.set(chunk, offset);
    offset += chunk.byteLength;
  }

  try {
    const parsed = JSON.parse(new TextDecoder().decode(raw));
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new MediaError("Request body must be a JSON object.", 400);
  }
}

function readString(body: Record<string, unknown>, field: string): string {
  const value = body[field];
  if (typeof value !== "string" || value.trim() === "") {
    throw new MediaError(`Field "${field}" is required.`, 400);
  }
  return value.trim();
}

function readOptionalPositiveInt(
  body: Record<string, unknown>,
  field: string,
  max: number,
): number | undefined {
  const value = body[field];
  if (value === undefined || value === null) return undefined;
  if (typeof value !== "number" || !Number.isInteger(value) || value <= 0) {
    throw new MediaError(`Field "${field}" must be a positive whole number.`, 400);
  }
  if (value > max) {
    throw new MediaError(`Field "${field}" must be at most ${max}.`, 400);
  }
  return value;
}

export function createHandler(settings: ServerSettings, deps: { now?: () => number } = {}) {
  const client = new B2Client(settings.b2);
  const verifier = settings.authRequired
    ? new FirebaseTokenVerifier({ projectId: settings.firebaseProjectId })
    : null;
  const now = deps.now ?? Date.now;

  async function authenticate(request: Request): Promise<string> {
    if (verifier === null) return settings.devUid;
    const user = await verifier.verify(request.headers.get("authorization"));
    return user.uid;
  }

  return async function handle(request: Request): Promise<Response> {
    const cors = applyCors(request, settings);
    const url = new URL(request.url);
    const method = request.method.toUpperCase();

    if (method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    try {
      if (method === "GET" && url.pathname === "/health") {
        return jsonResponse(200, {
          status: "ok",
          service: "deenolink-b2-media",
          time: new Date(now()).toISOString(),
          config: publicConfigView(settings),
        }, cors);
      }

      if (!url.pathname.startsWith("/v1/")) {
        throw new MediaError("Not found.", 404);
      }

      const uid = await authenticate(request);

      if (method === "POST" && url.pathname === "/v1/media/upload-url") {
        const body = await readJsonBody(request);
        const kind = readString(body, "kind");
        const contentType = normalizeContentType(readString(body, "contentType"));
        const sizeBytes = readOptionalPositiveInt(body, "sizeBytes", Number.MAX_SAFE_INTEGER);
        if (sizeBytes !== undefined && sizeBytes > settings.b2.maxUploadBytes) {
          throw new MediaError(
            `The file is larger than the ${settings.b2.maxUploadBytes} byte limit.`,
            413,
          );
        }
        const expiresInSeconds = readOptionalPositiveInt(
          body,
          "expiresInSeconds",
          settings.b2.presignTtlSeconds,
        );

        const key = buildObjectKey({
          prefix: settings.b2.objectPrefix,
          uid,
          kind,
          contentType,
          allowedContentTypes: settings.b2.allowedContentTypes,
        });
        const presigned = await client.presignUpload({ key, contentType, expiresInSeconds });
        return jsonResponse(200, {
          ...presigned,
          maxUploadBytes: settings.b2.maxUploadBytes,
        }, cors);
      }

      if (method === "POST" && url.pathname === "/v1/media/download-url") {
        const body = await readJsonBody(request);
        const key = assertReadableKey(readString(body, "key"), settings.b2.objectPrefix);
        const expiresInSeconds = readOptionalPositiveInt(
          body,
          "expiresInSeconds",
          settings.b2.presignTtlSeconds,
        );
        const fileName = typeof body.fileName === "string" ? body.fileName : undefined;
        const presigned = await client.presignDownload({ key, expiresInSeconds, fileName });
        return jsonResponse(200, presigned, cors);
      }

      if (method === "GET" && url.pathname === "/v1/media/head") {
        const rawKey = url.searchParams.get("key");
        if (rawKey === null) throw new MediaError("Query parameter 'key' is required.", 400);
        const key = assertReadableKey(rawKey, settings.b2.objectPrefix);
        const metadata = await client.headObject(key);
        if (metadata === null) throw new MediaError("No such media object.", 404);
        return jsonResponse(200, { found: true, ...metadata }, cors);
      }

      if (method === "DELETE" && url.pathname === "/v1/media") {
        const rawKey = url.searchParams.get("key");
        if (rawKey === null) throw new MediaError("Query parameter 'key' is required.", 400);
        const key = assertReadableKey(rawKey, settings.b2.objectPrefix);
        await client.deleteObject(key, uid);
        return jsonResponse(200, { deleted: true, key }, cors);
      }

      throw new MediaError("Not found.", 404);
    } catch (error) {
      if (error instanceof MediaError) {
        return jsonResponse(error.status, { error: error.message }, cors);
      }
      if (error instanceof ConfigError) {
        return jsonResponse(500, { error: "Server configuration error." }, cors);
      }
      // The message is logged, never returned: an unexpected failure could
      // otherwise echo an internal detail back to the caller.
      console.error("Unhandled media service error:", error);
      return jsonResponse(500, { error: "Internal server error." }, cors);
    }
  };
}

export function main(): void {
  let settings: ServerSettings;
  try {
    settings = loadSettings(systemEnv);
  } catch (error) {
    if (error instanceof ConfigError) {
      console.error(`Configuration error: ${error.message}`);
      console.error(
        "Copy server/.env.example to server/.env, fill it in, and keep that file out of git.",
      );
      Deno.exit(78); // EX_CONFIG
    }
    throw error;
  }

  const handler = createHandler(settings);
  Deno.serve({ hostname: settings.hostname, port: settings.port, onListen: () => {} }, handler);

  // Startup banner: the public view only. No key id, no application key.
  console.log(`deenolink-b2-media listening on ${settings.hostname}:${settings.port}`);
  console.log(JSON.stringify(publicConfigView(settings), null, 2));
  if (!settings.authRequired) {
    console.warn(
      "WARNING: B2_ALLOW_ANONYMOUS_DEV is on. Media endpoints are unauthenticated. " +
        "Never set this in production.",
    );
  }
}

if (import.meta.main) {
  main();
}
