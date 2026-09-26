/**
 * B2 client and object-key tests.
 *
 * The key-building rules are the security boundary: the client must never be
 * able to choose where an object lands, only which media category it is.
 */

import {
  assertEquals,
  assertMatch,
  assertNotEquals,
  assertRejects,
  assertThrows,
} from "./assert.ts";
import {
  assertReadableKey,
  B2Client,
  buildObjectKey,
  MediaError,
  normalizeContentType,
  ownerOfKey,
} from "../src/b2.ts";
import type { B2Settings } from "../src/config.ts";

const ALLOWED = ["image/jpeg", "image/png", "image/webp", "image/heic"];
const UUID = "550e8400-e29b-41d4-a716-446655440000";

const SETTINGS: B2Settings = {
  keyId: "AKIDEXAMPLE",
  applicationKey: "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
  bucket: "deenolink-media",
  endpoint: "https://s3.us-east-005.backblazeb2.com",
  region: "us-east-005",
  presignTtlSeconds: 900,
  maxUploadBytes: 5 * 1024 * 1024,
  allowedContentTypes: ALLOWED,
  objectPrefix: "media",
};

function keyFor(overrides: Partial<Parameters<typeof buildObjectKey>[0]> = {}): string {
  return buildObjectKey({
    prefix: "media",
    uid: "uid-123",
    kind: "avatar",
    contentType: "image/jpeg",
    allowedContentTypes: ALLOWED,
    randomId: UUID,
    ...overrides,
  });
}

Deno.test("buildObjectKey produces the media/users/{uid}/{kind}/{id}.{ext} shape", () => {
  assertEquals(keyFor(), `media/users/uid-123/avatar/${UUID}.jpg`);
  assertEquals(
    keyFor({ kind: "cover", contentType: "image/png" }),
    `media/users/uid-123/cover/${UUID}.png`,
  );
  assertEquals(
    keyFor({ contentType: "image/webp" }),
    `media/users/uid-123/avatar/${UUID}.webp`,
  );
});

Deno.test("buildObjectKey normalises the content type and ignores parameters", () => {
  assertEquals(keyFor({ contentType: "IMAGE/JPEG" }), `media/users/uid-123/avatar/${UUID}.jpg`);
  assertEquals(
    keyFor({ contentType: "image/jpeg; charset=binary" }),
    `media/users/uid-123/avatar/${UUID}.jpg`,
  );
});

Deno.test("buildObjectKey generates a unique id when none is supplied", () => {
  const a = buildObjectKey({
    prefix: "media",
    uid: "uid-123",
    kind: "avatar",
    contentType: "image/jpeg",
    allowedContentTypes: ALLOWED,
  });
  const b = buildObjectKey({
    prefix: "media",
    uid: "uid-123",
    kind: "avatar",
    contentType: "image/jpeg",
    allowedContentTypes: ALLOWED,
  });
  assertNotEquals(a, b);
  assertMatch(a, /^media\/users\/uid-123\/avatar\/[0-9a-f-]{36}\.jpg$/);
});

Deno.test("buildObjectKey rejects a content type that is not allowlisted", () => {
  const error = assertThrows(() => keyFor({ contentType: "application/x-msdownload" }));
  assertEquals(error instanceof MediaError, true);
  assertEquals((error as MediaError).status, 400);
  assertMatch((error as Error).message, /Unsupported content type/);
});

Deno.test("buildObjectKey rejects an unknown media kind", () => {
  for (const kind of ["secrets", "../escape", "AVATAR", "avatar/../../etc", "a".repeat(40)]) {
    const error = assertThrows(() => keyFor({ kind }));
    assertEquals(error instanceof MediaError, true);
    assertEquals((error as MediaError).status, 400);
  }
});

Deno.test("buildObjectKey rejects a uid that is not a Firebase-shaped id", () => {
  for (const uid of ["", "a".repeat(129), "has space", "has/slash", "has.dot", "../etc"]) {
    const error = assertThrows(() => keyFor({ uid }));
    assertEquals(error instanceof MediaError, true);
    assertEquals((error as MediaError).status, 401);
  }
});

Deno.test("assertReadableKey accepts a key this server would have produced", () => {
  assertEquals(
    assertReadableKey(`media/users/uid-123/avatar/${UUID}.jpg`, "media"),
    `media/users/uid-123/avatar/${UUID}.jpg`,
  );
});

Deno.test("assertReadableKey blocks traversal, absolute paths, and foreign prefixes", () => {
  const bad = [
    "",
    "   ",
    "/media/users/uid-123/avatar/x.jpg",
    "media/users/uid-123/avatar/",
    "media/users/../../etc/passwd",
    "media/../secrets/key",
    "media\\users\\uid-123",
    "other/users/uid-123/avatar/x.jpg",
    "media/users/uid-123",
    "media/users/uid-123/avatar/x\n.jpg",
    "media/users/uid-123/avatar/x jpg.jpg",
    "a".repeat(2000),
  ];
  for (const key of bad) {
    const error = assertThrows(() => assertReadableKey(key, "media"));
    assertEquals(
      error instanceof MediaError,
      true,
      `expected rejection for ${JSON.stringify(key)}`,
    );
  }
});

Deno.test("ownerOfKey extracts the uid only from a well-formed key", () => {
  assertEquals(ownerOfKey(`media/users/uid-123/avatar/${UUID}.jpg`, "media"), "uid-123");
  assertEquals(ownerOfKey("media/admins/uid-123/avatar/x.jpg", "media"), undefined);
  assertEquals(ownerOfKey("other/users/uid-123/avatar/x.jpg", "media"), undefined);
});

Deno.test("objectUrl uses path-style addressing with the bucket in the path", async () => {
  const client = new B2Client(SETTINGS);
  const presigned = await client.presignUpload({
    key: `media/users/uid 123/avatar/${UUID}.jpg`,
    contentType: "image/jpeg",
  });
  const url = new URL(presigned.url);
  assertEquals(url.origin, "https://s3.us-east-005.backblazeb2.com");
  assertEquals(url.pathname, `/deenolink-media/media/users/uid%20123/avatar/${UUID}.jpg`);
  assertEquals(presigned.method, "PUT");
  assertEquals(presigned.headers["content-type"], "image/jpeg");
});

Deno.test("presignUpload returns no credential material", async () => {
  const client = new B2Client(SETTINGS);
  const presigned = await client.presignUpload({
    key: `media/users/uid-123/avatar/${UUID}.jpg`,
    contentType: "image/jpeg",
  });
  const serialized = JSON.stringify(presigned);
  assertEquals(serialized.includes(SETTINGS.applicationKey), false);
  assertEquals(serialized.includes("wJalrXUtnFEMI"), false);
  // The key id is public by design: SigV4 puts it in the URL. The secret must
  // never appear.
  assertMatch(
    new URL(presigned.url).searchParams.get("X-Amz-Credential") ?? "",
    /^AKIDEXAMPLE\/\d{8}\/us-east-005\/s3\/aws4_request$/,
  );
});

Deno.test("presignUpload caps a client-requested ttl at the server ceiling", async () => {
  const client = new B2Client(SETTINGS);
  const long = await client.presignUpload({
    key: `media/users/uid-123/avatar/${UUID}.jpg`,
    contentType: "image/jpeg",
    expiresInSeconds: 604800,
  });
  assertEquals(long.expiresInSeconds, 900);
  assertEquals(new URL(long.url).searchParams.get("X-Amz-Expires"), "900");

  const short = await client.presignUpload({
    key: `media/users/uid-123/avatar/${UUID}.jpg`,
    contentType: "image/jpeg",
    expiresInSeconds: 120,
  });
  assertEquals(short.expiresInSeconds, 120);
});

Deno.test("presignUpload refuses a content type outside the allowlist", async () => {
  const client = new B2Client(SETTINGS);
  await assertRejects(() =>
    client.presignUpload({
      key: `media/users/uid-123/avatar/${UUID}.exe`,
      contentType: "application/x-msdownload",
    })
  );
});

Deno.test("presignDownload never exposes the application key", async () => {
  const client = new B2Client(SETTINGS);
  const presigned = await client.presignDownload({
    key: `media/users/uid-123/avatar/${UUID}.jpg`,
    fileName: "../../etc/passwd",
  });
  assertEquals(presigned.method, "GET");
  assertEquals(JSON.stringify(presigned).includes(SETTINGS.applicationKey), false);
  const disposition = new URL(presigned.url).searchParams.get("response-content-disposition");
  assertEquals(disposition?.includes(".."), false);
  assertEquals(disposition?.includes("/"), false);
});

Deno.test("deleteObject refuses a key the caller does not own", async () => {
  const client = new B2Client(SETTINGS);
  const error = await assertRejects(() =>
    client.deleteObject(`media/users/someone-else/avatar/${UUID}.jpg`, "uid-123")
  );
  assertEquals(error instanceof MediaError, true);
  assertEquals((error as MediaError).status, 403);
});

Deno.test("normalizeContentType strips parameters and lowercases", () => {
  assertEquals(normalizeContentType("Image/PNG; q=1"), "image/png");
  assertEquals(normalizeContentType(undefined), "");
});
