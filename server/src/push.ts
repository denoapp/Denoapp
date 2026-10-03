/**
 * FCM chat push delivery.
 *
 * Firestore has no push channel and this service has no database, so somebody
 * has to tell the far side that a message exists. The client does that by
 * calling POST /v1/push/send once its write is committed, and this module turns
 * that request into an actual FCM send.
 *
 * The design rule that matters: the client is never trusted. A request names
 * only `{kind, conversationId, messageId}`. The stored message is re-read
 * here, the caller must be the message's own sender, the recipients are
 * derived from the conversation's own membership, and the preview text is read
 * from the stored message rather than accepted from the caller. A client that
 * lies about what it sent gets a refusal or an empty fan-out, never somebody
 * else's notification.
 *
 * Zero npm dependencies, like the rest of the service: service-account signing,
 * the Firestore REST reads and the FCM HTTP v1 call are all done on WebCrypto
 * and fetch, so no third-party code can reach the service-account key.
 */

import { ConfigError } from "./config.ts";

/** A route-level failure with an HTTP status. Never carries a secret. */
export class PushError extends Error {
  override name = "PushError";
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

export const PUSH_KIND_DIRECT = "direct";
export const PUSH_KIND_GROUP = "group";
export type PushKind = typeof PUSH_KIND_DIRECT | typeof PUSH_KIND_GROUP;

/** Firestore paths this module reads, spelled out once. */
const USERS = "users";
const CHATS = "chats";
const GROUPS = "groups";
const MESSAGES = "messages";
const DEVICES = "devices";
/**
 * Per-user conversation settings. Batch 8 owns this document; the push layer
 * only reads it, so a mute set in the app silences the notification without the
 * push code needing to know anything about the mute UI.
 */
const CHAT_SETTINGS = "chatSettings";
const MUTED_CONVERSATIONS = "mutedConversations";

/** Bounds the work one request can cause. */
const MAX_ID_LENGTH = 128;
const MAX_PREVIEW_LENGTH = 140;
const MAX_DEVICES_PER_USER = 20;
const MAX_GROUP_MEMBERS = 500;

const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FIRESTORE_API = "https://firestore.googleapis.com/v1";
const FCM_API = "https://fcm.googleapis.com/v1";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
/** Google rejects a service-account assertion with a longer life than this. */
const ASSERTION_TTL_SECONDS = 3600;
/** Refreshed this long before expiry so an in-flight request cannot expire. */
const TOKEN_REFRESH_MARGIN_SECONDS = 60;

// ---------------------------------------------------------------------------
// Ports: what the delivery logic needs, so it can be tested without a network.
// ---------------------------------------------------------------------------

export interface FirestoreDocument {
  /** Full resource name, e.g. `projects/p/databases/(default)/documents/chats/x`. */
  name: string;
  fields: Record<string, unknown>;
  /**
   * The document's `updateTime`, when the transport reports one. A caller that
   * reads, decides and then writes passes it back as `expectUpdateTime` so the
   * write cannot land on a document that moved underneath it.
   */
  updateTime?: string;
}

export interface FirestoreGateway {
  /** A missing document is null, not an error. */
  getDocument(path: string): Promise<FirestoreDocument | null>;
  listDocuments(path: string): Promise<FirestoreDocument[]>;
  deleteDocument(path: string): Promise<void>;
}

/**
 * A write, addressed by document path.
 *
 * `fieldMask` is the set of field paths the write may touch. Firestore replaces
 * exactly those fields and leaves everything else alone, so a caller that
 * computes a new member list can never clobber a field it did not mean to
 * change - a name edit cannot blank a group's last message.
 */
export interface FirestoreWrite {
  /** Document path, e.g. `groups/x`. A trailing `-` asks Firestore to mint the id. */
  path: string;
  fields: Record<string, unknown>;
  /** Field paths to replace. Omitted means "create the document whole". */
  fieldMask?: string[];
  /**
   * Guard on the document's current state. `exists: false` is the
   * create-if-absent precondition: two concurrent creates of the same id, or a
   * create racing somebody else's, fails instead of overwriting.
   */
  expectExists?: boolean;
  /**
   * The document's `updateTime` the write was computed against. Firestore
   * refuses the write if the document has changed since, which is what stops
   * two admins adding members at once from silently losing one of the two
   * additions.
   */
  expectUpdateTime?: string;
}

/** A write capability, kept separate so a read-only consumer need not implement it. */
export interface FirestoreWriter {
  /** Applies one write and returns the committed document, or null if it was deleted. */
  commitDocument(write: FirestoreWrite): Promise<FirestoreDocument | null>;
}

export type SendResult = "delivered" | "unregistered" | "retry";

export interface FcmGateway {
  send(token: string, data: Record<string, string>): Promise<SendResult>;
}

export interface PushSettings {
  /** False means the route refuses to do anything at all. */
  readonly enabled: boolean;
  /** Raw service-account JSON, kept verbatim and parsed on use. */
  readonly serviceAccountJson: string;
}

export interface PushRequest {
  kind: PushKind;
  conversationId: string;
  messageId: string;
}

export interface PushOutcome {
  /** Devices FCM accepted. */
  delivered: number;
  /** Recipient accounts that were addressed. */
  recipients: number;
  /** Recipients whose conversation is muted (delivered silently). */
  mutedRecipients: number;
  /** Why nothing was sent, when nothing was. */
  skipped?: string;
  /** Device registrations deleted because FCM called them dead. */
  removedTokens: number;
}

// ---------------------------------------------------------------------------
// Small pure helpers, exported so the rules can be tested directly.
// ---------------------------------------------------------------------------

/**
 * A Firestore path segment supplied by a client. Anything that could change the
 * shape of the path is refused, because this value is interpolated straight into
 * a REST URL.
 */
export function assertDocumentId(value: string, field: string): string {
  const trimmed = value.trim();
  if (trimmed === "") {
    throw new PushError(`Field "${field}" is required.`, 400);
  }
  if (trimmed.length > MAX_ID_LENGTH || !/^[A-Za-z0-9_-]+$/.test(trimmed)) {
    throw new PushError(`Field "${field}" is not a valid id.`, 400);
  }
  return trimmed;
}

export function parsePushKind(value: string): PushKind {
  if (value === PUSH_KIND_DIRECT || value === PUSH_KIND_GROUP) return value;
  throw new PushError(`Field "kind" must be "${PUSH_KIND_DIRECT}" or "${PUSH_KIND_GROUP}".`, 400);
}

/**
 * The 1-to-1 conversation id, identical to the client's
 * `sorted(a, b).joinToString("_")`. Recomputed here from the stored message's
 * own participants, so a client cannot point the service at a different chat.
 */
export function canonicalPairId(firstUid: string, secondUid: string): string {
  return [firstUid, secondUid].sort().join("_");
}

/**
 * What a notification shows when the message has no text. Derived from the
 * stored media kind, so a voice note never previews as an empty body.
 */
export function attachmentPreview(mediaKind: string): string {
  switch (mediaKind.trim().toLowerCase()) {
    case "photo":
    case "image":
      return "Photo";
    case "video":
      return "Video";
    case "voice":
    case "audio":
      return "Voice message";
    case "file":
      return "File";
    default:
      return "Sent an attachment";
  }
}

/** The notification body: the stored text, shortened to a sane length. */
export function messagePreview(text: string, mediaKind: string): string {
  const trimmed = text.trim();
  if (trimmed === "") return attachmentPreview(mediaKind);
  // Collapsed so a wall of newlines cannot push the notification taller than
  // the shade allows.
  const collapsed = trimmed.replace(/\s+/g, " ");
  return collapsed.length <= MAX_PREVIEW_LENGTH
    ? collapsed
    : `${collapsed.slice(0, MAX_PREVIEW_LENGTH - 1)}…`;
}

/** The data payload. Every value is a string, as FCM requires. */
export function buildPushData(input: {
  kind: PushKind;
  conversationId: string;
  targetId: string;
  title: string;
  body: string;
  messageId: string;
  muted: boolean;
}): Record<string, string> {
  return {
    kind: input.kind,
    conversationId: input.conversationId,
    targetId: input.targetId,
    title: input.title,
    body: input.body,
    messageId: input.messageId,
    muted: input.muted ? "true" : "false",
  };
}

export function isMuted(settings: Record<string, unknown> | null, conversationId: string): boolean {
  if (settings === null) return false;
  const muted = settings[MUTED_CONVERSATIONS];
  if (!Array.isArray(muted)) return false;
  return muted.some((entry) => entry === conversationId);
}

function readString(doc: Record<string, unknown> | null, field: string): string {
  if (doc === null) return "";
  const value = doc[field];
  return typeof value === "string" ? value : "";
}

function readStringList(doc: Record<string, unknown> | null, field: string): string[] {
  if (doc === null) return [];
  const value = doc[field];
  if (!Array.isArray(value)) return [];
  return value.filter((entry): entry is string => typeof entry === "string" && entry !== "");
}

// ---------------------------------------------------------------------------
// Delivery.
// ---------------------------------------------------------------------------

export interface PushServiceDeps {
  firestore: FirestoreGateway;
  fcm: FcmGateway;
  /** Injected for deterministic tests. */
  now?: () => number;
}

/**
 * A message the service is allowed to announce, with its recipients and its
 * notification text already derived from what Firestore actually holds.
 */
interface ResolvedTarget {
  recipients: string[];
  kind: PushKind;
  conversationId: string;
  /** What a tap opens: the peer uid, or the group id. */
  targetId: string;
  title: string;
  body: string;
  messageId: string;
}

export class PushService {
  readonly #settings: PushSettings;
  readonly #firestore: FirestoreGateway;
  readonly #fcm: FcmGateway;

  constructor(settings: PushSettings, deps: PushServiceDeps) {
    this.#settings = settings;
    this.#firestore = deps.firestore;
    this.#fcm = deps.fcm;
  }

  get enabled(): boolean {
    return this.#settings.enabled;
  }

  /**
   * Sends the notification for an already-stored message.
   *
   * Every failure that is not a caller error is a 503 rather than a success
   * with a zero count: a message reported as "not delivered because we could not
   * ask" and one reported as "not delivered because the recipient has no
   * device" must not look the same to the caller.
   */
  async send(senderUid: string, request: PushRequest): Promise<PushOutcome> {
    if (!this.#settings.enabled) {
      throw new PushError("Push delivery is not enabled on this server.", 503);
    }
    const sender = assertDocumentId(senderUid, "uid");
    const conversationId = assertDocumentId(request.conversationId, "conversationId");
    const messageId = assertDocumentId(request.messageId, "messageId");

    const resolved = request.kind === PUSH_KIND_GROUP
      ? await this.#resolveGroup(sender, conversationId, messageId)
      : await this.#resolveDirect(sender, conversationId, messageId);
    if (resolved === null) {
      // An empty but successful result: nothing to address. Not an error.
      return { delivered: 0, recipients: 0, mutedRecipients: 0, removedTokens: 0 };
    }

    let delivered = 0;
    let mutedRecipients = 0;
    let removedTokens = 0;
    for (const uid of resolved.recipients) {
      const muted = isMuted(
        await this.#read(`${CHAT_SETTINGS}/${uid}`),
        conversationId,
      );
      if (muted) mutedRecipients += 1;
      const data = buildPushData({ ...resolved, muted });

      for (const device of await this.#devicesFor(uid)) {
        let result: SendResult;
        try {
          result = await this.#fcm.send(device.token, data);
        } catch (error) {
          // A credentials or network problem is not a per-device outcome: the
          // caller must be told the service could not do its job.
          console.error("Push send failed:", error);
          throw new PushError("Push delivery is temporarily unavailable.", 503);
        }
        if (result === "delivered") {
          delivered += 1;
          continue;
        }
        if (result === "unregistered") {
          // FCM says this token will never work again (uninstalled, or the
          // token was replaced). Deleting it here is what stops the service
          // from re-sending to a dead token on every message.
          removedTokens += 1;
          try {
            await this.#firestore.deleteDocument(device.path);
          } catch (error) {
            // The send already happened; a failed cleanup is logged, not fatal.
            console.error("Unable to delete a dead push token:", error);
          }
        }
      }
    }

    return { delivered, recipients: resolved.recipients.length, mutedRecipients, removedTokens };
  }

  /**
   * A 1-to-1 message. The recipients are derived from the stored message's own
   * two participants, and the conversation id is recomputed from them, so the
   * only thing a client chooses is which of its own messages to announce.
   */
  async #resolveDirect(
    senderUid: string,
    conversationId: string,
    messageId: string,
  ): Promise<ResolvedTarget | null> {
    const message = await this.#read(`${CHATS}/${conversationId}/${MESSAGES}/${messageId}`);
    if (message === null) return null;
    // The caller's own message, or nobody's.
    if (readString(message, "senderId") !== senderUid) {
      throw new PushError("You can only send a notification for your own message.", 403);
    }
    // A withdrawn message must not announce itself.
    if (message["unsent"] === true) return null;

    const receiverId = readString(message, "receiverId");
    if (receiverId === "" || receiverId === senderUid) {
      throw new PushError("That message has no usable recipient.", 409);
    }
    // The id must be the pair the stored message actually belongs to. A client
    // pointing at some other pair gets a silent no-op instead of a fan-out.
    if (canonicalPairId(senderUid, receiverId) !== conversationId) return null;

    const senderName = readString(await this.#read(`${USERS}/${senderUid}`), "fullName");
    return {
      recipients: [receiverId],
      kind: PUSH_KIND_DIRECT,
      conversationId,
      targetId: receiverId,
      title: senderName.trim() === "" ? "New message" : senderName.trim(),
      body: messagePreview(readString(message, "text"), readString(message, "mediaKind")),
      messageId,
    };
  }

  /**
   * A group message. The recipient list is the group document's own membership
   * with the caller removed, so a member removed since the write is not sent
   * anything, and the sender never notifies themselves.
   */
  async #resolveGroup(
    senderUid: string,
    conversationId: string,
    messageId: string,
  ): Promise<ResolvedTarget | null> {
    const group = await this.#read(`${GROUPS}/${conversationId}`);
    if (group === null) return null;
    const members = readStringList(group, "memberIds").slice(0, MAX_GROUP_MEMBERS);
    // Not a member: the caller may not announce anything to this group.
    if (!members.includes(senderUid)) {
      throw new PushError("You are not a member of this group.", 403);
    }

    const message = await this.#read(`${GROUPS}/${conversationId}/${MESSAGES}/${messageId}`);
    if (message === null) return null;
    if (readString(message, "senderId") !== senderUid) {
      throw new PushError("You can only send a notification for your own message.", 403);
    }
    if (message["unsent"] === true) return null;

    const name = readString(group, "name").trim();
    return {
      recipients: members.filter((uid) => uid !== senderUid),
      kind: PUSH_KIND_GROUP,
      conversationId,
      targetId: conversationId,
      title: name === "" ? "Group chat" : name,
      body: messagePreview(readString(message, "text"), readString(message, "mediaKind")),
      messageId,
    };
  }

  /** A read that turns any transport failure into a 503, never a false zero. */
  async #read(path: string): Promise<Record<string, unknown> | null> {
    let document: FirestoreDocument | null;
    try {
      document = await this.#firestore.getDocument(path);
    } catch (error) {
      console.error(`Firestore read failed for ${path}:`, error);
      throw new PushError("Could not read the conversation to deliver this notification.", 503);
    }
    return document?.fields ?? null;
  }

  /** The live tokens for one account, newest first, bounded per account. */
  async #devicesFor(uid: string): Promise<{ path: string; token: string }[]> {
    let documents: FirestoreDocument[];
    try {
      documents = await this.#firestore.listDocuments(`${USERS}/${uid}/${DEVICES}`);
    } catch (error) {
      console.error(`Firestore device list failed for ${uid}:`, error);
      throw new PushError("Could not read the recipient's devices.", 503);
    }

    const seen = new Set<string>();
    const devices: { path: string; token: string }[] = [];
    for (const document of documents) {
      if (devices.length >= MAX_DEVICES_PER_USER) break;
      const token = readString(document.fields, "token").trim();
      if (token === "" || seen.has(token)) continue;
      seen.add(token);
      devices.push({ path: relativeDocumentPath(document.name), token });
    }
    return devices;
  }
}

/** `projects/../documents/chats/x` -> `chats/x`, which is what a delete takes. */
export function relativeDocumentPath(resourceName: string): string {
  const marker = "/documents/";
  const index = resourceName.indexOf(marker);
  return index === -1 ? resourceName : resourceName.slice(index + marker.length);
}

// ---------------------------------------------------------------------------
// Service-account signing and the two Google APIs, over WebCrypto.
// ---------------------------------------------------------------------------

const encoder = new TextEncoder();

type Bytes = Uint8Array<ArrayBuffer>;

function base64UrlFromBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlFromString(value: string): string {
  return base64UrlFromBytes(encoder.encode(value));
}

function base64ToBytes(value: string): Bytes {
  const binary = atob(value);
  const out = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

export interface ServiceAccountCredential {
  clientEmail: string;
  privateKeyPem: string;
  projectId: string;
}

const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PROJECT_PATTERN = /^[a-z0-9][a-z0-9-]{4,29}[a-z0-9]$/;

/**
 * Parses the service-account JSON. Only the three fields this module signs
 * with are kept, and a malformed or placeholder document is a configuration
 * error naming the variable - never a value.
 */
export function parseServiceAccountJson(raw: string): ServiceAccountCredential {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new ConfigError(
      "FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON. Paste the whole file " +
        "as a single line, or point the variable at the mounted file contents.",
    );
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new ConfigError("FIREBASE_SERVICE_ACCOUNT_JSON must be a JSON object.");
  }
  const record = parsed as Record<string, unknown>;
  const clientEmail = record["client_email"];
  const privateKeyPem = record["private_key"];
  const projectId = record["project_id"];

  if (typeof clientEmail !== "string" || !EMAIL_PATTERN.test(clientEmail)) {
    throw new ConfigError('FIREBASE_SERVICE_ACCOUNT_JSON is missing a valid "client_email".');
  }
  if (typeof privateKeyPem !== "string" || !privateKeyPem.includes("BEGIN PRIVATE KEY")) {
    throw new ConfigError(
      'FIREBASE_SERVICE_ACCOUNT_JSON is missing a valid "private_key". The \\n in the ' +
        "key must survive as real newlines.",
    );
  }
  if (typeof projectId !== "string" || !PROJECT_PATTERN.test(projectId)) {
    throw new ConfigError('FIREBASE_SERVICE_ACCOUNT_JSON is missing a valid "project_id".');
  }
  return { clientEmail, privateKeyPem, projectId };
}

export interface AccessTokenProvider {
  token(): Promise<string>;
}

/**
 * Firestore's own OAuth scope, used by the group service that writes group
 * documents. It is separate from [FCM_SCOPE] because the messaging scope says
 * nothing about Firestore, and a token minted for one API is not accepted by
 * the other.
 */
export const FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore";

/**
 * Mints and caches an OAuth access token for the service account.
 *
 * A Google-signed JWT assertion is exchanged for a short-lived access token; the
 * assertion itself is never used as a bearer credential. The token is cached
 * until shortly before it expires, and concurrent callers share one exchange.
 *
 * The scope is per instance and defaults to [FCM_SCOPE], so the push path keeps
 * exactly the credential it had before a caller that needs Firestore started
 * asking for its own.
 */
export class GoogleAccessTokenProvider implements AccessTokenProvider {
  readonly #credential: ServiceAccountCredential;
  readonly #fetch: typeof fetch;
  readonly #now: () => number;
  readonly #scope: string;
  #key: Promise<CryptoKey> | null = null;
  #token: { value: string; expiresAtSeconds: number } | null = null;
  #inFlight: Promise<string> | null = null;

  constructor(
    credential: ServiceAccountCredential,
    options: { fetchImpl?: typeof fetch; now?: () => number; scope?: string } = {},
  ) {
    this.#credential = credential;
    this.#fetch = options.fetchImpl ?? fetch;
    this.#now = options.now ?? Date.now;
    this.#scope = options.scope ?? FCM_SCOPE;
  }

  async token(): Promise<string> {
    const nowSeconds = Math.floor(this.#now() / 1000);
    const cached = this.#token;
    if (cached !== null && cached.expiresAtSeconds - TOKEN_REFRESH_MARGIN_SECONDS > nowSeconds) {
      return cached.value;
    }
    // One exchange at a time: a burst of messages must not spend a round trip
    // per recipient on the same assertion.
    if (this.#inFlight !== null) return this.#inFlight;
    const task = this.#exchange()
      .then((result) => {
        this.#token = result;
        return result.value;
      })
      .finally(() => {
        if (this.#inFlight === task) this.#inFlight = null;
      });
    this.#inFlight = task;
    return task;
  }

  async #exchange(): Promise<{ value: string; expiresAtSeconds: number }> {
    const issuedAt = Math.floor(this.#now() / 1000);
    const header = base64UrlFromString(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const claims = base64UrlFromString(
      JSON.stringify({
        iss: this.#credential.clientEmail,
        scope: this.#scope,
        aud: OAUTH_TOKEN_URL,
        iat: issuedAt,
        exp: issuedAt + ASSERTION_TTL_SECONDS,
      }),
    );
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      await this.#signingKey(),
      encoder.encode(`${header}.${claims}`),
    );
    const assertion = `${header}.${claims}.${base64UrlFromBytes(new Uint8Array(signature))}`;

    let response: Response;
    try {
      response = await this.#fetch(OAUTH_TOKEN_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion,
        }).toString(),
      });
    } catch (error) {
      console.error("Could not reach the Google token endpoint:", error);
      throw new PushError("Push delivery is temporarily unavailable.", 503);
    }
    if (!response.ok) {
      // The body can echo the assertion, so it is logged only as a status.
      console.error(`Google token endpoint returned ${response.status}.`);
      throw new PushError("Push delivery is not authorized.", 503);
    }
    const body = (await response.json()) as Record<string, unknown>;
    const value = body["access_token"];
    const expiresIn = body["expires_in"];
    if (typeof value !== "string" || value === "") {
      throw new PushError("Push delivery is not authorized.", 503);
    }
    const lifetime = typeof expiresIn === "number" && expiresIn > 0 ? expiresIn : 3600;
    return { value, expiresAtSeconds: issuedAt + lifetime };
  }

  #signingKey(): Promise<CryptoKey> {
    if (this.#key === null) {
      this.#key = importPrivateKey(this.#credential.privateKeyPem);
    }
    return this.#key;
  }
}

/** PKCS#8 PEM to a signing key. */
export function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  if (body === "") {
    throw new ConfigError("FIREBASE_SERVICE_ACCOUNT_JSON has an empty private key.");
  }
  return crypto.subtle.importKey(
    "pkcs8",
    base64ToBytes(body),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Decodes one Firestore REST field map into plain values. */
export function decodeFirestoreFields(
  fields: Record<string, unknown> | undefined,
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [name, raw] of Object.entries(fields ?? {})) {
    out[name] = decodeFirestoreValue(raw);
  }
  return out;
}

function decodeFirestoreValue(raw: unknown): unknown {
  if (raw === null || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  if (typeof value["stringValue"] === "string") return value["stringValue"];
  if (typeof value["booleanValue"] === "boolean") return value["booleanValue"];
  if (typeof value["integerValue"] === "string") return Number(value["integerValue"]);
  if (typeof value["doubleValue"] === "number") return value["doubleValue"];
  if (typeof value["timestampValue"] === "string") return value["timestampValue"];
  if (value["nullValue"] === null) return null;
  if ("arrayValue" in value) {
    const array = value["arrayValue"] as { values?: unknown[] } | null;
    return (array?.values ?? []).map(decodeFirestoreValue);
  }
  if ("mapValue" in value) {
    const map = value["mapValue"] as { fields?: Record<string, unknown> } | null;
    return decodeFirestoreFields(map?.fields);
  }
  return null;
}

/** The Firestore REST reads this module needs, authorized with the token. */
export class FirestoreRestGateway implements FirestoreGateway, FirestoreWriter {
  readonly #projectId: string;
  readonly #tokens: AccessTokenProvider;
  readonly #fetch: typeof fetch;

  constructor(
    projectId: string,
    tokens: AccessTokenProvider,
    options: { fetchImpl?: typeof fetch } = {},
  ) {
    this.#projectId = projectId;
    this.#tokens = tokens;
    this.#fetch = options.fetchImpl ?? fetch;
  }

  #url(path: string): string {
    return `${FIRESTORE_API}/projects/${this.#projectId}/databases/(default)/documents/${path}`;
  }

  async #headers(): Promise<Record<string, string>> {
    return { authorization: `Bearer ${await this.#tokens.token()}`, accept: "application/json" };
  }

  async getDocument(path: string): Promise<FirestoreDocument | null> {
    let response: Response;
    try {
      response = await this.#fetch(this.#url(path), { method: "GET", headers: await this.#headers() });
    } catch (error) {
      throw new FirestoreTransportError(String(error));
    }
    // A document that does not exist is a normal answer, not a failure.
    if (response.status === 404) return null;
    if (!response.ok) {
      throw new FirestoreTransportError(`Firestore returned ${response.status} for ${path}.`);
    }
    const body = (await response.json()) as {
      name?: string;
      fields?: Record<string, unknown>;
      updateTime?: string;
    };
    return {
      name: body.name ?? path,
      fields: decodeFirestoreFields(body.fields),
      ...(body.updateTime === undefined ? {} : { updateTime: body.updateTime }),
    };
  }

  async listDocuments(path: string): Promise<FirestoreDocument[]> {
    const documents: FirestoreDocument[] = [];
    let pageToken: string | undefined;
    do {
      const url = new URL(`${this.#url(path)}/:listDocuments`);
      url.searchParams.set("pageSize", "200");
      if (pageToken !== undefined) url.searchParams.set("pageToken", pageToken);
      let response: Response;
      try {
        response = await this.#fetch(url.toString(), {
          method: "GET",
          headers: await this.#headers(),
        });
      } catch (error) {
        throw new FirestoreTransportError(String(error));
      }
      if (!response.ok) {
        throw new FirestoreTransportError(`Firestore returned ${response.status} for ${path}.`);
      }
      const body = (await response.json()) as {
        documents?: Array<{ name?: string; fields?: Record<string, unknown> }>;
        nextPageToken?: string;
      };
      for (const document of body.documents ?? []) {
        documents.push({
          name: document.name ?? "",
          fields: decodeFirestoreFields(document.fields),
        });
      }
      pageToken = typeof body.nextPageToken === "string" && body.nextPageToken !== ""
        ? body.nextPageToken
        : undefined;
    } while (pageToken !== undefined && documents.length < 1000);
    return documents;
  }

  async deleteDocument(path: string): Promise<void> {
    try {
      const response = await this.#fetch(this.#url(path), {
        method: "DELETE",
        headers: await this.#headers(),
      });
      // 404 means somebody else already removed it: the goal is reached.
      if (!response.ok && response.status !== 404) {
        throw new FirestoreTransportError(`Firestore returned ${response.status} for ${path}.`);
      }
    } catch (error) {
      if (error instanceof FirestoreTransportError) throw error;
      throw new FirestoreTransportError(String(error));
    }
  }

  async commitDocument(write: FirestoreWrite): Promise<FirestoreDocument | null> {
    // A path ending in `-` asks Firestore to mint the id, and that create has to
    // go through `createDocument` rather than `:commit`.
    //
    // `:commit` answers a write to a `-` path with `updateTime` and no
    // `document`, so the generated id is never echoed back and there is nothing
    // to parse. It also refuses the combination outright: `currentDocument` is a
    // precondition about a document that exists, and a `-` path has none, so
    // `currentDocument.exists: false` is a 400 rather than a create-if-absent
    // guard.
    //
    // `createDocument` mints the id and returns the whole stored document, name
    // included, which is the only reason the caller can learn which id it got.
    // Create-if-absent still holds without a precondition: Firestore generated
    // this id, so there is nothing for the write to overwrite.
    const cleanPath = write.path.replace(/^\/+|\/+$/g, "");
    if (cleanPath.endsWith("/-")) {
      return await this.#createDocument(cleanPath.slice(0, -2), write.fields);
    }
    const document: Record<string, unknown> = {
      name: this.#documentName(write.path),
      fields: encodeFirestoreFields(write.fields),
    };
    const entry: Record<string, unknown> = { update: document };
    if (write.fieldMask !== undefined) {
      entry["updateMask"] = { fieldPaths: write.fieldMask };
    }
    if (write.expectExists !== undefined || write.expectUpdateTime !== undefined) {
      const currentDocument: Record<string, unknown> = {};
      if (write.expectExists !== undefined) currentDocument["exists"] = write.expectExists;
      if (write.expectUpdateTime !== undefined) {
        currentDocument["updateTime"] = write.expectUpdateTime;
      }
      entry["currentDocument"] = currentDocument;
    }

    let response: Response;
    try {
      response = await this.#fetch(
        `${this.#url("")}:commit`,
        {
          method: "POST",
          headers: { ...(await this.#headers()), "content-type": "application/json" },
          body: JSON.stringify({ writes: [entry] }),
        },
      );
    } catch (error) {
      throw new FirestoreTransportError(String(error));
    }
    if (response.status === 409 || response.status === 412) {
      // A failed precondition is not a transport fault: the caller's assumption
      // about the current document was wrong. It is reported as such so the
      // caller can re-read and decide, rather than retrying blindly.
      throw new FirestorePreconditionError(`Firestore refused the write: ${response.status}`);
    }
    if (!response.ok) {
      throw new FirestoreTransportError(`Firestore returned ${response.status} on commit.`);
    }
    const body = (await response.json()) as {
      writeResults?: Array<{
        updateTime?: string;
        exists?: boolean;
        document?: { name?: string; fields?: Record<string, unknown> };
      }>;
    };
    const result = body.writeResults?.[0];
    if (result === undefined) {
      throw new FirestoreTransportError("Firestore committed nothing.");
    }
    if (result.exists === false) {
      // The write matched a deleted document and so changed nothing.
      return null;
    }
    const name = document["name"] as string;
    const stored = result.document;
    if (stored === undefined) {
      return { name, fields: write.fields };
    }
    return {
      name: stored.name ?? name,
      fields: decodeFirestoreFields(stored.fields),
    };
  }

  /**
   * Creates a document under `collectionPath` with an id minted by Firestore,
   * and returns the stored document including the id it was given.
   */
  async #createDocument(
    collectionPath: string,
    fields: Record<string, unknown>,
  ): Promise<FirestoreDocument | null> {
    let response: Response;
    try {
      response = await this.#fetch(this.#url(collectionPath), {
        method: "POST",
        headers: { ...(await this.#headers()), "content-type": "application/json" },
        body: JSON.stringify({ fields: encodeFirestoreFields(fields) }),
      });
    } catch (error) {
      throw new FirestoreTransportError(String(error));
    }
    if (response.status === 404) return null;
    if (!response.ok) {
      throw new FirestoreTransportError(
        `Firestore returned ${response.status} creating a document in ${collectionPath}.`,
      );
    }
    const body = (await response.json()) as {
      name?: string;
      fields?: Record<string, unknown>;
      updateTime?: string;
    };
    return {
      name: body.name ?? "",
      fields: decodeFirestoreFields(body.fields),
      ...(body.updateTime === undefined ? {} : { updateTime: body.updateTime }),
    };
  }

  /**
   * The full resource name for a document path.
   *
   * Firestore mints a document id when the last segment is `-`, so a create can
   * ask the server for an id instead of trusting a client's clock or counter.
   */
  #documentName(path: string): string {
    const clean = path.replace(/^\/+|\/+$/g, "");
    return `${FIRESTORE_API}/projects/${this.#projectId}/databases/(default)/documents/${clean}`;
  }
}

/** Encodes plain values into a Firestore REST field map. */
export function encodeFirestoreFields(
  fields: Record<string, unknown>,
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [name, value] of Object.entries(fields)) {
    out[name] = encodeFirestoreValue(value);
  }
  return out;
}

function encodeFirestoreValue(value: unknown): unknown {
  if (value === null) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    return Number.isInteger(value)
      ? { integerValue: String(value) }
      : { doubleValue: value };
  }
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map((item) => encodeFirestoreValue(item)) } };
  }
  if (typeof value === "object") {
    return { mapValue: { fields: encodeFirestoreFields(value as Record<string, unknown>) } };
  }
  throw new FirestoreTransportError("A value cannot be stored in Firestore.");
}

/** A failed precondition: the document was not in the state the write required. */
export class FirestorePreconditionError extends Error {
  override name = "FirestorePreconditionError";
}

/** Any Firestore access failure. The message never contains a response body. */
export class FirestoreTransportError extends Error {
  override name = "FirestoreTransportError";
}

/** FCM HTTP v1, one message per call. */
export class FcmRestGateway implements FcmGateway {
  readonly #projectId: string;
  readonly #tokens: AccessTokenProvider;
  readonly #fetch: typeof fetch;

  constructor(
    projectId: string,
    tokens: AccessTokenProvider,
    options: { fetchImpl?: typeof fetch } = {},
  ) {
    this.#projectId = projectId;
    this.#tokens = tokens;
    this.#fetch = options.fetchImpl ?? fetch;
  }

  async send(token: string, data: Record<string, string>): Promise<SendResult> {
    const response = await this.#fetch(
      `${FCM_API}/projects/${this.#projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          authorization: `Bearer ${await this.#tokens.token()}`,
          "content-type": "application/json",
        },
        // Data-only. The app builds and owns the notification, which is what
        // lets a muted conversation go to the silent channel and a tap route
        // anywhere, instead of being fixed by the system shade.
        body: JSON.stringify({ message: { token, data } }),
      },
    );
    if (response.ok) return "delivered";

    // Only the status and a code are used: the body can echo the token, so it
    // is read for classification and never logged.
    const body = await response.text().catch(() => "");
    return classifySendFailure(response.status, body);
  }
}

/**
 * Decides what a failed FCM call means.
 *
 * `unregistered` is the only permanent outcome: FCM will never accept that
 * token again, so the device document is deleted rather than retried forever.
 * Everything else is a `retry` and leaves the registration in place, because
 * deleting a live token on a transient error is how a user silently stops
 * receiving messages.
 */
export function classifySendFailure(status: number, body: string): SendResult {
  const upper = body.toUpperCase();
  if (status === 404 || upper.includes("UNREGISTERED")) return "unregistered";
  if (
    status === 400 && upper.includes("REGISTRATION") &&
    (upper.includes("NOT A VALID") || upper.includes("NOT-REGISTERED"))
  ) {
    return "unregistered";
  }
  return "retry";
}

/** Wires the real gateways to a service-account credential. */
export function createPushService(
  settings: PushSettings,
  options: { fetchImpl?: typeof fetch; now?: () => number } = {},
): PushService {
  const credential = parseServiceAccountJson(settings.serviceAccountJson);
  const tokens = new GoogleAccessTokenProvider(credential, options);
  // The credential's own project is authoritative for the API calls; the
  // settings project is only used for ID-token verification.
  return new PushService(settings, {
    firestore: new FirestoreRestGateway(credential.projectId, tokens, options),
    fcm: new FcmRestGateway(credential.projectId, tokens, options),
    ...(options.now ? { now: options.now } : {}),
  });
}
