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
 * The four kinds of 1-to-1 chat attachment. These are deliberately separate
 * from MEDIA_KINDS: a chat object lives under a *conversation* namespace, not
 * under `users/{uid}`, because it is readable by two people instead of one.
 */
export const CHAT_MEDIA_KINDS = ["photo", "video", "file", "voice"] as const;
export type ChatMediaKind = typeof CHAT_MEDIA_KINDS[number];

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
  "image/gif": "gif",
  "image/bmp": "bmp",
  "video/mp4": "mp4",
  "video/quicktime": "mov",
  "video/webm": "webm",
  "video/3gpp": "3gp",
  "video/x-matroska": "mkv",
  "audio/mp4": "m4a",
  "audio/aac": "aac",
  "audio/ogg": "ogg",
  "audio/webm": "webm",
  "audio/mpeg": "mp3",
  "audio/3gpp": "3gp",
  "audio/amr": "amr",
  "audio/wav": "wav",
  "audio/x-wav": "wav",
  "application/pdf": "pdf",
  "application/zip": "zip",
  "application/x-zip-compressed": "zip",
  "application/json": "json",
  "application/msword": "doc",
  "application/vnd.ms-excel": "xls",
  "application/vnd.ms-powerpoint": "ppt",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "xlsx",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation": "pptx",
  "application/octet-stream": "bin",
  "text/plain": "txt",
  "text/csv": "csv",
};

/**
 * A chat kind is only ever paired with a content type of the matching top-level
 * type. `file` is the one kind with no such rule, because "file" is exactly the
 * catch-all a user picks from a document chooser; it is still bounded by the
 * server's own allow-list and by the size cap.
 */
const CHAT_KIND_CONTENT_TYPE_PREFIX: Readonly<Record<ChatMediaKind, string | null>> = {
  photo: "image/",
  video: "video/",
  voice: "audio/",
  file: null,
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
 * The checks every client-supplied key has to pass before any prefix- or
 * namespace-specific rule is applied: bounded length, no traversal, no control
 * characters, no leading or trailing slash.
 */
function assertKeyShape(key: string): string {
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
  return trimmed;
}

/**
 * Validates a key that arrived from a client. Only keys inside the expected
 * prefix are addressable, and every segment must look like something this
 * server would have produced.
 */
export function assertReadableKey(key: string, prefix: string): string {
  const trimmed = assertKeyShape(key);
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

// ---------------------------------------------------------------------------
// Chat media
//
// A chat object key is `media/chats/{uploader}/{peer}/{kind}/{id}.{ext}`.
//
// The uploader is always the first uid segment and never the second, so the key
// alone answers both access questions the service needs to answer, with no
// Firestore lookup and no state of its own:
//
//   read   - the requester is one of the two participants, so neither a
//            stranger nor a third member of some other conversation can mint a
//            GET for it.
//   delete - only the uploader may delete. The receiver can hide a message from
//            its own list, but it must not be able to destroy an object the
//            sender's own history still points at.
//
// The pair is not verified against a conversation document here: the reference
// only becomes reachable through a Firestore message, and the message rules
// already require the caller to be a participant of the conversation it is
// written into. Both checks have to pass, and neither alone is enough.
// ---------------------------------------------------------------------------

export interface ChatKey {
  uploader: string;
  peer: string;
  kind: ChatMediaKind;
}

export interface BuildChatObjectKeyInput {
  prefix: string;
  uploaderUid: string;
  peerUid: string;
  kind: string;
  contentType: string;
  allowedContentTypes: readonly string[];
  maxUploadBytes: number;
  sizeBytes?: number;
  randomId?: string;
}

function assertChatKind(kind: string): ChatMediaKind {
  if (!(CHAT_MEDIA_KINDS as readonly string[]).includes(kind)) {
    throw new MediaError("Unknown chat media kind.", 400);
  }
  return kind as ChatMediaKind;
}

function assertContentTypeForChatKind(
  kind: ChatMediaKind,
  contentType: string,
  allowedContentTypes: readonly string[],
): { contentType: string; extension: string } {
  if (!allowedContentTypes.includes(contentType)) {
    throw new MediaError(
      `Unsupported content type. Allowed: ${allowedContentTypes.join(", ")}.`,
      400,
    );
  }
  const required = CHAT_KIND_CONTENT_TYPE_PREFIX[kind];
  if (required !== null && !contentType.startsWith(required)) {
    throw new MediaError(`A ${kind} must be a ${required}* object.`, 400);
  }
  const extension = EXTENSION_BY_CONTENT_TYPE[contentType];
  if (extension === undefined) {
    throw new MediaError(`No file extension is mapped for ${contentType}.`, 400);
  }
  return { contentType, extension };
}

/** Builds the chat key server-side, from the authenticated uid and the peer. */
export function buildChatObjectKey(input: BuildChatObjectKeyInput): string {
  assertSafeUid(input.uploaderUid);
  assertSafeUid(input.peerUid);
  if (input.uploaderUid === input.peerUid) {
    throw new MediaError("A chat attachment needs a peer other than yourself.", 400);
  }

  const kind = assertChatKind(input.kind);
  const { extension } = assertContentTypeForChatKind(
    kind,
    normalizeContentType(input.contentType),
    input.allowedContentTypes,
  );

  if (input.sizeBytes !== undefined) {
    if (input.sizeBytes > input.maxUploadBytes) {
      throw new MediaError(
        `The file is larger than the ${input.maxUploadBytes} byte limit.`,
        413,
      );
    }
  }

  const randomId = input.randomId ?? crypto.randomUUID();
  if (!RANDOM_ID_PATTERN.test(randomId)) {
    throw new MediaError("Could not generate a media id.", 500);
  }

  return `${input.prefix}/chats/${input.uploaderUid}/${input.peerUid}/${kind}/${
    randomId
  }.${extension}`;
}

/**
 * Validates a chat key that arrived from a client. Every segment is checked
 * against the same patterns the builder uses, so a key that this function
 * accepts is exactly a key this server could have produced.
 */
export function assertChatKey(key: string, prefix: string): ChatKey {
  const trimmed = assertKeyShape(key);
  const segments = trimmed.split("/");
  if (segments.length !== 6) throw new MediaError("Invalid media key.", 400);
  if (segments[0] !== prefix || segments[1] !== "chats") {
    throw new MediaError("Invalid media key.", 400);
  }
  for (const segment of segments) {
    if (!KEY_SEGMENT_PATTERN.test(segment)) {
      throw new MediaError("Invalid media key.", 400);
    }
  }
  const [uploader, peer] = segments.slice(2, 4);
  if (uploader === peer) throw new MediaError("Invalid media key.", 400);
  return {
    uploader: uploader!,
    peer: peer!,
    kind: assertChatKind(segments[4]!),
  };
}

/** True when the key is shaped like a chat object rather than a user object. */
export function isChatKeyShape(key: string, prefix: string): boolean {
  const trimmed = key.trim();
  return trimmed.startsWith(`${prefix}/chats/`);
}

/** A participant of that conversation may read the object. */
export function canReadChatKey(chatKey: ChatKey, requesterUid: string): boolean {
  return requesterUid === chatKey.uploader || requesterUid === chatKey.peer;
}

/** Only the uploader may delete: the receiver's copy of the history is not theirs to erase. */
export function canDeleteChatKey(chatKey: ChatKey, requesterUid: string): boolean {
  return requesterUid === chatKey.uploader;
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
    // A chat object is judged against the chat ceiling, not the much smaller
    // profile-media one: a 10 MB voice note is valid, and reporting it as an
    // over-limit object would make every attachment of that size unreadable.
    const ceiling = isChatKeyShape(key, this.settings.objectPrefix)
      ? this.settings.maxChatUploadBytes
      : this.settings.maxUploadBytes;
    if (sizeBytes !== null && sizeBytes > ceiling) {
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
   * Deletes an object. Ownership is checked here as well as at the route. The
   * route refuses a caller before anything is signed; this is the backstop that
   * survives a future caller who forgets to.
   *
   * For a user object that means the owner. For a chat object it means the
   * uploader: a receiver may hide the message from itself, but the sender's
   * history still points at the object, so the receiver may not destroy it.
   */
  async deleteObject(key: string, requesterUid: string): Promise<void> {
    const chat = isChatKeyShape(key, this.settings.objectPrefix)
      ? assertChatKey(key, this.settings.objectPrefix)
      : null;

    if (chat !== null) {
      if (!canDeleteChatKey(chat, requesterUid)) {
        throw new MediaError("You may only delete media you uploaded.", 403);
      }
    } else if (ownerOfKey(key, this.settings.objectPrefix) !== requesterUid) {
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
