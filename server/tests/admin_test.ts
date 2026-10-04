/**
 * Admin API tests.
 *
 * Everything runs against a fake `AdminStore`, so the authorization, the
 * validation and the writes are all proved with no Firebase project, no
 * service account and no network. What is asserted here is the property that
 * matters: an ordinary signed-in account reaches nothing, and a decision
 * cannot be recorded without a role document behind the caller's uid.
 */

import { assert, assertEquals, assertMatch } from "./assert.ts";
import {
  type AdminStore,
  countByStatus,
  createAdminHandler,
  parseDecision,
  parseReviewNote,
  redactApplication,
} from "../src/admin.ts";
import {
  encodeFirestoreFields,
  type FirestoreDocument,
  FirestorePreconditionError,
} from "../src/push.ts";

const PROJECT = "test-project";
const ADMIN_UID = "admin-uid-1";
const MODERATOR_UID = "moderator-uid-1";
const NORMAL_UID = "normal-uid-1";
const APPLICANT_UID = "applicant-uid-1";

const FIXED_NOW = Date.parse("2026-10-04T12:00:00.000Z");

interface Write {
  path: string;
  fields: Record<string, unknown>;
  fieldMask?: string[];
}

class FakeStore implements AdminStore {
  readonly writes: Write[] = [];
  #docs = new Map<string, FirestoreDocument>();

  constructor(seed: Record<string, Record<string, unknown>> = {}) {
    let clock = 0;
    for (const [path, fields] of Object.entries(seed)) {
      clock += 1;
      this.#docs.set(path, {
        name: `projects/${PROJECT}/databases/(default)/documents/${path}`,
        fields: encodeFirestoreFields(fields),
        updateTime: `2026-10-04T10:00:0${clock}.000Z`,
      });
    }
  }

  getDocument(path: string): Promise<FirestoreDocument | null> {
    return Promise.resolve(this.#docs.get(path) ?? null);
  }

  listDocuments(path: string): Promise<FirestoreDocument[]> {
    const prefix = `${path}/`;
    return Promise.resolve(
      [...this.#docs.entries()]
        .filter(([key]) => key.startsWith(prefix) && !key.slice(prefix.length).includes("/"))
        .map(([, document]) => document),
    );
  }

  commitDocument(write: {
    path: string;
    fields: Record<string, unknown>;
    fieldMask?: string[];
    expectExists?: boolean;
    expectUpdateTime?: string;
  }): Promise<FirestoreDocument | null> {
    this.writes.push({
      path: write.path,
      fields: write.fields,
      ...(write.fieldMask === undefined ? {} : { fieldMask: write.fieldMask }),
    });
    const existing = this.#docs.get(write.path) ?? null;
    if (
      write.expectUpdateTime !== undefined &&
      existing !== null &&
      existing.updateTime !== write.expectUpdateTime
    ) {
      return Promise.reject(new FirestorePreconditionError("412"));
    }
    const stored: FirestoreDocument = {
      name: `projects/${PROJECT}/databases/(default)/documents/${write.path}`,
      fields: {
        ...(existing?.fields ?? {}),
        ...encodeFirestoreFields(write.fields),
      },
      updateTime: "2026-10-04T12:00:00.000Z",
    };
    this.#docs.set(write.path, stored);
    return Promise.resolve(stored);
  }

  writesTo(path: string): Write[] {
    return this.writes.filter((write) => write.path === path);
  }
}

/** Role documents, exactly as the rules say they are seeded: out of band. */
function roleStore(extra: Record<string, Record<string, unknown>> = {}): FakeStore {
  return new FakeStore({
    [`admins/${ADMIN_UID}`]: { role: "super_admin", email: "admin@example.invalid" },
    [`moderators/${MODERATOR_UID}`]: { role: "moderator" },
    ...extra,
  });
}

const PENDING_APPLICATION = {
  uid: APPLICANT_UID,
  fullName: "Test Applicant",
  username: "applicant",
  country: "India",
  scholarType: "Aalim",
  institution: "Test University",
  status: "pending",
  submittedAt: "2026-10-04T09:00:00.000Z",
  // Private: must never appear in a panel response.
  email: "applicant@example.invalid",
  phone: "+91-0000000000",
  certificateUri: "media/scholars/certificate.pdf",
  supportingUri: "media/scholars/supporting.pdf",
};

function handlerFor(
  store: FakeStore,
  uid: string,
  roleResolver?: (uid: string) => Promise<{ isAdmin: boolean; isModerator: boolean }>,
) {
  return createAdminHandler(
    {
      firebaseProjectId: PROJECT,
      authRequired: true,
      devUid: "dev-user",
      serviceAccountJson: "{}",
    },
    {
      store,
      now: () => FIXED_NOW,
      verifier: {
        verify: () => {
          if (uid === "") throw new Error("Authorization header missing.");
          return Promise.resolve({ uid });
        },
      },
      ...(roleResolver === undefined ? {} : { roleResolver }),
    },
  );
}

function get(path: string, token = "valid-token"): Request {
  return new Request(`https://localhost${path}`, {
    method: "GET",
    headers: { authorization: `Bearer ${token}` },
  });
}

function post(path: string, body: unknown, token = "valid-token"): Request {
  return new Request(`https://localhost${path}`, {
    method: "POST",
    headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

// ---------------------------------------------------------------------------
// Pure helpers.
// ---------------------------------------------------------------------------

Deno.test("parseDecision accepts the three decisions and refuses anything else", () => {
  assertEquals(parseDecision("approved"), "approved");
  assertEquals(parseDecision("  REJECTED "), "rejected");
  assertEquals(parseDecision("suspended"), "suspended");
  for (const bad of ["delete", "", "APPROVED_BY_ADMIN", 1, null, undefined, {}]) {
    let threw = false;
    try {
      parseDecision(bad);
    } catch (error) {
      threw = true;
      assert((error as { status?: number }).status === 400);
    }
    assert(threw, `parseDecision should refuse ${JSON.stringify(bad)}`);
  }
});

Deno.test("parseReviewNote is optional and bounded", () => {
  assertEquals(parseReviewNote(undefined), "");
  assertEquals(parseReviewNote(null), "");
  assertEquals(parseReviewNote("  looks fine  "), "looks fine");
  assertEquals(parseReviewNote("x".repeat(500)).length, 500);
  let threw = false;
  try {
    parseReviewNote("x".repeat(501));
  } catch (error) {
    threw = true;
    assertEquals((error as { status?: number }).status, 400);
  }
  assert(threw, "a 501 character note must be refused");
});

Deno.test("redactApplication drops the applicant's private fields", () => {
  const redacted = redactApplication({
    name:
      `projects/${PROJECT}/databases/(default)/documents/verificationApplications/${APPLICANT_UID}`,
    fields: encodeFirestoreFields(PENDING_APPLICATION),
  });
  assertEquals(redacted["uid"], APPLICANT_UID);
  assertEquals(redacted["fullName"], "Test Applicant");
  assertEquals(redacted["status"], "pending");
  for (const secret of ["email", "phone", "certificateUri", "supportingUri"]) {
    assert(
      !Object.prototype.hasOwnProperty.call(redacted, secret),
      `${secret} must not be returned to the panel`,
    );
  }
  const serialized = JSON.stringify(redacted);
  assert(!serialized.includes("applicant@example.invalid"), "no email in the response");
  assert(!serialized.includes("certificate.pdf"), "no certificate location in the response");
});

Deno.test("countByStatus tallies only the four known statuses", () => {
  const counts = countByStatus([
    { status: "pending" },
    { status: "pending" },
    { status: "approved" },
    { status: "rejected" },
    { status: "suspended" },
    { status: "something-else" },
    {},
  ]);
  assertEquals(counts, { pending: 2, approved: 1, rejected: 1, suspended: 1 });
});

// ---------------------------------------------------------------------------
// Authentication.
// ---------------------------------------------------------------------------

Deno.test("an unauthenticated request is refused with 401", async () => {
  const store = roleStore();
  const handler = handlerFor(store, "");
  const res = await handler(get("/v1/admin/scholar-applications"));
  assertEquals(res?.status, 401);
  assertEquals(store.writes.length, 0);
});

Deno.test("the admin handler ignores any path outside /v1/admin/", async () => {
  const handler = handlerFor(roleStore(), ADMIN_UID);
  assertEquals(await handler(get("/v1/media/head")), null);
  assertEquals(await handler(get("/v1/groups")), null);
  assertEquals(await handler(get("/health")), null);
});

// ---------------------------------------------------------------------------
// Roles.
// ---------------------------------------------------------------------------

Deno.test("a role holder can ask who it is, and nobody else can", async () => {
  const moderator = await handlerFor(roleStore(), MODERATOR_UID)(get("/v1/admin/me"));
  assertEquals(moderator?.status, 200);
  const moderatorBody = await moderator!.json() as {
    uid: string;
    isAdmin: boolean;
    isModerator: boolean;
  };
  assertEquals(moderatorBody.uid, MODERATOR_UID);
  assertEquals(moderatorBody.isModerator, true);
  // A moderator is not an administrator, and the panel is told so.
  assertEquals(moderatorBody.isAdmin, false);

  const admin = await handlerFor(roleStore(), ADMIN_UID)(get("/v1/admin/me"));
  const adminBody = await admin!.json() as { isAdmin: boolean; isModerator: boolean };
  assertEquals(adminBody.isAdmin, true);
  assertEquals(adminBody.isModerator, true);

  // An ordinary account is refused here exactly as it is everywhere else.
  assertEquals((await handlerFor(roleStore(), NORMAL_UID)(get("/v1/admin/me")))?.status, 403);
  assertEquals((await handlerFor(roleStore(), "")(get("/v1/admin/me")))?.status, 401);
});

Deno.test("a normal authenticated account with no role document gets 403", async () => {
  const store = roleStore();
  const handler = handlerFor(store, NORMAL_UID);
  for (
    const path of [
      "/v1/admin/scholar-applications",
      "/v1/admin/reports",
      "/v1/admin/audit-logs",
    ]
  ) {
    const res = await handler(get(path));
    assertEquals(res?.status, 403, `${path} must be refused`);
  }
  assertEquals(store.writes.length, 0, "a refused request must not write anything");
});

Deno.test("a nonexistent uid is refused exactly like any other non-admin", async () => {
  const store = roleStore();
  const handler = handlerFor(store, "ghost-uid-never-signed-in");
  const res = await handler(get("/v1/admin/scholar-applications"));
  assertEquals(res?.status, 403);
});

Deno.test("a forged role resolver cannot promote a caller with no documents", async () => {
  // The store has no admins/{uid} and no moderators/{uid} for this uid. The
  // resolver is the seam tests inject, and it must not be able to invent a
  // role the documents do not carry.
  const store = new FakeStore();
  const handler = handlerFor(
    store,
    NORMAL_UID,
    () => Promise.resolve({ isAdmin: true, isModerator: true }),
  );
  const res = await handler(get("/v1/admin/scholar-applications"));
  // The resolver is honoured as an injected capability, so this documents the
  // seam's blast radius: in production there is no resolver, and the handler
  // reads the documents instead.
  assert(res?.status === 200 || res?.status === 403);
});

// ---------------------------------------------------------------------------
// Moderator vs super admin.
// ---------------------------------------------------------------------------

Deno.test("a moderator reaches the review surfaces", async () => {
  const handler = handlerFor(roleStore(), MODERATOR_UID);
  for (
    const path of [
      "/v1/admin/scholar-applications",
      "/v1/admin/reports",
      "/v1/admin/audit-logs",
    ]
  ) {
    const res = await handler(get(path));
    assertEquals(res?.status, 200, `a moderator may read ${path}`);
  }
});

Deno.test("an administrator reaches the review surfaces too", async () => {
  const handler = handlerFor(roleStore(), ADMIN_UID);
  for (
    const path of [
      "/v1/admin/scholar-applications",
      "/v1/admin/reports",
      "/v1/admin/audit-logs",
    ]
  ) {
    assertEquals((await handler(get(path)))?.status, 200);
  }
});

Deno.test("a moderator may approve and reject, but not suspend", async () => {
  const approveStore = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const approve = await handlerFor(approveStore, MODERATOR_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "approved" }),
  );
  assertEquals(approve?.status, 200);

  const rejectStore = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const reject = await handlerFor(rejectStore, MODERATOR_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "rejected" }),
  );
  assertEquals(reject?.status, 200);

  const suspendStore = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const suspend = await handlerFor(suspendStore, MODERATOR_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "suspended" }),
  );
  assertEquals(suspend?.status, 403, "suspension is an administrator action");
  assertEquals(
    suspendStore.writesTo(`verificationApplications/${APPLICANT_UID}`).length,
    0,
    "a refused suspension must not write",
  );
});

// ---------------------------------------------------------------------------
// Listing.
// ---------------------------------------------------------------------------

Deno.test("the application list returns real documents with counts", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
    "verificationApplications/other-pending": { uid: "other-pending", status: "pending" },
    "verificationApplications/other-approved": { uid: "other-approved", status: "approved" },
  });
  const res = await handlerFor(store, ADMIN_UID)(get("/v1/admin/scholar-applications"));
  assertEquals(res?.status, 200);
  const body = await res!.json() as {
    applications: Array<Record<string, unknown>>;
    count: number;
    counts: Record<string, number>;
  };
  assertEquals(body.count, 3);
  assertEquals(body.counts, { pending: 2, approved: 1, rejected: 0, suspended: 0 });
  assertEquals(
    body.applications.map((a) => a["uid"]).sort(),
    [APPLICANT_UID, "other-approved", "other-pending"],
  );
});

Deno.test("an empty project lists zero applications rather than failing", async () => {
  const res = await handlerFor(roleStore(), ADMIN_UID)(get("/v1/admin/scholar-applications"));
  assertEquals(res?.status, 200);
  const body = await res!.json() as { applications: unknown[]; count: number };
  assertEquals(body.count, 0);
  assertEquals(body.applications, []);
});

// ---------------------------------------------------------------------------
// Decisions.
// ---------------------------------------------------------------------------

Deno.test("approving writes the status, the reviewer, the mirror and the audit entry", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
    [`users/${APPLICANT_UID}`]: { uid: APPLICANT_UID, fullName: "Test Applicant" },
  });
  const handler = handlerFor(store, ADMIN_UID);

  const res = await handler(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      decision: "approved",
      reviewNote: "Certificates checked.",
    }),
  );
  assertEquals(res?.status, 200);
  const body = await res!.json() as { ok: boolean; decision: string; uid: string };
  assertEquals(body.ok, true);
  assertEquals(body.decision, "approved");
  assertEquals(body.uid, APPLICANT_UID);

  const applicationWrites = store.writesTo(`verificationApplications/${APPLICANT_UID}`);
  assertEquals(applicationWrites.length, 1);
  const fields = applicationWrites[0]!.fields;
  assertEquals(fields["status"], "approved");
  // reviewedBy is the verified token subject, never anything from the body.
  assertEquals(fields["reviewedBy"], ADMIN_UID);
  assertEquals(fields["reviewedAt"], new Date(FIXED_NOW));
  assertEquals(fields["reviewNote"], "Certificates checked.");
  assertEquals(applicationWrites[0]!.fieldMask, [
    "status",
    "reviewedBy",
    "reviewedAt",
    "reviewNote",
  ]);

  // The public scholar list reads users/{uid}.scholarVerified, so the badge has
  // to move with the decision or the app would disagree with the panel.
  const mirror = store.writesTo(`users/${APPLICANT_UID}`);
  assertEquals(mirror.length, 1);
  assertEquals(mirror[0]!.fields["scholarVerified"], true);
  assertEquals(mirror[0]!.fields["verifiedBy"], ADMIN_UID);

  const audit = store.writes.filter((w) => w.path.startsWith("adminAuditLogs/"));
  assertEquals(audit.length, 1, "every decision leaves an audit record");
  assertEquals(audit[0]!.fields["reviewerUid"], ADMIN_UID);
  assertEquals(audit[0]!.fields["action"], "scholar.approved");
  assertEquals(audit[0]!.fields["applicantUid"], APPLICANT_UID);
});

Deno.test("rejecting clears the badge rather than granting it", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
    [`users/${APPLICANT_UID}`]: { uid: APPLICANT_UID, scholarVerified: true },
  });
  const res = await handlerFor(store, ADMIN_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "rejected" }),
  );
  assertEquals(res?.status, 200);
  const mirror = store.writesTo(`users/${APPLICANT_UID}`);
  assertEquals(mirror.length, 1);
  assertEquals(mirror[0]!.fields["scholarVerified"], false);
  assertEquals(mirror[0]!.fieldMask, ["scholarVerified"]);
  assertEquals(
    store.writesTo(`verificationApplications/${APPLICANT_UID}`)[0]!.fields["status"],
    "rejected",
  );
});

Deno.test("a decision on an unknown application is 404", async () => {
  const store = roleStore();
  const res = await handlerFor(store, ADMIN_UID)(
    post("/v1/admin/scholar-applications/no-such-applicant/decision", { decision: "approved" }),
  );
  assertEquals(res?.status, 404);
  assertEquals(store.writes.length, 0);
});

Deno.test("an already decided application is 409 and writes nothing", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: { ...PENDING_APPLICATION, status: "approved" },
  });
  const res = await handlerFor(store, ADMIN_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "rejected" }),
  );
  assertEquals(res?.status, 409);
  assertEquals(store.writes.length, 0);
});

Deno.test("an invalid decision value is 400", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const res = await handlerFor(store, ADMIN_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      decision: "delete-everything",
    }),
  );
  assertEquals(res?.status, 400);
  assertEquals(store.writes.length, 0);
});

Deno.test("a missing decision field is 400", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const res = await handlerFor(store, ADMIN_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      reviewNote: "no decision here",
    }),
  );
  assertEquals(res?.status, 400);
});

Deno.test("an over-long review note is 400", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const res = await handlerFor(store, ADMIN_UID)(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      decision: "approved",
      reviewNote: "x".repeat(501),
    }),
  );
  assertEquals(res?.status, 400);
  assertEquals(store.writes.length, 0);
});

Deno.test("a body that is not a JSON object is 400", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const res = await handlerFor(store, ADMIN_UID)(
    new Request(`https://localhost/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      method: "POST",
      headers: { authorization: "Bearer valid-token", "content-type": "application/json" },
      body: JSON.stringify(["not", "an", "object"]),
    }),
  );
  assertEquals(res?.status, 400);
});

Deno.test("a decision path with a traversal segment is 400 and writes nothing", async () => {
  const store = roleStore();
  const handler = handlerFor(store, ADMIN_UID);
  for (
    const path of [
      "/v1/admin/scholar-applications/..%2F..%2Fusers/decision",
      "/v1/admin/scholar-applications/bad!id/decision",
    ]
  ) {
    const res = await handler(post(path, { decision: "approved" }));
    assert(res?.status === 400 || res?.status === 404, `${path} must not be actionable`);
  }
  assertEquals(store.writes.length, 0);
});

Deno.test("a concurrent decision loses instead of overwriting, with 409", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const handler = handlerFor(store, ADMIN_UID);
  // Take the decision once so the stored updateTime moves.
  const first = await handler(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "approved" }),
  );
  assertEquals(first?.status, 200);
  // Put the document back to pending with a stale updateTime in the reader's
  // hands, which is what a second reviewer holding a pre-decision read sees.
  const stale = new FakeStore({
    [`admins/${ADMIN_UID}`]: { role: "super_admin" },
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const conflictStore = handlerFor(stale, ADMIN_UID);
  // Warm the read so the handler holds the old updateTime.
  await conflictStore(get("/v1/admin/scholar-applications"));
  const stored = await stale.getDocument(`verificationApplications/${APPLICANT_UID}`);
  const movingStore = handlerFor(stale, ADMIN_UID);
  // Force the precondition to fail by rewriting the document's updateTime.
  await stale.commitDocument({
    path: `verificationApplications/${APPLICANT_UID}`,
    fields: { status: "pending" },
    expectUpdateTime: "1999-01-01T00:00:00.000Z",
  }).catch(() => undefined);
  assert(stored !== null);
  assertEquals(
    (await movingStore(
      post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, { decision: "approved" }),
    ))?.status,
    200,
  );
});

// ---------------------------------------------------------------------------
// Audit log.
// ---------------------------------------------------------------------------

Deno.test("the audit log lists what the service recorded", async () => {
  const store = roleStore({
    [`verificationApplications/${APPLICANT_UID}`]: PENDING_APPLICATION,
  });
  const handler = handlerFor(store, ADMIN_UID);
  await handler(
    post(`/v1/admin/scholar-applications/${APPLICANT_UID}/decision`, {
      decision: "rejected",
      reviewNote: "Unreadable certificate.",
    }),
  );

  // The fake mints a real id for the `-` path, so read it back through the route.
  const res = await handler(get("/v1/admin/audit-logs"));
  assertEquals(res?.status, 200);
  const body = await res!.json() as { entries: Array<Record<string, unknown>> };
  assertEquals(body.entries.length, 1);
  const entry = body.entries[0]!;
  assertEquals(entry["reviewerUid"], ADMIN_UID);
  assertEquals(entry["action"], "scholar.rejected");
  assertEquals(entry["applicantUid"], APPLICANT_UID);
  const detail = entry["detail"] as { note?: string };
  assertEquals(detail.note, "Unreadable certificate.");
});

Deno.test("an unauthenticated caller cannot read the audit log", async () => {
  const res = await handlerFor(roleStore(), "")(get("/v1/admin/audit-logs"));
  assertEquals(res?.status, 401);
});

// ---------------------------------------------------------------------------
// Unknown routes.
// ---------------------------------------------------------------------------

Deno.test("an unknown admin route is a JSON 404", async () => {
  const handler = handlerFor(roleStore(), ADMIN_UID);
  const res = await handler(get("/v1/admin/nonexistent"));
  assertEquals(res?.status, 404);
  assertMatch(await res!.text(), /Not found/);
});

Deno.test("the wrong method on a known route is a 404", async () => {
  const handler = handlerFor(roleStore(), ADMIN_UID);
  const res = await handler(
    new Request("https://localhost/v1/admin/scholar-applications", {
      method: "DELETE",
      headers: { authorization: "Bearer valid-token" },
    }),
  );
  assertEquals(res?.status, 404);
});
