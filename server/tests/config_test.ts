/**
 * Configuration tests. The important property is that configuration fails
 * closed and that no error message ever contains credential material.
 */

import { assertEquals, assertMatch, assertThrows } from "./assert.ts";
import { ConfigError, type EnvSource, loadSettings, publicConfigView } from "../src/config.ts";

const VALID: Record<string, string> = {
  B2_KEY_ID: "0055a1b2c3d4e5f60001",
  B2_APPLICATION_KEY: "K0055a1b2c3d4e5f6000102030405060708091011121314151617181920",
  FIREBASE_PROJECT_ID: "deenolink-prod",
};

function env(overrides: Record<string, string | undefined> = {}): EnvSource {
  const merged: Record<string, string | undefined> = { ...VALID, ...overrides };
  return { get: (key) => merged[key] };
}

const SECRET = VALID.B2_APPLICATION_KEY!;

Deno.test("loads the documented defaults from only the two credentials", () => {
  const settings = loadSettings(env());
  assertEquals(settings.b2.bucket, "deenolink-media");
  assertEquals(settings.b2.endpoint, "https://s3.us-east-005.backblazeb2.com");
  assertEquals(settings.b2.region, "us-east-005");
  assertEquals(settings.b2.objectPrefix, "media");
  assertEquals(settings.b2.presignTtlSeconds, 900);
  assertEquals(settings.b2.maxUploadBytes, 5 * 1024 * 1024);
  assertEquals(settings.port, 8787);
  assertEquals(settings.authRequired, true);
  assertEquals([...settings.b2.allowedContentTypes], [
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
  ]);
});

Deno.test("fails closed when B2_KEY_ID is missing, without echoing any secret", () => {
  const error = assertThrows(() => loadSettings(env({ B2_KEY_ID: undefined })));
  assertEquals(error instanceof ConfigError, true);
  assertMatch((error as Error).message, /B2_KEY_ID/);
  assertEquals((error as Error).message.includes(SECRET), false);
});

Deno.test("fails closed when B2_APPLICATION_KEY is missing or blank", () => {
  for (const value of [undefined, "", "   "]) {
    const error = assertThrows(() => loadSettings(env({ B2_APPLICATION_KEY: value })));
    assertEquals(error instanceof ConfigError, true);
    assertEquals((error as Error).message.includes(SECRET), false);
  }
});

Deno.test("rejects placeholder credential values", () => {
  for (const value of ["changeme", "CHANGEME", "your-application-key", "todo", "xxx"]) {
    const error = assertThrows(() => loadSettings(env({ B2_APPLICATION_KEY: value })));
    assertEquals(error instanceof ConfigError, true);
    assertMatch((error as Error).message, /placeholder/);
  }
});

Deno.test("rejects a credential containing a newline", () => {
  const error = assertThrows(() => loadSettings(env({ B2_APPLICATION_KEY: "abc\ndef" })));
  assertMatch((error as Error).message, /line break/);
});

Deno.test("requires FIREBASE_PROJECT_ID", () => {
  const error = assertThrows(() => loadSettings(env({ FIREBASE_PROJECT_ID: undefined })));
  assertMatch((error as Error).message, /FIREBASE_PROJECT_ID/);
});

Deno.test("derives the endpoint from B2_REGION and the region from B2_ENDPOINT", () => {
  const fromRegion = loadSettings(env({ B2_REGION: "us-west-004" }));
  assertEquals(fromRegion.b2.endpoint, "https://s3.us-west-004.backblazeb2.com");
  assertEquals(fromRegion.b2.region, "us-west-004");

  const fromEndpoint = loadSettings(env({
    B2_ENDPOINT: "https://s3.eu-central-003.backblazeb2.com",
  }));
  assertEquals(fromEndpoint.b2.region, "eu-central-003");
  assertEquals(fromEndpoint.b2.endpoint, "https://s3.eu-central-003.backblazeb2.com");
});

Deno.test("rejects a non-https or non-bare B2_ENDPOINT", () => {
  assertThrows(() => loadSettings(env({ B2_ENDPOINT: "http://s3.us-east-005.backblazeb2.com" })));
  assertThrows(() =>
    loadSettings(env({ B2_ENDPOINT: "https://s3.us-east-005.backblazeb2.com/x" }))
  );
});

Deno.test("validates numeric bounds", () => {
  assertThrows(() => loadSettings(env({ PORT: "0" })));
  assertThrows(() => loadSettings(env({ PORT: "70000" })));
  assertThrows(() => loadSettings(env({ PORT: "abc" })));
  assertThrows(() => loadSettings(env({ B2_PRESIGN_TTL_SECONDS: "10" })));
  assertThrows(() => loadSettings(env({ B2_PRESIGN_TTL_SECONDS: String(8 * 24 * 60 * 60) })));
});

Deno.test("the anonymous dev bypass cannot be enabled in production", () => {
  const error = assertThrows(() =>
    loadSettings(env({ B2_ALLOW_ANONYMOUS_DEV: "true", DENO_ENV: "production" }))
  );
  assertMatch((error as Error).message, /cannot be enabled/);

  const dev = loadSettings(env({ B2_ALLOW_ANONYMOUS_DEV: "true" }));
  assertEquals(dev.authRequired, false);
});

Deno.test("publicConfigView exposes no credential material", () => {
  const view = publicConfigView(loadSettings(env()));
  const serialized = JSON.stringify(view);
  assertEquals(serialized.includes(SECRET), false);
  assertEquals(serialized.includes(VALID.B2_KEY_ID!), false);
  assertEquals(view.credentialsPresent, { b2KeyId: true, b2ApplicationKey: true });
  assertEquals(view.bucket, "deenolink-media");
  assertEquals(view.endpoint, "https://s3.us-east-005.backblazeb2.com");
});

Deno.test("publicConfigView does not gain a keyId when the dev bypass is on", () => {
  const view = publicConfigView(loadSettings(env({ B2_ALLOW_ANONYMOUS_DEV: "true" })));
  assertEquals(JSON.stringify(view).includes(VALID.B2_KEY_ID!), false);
  assertEquals(view.authRequired, false);
});

Deno.test("rejects a non-boolean flag rather than defaulting it", () => {
  const error = assertThrows(() => loadSettings(env({ B2_ALLOW_ANONYMOUS_DEV: "yes-please" })));
  assertMatch((error as Error).message, /must be true or false/);
});
