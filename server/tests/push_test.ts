/**
 * Push delivery tests: the route's authentication and refusal behaviour, the
 * membership/sender rules, and the token cleanup.
 *
 * The Firestore and FCM gateways are fakes, so this suite needs no Firebase
 * project, no credentials and no network. That is the point: the properties
 * worth testing here are authorization decisions, and those must be provable
 * without a network.
 */

import { assert, assertEquals, assertMatch, assertRejects } from "./assert.ts";
import { createHandler } from "../src/main.ts";
import { type EnvSource, ConfigError, loadSettings, type ServerSettings } from "../src/config.ts";
import {
  assertDocumentId,
  attachmentPreview,
  buildPushData,
  canonicalPairId,
  classifySendFailure,
  decodeFirestoreFields,
  isMuted,
  messagePreview,
  parsePushKind,
  parseServiceAccountJson,
  PushError,
  PushService,
  relativeDocumentPath,
  type FirestoreDocument,
  type FirestoreGateway,
  type SendResult,
  type FcmGateway,
} from "../src/push.ts";

const VALID: Record<string, string> = {
  B2_KEY_ID: "0055a1b2c3d4e5f60001",
  B2_APPLICATION_KEY: "K0055a1b2c3d4e5f6000102030405060708091011121314151617181920",
  FIREBASE_PROJECT_ID: "deenolink-prod",
};

function settingsFor(overrides: Record<string, string> = {}): ServerSettings {
  const merged: Record<string, string | undefined> = { ...VALID, ...overrides };
  const env: EnvSource = { get: (key) => merged[key] };
  return loadSettings(env);
}

/** An in-memory Firestore: paths to field maps, as the REST gateway returns. */
class FakeFirestore implements FirestoreGateway {
  readonly docs = new Map<string, Record<string, unknown>>();
  readonly deleted: string[] = [];
  listCalls: string[] = [];

  seed(path: string, fields: Record<string, unknown>): this {
    this.docs.set(path, fields);
    return this;
  }

  async getDocument(path: string): Promise<FirestoreDocument | null> {
    const fields = this.docs.get(path);
    return fields === undefined
      ? null
      : { name: `projects/p/databases/(default)/documents/${path}`, fields };
  }

  async listDocuments(path: string): Promise<FirestoreDocument[]> {
    this.listCalls.push(path);
    const prefix = `${path}/`;
    const out: FirestoreDocument[] = [];
    for (const [path2, fields] of this.docs) {
      if (path2.startsWith(prefix)) {
        out.push({
          name: `projects/p/databases/(default)/documents/${path2}`,
          fields,
        });
      }
    }
    return out;
  }

  async deleteDocument(path: string): Promise<void> {
    this.deleted.push(path);
    this.docs.delete(path);
  }
}

class FakeFcm implements FcmGateway {
  readonly sent: { token: string; data: Record<string, string> }[] = [];
  results = new Map<string, SendResult>();

  async send(token: string, data: Record<string, string>): Promise<SendResult> {
    this.sent.push({ token, data });
    return this.results.get(token) ?? "delivered";
  }
}

const PUSH_SETTINGS = { enabled: true, serviceAccountJson: "{}" };

function service(
  firestore: FakeFirestore,
  fcm: FakeFcm,
  enabled = true,
): PushService {
  return new PushService({ ...PUSH_SETTINGS, enabled }, { firestore, fcm });
}

// ---------------------------------------------------------------------------
// Path safety
// ---------------------------------------------------------------------------

Deno.test("assertDocumentId refuses anything that could change the URL path", () => {
  assertEquals(assertDocumentId("abc-123_X", "messageId"), "abc-123_X");
  for (const bad of ["", "  ", "a/b", "..", "a?b=1", "a#f", "a\nb", "x".repeat(200)]) {
    const error = assertThrowsAny(() => assertDocumentId(bad, "messageId"));
    assert(error instanceof PushError, `expected a PushError for ${JSON.stringify(bad)}`);
    assertEquals((error as PushError).status, 400);
  }
});

Deno.test("parsePushKind accepts only the two known kinds", () => {
  assertEquals(parsePushKind("direct"), "direct");
  assertEquals(parsePushKind("group"), "group");
  const error = assertThrowsAny(() => parsePushKind("broadcast"));
  assertEquals((error as PushError).status, 400);
});

function assertThrowsAny(fn: () => unknown): unknown {
  try {
    fn();
  } catch (error) {
    return error;
  }
  throw new Error("expected the call to throw");
}

// ---------------------------------------------------------------------------
// Payload building
// ---------------------------------------------------------------------------

Deno.test("the pair id matches the client's sorted(a, b).joinToString(\"_\")", () => {
  assertEquals(canonicalPairId("zed", "abe"), "abe_zed");
  assertEquals(canonicalPairId("abe", "zed"), "abe_zed");
  assertEquals(canonicalPairId("same", "same"), "same_same");
});

Deno.test("every push data value is a string, as FCM requires", () => {
  const data = buildPushData({
    kind: "group",
    conversationId: "g1",
    targetId: "g1",
    title: "Team",
    body: "hi",
    messageId: "m1",
    muted: true,
  });
  for (const value of Object.values(data)) {
    assertEquals(typeof value, "string");
  }
  assertEquals(data["muted"], "true");
  assertEquals(buildPushData({
    kind: "direct",
    conversationId: "c",
    targetId: "c",
    title: "t",
    body: "b",
    messageId: "m",
    muted: false,
  })["muted"], "false");
});

Deno.test("an empty message previews as its attachment kind, not as nothing", () => {
  assertEquals(attachmentPreview("photo"), "Photo");
  assertEquals(attachmentPreview("voice"), "Voice message");
  assertEquals(attachmentPreview(""), "Sent an attachment");
  assertEquals(messagePreview("", "video"), "Video");
});

Deno.test("a long message is shortened and collapsed for the shade", () => {
  const preview = messagePreview("line one\n\nline   two", "");
  assertEquals(preview, "line one line two");
  const long = messagePreview("a".repeat(400), "");
  assert(long.length <= 140, `preview was ${long.length} characters`);
  assertMatch(long, /…$/);
});

Deno.test("a mute is honoured only for the conversation it names", () => {
  const settings = { mutedConversations: ["chat_a"] };
  assertEquals(isMuted(settings, "chat_a"), true);
  assertEquals(isMuted(settings, "chat_b"), false);
  assertEquals(isMuted(null, "chat_a"), false);
  assertEquals(isMuted({ mutedConversations: "chat_a" }, "chat_a"), false);
});

Deno.test("Firestore field decoding handles every type the app writes", () => {
  const fields = decodeFirestoreFields({
    senderId: { stringValue: "u1" },
    unsent: { booleanValue: false },
    createdAt: { timestampValue: "2026-09-29T10:00:00Z" },
    count: { integerValue: "3" },
    memberIds: { arrayValue: { values: [{ stringValue: "u1" }, { stringValue: "u2" }] } },
    replyTo: { nullValue: null },
  });
  assertEquals(fields["senderId"], "u1");
  assertEquals(fields["unsent"], false);
  assertEquals(fields["count"], 3);
  assertEquals(fields["memberIds"], ["u1", "u2"]);
  assertEquals(fields["replyTo"], null);
});

Deno.test("a dead token is recognised, a transient failure is not", () => {
  assertEquals(classifySendFailure(404, "{}"), "unregistered");
  assertEquals(classifySendFailure(400, `{"error":{"status":"UNREGISTERED"}}`), "unregistered");
  assertEquals(
    classifySendFailure(400, "The registration token is not a valid FCM registration token"),
    "unregistered",
  );
  // 500 and a rate limit are retryable: deleting a live token here is how a user
  // silently stops receiving messages.
  assertEquals(classifySendFailure(500, "internal"), "retry");
  assertEquals(classifySendFailure(429, "quota"), "retry");
  assertEquals(classifySendFailure(401, "unauthorized"), "retry");
});

Deno.test("a resource name is reduced to the path a delete needs", () => {
  assertEquals(
    relativeDocumentPath("projects/p/databases/(default)/documents/users/u1/devices/d1"),
    "users/u1/devices/d1",
  );
});

// ---------------------------------------------------------------------------
// 1-to-1 delivery
// ---------------------------------------------------------------------------

function directFixture(): { firestore: FakeFirestore; fcm: FakeFcm } {
  const firestore = new FakeFirestore()
    .seed("chats/abe_zed/messages/m1", {
      senderId: "abe",
      receiverId: "zed",
      text: "hello there",
      unsent: false,
    })
    .seed("users/abe", { fullName: "Abe" })
    .seed("users/zed/devices/d1", { token: "tok-zed", platform: "android" });
  return { firestore, fcm: new FakeFcm() };
}

Deno.test("a 1-to-1 push reaches the receiver's device with the sender's name", async () => {
  const { firestore, fcm } = directFixture();
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "direct",
    conversationId: "abe_zed",
    messageId: "m1",
  });
  assertEquals(outcome.delivered, 1);
  assertEquals(outcome.recipients, 1);
  assertEquals(fcm.sent.length, 1);
  assertEquals(fcm.sent[0]?.token, "tok-zed");
  assertEquals(fcm.sent[0]?.data["title"], "Abe");
  assertEquals(fcm.sent[0]?.data["body"], "hello there");
  assertEquals(fcm.sent[0]?.data["targetId"], "zed");
  assertEquals(fcm.sent[0]?.data["kind"], "direct");
});

Deno.test("a caller cannot push somebody else's message", async () => {
  const { firestore, fcm } = directFixture();
  const error = await assertRejects(() =>
    service(firestore, fcm).send("zed", { kind: "direct", conversationId: "abe_zed", messageId: "m1" })
  );
  assert(error instanceof PushError, "expected a PushError");
  assertEquals((error as PushError).status, 403);
  assertEquals(fcm.sent.length, 0, "no device may be contacted for a refused request");
});

Deno.test("a second conversation is delivered on its own terms", async () => {
  const { firestore, fcm } = directFixture();
  firestore.seed("chats/abe_mal/messages/m2", {
    senderId: "abe",
    receiverId: "mal",
    text: "wrong pair",
  });
  firestore.seed("users/mal/devices/d9", { token: "tok-mal" });
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "direct",
    conversationId: "abe_mal",
    messageId: "m2",
  });
  // abe_mal is the canonical pair of this message's own participants, so it is
  // a valid request and reaches only mal. The refused shape is a mismatch,
  // which the next test covers.
  assertEquals(outcome.delivered, 1);
  assertEquals(fcm.sent[0]?.token, "tok-mal");
});

Deno.test("a mismatched pair id produces no fan-out at all", async () => {
  const firestore = new FakeFirestore()
    .seed("chats/other/messages/m1", {
      senderId: "abe",
      receiverId: "zed",
      text: "hi",
    })
    .seed("users/zed/devices/d1", { token: "tok-zed" });
  const fcm = new FakeFcm();
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "direct",
    conversationId: "other",
    messageId: "m1",
  });
  assertEquals(outcome.delivered, 0);
  assertEquals(fcm.sent.length, 0);
});

Deno.test("a withdrawn message never announces itself", async () => {
  const { firestore, fcm } = directFixture();
  firestore.seed("chats/abe_zed/messages/m2", {
    senderId: "abe",
    receiverId: "zed",
    text: "removed",
    unsent: true,
  });
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "direct",
    conversationId: "abe_zed",
    messageId: "m2",
  });
  assertEquals(outcome.delivered, 0);
  assertEquals(fcm.sent.length, 0);
});

Deno.test("a missing message is an empty result, not an error", async () => {
  const { firestore, fcm } = directFixture();
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "direct",
    conversationId: "abe_zed",
    messageId: "does-not-exist",
  });
  assertEquals(outcome.delivered, 0);
  assertEquals(outcome.recipients, 0);
});

// ---------------------------------------------------------------------------
// Group delivery
// ---------------------------------------------------------------------------

function groupFixture(): { firestore: FakeFirestore; fcm: FakeFcm } {
  const firestore = new FakeFirestore()
    .seed("groups/g1", { name: "Team", memberIds: ["abe", "zed", "cy"] })
    .seed("groups/g1/messages/m1", { senderId: "abe", text: "morning" })
    .seed("users/zed/devices/d1", { token: "tok-zed" })
    .seed("users/cy/devices/d1", { token: "tok-cy" });
  return { firestore, fcm: new FakeFcm() };
}

Deno.test("a group push reaches every member except the sender", async () => {
  const { firestore, fcm } = groupFixture();
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(outcome.recipients, 2);
  assertEquals(outcome.delivered, 2);
  const tokens = fcm.sent.map((s) => s.token).sort();
  assertEquals(tokens, ["tok-cy", "tok-zed"]);
  assertEquals(fcm.sent[0]?.data["title"], "Team");
  assertEquals(fcm.sent[0]?.data["targetId"], "g1");
});

Deno.test("a non-member cannot push to a group", async () => {
  const { firestore, fcm } = groupFixture();
  const error = await assertRejects(() =>
    service(firestore, fcm).send("outsider", { kind: "group", conversationId: "g1", messageId: "m1" })
  );
  assertEquals((error as PushError).status, 403);
  assertEquals(fcm.sent.length, 0);
});

Deno.test("a muted member is still delivered, on the silent channel", async () => {
  const { firestore, fcm } = groupFixture();
  firestore.seed("chatSettings/zed", { mutedConversations: ["g1"] });
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(outcome.mutedRecipients, 1);
  // A mute must never lose the message, only the sound.
  assertEquals(outcome.delivered, 2);
  const zed = fcm.sent.find((s) => s.token === "tok-zed");
  const cy = fcm.sent.find((s) => s.token === "tok-cy");
  assertEquals(zed?.data["muted"], "true");
  assertEquals(cy?.data["muted"], "false");
});

Deno.test("a member removed after the write is not contacted", async () => {
  const { firestore, fcm } = groupFixture();
  firestore.seed("groups/g1", { name: "Team", memberIds: ["abe", "zed"] });
  await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(fcm.sent.map((s) => s.token), ["tok-zed"]);
});

Deno.test("one account with several devices gets all of them", async () => {
  const { firestore, fcm } = groupFixture();
  firestore.seed("users/zed/devices/d2", { token: "tok-zed-2" });
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(outcome.delivered, 3);
});

Deno.test("a duplicate token for one account is sent to once", async () => {
  const { firestore, fcm } = groupFixture();
  firestore.seed("users/zed/devices/dup", { token: "tok-zed" });
  await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(fcm.sent.filter((s) => s.token === "tok-zed").length, 1);
});

Deno.test("a token FCM calls dead is deleted, and only that one", async () => {
  const { firestore, fcm } = groupFixture();
  fcm.results.set("tok-cy", "unregistered");
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(outcome.delivered, 1);
  assertEquals(outcome.removedTokens, 1);
  assertEquals(firestore.deleted, ["users/cy/devices/d1"]);
  assertEquals(firestore.docs.has("users/zed/devices/d1"), true);
});

Deno.test("a transient FCM failure leaves the registration alone", async () => {
  const { firestore, fcm } = groupFixture();
  fcm.results.set("tok-zed", "retry");
  const outcome = await service(firestore, fcm).send("abe", {
    kind: "group",
    conversationId: "g1",
    messageId: "m1",
  });
  assertEquals(outcome.delivered, 1);
  assertEquals(outcome.removedTokens, 0);
  assertEquals(firestore.deleted.length, 0);
});

Deno.test("push refuses to do anything when it is switched off", async () => {
  const { firestore, fcm } = directFixture();
  const error = await assertRejects(() =>
    service(firestore, fcm, false).send("abe", {
      kind: "direct",
      conversationId: "abe_zed",
      messageId: "m1",
    })
  );
  assertEquals((error as PushError).status, 503);
  assertEquals(fcm.sent.length, 0);
});

// ---------------------------------------------------------------------------
// The route
// ---------------------------------------------------------------------------

function post(
  handler: (r: Request) => Promise<Response>,
  path: string,
  body: unknown,
  authorization = "Bearer dev-token",
): Promise<Response> {
  return handler(
    new Request(`http://localhost:8787${path}`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization },
      body: JSON.stringify(body),
    }),
  );
}

Deno.test("the push route only answers POST, and needs a JSON body", async () => {
  const { firestore, fcm } = directFixture();
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }), {
    push: service(firestore, fcm),
  });

  const wrongMethod = await handler(
    new Request("http://localhost:8787/v1/push/send", {
      method: "GET",
      headers: { authorization: "Bearer dev-token" },
    }),
  );
  assertEquals(wrongMethod.status, 404);

  const noBody = await handler(
    new Request("http://localhost:8787/v1/push/send", {
      method: "POST",
      headers: { authorization: "Bearer dev-token" },
    }),
  );
  assertEquals(noBody.status, 400);
  assertEquals(fcm.sent.length, 0);
});

Deno.test("the push route answers 503 when push is not enabled", async () => {
  const handler = createHandler(
    settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }),
    { push: new PushService({ ...PUSH_SETTINGS, enabled: false }, { firestore: new FakeFirestore(), fcm: new FakeFcm() }) },
  );
  const response = await post(handler, "/v1/push/send", {
    kind: "direct",
    conversationId: "abe_zed",
    messageId: "m1",
  });
  assertEquals(response.status, 503);
  const body = await response.json();
  assertMatch(body.error, /not enabled/);
});

Deno.test("the push route reports what was actually delivered", async () => {
  const { firestore, fcm } = directFixture();
  // The dev bypass makes the caller the dev uid, so it is set to the message's
  // own sender: the route is exercised end to end without a real token.
  const handler = createHandler(
    settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true", B2_DEV_UID: "abe" }),
    { push: service(firestore, fcm) },
  );
  const response = await post(handler, "/v1/push/send", {
    kind: "direct",
    conversationId: "abe_zed",
    messageId: "m1",
  });
  assertEquals(response.status, 200);
  const body = await response.json();
  assertEquals(body.delivered, 1);
  assertEquals(body.recipients, 1);
});

Deno.test("the push route rejects a malformed id before any read", async () => {
  const { firestore, fcm } = directFixture();
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }), {
    push: service(firestore, fcm),
  });
  const response = await post(handler, "/v1/push/send", {
    kind: "direct",
    conversationId: "abe/../secrets",
    messageId: "m1",
  });
  assertEquals(response.status, 400);
  assertEquals(fcm.sent.length, 0);
});

Deno.test("the push route rejects an unknown kind", async () => {
  const { firestore, fcm } = directFixture();
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }), {
    push: service(firestore, fcm),
  });
  const response = await post(handler, "/v1/push/send", {
    kind: "broadcast",
    conversationId: "abe_zed",
    messageId: "m1",
  });
  assertEquals(response.status, 400);
});

Deno.test("the health view reports push state without the credential", async () => {
  const handler = createHandler(settingsFor({ PUSH_ENABLED: "false" }));
  const response = await handler(new Request("http://localhost:8787/health"));
  const text = await response.text();
  assertEquals(text.includes("private_key"), false);
  const body = JSON.parse(text);
  assertEquals(body.config.pushEnabled, false);
  assertEquals(body.config.credentialsPresent.firebaseServiceAccount, false);
});

Deno.test("PUSH_ENABLED without a service account is a startup error", async () => {
  let thrown: unknown = null;
  try {
    settingsFor({ PUSH_ENABLED: "true" });
  } catch (error) {
    thrown = error;
  }
  assert(thrown !== null, "expected loadSettings to refuse an incomplete push config");
  assertMatch(String((thrown as Error).message), /FIREBASE_SERVICE_ACCOUNT_JSON/);
});

Deno.test("a service account must carry the three signing fields", () => {
  const error = assertThrowsAny(() => parseServiceAccountJson('{"type":"service_account"}'));
  assert(error instanceof ConfigError, "expected a ConfigError");
  assertMatch(String((error as Error).message), /client_email/);

  // A valid-shaped account parses to exactly what the signer needs.
  const credential = parseServiceAccountJson(
    JSON.stringify({
      type: "service_account",
      project_id: "deenolink-prod",
      private_key: "-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----\n",
      client_email: "firebase-adminsdk-x@deenolink-prod.iam.gserviceaccount.com",
    }),
  );
  assertEquals(credential.projectId, "deenolink-prod");
});

Deno.test("a malformed key fails at handler creation, not at the first message", () => {
  const settings = settingsFor({
    PUSH_ENABLED: "true",
    FIREBASE_SERVICE_ACCOUNT_JSON: '{"type":"service_account","project_id":"deenolink-prod"}',
  });
  const error = assertThrowsAny(() => createHandler(settings));
  assert(error instanceof ConfigError, "expected a ConfigError from createHandler");
});
