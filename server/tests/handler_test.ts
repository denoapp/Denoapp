/**
 * HTTP surface tests: health/config exposure, the auth gate, and the rule that
 * no response ever contains credential material.
 */

import { assertEquals, assertMatch, assertNotEquals } from "./assert.ts";
import { createHandler } from "../src/main.ts";
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
  assertEquals(body.config.credentialsPresent, { b2KeyId: true, b2ApplicationKey: true });
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
    [{ kind: "avatar", contentType: "application/zip" }, 400],
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
