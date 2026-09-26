/**
 * Backblaze B2 client, speaking the S3-compatible API over SigV4.
 *
 * The bucket is private: nothing is ever served without a signature. Clients
 * never receive a B2 credential; they receive a short-lived presigned URL that
 * is scoped to a single object key the *server* generated.
 */

import type { B2Settings } from "./config.ts";
import { encodeObjectPath, encodeRfc3986, presignUrl, signRequest } from "./sigv4.ts";

export class MediaError extends Error {
  override name = "MediaError";
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/**
 * Media categories the server is willing to mint keys for. Posts/reels/stories
 * are declared here so the shape is stable, but the intent is that Phase 1 only
 * uses "avatar" and "cover".
 */
export const MEDIA_KINDS = ["avatar", "cover", "post", "reel", "story"] as const;
export type MediaKind = typeof MEDIA_KINDS[number];

/**
 * The extension is derived from a fixed table rather than from anything the
 * client sends, so a caller cannot influence the path beyond choosing a known
 * media type.
 */
const EXTENSION_BY_CONTENT_TYPE: Readonly<Record<string, string>> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "image/heic": "heic",
  "image/heif": "heif",
  "video/mp4": "mp4",
  "video/quicktime": "mov",
};

const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const KIND_PATTERN = /^[a-z][a-z0-9-]{0,31}$/;
const RANDOM_ID_PATTERN = /^[0-9a-fA-F-]{36}$/;
const KEY_SEGMENT_PATTERN = /^[A-Za-z0-9._~-]{1,255}$/;

export function assertSafeUid(uid: string): void {
  if (!UID_PATTERN.test(uid)) {
    throw new MediaError("The authenticated user id is not in a usable format.", 401);
  }
}

export function normalizeContentType(contentType: string | undefined): string {
  return (contentType ?? "").split(";")[0]!.trim().toLowerCase();
}

export interface BuildObjectKeyInput {
  prefix: string;
  uid: string;
  kind: string;
  contentType: string | undefined;
  allowedContentTypes: readonly string[];
  randomId?: string;
}

/**
 * Builds the key server-side. The client supplies only the media category and
 * the content type; it can never choose the path, which prevents traversal and
 * keeps the layout identical to the one already enforced in storage.rules
 * ("media/users/{uid}/...").
 */
export function buildObjectKey(input: BuildObjectKeyInput): string {
  assertSafeUid(input.uid);

  if (!KIND_PATTERN.test(input.kind)) {
    throw new MediaError("Unknown media kind.", 400);
  }
  if (!(MEDIA_KINDS as readonly string[]).includes(input.kind)) {
    throw new MediaError("Unknown media kind.", 400);
  }

  const contentType = normalizeContentType(input.contentType);
  if (!input.allowedContentTypes.includes(contentType)) {
    throw new MediaError(
      `Unsupported content type. Allowed: ${input.allowedContentTypes.join(", ")}.`,
      400,
    );
  }
  const extension = EXTENSION_BY_CONTENT_TYPE[contentType];
  if (extension === undefined) {
    throw new MediaError(`No file extension is mapped for ${contentType}.`, 400);
  }

  const randomId = input.randomId ?? crypto.randomUUID();
  if (!RANDOM_ID_PATTERN.test(randomId)) {
    throw new MediaError("Could not generate a media id.", 500);
  }

  return `${input.prefix}/users/${input.uid}/${input.kind}/${randomId}.${extension}`;
}

/**
 * Validates a key that arrived from a client. Only keys inside the expected
 * prefix are addressable, and every segment must look like something this
 * server would have produced.
 */
export function assertReadableKey(key: string, prefix: string): string {
  const trimmed = key.trim();
  if (trimmed.length === 0 || trimmed.length > 1024) {
    throw new MediaError("Invalid media key.", 400);
  }
  if (trimmed.includes("\\") || trimmed.includes("..")) {
    throw new MediaError("Invalid media key.", 400);
  }
  for (const char of trimmed) {
    const code = char.codePointAt(0) ?? 0;
    if (code < 0x20 || code === 0x7f) {
      throw new MediaError("Invalid media key.", 400);
    }
  }
  if (trimmed.startsWith("/") || trimmed.endsWith("/")) {
    throw new MediaError("Invalid media key.", 400);
  }
  if (!trimmed.startsWith(`${prefix}/users/`)) {
    throw new MediaError("Invalid media key.", 400);
  }
  const segments = trimmed.split("/");
  if (segments.length < 5 || segments.length > 8) {
    throw new MediaError("Invalid media key.", 400);
  }
  for (const segment of segments) {
    if (!KEY_SEGMENT_PATTERN.test(segment)) {
      throw new MediaError("Invalid media key.", 400);
    }
  }
  return trimmed;
}

/** Returns the uid segment of a key, or undefined when the shape is unexpected. */
export function ownerOfKey(key: string, prefix: string): string | undefined {
  const segments = key.split("/");
  if (segments[0] !== prefix || segments[1] !== "users") return undefined;
  const uid = segments[2];
  return uid !== undefined && UID_PATTERN.test(uid) ? uid : undefined;
}

/** Strips anything that could break out of a quoted Content-Disposition value. */
function safeDownloadFileName(name: string): string {
  const base = name.split("/").pop() ?? "download";
  const cleaned = base.replace(/[^\w.\- ]+/g, "_").slice(0, 80);
  return cleaned === "" || cleaned === "." || cleaned === ".." ? "download" : cleaned;
}

export interface PresignedUpload {
  url: string;
  method: "PUT";
  /** Headers the client must send exactly as given, or the signature fails. */
  headers: Record<string, string>;
  key: string;
  expiresInSeconds: number;
  expiresAt: string;
}

export interface PresignedDownload {
  url: string;
  method: "GET";
  key: string;
  expiresInSeconds: number;
  expiresAt: string;
}

export interface ObjectMetadata {
  key: string;
  sizeBytes: number | null;
  contentType: string | null;
  lastModified: string | null;
  fileId: string | null;
}

export class B2Client {
  constructor(
    private readonly settings: B2Settings,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  /**
   * B2's S3 endpoint is addressed path-style: /{bucket}/{key}. Virtual-host
   * style is not supported for custom domains on B2.
   */
  private objectUrl(key: string): URL {
    const path = `/${encodeRfc3986(this.settings.bucket)}/${encodeObjectPath(key)}`;
    return new URL(`${this.settings.endpoint}${path}`);
  }

  /** Caps any client-requested lifetime at the server's own ceiling. */
  private effectiveTtl(requested?: number): number {
    const cap = this.settings.presignTtlSeconds;
    if (requested === undefined || !Number.isFinite(requested)) return cap;
    return Math.max(60, Math.min(Math.floor(requested), cap));
  }

  async presignUpload(input: {
    key: string;
    contentType: string;
    expiresInSeconds?: number;
  }): Promise<PresignedUpload> {
    const expiresInSeconds = this.effectiveTtl(input.expiresInSeconds);
    const contentType = normalizeContentType(input.contentType);
    if (!this.settings.allowedContentTypes.includes(contentType)) {
      throw new MediaError("Unsupported content type for upload.", 400);
    }

    const signed = await presignUrl({
      method: "PUT",
      url: this.objectUrl(input.key),
      headers: { "content-type": contentType },
      expiresInSeconds,
      keyId: this.settings.keyId,
      secretAccessKey: this.settings.applicationKey,
      region: this.settings.region,
    });

    return {
      url: signed.toString(),
      method: "PUT",
      headers: { "content-type": contentType },
      key: input.key,
      expiresInSeconds,
      expiresAt: new Date(Date.now() + expiresInSeconds * 1000).toISOString(),
    };
  }

  async presignDownload(input: {
    key: string;
    expiresInSeconds?: number;
    fileName?: string;
  }): Promise<PresignedDownload> {
    const expiresInSeconds = this.effectiveTtl(input.expiresInSeconds);

    // response-content-disposition must be signed as a query parameter, so it
    // is passed through the query list rather than as a header.
    const query: Array<readonly [string, string]> = input.fileName
      ? [
        [
          "response-content-disposition",
          `attachment; filename="${safeDownloadFileName(input.fileName)}"`,
        ],
      ]
      : [];

    const signed = await presignUrl({
      method: "GET",
      url: this.objectUrl(input.key),
      query,
      expiresInSeconds,
      keyId: this.settings.keyId,
      secretAccessKey: this.settings.applicationKey,
      region: this.settings.region,
    });

    return {
      url: signed.toString(),
      method: "GET",
      key: input.key,
      expiresInSeconds,
      expiresAt: new Date(Date.now() + expiresInSeconds * 1000).toISOString(),
    };
  }

  /** Verifies the key really exists and is readable, without downloading it. */
  async headObject(key: string): Promise<ObjectMetadata | null> {
    const url = this.objectUrl(key);
    const headers = await signRequest({
      method: "HEAD",
      url,
      keyId: this.settings.keyId,
      secretAccessKey: this.settings.applicationKey,
      region: this.settings.region,
    });

    const response = await this.fetchImpl(url, { method: "HEAD", headers });
    if (response.status === 404) return null;
    if (!response.ok) {
      throw new MediaError(`B2 rejected the metadata request (${response.status}).`, 502);
    }
    const length = response.headers.get("content-length");
    const sizeBytes = length !== null && /^\d+$/.test(length) ? Number(length) : null;
    if (sizeBytes !== null && sizeBytes > this.settings.maxUploadBytes) {
      throw new MediaError("The stored object is larger than the configured limit.", 409);
    }
    return {
      key,
      sizeBytes,
      contentType: response.headers.get("content-type"),
      lastModified: response.headers.get("last-modified"),
      fileId: response.headers.get("x-bz-file-id"),
    };
  }

  /**
   * Deletes an object. Ownership is checked here rather than at the route so
   * the rule cannot be forgotten by a future caller.
   */
  async deleteObject(key: string, requesterUid: string): Promise<void> {
    if (ownerOfKey(key, this.settings.objectPrefix) !== requesterUid) {
      throw new MediaError("You may only delete your own media.", 403);
    }
    const url = this.objectUrl(key);
    const headers = await signRequest({
      method: "DELETE",
      url,
      keyId: this.settings.keyId,
      secretAccessKey: this.settings.applicationKey,
      region: this.settings.region,
    });
    const response = await this.fetchImpl(url, { method: "DELETE", headers });
    if (response.status === 404) return;
    if (!response.ok) {
      throw new MediaError(`B2 rejected the delete request (${response.status}).`, 502);
    }
  }
}
