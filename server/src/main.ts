/**
 * DeenoLink B2 media and push service.
 *
 * A small HTTP surface that mints short-lived, per-object presigned URLs for the
 * private Backblaze B2 bucket, and delivers FCM chat notifications. No B2
 * credential is ever returned to a client, no service-account key ever leaves
 * the server, and neither is ever present in the Android artifact.
 *
 * Routes:
 *   GET    /health                 configuration + liveness, no secrets
 *   POST   /v1/media/upload-url    mint a presigned PUT for a server-made key
 *   POST   /v1/media/download-url  mint a presigned GET for an existing key
 *   GET    /v1/media/head?key=     object metadata, signed request to B2
 *   DELETE /v1/media?key=          delete, owner only
 *   POST   /v1/push/send           deliver a chat push for a stored message
 *   POST   /v1/groups              create a group, member list verified
 *   POST   /v1/groups/members/add add real accounts to a group
 *   POST   /v1/groups/members/remove remove one member
 *
 * Every /v1 route requires "Authorization: Bearer <Firebase ID token>".
 */

import { FirebaseTokenVerifier } from "./auth.ts";
import {
  assertChatKey,
  assertReadableKey,
  B2Client,
  buildChatObjectKey,
  buildObjectKey,
  canDeleteChatKey,
  canReadChatKey,
  isChatKeyShape,
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
import {
  createPushService,
  parsePushKind,
  PushError,
  PushService,
  type PushOutcome,
  type PushRequest,
} from "./push.ts";
import {
  createGroupService,
  GroupError,
  GroupService,
} from "./groups.ts";

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

function readOptionalString(body: Record<string, unknown>, field: string): string {
  const value = body[field];
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") {
    throw new MediaError(`Field "${field}" must be a string.`, 400);
  }
  return value;
}

function readStringArray(body: Record<string, unknown>, field: string): string[] {
  const value = body[field];
  if (!Array.isArray(value)) {
    throw new MediaError(`Field "${field}" must be an array of strings.`, 400);
  }
  const out: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") {
      throw new MediaError(`Field "${field}" must be an array of strings.`, 400);
    }
    out.push(item);
  }
  return out;
}

export interface HandlerDeps {
  now?: () => number;
  /**
   * The B2 client, injectable so the route-level authorization decisions can be
   * tested without a bucket. Production always uses the real one; the seam
   * exists so a test can prove a refused request never reaches the client at
   * all, which is the property that actually matters here.
   */
  client?: B2Client;
  /**
   * The push service, injectable for the same reason: the membership and
   * sender rules can be tested with no Firebase project and no credentials.
   */
  push?: PushService;
  /**
   * The group service, injectable for the same reason: the real-account and
   * admin checks can be tested with no Firebase project and no credentials.
   */
  groups?: GroupService;
}

export function createHandler(settings: ServerSettings, deps: HandlerDeps = {}) {
  const client = deps.client ?? new B2Client(settings.b2);
  const verifier = settings.authRequired
    ? new FirebaseTokenVerifier({ projectId: settings.firebaseProjectId })
    : null;
  const now = deps.now ?? Date.now;
  // Built only when push is on, so a server without a service account does not
  // even parse a key it will never use.
  const push = deps.push ??
    (settings.push.enabled ? createPushService(settings.push, { now }) : null);
  // Built only when groups are on, so a server without a service account does
  // not even parse a key it will never use.
  const groups = deps.groups ??
    (settings.groups.enabled ? createGroupService(settings.groups, { now }) : null);

  function requireGroups(): GroupService {
    // Refused with 503 rather than a fake success. A client told "created"
    // when nothing was written would show a group that does not exist, and the
    // membership it believes it stored was never checked against a real account.
    if (groups === null) {
      throw new GroupError("Group writes are not enabled on this server.", 503);
    }
    return groups;
  }

  async function authenticate(request: Request): Promise<string> {
    if (verifier === null) return settings.devUid;
    const user = await verifier.verify(request.headers.get("authorization"));
    return user.uid;
  }

  /**
   * Resolves a client-supplied key to a validated one and enforces who may
   * reach it.
   *
   * A user object keeps the existing rule: any authenticated caller may read it,
   * as before. A chat object is readable only by the two uids in its own key,
   * which is what stops one member of a conversation from reading another
   * conversation's media. The delete rule is stricter still and lives in
   * B2Client.deleteObject, where it cannot be forgotten.
   */
  function assertReadableByCaller(rawKey: string, uid: string): string {
    if (isChatKeyShape(rawKey, settings.b2.objectPrefix)) {
      const chatKey = assertChatKey(rawKey, settings.b2.objectPrefix);
      if (!canReadChatKey(chatKey, uid)) {
        throw new MediaError("You do not have access to this media.", 403);
      }
      return rawKey.trim();
    }
    return assertReadableKey(rawKey, settings.b2.objectPrefix);
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

        // A chat attachment is addressed by the authenticated caller and the
        // peer it is going to. The kind then has to match the content type, so
        // "photo" cannot be used to park a video or an arbitrary document.
        const chatKind = body.chatKind;
        if (chatKind !== undefined && chatKind !== null) {
          if (typeof chatKind !== "string") {
            throw new MediaError("Field \"chatKind\" must be a string.", 400);
          }
          const contentType = normalizeContentType(readString(body, "contentType"));
          const sizeBytes = readOptionalPositiveInt(
            body,
            "sizeBytes",
            Number.MAX_SAFE_INTEGER,
          );
          if (sizeBytes !== undefined && sizeBytes > settings.b2.maxChatUploadBytes) {
            throw new MediaError(
              `The file is larger than the ${settings.b2.maxChatUploadBytes} byte limit.`,
              413,
            );
          }
          const expiresInSeconds = readOptionalPositiveInt(
            body,
            "expiresInSeconds",
            settings.b2.presignTtlSeconds,
          );

          const key = buildChatObjectKey({
            prefix: settings.b2.objectPrefix,
            uploaderUid: uid,
            peerUid: readString(body, "peerUid"),
            kind: chatKind,
            contentType,
            allowedContentTypes: settings.b2.allowedContentTypes,
            maxUploadBytes: settings.b2.maxChatUploadBytes,
            sizeBytes,
          });
          const presigned = await client.presignUpload({ key, contentType, expiresInSeconds });
          return jsonResponse(200, {
            ...presigned,
            maxUploadBytes: settings.b2.maxChatUploadBytes,
          }, cors);
        }

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
        const key = assertReadableByCaller(readString(body, "key"), uid);
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
        const key = assertReadableByCaller(rawKey, uid);
        const metadata = await client.headObject(key);
        if (metadata === null) throw new MediaError("No such media object.", 404);
        return jsonResponse(200, { found: true, ...metadata }, cors);
      }

      if (method === "POST" && url.pathname === "/v1/push/send") {
        // Refused with 503 rather than a fake success: a client that is told
        // "sent" when nothing was sent will not retry, and the notification is
        // lost silently.
        if (push === null || !push.enabled) {
          throw new PushError("Push delivery is not enabled on this server.", 503);
        }
        const body = await readJsonBody(request);
        const kind = parsePushKind(readString(body, "kind"));
        const pushRequest: PushRequest = {
          kind,
          // Both ids are path segments in a REST URL, so they are validated
          // before they are used for anything.
          conversationId: readString(body, "conversationId"),
          messageId: readString(body, "messageId"),
        };
        // uid is the verified token subject, never anything from the body.
        const outcome: PushOutcome = await push.send(uid, pushRequest);
        const response: Record<string, unknown> = {
          delivered: outcome.delivered,
          recipients: outcome.recipients,
          mutedRecipients: outcome.mutedRecipients,
        };
        if (outcome.skipped !== undefined) response["skipped"] = outcome.skipped;
        if (outcome.removedTokens > 0) response["removedTokens"] = outcome.removedTokens;
        return jsonResponse(200, response, cors);
      }

      if (method === "POST" && url.pathname === "/v1/groups") {
        const service = requireGroups();
        const body = await readJsonBody(request);
        const created = await service.createGroup(uid, {
          name: readString(body, "name"),
          description: readOptionalString(body, "description"),
          topic: readOptionalString(body, "topic"),
          memberIds: readStringArray(body, "memberIds"),
        });
        // uid is the verified token subject and is the creator; nothing in the
        // body can say who created the group.
        return jsonResponse(200, {
          groupId: created.id,
          creatorId: created.creatorId,
          memberIds: created.memberIds,
          adminIds: created.adminIds,
          memberCount: created.memberIds.length,
        }, cors);
      }

      if (method === "POST" && url.pathname === "/v1/groups/members/add") {
        const service = requireGroups();
        const body = await readJsonBody(request);
        const updated = await service.addMembers(
          uid,
          readString(body, "groupId"),
          readStringArray(body, "memberIds"),
        );
        return jsonResponse(200, {
          groupId: updated.id,
          memberIds: updated.memberIds,
          adminIds: updated.adminIds,
          memberCount: updated.memberIds.length,
        }, cors);
      }

      if (method === "POST" && url.pathname === "/v1/groups/members/remove") {
        const service = requireGroups();
        const body = await readJsonBody(request);
        const updated = await service.removeMember(
          uid,
          readString(body, "groupId"),
          readString(body, "memberId"),
        );
        return jsonResponse(200, {
          groupId: updated.id,
          memberIds: updated.memberIds,
          adminIds: updated.adminIds,
          memberCount: updated.memberIds.length,
        }, cors);
      }

      if (method === "DELETE" && url.pathname === "/v1/media") {
        const rawKey = url.searchParams.get("key");
        if (rawKey === null) throw new MediaError("Query parameter 'key' is required.", 400);
        // A chat object is deleted by its uploader alone. The receiver may hide
        // the message from its own list, but the sender's history still points
        // at the object, so the receiver must not be able to destroy it. The
        // same rule is re-checked inside deleteObject, so a future caller cannot
        // forget it; this is the check that makes the refusal observable before
        // any request is signed.
        const chat = isChatKeyShape(rawKey, settings.b2.objectPrefix)
          ? assertChatKey(rawKey, settings.b2.objectPrefix)
          : null;
        if (chat !== null && !canDeleteChatKey(chat, uid)) {
          throw new MediaError("You may only delete media you uploaded.", 403);
        }
        const key = chat !== null ? rawKey.trim() : assertReadableKey(rawKey, settings.b2.objectPrefix);
        await client.deleteObject(key, uid);
        return jsonResponse(200, { deleted: true, key }, cors);
      }

      throw new MediaError("Not found.", 404);
    } catch (error) {
      if (error instanceof MediaError) {
        return jsonResponse(error.status, { error: error.message }, cors);
      }
      if (error instanceof GroupError) {
        // A group rejection is a decision, not a fault: the message is written
        // for the caller and names no document, so it is safe to return as is.
        return jsonResponse(error.status, { error: error.message }, cors);
      }
      if (error instanceof PushError) {
        // A Firestore or FCM transport failure is logged with its detail and
        // answered with a fixed message: the upstream body can contain a
        // project path or a token, and neither belongs in a response.
        if (error.status >= 500) console.error("Push delivery failed:", error.message);
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
    exitOnConfigError(error);
  }

  let handler: (request: Request) => Promise<Response>;
  try {
    // createHandler builds the push service, so a malformed service-account key
    // is caught here as a startup error rather than at the first message.
    handler = createHandler(settings);
  } catch (error) {
    exitOnConfigError(error);
  }
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

/** A configuration error is fatal and explained; anything else is rethrown. */
function exitOnConfigError(error: unknown): never {
  if (error instanceof ConfigError) {
    console.error(`Configuration error: ${error.message}`);
    console.error(
      "Copy server/.env.example to server/.env, fill it in, and keep that file out of git.",
    );
    Deno.exit(78); // EX_CONFIG
  }
  throw error;
}

if (import.meta.main) {
  main();
}
