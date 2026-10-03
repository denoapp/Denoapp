/**
 * Chat media key tests.
 *
 * A chat object key is the access control for chat media: read access is
 * "you are one of the two uids in this key", delete access is "you are the
 * uploader". These tests pin both, plus the kind/content-type pairing and the
 * fact that a client can never choose the path itself.
 */

import {
  assertEquals,
  assertMatch,
  assertNotEquals,
  assertThrows,
} from "./assert.ts";
import {
  assertChatKey,
  buildChatObjectKey,
  canDeleteChatKey,
  canReadChatKey,
  CHAT_MEDIA_KINDS,
  isChatKeyShape,
  MediaError,
} from "../src/b2.ts";

const ALLOWED = [
  "image/jpeg",
  "image/png",
  "video/mp4",
  "audio/mp4",
  "application/pdf",
  "text/plain",
];
const UUID = "550e8400-e29b-41d4-a716-446655440000";
const MAX = 25 * 1024 * 1024;

function chatKeyFor(
  overrides: Partial<Parameters<typeof buildChatObjectKey>[0]> = {},
): string {
  return buildChatObjectKey({
    prefix: "media",
    uploaderUid: "uid-alice",
    peerUid: "uid-bob",
    kind: "photo",
    contentType: "image/jpeg",
    allowedContentTypes: ALLOWED,
    maxUploadBytes: MAX,
    randomId: UUID,
    ...overrides,
  });
}

Deno.test("buildChatObjectKey produces the chats/{uploader}/{peer}/{kind}/{id} shape", () => {
  assertEquals(chatKeyFor(), `media/chats/uid-alice/uid-bob/photo/${UUID}.jpg`);
  assertEquals(
    chatKeyFor({ kind: "voice", contentType: "audio/mp4" }),
    `media/chats/uid-alice/uid-bob/voice/${UUID}.m4a`,
  );
  assertEquals(
    chatKeyFor({ kind: "video", contentType: "video/mp4" }),
    `media/chats/uid-alice/uid-bob/video/${UUID}.mp4`,
  );
  assertEquals(
    chatKeyFor({ kind: "file", contentType: "application/pdf" }),
    `media/chats/uid-alice/uid-bob/file/${UUID}.pdf`,
  );
});

Deno.test("the uploader is always the first uid segment, never the peer", () => {
  const fromAlice = chatKeyFor({ uploaderUid: "uid-alice", peerUid: "uid-bob" });
  const fromBob = chatKeyFor({ uploaderUid: "uid-bob", peerUid: "uid-alice" });
  assertNotEquals(fromAlice, fromBob);
  assertMatch(fromAlice, /^media\/chats\/uid-alice\//);
  assertMatch(fromBob, /^media\/chats\/uid-bob\//);
});

Deno.test("buildChatObjectKey normalises the content type and ignores parameters", () => {
  assertEquals(
    chatKeyFor({ contentType: "IMAGE/JPEG" }),
    `media/chats/uid-alice/uid-bob/photo/${UUID}.jpg`,
  );
  assertEquals(
    chatKeyFor({ contentType: "image/jpeg; charset=binary" }),
    `media/chats/uid-alice/uid-bob/photo/${UUID}.jpg`,
  );
});

Deno.test("buildChatObjectKey generates a unique id when none is supplied", () => {
  const a = chatKeyFor({ randomId: undefined });
  const b = chatKeyFor({ randomId: undefined });
  assertNotEquals(a, b);
  assertMatch(
    a,
    /^media\/chats\/uid-alice\/uid-bob\/photo\/[0-9a-f-]{36}\.jpg$/,
  );
});

Deno.test("buildChatObjectKey refuses to attach to yourself", () => {
  const error = assertThrows(() => chatKeyFor({ peerUid: "uid-alice" }));
  assertEquals(error instanceof MediaError, true);
  assertEquals((error as MediaError).status, 400);
});

Deno.test("buildChatObjectKey rejects an unknown chat kind", () => {
  for (const kind of ["secrets", "PHOTO", "chat/../escape", ""]) {
    const error = assertThrows(() => chatKeyFor({ kind }));
    assertEquals(error instanceof MediaError, true);
    assertEquals((error as MediaError).status, 400);
  }
});

Deno.test("a chat kind only accepts its own top-level content type", () => {
  const cases: Array<[string, string]> = [
    ["photo", "video/mp4"],
    ["photo", "application/pdf"],
    ["video", "image/jpeg"],
    ["voice", "image/jpeg"],
    ["voice", "video/mp4"],
  ];
  for (const [kind, contentType] of cases) {
    const error = assertThrows(() => chatKeyFor({ kind, contentType }));
    assertEquals(error instanceof MediaError, true, `${kind}/${contentType} should be rejected`);
    assertEquals((error as MediaError).status, 400);
  }
});

Deno.test("the file kind accepts any allowlisted document type", () => {
  for (const contentType of ["application/pdf", "text/plain", "image/png", "audio/mp4"]) {
    const key = chatKeyFor({ kind: "file", contentType });
    assertMatch(key, /^media\/chats\/uid-alice\/uid-bob\/file\//);
  }
});

Deno.test("buildChatObjectKey rejects a content type that is not allowlisted", () => {
  const error = assertThrows(() => chatKeyFor({ contentType: "application/x-msdownload" }));
  assertEquals(error instanceof MediaError, true);
  assertEquals((error as MediaError).status, 400);
  assertMatch((error as Error).message, /Unsupported content type/);
});

Deno.test("buildChatObjectKey enforces the chat size ceiling", () => {
  const error = assertThrows(() => chatKeyFor({ sizeBytes: MAX + 1 }));
  assertEquals(error instanceof MediaError, true);
  assertEquals((error as MediaError).status, 413);

  const atLimit = chatKeyFor({ sizeBytes: MAX });
  assertMatch(atLimit, /^media\/chats\//);
});

Deno.test("buildChatObjectKey rejects a uid that is not a Firebase-shaped id", () => {
  for (const uid of ["", "a".repeat(129), "has space", "has/slash", "has.dot", "../etc"]) {
    const error = assertThrows(() => chatKeyFor({ uploaderUid: uid }));
    assertEquals(error instanceof MediaError, true);
    assertEquals((error as MediaError).status, 401);
  }
});

Deno.test("assertChatKey round-trips a key this server produced", () => {
  const key = chatKeyFor();
  const parsed = assertChatKey(key, "media");
  assertEquals(parsed.uploader, "uid-alice");
  assertEquals(parsed.peer, "uid-bob");
  assertEquals(parsed.kind, "photo");
});

Deno.test("assertChatKey blocks traversal, wrong depth and foreign prefixes", () => {
  const bad = [
    "",
    "   ",
    "/media/chats/uid-alice/uid-bob/photo/x.jpg",
    "media/chats/uid-alice/uid-bob/photo/",
    "media/chats/uid-alice/uid-bob/photo/../../../../etc/passwd",
    "media/chats/uid-alice/uid-bob",
    "media/chats/uid-alice/uid-bob/photo/extra/x.jpg",
    "media\\chats\\uid-alice\\uid-bob",
    "other/chats/uid-alice/uid-bob/photo/x.jpg",
    "media/chats/uid-alice/uid-alice/photo/x.jpg",
    "media/chats/uid-alice/uid-bob/secrets/x.jpg",
  ];
  for (const key of bad) {
    const error = assertThrows(() => assertChatKey(key, "media"));
    assertEquals(error instanceof MediaError, true, `should have rejected: ${key}`);
  }
});

Deno.test("a user object key is never mistaken for a chat key", () => {
  assertEquals(isChatKeyShape(`media/users/uid-123/avatar/${UUID}.jpg`, "media"), false);
  assertEquals(isChatKeyShape(chatKeyFor(), "media"), true);
});

Deno.test("read access is limited to the two participants", () => {
  const chatKey = assertChatKey(chatKeyFor(), "media");
  assertEquals(canReadChatKey(chatKey, "uid-alice"), true, "the uploader can read it");
  assertEquals(canReadChatKey(chatKey, "uid-bob"), true, "the receiver can read it");
  assertEquals(canReadChatKey(chatKey, "uid-carol"), false, "a third party cannot");
  assertEquals(canReadChatKey(chatKey, "local-dev-user"), false);
});

Deno.test("delete access is limited to the uploader", () => {
  const chatKey = assertChatKey(chatKeyFor(), "media");
  assertEquals(canDeleteChatKey(chatKey, "uid-alice"), true);
  assertEquals(
    canDeleteChatKey(chatKey, "uid-bob"),
    false,
    "the receiver must not be able to destroy an object the sender's history points at",
  );
  assertEquals(canDeleteChatKey(chatKey, "uid-carol"), false);
});

Deno.test("every declared chat kind round-trips through the parser", () => {
  const contentTypeFor: Record<string, string> = {
    photo: "image/jpeg",
    video: "video/mp4",
    file: "application/pdf",
    voice: "audio/mp4",
  };
  assertEquals(CHAT_MEDIA_KINDS.length, 4);
  for (const kind of CHAT_MEDIA_KINDS) {
    const key = chatKeyFor({ kind, contentType: contentTypeFor[kind]! });
    assertEquals(assertChatKey(key, "media").kind, kind);
  }
});
