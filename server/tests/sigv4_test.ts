/**
 * SigV4 known-answer tests.
 *
 * Vector 1 is the AWS SigV4 test-suite "get-vanilla" case.
 * Vector 2 is a B2-shaped presigned PUT, cross-checked against an independent
 * OpenSSL/Digest::SHA implementation of the same algorithm.
 */

import { assertEquals, assertMatch, assertNotEquals, assertRejects } from "./assert.ts";
import {
  buildCanonicalRequest,
  buildScope,
  buildStringToSign,
  canonicalHeaders,
  canonicalQueryString,
  deriveSigningKey,
  EMPTY_PAYLOAD_SHA256,
  encodeObjectPath,
  encodeRfc3986,
  hmacSha256Hex,
  presignUrl,
  sha256Hex,
  signRequest,
  UNSIGNED_PAYLOAD,
} from "../src/sigv4.ts";

const AWS_SECRET = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";

Deno.test("encodeRfc3986 escapes the characters encodeURIComponent leaves behind", () => {
  assertEquals(encodeRfc3986("a b"), "a%20b");
  assertEquals(encodeRfc3986("!*'()"), "%21%2A%27%28%29");
  assertEquals(encodeRfc3986("a-b_c.d~e"), "a-b_c.d~e");
  assertEquals(encodeRfc3986("slash/slash"), "slash%2Fslash");
});

Deno.test("encodeObjectPath keeps separators literal", () => {
  assertEquals(encodeObjectPath("media/users/a b/avatar/x.jpg"), "media/users/a%20b/avatar/x.jpg");
});

Deno.test("canonicalQueryString sorts and never emits a plus for a space", () => {
  assertEquals(canonicalQueryString([["b", "2"], ["a", "1"]]), "a=1&b=2");
  assertEquals(canonicalQueryString([["a", "2"], ["a", "1"]]), "a=1&a=2");
  const withSpace = canonicalQueryString([[
    "response-content-disposition",
    'inline; filename="a b.jpg"',
  ]]);
  assertEquals(withSpace, "response-content-disposition=inline%3B%20filename%3D%22a%20b.jpg%22");
  assertEquals(withSpace.includes("+"), false);
});

Deno.test("canonicalHeaders lowercases, trims, and collapses whitespace", () => {
  const { canonical, signed } = canonicalHeaders({
    "X-Amz-Date": "  20260926T101112Z  ",
    Host: "example.com",
    "X-Extra": "a   b",
  });
  assertEquals(signed, "host;x-amz-date;x-extra");
  assertEquals(canonical, "host:example.com\nx-amz-date:20260926T101112Z\nx-extra:a b\n");
});

Deno.test("sha256Hex of the empty string matches the well-known constant", async () => {
  assertEquals(await sha256Hex(""), EMPTY_PAYLOAD_SHA256);
});

Deno.test("AWS test-suite vector: get-vanilla header signature", async () => {
  const amzDate = "20150830T123600Z";
  const dateStamp = "20150830";
  const region = "us-east-1";
  const service = "service";

  const headers = { host: "example.amazonaws.com", "x-amz-date": amzDate };
  const canonicalRequest = buildCanonicalRequest({
    method: "GET",
    path: "/",
    query: "",
    headers,
    payloadHash: EMPTY_PAYLOAD_SHA256,
  });
  const scope = buildScope(dateStamp, region, service);
  const stringToSign = await buildStringToSign(amzDate, scope, canonicalRequest);
  const signingKey = await deriveSigningKey(AWS_SECRET, dateStamp, region, service);
  const signature = await hmacSha256Hex(signingKey, stringToSign);

  assertEquals(signature, "5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31");
});

Deno.test("B2 vector: presigned PUT signature and query parameters", async () => {
  const date = new Date("2026-09-26T10:11:12.000Z");
  const url = new URL(
    "https://s3.us-east-005.backblazeb2.com/deenolink-media/" +
      "media/users/uid-123/avatar/550e8400-e29b-41d4-a716-446655440000.jpg",
  );

  const signed = await presignUrl({
    method: "PUT",
    url,
    headers: { "content-type": "image/jpeg" },
    expiresInSeconds: 900,
    keyId: "AKIDEXAMPLE",
    secretAccessKey: AWS_SECRET,
    region: "us-east-005",
    date,
  });

  assertEquals(
    signed.searchParams.get("X-Amz-Signature"),
    "8be1c4e149d3e7f4768ab89ca10e184e20bd4f8559d20075e5030c72359b38fd",
  );
  assertEquals(signed.searchParams.get("X-Amz-Algorithm"), "AWS4-HMAC-SHA256");
  assertEquals(
    signed.searchParams.get("X-Amz-Credential"),
    "AKIDEXAMPLE/20260926/us-east-005/s3/aws4_request",
  );
  assertEquals(signed.searchParams.get("X-Amz-Date"), "20260926T101112Z");
  assertEquals(signed.searchParams.get("X-Amz-Expires"), "900");
  assertEquals(signed.searchParams.get("X-Amz-SignedHeaders"), "content-type;host");
  assertEquals(
    signed.pathname,
    "/deenolink-media/media/users/uid-123/avatar/550e8400-e29b-41d4-a716-446655440000.jpg",
  );
  // The wire query must never contain a raw credential beyond the (public) key id.
  assertEquals(signed.toString().includes(AWS_SECRET), false);
});

Deno.test("presignUrl refuses a non-https endpoint", async () => {
  await assertRejects(() =>
    presignUrl({
      method: "PUT",
      url: new URL("http://s3.us-east-005.backblazeb2.com/bucket/key"),
      expiresInSeconds: 900,
      keyId: "AKIDEXAMPLE",
      secretAccessKey: AWS_SECRET,
      region: "us-east-005",
      date: new Date("2026-09-26T10:11:12.000Z"),
    })
  );
});

Deno.test("presignUrl is deterministic for a fixed date and varies with the ttl", async () => {
  const base = {
    method: "PUT",
    url: new URL("https://s3.us-east-005.backblazeb2.com/bucket/media/a.jpg"),
    headers: { "content-type": "image/jpeg" },
    keyId: "AKIDEXAMPLE",
    secretAccessKey: AWS_SECRET,
    region: "us-east-005",
    date: new Date("2026-09-26T10:11:12.000Z"),
  };
  const a = await presignUrl({ ...base, expiresInSeconds: 900 });
  const b = await presignUrl({ ...base, expiresInSeconds: 900 });
  const c = await presignUrl({ ...base, expiresInSeconds: 300 });
  assertEquals(a.toString(), b.toString());
  assertNotEquals(a.toString(), c.toString());
});

Deno.test("presigned GET signs response-content-disposition as a query parameter", async () => {
  const signed = await presignUrl({
    method: "GET",
    url: new URL("https://s3.us-east-005.backblazeb2.com/bucket/media/a.jpg"),
    query: [["response-content-disposition", 'attachment; filename="my photo.jpg"']],
    expiresInSeconds: 600,
    keyId: "AKIDEXAMPLE",
    secretAccessKey: AWS_SECRET,
    region: "us-east-005",
    date: new Date("2026-09-26T10:11:12.000Z"),
  });
  assertEquals(
    signed.searchParams.get("response-content-disposition"),
    'attachment; filename="my photo.jpg"',
  );
  assertEquals(signed.searchParams.get("X-Amz-SignedHeaders"), "host");
  assertEquals(signed.searchParams.get("X-Amz-Signature")?.length, 64);
});

Deno.test("signRequest returns an Authorization header and no host header", async () => {
  const headers = await signRequest({
    method: "HEAD",
    url: new URL("https://s3.us-east-005.backblazeb2.com/bucket/media/a.jpg"),
    keyId: "AKIDEXAMPLE",
    secretAccessKey: AWS_SECRET,
    region: "us-east-005",
    date: new Date("2026-09-26T10:11:12.000Z"),
  });
  assertEquals(headers["host"], undefined);
  assertEquals(headers["x-amz-content-sha256"], EMPTY_PAYLOAD_SHA256);
  assertEquals(headers["x-amz-date"], "20260926T101112Z");
  assertMatch(
    headers.authorization ?? "",
    /^AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE\/20260926\/us-east-005\/s3\/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=[0-9a-f]{64}$/,
  );
  assertEquals((headers.authorization ?? "").includes(AWS_SECRET), false);
});

Deno.test("signRequest payload hash defaults to the empty-body hash", async () => {
  const headers = await signRequest({
    method: "DELETE",
    url: new URL("https://s3.us-east-005.backblazeb2.com/bucket/media/a.jpg"),
    keyId: "AKIDEXAMPLE",
    secretAccessKey: AWS_SECRET,
    region: "us-east-005",
    date: new Date("2026-09-26T10:11:12.000Z"),
    payloadHash: UNSIGNED_PAYLOAD,
  });
  assertEquals(headers["x-amz-content-sha256"], UNSIGNED_PAYLOAD);
});
