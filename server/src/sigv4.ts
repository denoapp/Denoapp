/**
 * Minimal AWS Signature Version 4 for S3-compatible endpoints (Backblaze B2).
 *
 * Implemented directly on WebCrypto so the server has zero npm dependencies and
 * therefore no third-party supply chain in the path of the B2 credentials.
 *
 * The canonical request and string-to-sign logic is verified against the AWS
 * SigV4 test-suite "get-vanilla" known-answer vector and against a B2-shaped
 * presigned PUT vector; see tests/sigv4_test.ts.
 */

export const SIGV4_ALGORITHM = "AWS4-HMAC-SHA256";
export const UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
/** SHA-256 of the empty string, used for the bodyless requests we actually send. */
export const EMPTY_PAYLOAD_SHA256 =
  "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
export const DEFAULT_SERVICE = "s3";

const encoder = new TextEncoder();

/**
 * WebCrypto's BufferSource is BufferSource over a non-shared ArrayBuffer, while
 * a bare `Uint8Array` is generic over ArrayBufferLike. Pinning the buffer type
 * here keeps every crypto call site free of casts.
 */
export type Bytes = Uint8Array<ArrayBuffer>;

function alloc(length: number): Bytes {
  return new Uint8Array(new ArrayBuffer(length));
}

/** RFC 3986 encoding: encodeURIComponent leaves !'()* unescaped, SigV4 must not. */
export function encodeRfc3986(value: string): string {
  return encodeURIComponent(value).replace(
    /[!'()*]/g,
    (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`,
  );
}

/** Encodes each path segment but keeps the separators literal. */
export function encodeObjectPath(path: string): string {
  return path.split("/").map(encodeRfc3986).join("/");
}

function compare(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * Sorts and RFC 3986 encodes query parameters. This is both the canonical form
 * used for signing and the exact byte sequence emitted on the wire, so a value
 * containing a space is encoded as %20 and never as "+".
 */
export function canonicalQueryString(pairs: Iterable<readonly [string, string]>): string {
  return [...pairs]
    .map(([name, value]) => [encodeRfc3986(name), encodeRfc3986(value)] as const)
    .sort((a, b) => (a[0] === b[0] ? compare(a[1], b[1]) : compare(a[0], b[0])))
    .map(([name, value]) => `${name}=${value}`)
    .join("&");
}

/**
 * Lowercases names, trims values, and collapses internal whitespace runs to a
 * single space, per the SigV4 canonicalization rules.
 */
export function canonicalHeaders(headers: Readonly<Record<string, string>>): {
  canonical: string;
  signed: string;
} {
  const entries = Object.entries(headers)
    .map(([name, value]) => [name.trim().toLowerCase(), value.trim().replace(/\s+/g, " ")] as const)
    .sort((a, b) => compare(a[0], b[0]));
  return {
    canonical: entries.map(([name, value]) => `${name}:${value}\n`).join(""),
    signed: entries.map(([name]) => name).join(";"),
  };
}

export function formatAmzDate(date: Date): { amzDate: string; dateStamp: string } {
  const amzDate = date.toISOString().replace(/[:-]/g, "").replace(/\.\d{3}/, "");
  return { amzDate, dateStamp: amzDate.slice(0, 8) };
}

export function toHex(bytes: Bytes): string {
  let out = "";
  for (const byte of bytes) out += byte.toString(16).padStart(2, "0");
  return out;
}

export function fromHex(hex: string): Bytes {
  if (hex.length % 2 !== 0 || !/^[0-9a-fA-F]*$/.test(hex)) {
    throw new Error("Invalid hex string.");
  }
  const out = alloc(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export async function sha256Hex(data: string | Bytes): Promise<string> {
  const input = typeof data === "string" ? encoder.encode(data) : data;
  return toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", input)));
}

async function hmacSha256(key: Bytes, data: string): Promise<Bytes> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    key,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return new Uint8Array(await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(data)));
}

export async function hmacSha256Hex(key: Bytes, data: string): Promise<string> {
  return toHex(await hmacSha256(key, data));
}

/** kDate -> kRegion -> kService -> kSigning. */
export async function deriveSigningKey(
  secretAccessKey: string,
  dateStamp: string,
  region: string,
  service: string = DEFAULT_SERVICE,
): Promise<Bytes> {
  const kDate = await hmacSha256(encoder.encode(`AWS4${secretAccessKey}`), dateStamp);
  const kRegion = await hmacSha256(kDate, region);
  const kService = await hmacSha256(kRegion, service);
  return await hmacSha256(kService, "aws4_request");
}

export function buildScope(dateStamp: string, region: string, service: string): string {
  return `${dateStamp}/${region}/${service}/aws4_request`;
}

export interface CanonicalRequestInput {
  method: string;
  /** Already percent-encoded path, e.g. "/bucket/a%20b.jpg". */
  path: string;
  /** Canonical query string, e.g. "b=2&a=1". */
  query: string;
  headers: Readonly<Record<string, string>>;
  payloadHash: string;
}

export function buildCanonicalRequest(input: CanonicalRequestInput): string {
  const { canonical, signed } = canonicalHeaders(input.headers);
  return [
    input.method.toUpperCase(),
    input.path === "" ? "/" : input.path,
    input.query,
    canonical,
    signed,
    input.payloadHash,
  ].join("\n");
}

export async function buildStringToSign(
  amzDate: string,
  scope: string,
  canonicalRequest: string,
): Promise<string> {
  return [SIGV4_ALGORITHM, amzDate, scope, await sha256Hex(canonicalRequest)].join("\n");
}

function assertAbsoluteHttps(url: URL): void {
  if (url.protocol !== "https:") {
    throw new Error("Refusing to sign a request for a non-https endpoint.");
  }
}

function lowerCaseKeys(headers: Readonly<Record<string, string>>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [name, value] of Object.entries(headers)) out[name.trim().toLowerCase()] = value;
  return out;
}

export interface PresignInput {
  method: string;
  /** Origin plus already-encoded path. Must be https. */
  url: URL;
  /** Extra headers the client must send byte-for-byte alongside the request. */
  headers?: Readonly<Record<string, string>>;
  /** Non-SigV4 query parameters to include and sign. */
  query?: Iterable<readonly [string, string]>;
  expiresInSeconds: number;
  keyId: string;
  secretAccessKey: string;
  region: string;
  service?: string;
  date?: Date;
}

/**
 * Produces a presigned URL: the signature travels in the query string, so the
 * holder needs no B2 credential. This is the only way bytes reach the private
 * bucket, which makes the returned URL itself a secret that must be treated
 * like a password for its lifetime.
 */
export async function presignUrl(input: PresignInput): Promise<URL> {
  const service = input.service ?? DEFAULT_SERVICE;
  assertAbsoluteHttps(input.url);

  const { amzDate, dateStamp } = formatAmzDate(input.date ?? new Date());
  const scope = buildScope(dateStamp, input.region, service);
  const extraPairs = [...(input.query ?? [])];

  const queryPairs: Array<readonly [string, string]> = [
    ...extraPairs,
    ["X-Amz-Algorithm", SIGV4_ALGORITHM],
    ["X-Amz-Credential", `${input.keyId}/${scope}`],
    ["X-Amz-Date", amzDate],
    ["X-Amz-Expires", String(input.expiresInSeconds)],
  ];

  const headers: Record<string, string> = {
    host: input.url.host,
    ...lowerCaseKeys(input.headers ?? {}),
  };
  const { signed } = canonicalHeaders(headers);

  const queryWithSignedHeaders: Array<readonly [string, string]> = [
    ...queryPairs,
    ["X-Amz-SignedHeaders", signed],
  ];
  const canonicalRequest = buildCanonicalRequest({
    method: input.method,
    path: input.url.pathname,
    query: canonicalQueryString(queryWithSignedHeaders),
    headers,
    payloadHash: UNSIGNED_PAYLOAD,
  });
  const stringToSign = await buildStringToSign(amzDate, scope, canonicalRequest);
  const signingKey = await deriveSigningKey(
    input.secretAccessKey,
    dateStamp,
    input.region,
    service,
  );
  const signature = toHex(await hmacSha256(signingKey, stringToSign));

  // Emitted in canonical order with the signature appended, using the same
  // encoder that produced the string that was signed.
  const wireQuery = canonicalQueryString([
    ...queryWithSignedHeaders,
    ["X-Amz-Signature", signature],
  ]);
  return new URL(`${input.url.origin}${input.url.pathname}?${wireQuery}`);
}

export interface SignRequestInput {
  method: string;
  /** Origin plus already-encoded path. Must be https. */
  url: URL;
  headers?: Readonly<Record<string, string>>;
  query?: Iterable<readonly [string, string]>;
  keyId: string;
  secretAccessKey: string;
  region: string;
  service?: string;
  date?: Date;
  /** SHA-256 of the body. Defaults to the empty-body hash. */
  payloadHash?: string;
}

/**
 * Produces the Authorization header for a request the server itself makes
 * (HEAD, GET, DELETE). Those carry no body, so the empty-body hash applies and
 * no caller bytes are ever buffered for signing.
 *
 * `host` is intentionally absent from the result: fetch derives it from the
 * URL and forbids overriding it. It is still part of the signed headers.
 */
export async function signRequest(input: SignRequestInput): Promise<Record<string, string>> {
  const service = input.service ?? DEFAULT_SERVICE;
  assertAbsoluteHttps(input.url);

  const { amzDate, dateStamp } = formatAmzDate(input.date ?? new Date());
  const scope = buildScope(dateStamp, input.region, service);
  const payloadHash = input.payloadHash ?? EMPTY_PAYLOAD_SHA256;
  const queryPairs = input.query ??
    [...input.url.searchParams.entries()];

  const headers: Record<string, string> = {
    host: input.url.host,
    "x-amz-content-sha256": payloadHash,
    "x-amz-date": amzDate,
    ...lowerCaseKeys(input.headers ?? {}),
  };
  const { signed } = canonicalHeaders(headers);

  const canonicalRequest = buildCanonicalRequest({
    method: input.method,
    path: input.url.pathname,
    query: canonicalQueryString(queryPairs),
    headers,
    payloadHash,
  });
  const stringToSign = await buildStringToSign(amzDate, scope, canonicalRequest);
  const signingKey = await deriveSigningKey(
    input.secretAccessKey,
    dateStamp,
    input.region,
    service,
  );
  const signature = toHex(await hmacSha256(signingKey, stringToSign));

  return {
    ...lowerCaseKeys(input.headers ?? {}),
    "x-amz-content-sha256": payloadHash,
    "x-amz-date": amzDate,
    authorization:
      `${SIGV4_ALGORITHM} Credential=${input.keyId}/${scope}, SignedHeaders=${signed}, Signature=${signature}`,
  };
}
