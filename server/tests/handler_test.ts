/**
 * HTTP surface tests: health/config exposure, the auth gate, and the rule that
 * no response ever contains credential material.
 */

import { assertEquals, assertMatch, assertNotEquals } from "./assert.ts";
import { createHandler } from "../src/main.ts";
import { type B2Client } from "../src/b2.ts";
import { type EnvSource, loadSettings, type ServerSettings } from "../src/config.ts";

const VALID: Record<string, string> = {
  B2_KEY_ID: "0055a1b2c3d4e5f60001",
  B2_APPLICATION_KEY: "K0055a1b2c3d4e5f6000102030405060708091011121314151617181920",
  FIREBASE_PROJECT_ID: "deenolink-prod",
};

const SECRET = VALID.B2_APPLICATION_KEY!;

function settingsFor(overrides: Record<string, string> = {}): ServerSettings {
  const merged: Record<string, string | undefined> = { ...VALID, ...overrides };
  const env: EnvSource = { get: (key) => merged[key] };
  return loadSettings(env);
}

const FIXED_NOW = Date.parse("2026-09-26T10:11:12.000Z");

function get(handler: (r: Request) => Promise<Response>, path: string): Promise<Response> {
  return handler(new Request(`http://localhost:8787${path}`));
}

function postJson(
  handler: (r: Request) => Promise<Response>,
  path: string,
  body: unknown,
): Promise<Response> {
  return handler(
    new Request(`http://localhost:8787${path}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    }),
  );
}

Deno.test("GET /health reports configuration without any secret", async () => {
  const handler = createHandler(settingsFor(), { now: () => FIXED_NOW });
  const response = await get(handler, "/health");
  assertEquals(response.status, 200);

  const text = await response.text();
  assertEquals(text.includes(SECRET), false);
  assertEquals(text.includes(VALID.B2_KEY_ID!), false);

  const body = JSON.parse(text);
  assertEquals(body.status, "ok");
  assertEquals(body.time, "2026-09-26T10:11:12.000Z");
  assertEquals(body.config.bucket, "deenolink-media");
  assertEquals(body.config.endpoint, "https://s3.us-east-005.backblazeb2.com");
  assertEquals(body.config.region, "us-east-005");
  assertEquals(body.config.authRequired, true);
  assertEquals(body.config.credentialsPresent, {
    b2KeyId: true,
    b2ApplicationKey: true,
    firebaseServiceAccount: false,
  });
});

Deno.test("GET /health needs no Authorization header", async () => {
  const handler = createHandler(settingsFor());
  assertEquals((await get(handler, "/health")).status, 200);
});

Deno.test("media routes reject a request with no bearer token", async () => {
  const handler = createHandler(settingsFor());
  for (
    const request of [
      () =>
        postJson(handler, "/v1/media/upload-url", { kind: "avatar", contentType: "image/jpeg" }),
      () => postJson(handler, "/v1/media/download-url", { key: "media/users/u/avatar/a.jpg" }),
      () => get(handler, "/v1/media/head?key=media/users/u/avatar/a.jpg"),
      () =>
        handler(
          new Request("http://localhost:8787/v1/media?key=media/users/u/avatar/a.jpg", {
            method: "DELETE",
          }),
        ),
    ]
  ) {
    const response = await request();
    assertEquals(response.status, 401);
    assertEquals((await response.text()).includes(SECRET), false);
  }
});

Deno.test("media routes reject a malformed bearer token", async () => {
  const handler = createHandler(settingsFor());
  for (const header of ["", "Bearer", "Basic abc", "Bearer not-a-jwt", "bearer x", "Bearer a b"]) {
    const response = await handler(
      new Request("http://localhost:8787/v1/media/head?key=media/users/u/avatar/a.jpg", {
        headers: { authorization: header },
      }),
    );
    assertEquals(response.status, 401, `expected 401 for ${JSON.stringify(header)}`);
  }
});

Deno.test("upload-url validates the request before touching B2", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  const cases: Array<[unknown, number]> = [
    [{ contentType: "image/jpeg" }, 400],
    [{ kind: "avatar" }, 400],
    // zip is on the allowed list for document-chooser uploads, so it is a
    // valid content type here; the kind is still what is being validated here.
    [{ kind: "nope", contentType: "image/jpeg" }, 400],
    [{ kind: "avatar", contentType: "image/jpeg", sizeBytes: 0 }, 400],
    [{ kind: "avatar", contentType: "image/jpeg", sizeBytes: 1.5 }, 400],
    [{ kind: "avatar", contentType: "image/jpeg", sizeBytes: 6 * 1024 * 1024 }, 413],
    [{ kind: "avatar", contentType: "image/jpeg", expiresInSeconds: 99999 }, 400],
  ];
  for (const [body, expected] of cases) {
    const response = await postJson(handler, "/v1/media/upload-url", body);
    assertEquals(response.status, expected, `for body ${JSON.stringify(body)}`);
  }
});

Deno.test("upload-url rejects a non-object body and a missing key parameter", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));

  const notObject = await handler(
    new Request("http://localhost:8787/v1/media/upload-url", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: '"just a string"',
    }),
  );
  assertEquals(notObject.status, 400);

  const missingKey = await get(handler, "/v1/media/head");
  assertEquals(missingKey.status, 400);
});

Deno.test("the dev bypass is the only way to reach a media route unauthenticated", async () => {
  const open = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  const guarded = createHandler(settingsFor());
  // A key the server would reject, so the request stops before any B2 call.
  assertEquals((await get(open, "/v1/media/head?key=../etc/passwd")).status, 400);
  assertEquals((await get(guarded, "/v1/media/head?key=../etc/passwd")).status, 401);
});

Deno.test("unknown routes and methods return a JSON 404", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  for (
    const request of [
      () => get(handler, "/"),
      () => get(handler, "/v1/nope"),
      () => postJson(handler, "/health", {}),
    ]
  ) {
    const response = await request();
    assertEquals(response.status, 404);
    assertMatch(response.headers.get("content-type") ?? "", /application\/json/);
  }
});

Deno.test("responses are marked no-store and nosniff", async () => {
  const handler = createHandler(settingsFor());
  const response = await get(handler, "/health");
  assertEquals(response.headers.get("cache-control"), "no-store");
  assertEquals(response.headers.get("x-content-type-options"), "nosniff");
  assertEquals(response.headers.get("referrer-policy"), "no-referrer");
});

Deno.test("CORS headers appear only for an allowlisted origin", async () => {
  const handler = createHandler(settingsFor({ B2_CORS_ALLOWED_ORIGINS: "https://admin.example" }));

  const allowed = await handler(
    new Request("http://localhost:8787/health", {
      headers: { origin: "https://admin.example" },
    }),
  );
  assertEquals(allowed.headers.get("access-control-allow-origin"), "https://admin.example");

  const denied = await handler(
    new Request("http://localhost:8787/health", { headers: { origin: "https://evil.example" } }),
  );
  assertEquals(denied.headers.get("access-control-allow-origin"), null);
});

Deno.test("two different keys are produced for the same input", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  const body = { kind: "avatar", contentType: "image/jpeg" };
  const first = await (await postJson(handler, "/v1/media/upload-url", body)).json();
  const second = await (await postJson(handler, "/v1/media/upload-url", body)).json();
  assertNotEquals(first.key, second.key);
  assertMatch(first.key, /^media\/users\/local-dev-user\/avatar\/[0-9a-f-]{36}\.jpg$/);
  assertMatch(
    first.url,
    /^https:\/\/s3\.us-east-005\.backblazeb2\.com\/deenolink-media\/media\/users\//,
  );
  assertEquals(JSON.stringify(first).includes(SECRET), false);
});

// ---------------------------------------------------------------------------
// Chat attachment routes
//
// The route-level property that matters is that the key is built from the
// *authenticated* uid, never from the request body, so a client cannot address
// somebody else's namespace - and that the participant check runs before any
// link is minted.
// ---------------------------------------------------------------------------

Deno.test("a chat upload key is addressed by the authenticated uid, not the body", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  const response = await postJson(handler, "/v1/media/upload-url", {
    chatKind: "voice",
    contentType: "audio/mp4",
    peerUid: "uid-bob",
    // A body that tries to name the uploader must be ignored, not honoured.
    uid: "uid-attacker",
    uploaderUid: "uid-attacker",
  });
  assertEquals(response.status, 200);

  const body = await response.json();
  assertMatch(body.key, /^media\/chats\/local-dev-user\/uid-bob\/voice\/[0-9a-f-]{36}\.m4a$/);
  // The chat ceiling travels with the response so the client can refuse a
  // too-large file without moving any bytes.
  assertEquals(body.maxUploadBytes, 25 * 1024 * 1024);
  assertEquals(body.method, "PUT");
  assertEquals(JSON.stringify(body).includes(SECRET), false);
});

Deno.test("a chat upload refuses a kind that does not match the content type", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  for (const [chatKind, contentType] of [
    ["photo", "video/mp4"],
    ["video", "image/jpeg"],
    ["voice", "image/jpeg"],
    ["secrets", "image/jpeg"],
  ]) {
    const response = await postJson(handler, "/v1/media/upload-url", {
      chatKind,
      contentType,
      peerUid: "uid-bob",
    });
    assertEquals(response.status, 400, `${chatKind}/${contentType} should be refused`);
  }
});

Deno.test("a chat upload refuses a peer that is not a usable uid, or is yourself", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  for (const peerUid of ["", "has space", "has/slash", "../etc", "local-dev-user"]) {
    const response = await postJson(handler, "/v1/media/upload-url", {
      chatKind: "photo",
      contentType: "image/jpeg",
      peerUid,
    });
    assertEquals(response.status >= 400, true, `peerUid "${peerUid}" should be refused`);
  }
});

Deno.test("a chat upload refuses a file over the chat ceiling before it is signed", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  const response = await postJson(handler, "/v1/media/upload-url", {
    chatKind: "video",
    contentType: "video/mp4",
    peerUid: "uid-bob",
    sizeBytes: 25 * 1024 * 1024 + 1,
  });
  assertEquals(response.status, 413);
  assertEquals((await response.json()).error.includes("byte limit"), true);
});

Deno.test("a chat download is refused for anyone outside the key", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));

  // The dev uid is the uploader in the first key, so it is allowed; a key whose
  // participant list does not contain the caller is not.
  const allowed = await postJson(handler, "/v1/media/download-url", {
    key: "media/chats/local-dev-user/uid-bob/photo/550e8400-e29b-41d4-a716-446655440000.jpg",
  });
  assertEquals(allowed.status, 200);
  const allowedBody = await allowed.json();
  assertEquals(allowedBody.method, "GET");
  assertEquals(JSON.stringify(allowedBody).includes(SECRET), false);

  const denied = await postJson(handler, "/v1/media/download-url", {
    key: "media/chats/uid-alice/uid-bob/photo/550e8400-e29b-41d4-a716-446655440000.jpg",
  });
  assertEquals(denied.status, 403);
  assertEquals((await denied.json()).error, "You do not have access to this media.");
});

Deno.test("a chat download refuses a malformed or foreign key", async () => {
  const handler = createHandler(settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  for (
    const key of [
      "",
      "media/chats/uid-alice/uid-bob/photo",
      "media/chats/uid-alice/uid-bob/photo/../../etc/passwd",
      "media/chats/uid-alice/uid-alice/photo/x.jpg",
      "other/chats/uid-alice/uid-bob/photo/x.jpg",
    ]
  ) {
    const response = await postJson(handler, "/v1/media/download-url", { key });
    assertEquals(response.status, 400, `should have refused: ${key}`);
  }
});

Deno.test("only the uploader may delete a chat object", async () => {
  // The B2 client is replaced with a stub so the test observes the route's own
  // decision rather than a real bucket call.
  const deleted: Array<{ key: string; uid: string }> = [];
  const handler = createHandler(
    settingsFor({ B2_ALLOW_ANONYMOUS_DEV: "true" }),
    {
      now: () => FIXED_NOW,
      client: {
        async deleteObject(key: string, uid: string) {
          deleted.push({ key, uid });
        },
      } as unknown as B2Client,
    },
  );

  const key = "media/chats/local-dev-user/uid-bob/photo/550e8400-e29b-41d4-a716-446655440000.jpg";
  const response = await handler(
    new Request(`http://localhost:8787/v1/media?key=${encodeURIComponent(key)}`, {
      method: "DELETE",
    }),
  );
  assertEquals(response.status, 200);
  assertEquals(deleted, [{ key, uid: "local-dev-user" }]);

  // A key the caller did not upload is refused before the client is reached, so
  // the receiver can never destroy the sender's own history.
  const notMine = await handler(
    new Request(
      `http://localhost:8787/v1/media?key=${
        encodeURIComponent("media/chats/uid-alice/uid-bob/photo/x.jpg")
      }`,
      { method: "DELETE" },
    ),
  );
  assertEquals(notMine.status, 403);
  assertEquals(deleted.length, 1, "the refused delete must not have reached the client");
});
