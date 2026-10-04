/**
 * Admin and Moderator backend APIs for the DeenoLink staging admin panel.
 *
 * Authorization is decided here, on the server, and never by the caller:
 *   - `admins/{uid}` exists      -> super admin, every /v1/admin/* route.
 *   - `moderators/{uid}` exists  -> moderator, and only the three review
 *                                   surfaces below.
 *   - anything else              -> 403, even with a perfectly valid token.
 *   - no/invalid token           -> 401.
 *
 * The hardcoded "everyone is an admin" default that used to live in
 * `resolveRoles` is gone. A uid with no role document resolves to no roles,
 * so an ordinary signed-in account is refused rather than trusted.
 *
 * Reads of an application go through `redactApplication`: an application
 * carries an email address, a phone number and the URIs of the certificate
 * and supporting documents the applicant uploaded, and the panel list is not
 * the place those belong. A reviewer who needs the document itself fetches it
 * from storage through the media service, which is the only component that
 * knows how to sign for it.
 *
 * Every decision writes an audit record naming the reviewer, so an approval
 * cannot be made without leaving a trace of who made it.
 */

import { type AuthenticatedUser, FirebaseTokenVerifier } from "./auth.ts";
import {
  decodeFirestoreFields,
  FIRESTORE_SCOPE,
  type FirestoreDocument,
  FirestoreRestGateway,
  GoogleAccessTokenProvider,
  parseServiceAccountJson,
} from "./push.ts";
import { ConfigError } from "./config.ts";

export class AdminError extends Error {
  override name = "AdminError";
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/** The request handler shape `createAdminHandler` returns. */
export type AdminHandler = (request: Request) => Promise<Response | null>;

/**
 * The slice of Firestore this module needs.
 *
 * Narrower than `FirestoreRestGateway` on purpose: a test supplies a map and
 * asserts on the writes that were issued, so the authorization and the
 * validation are proved without a Firebase project, a service account, or a
 * network.
 */
export interface AdminStore {
  getDocument(path: string): Promise<FirestoreDocument | null>;
  listDocuments(path: string): Promise<FirestoreDocument[]>;
  commitDocument(write: {
    path: string;
    fields: Record<string, unknown>;
    fieldMask?: string[];
    expectExists?: boolean;
    expectUpdateTime?: string;
  }): Promise<FirestoreDocument | null>;
}

export interface AdminSettings {
  firebaseProjectId: string;
  authRequired: boolean;
  devUid: string;
  /** Raw service-account JSON. Never logged, never returned. */
  serviceAccountJson: string;
}

export interface AdminDeps {
  now?: () => number;
  fetchImpl?: typeof fetch;
  verifier?: { verify(auth: string | null | undefined): Promise<AuthenticatedUser> };
  store?: AdminStore;
}

const ADMINS = "admins";
const MODERATORS = "moderators";
const APPLICATIONS = "verificationApplications";
const AUDIT_LOGS = "adminAuditLogs";
const USERS = "users";
const REPORTS = "reports";

/** Decision values the panel may record. Anything else is a 400. */
export const DECISION_APPROVED = "approved";
export const DECISION_REJECTED = "rejected";
export const DECISION_SUSPENDED = "suspended";
const DECISIONS = [DECISION_APPROVED, DECISION_REJECTED, DECISION_SUSPENDED] as const;

/** A decision may only be made on an application nobody has decided yet. */
const PENDING = "pending";

const MAX_REVIEW_NOTE_LENGTH = 500;
const MAX_BODY_BYTES = 16 * 1024;

/**
 * Fields an application may carry, per `firestore.rules`.
 *
 * `email`, `phone`, `certificateUri` and `supportingUri` are deliberately
 * absent: they are the applicant's private contact details and the locations
 * of the qualification documents, and the panel list has no use for them.
 */
const LISTED_FIELDS = [
  "uid",
  "fullName",
  "username",
  "country",
  "region",
  "city",
  "scholarType",
  "expertise",
  "institution",
  "qualification",
  "specialization",
  "educationYears",
  "experienceYears",
  "introduction",
  "status",
  "submittedAt",
  "reviewedAt",
  "reviewedBy",
  "reviewNote",
] as const;

const SCHOLAR_APPLICATIONS = "scholar-applications";
/** URL segment for the audit trail. The collection behind it is adminAuditLogs. */
const AUDIT_LOG_ROUTE = "audit-logs";

/** The routes a moderator is allowed to reach. */
const MODERATOR_PREFIXES = [
  `/v1/admin/${SCHOLAR_APPLICATIONS}`,
  `/v1/admin/${REPORTS}`,
  `/v1/admin/${AUDIT_LOG_ROUTE}`,
] as const;

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body, null, 2), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
    },
  });
}

/** A uid taken from a URL path, checked before it is interpolated anywhere. */
export function parsePathUid(segment: string): string {
  if (segment.length === 0 || segment.length > 128 || !/^[A-Za-z0-9_-]+$/.test(segment)) {
    throw new AdminError("That applicant id is not valid.", 400);
  }
  return segment;
}

export function parseDecision(value: unknown): typeof DECISIONS[number] {
  if (typeof value !== "string") {
    throw new AdminError('Field "decision" is required.', 400);
  }
  const decision = value.trim().toLowerCase();
  if (!(DECISIONS as readonly string[]).includes(decision)) {
    throw new AdminError(
      `Field "decision" must be one of: ${DECISIONS.join(", ")}.`,
      400,
    );
  }
  return decision as typeof DECISIONS[number];
}

/** A review note: optional, and bounded the way the rules bound it. */
export function parseReviewNote(value: unknown): string {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") {
    throw new AdminError('Field "reviewNote" must be a string.', 400);
  }
  const note = value.trim();
  if (note.length > MAX_REVIEW_NOTE_LENGTH) {
    throw new AdminError(
      `Field "reviewNote" may be at most ${MAX_REVIEW_NOTE_LENGTH} characters.`,
      400,
    );
  }
  return note;
}

/**
 * One application, reduced to the fields the panel may show.
 *
 * The uid comes from the document id, which is the applicant's own uid by the
 * rules' key choice, so it cannot be spoofed by a stored field.
 */
export function redactApplication(document: FirestoreDocument): Record<string, unknown> {
  // Firestore REST hands back encoded values; the panel gets plain ones.
  const fields = decodeFirestoreFields(document.fields);
  const uid = document.name.split("/").pop() ?? "";
  const out: Record<string, unknown> = { uid };
  for (const field of LISTED_FIELDS) {
    if (field === "uid") continue;
    if (Object.prototype.hasOwnProperty.call(fields, field)) out[field] = fields[field];
  }
  return out;
}

/** Counts by status, so the panel's summary cards come from the data itself. */
export function countByStatus(
  applications: readonly Record<string, unknown>[],
): Record<string, number> {
  const counts: Record<string, number> = {
    pending: 0,
    approved: 0,
    rejected: 0,
    suspended: 0,
  };
  for (const application of applications) {
    const status = application["status"];
    if (typeof status === "string" && Object.prototype.hasOwnProperty.call(counts, status)) {
      counts[status] = (counts[status] ?? 0) + 1;
    }
  }
  return counts;
}

async function readJsonBody(request: Request): Promise<Record<string, unknown>> {
  if (request.body === null) throw new AdminError("A JSON request body is required.", 400);
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    if (value === undefined) continue;
    total += value.byteLength;
    if (total > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new AdminError("Request body is too large.", 413);
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
    const parsed: unknown = JSON.parse(new TextDecoder().decode(raw));
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
  } catch {
    throw new AdminError("Request body must be a JSON object.", 400);
  }
}

export function createAdminHandler(
  settings: AdminSettings,
  deps: AdminDeps = {},
): AdminHandler {
  const now = deps.now ?? Date.now;
  const fetchImpl = deps.fetchImpl ?? fetch;

  // Built only when auth is on, so a test with no project never reaches for the
  // token key service.
  const verifier = deps.verifier ??
    (settings.authRequired
      ? new FirebaseTokenVerifier({ projectId: settings.firebaseProjectId, fetchImpl })
      : null);

  const store: AdminStore = deps.store ?? createAdminStore(settings, fetchImpl);

  async function authenticate(request: Request): Promise<string> {
    // The deliberate local-dev bypass, and it is the only path that does not
    // verify a signature. authRequired is false only when the operator set the
    // documented dev flag, which loadSettings refuses in production.
    if (verifier === null) return settings.devUid;
    const user = await verifier.verify(request.headers.get("authorization"));
    return user.uid;
  }

  /**
   * What the caller's uid is actually entitled to.
   *
   * Both reads go through the service account, so they answer for the
   * collections the rules close to clients: no account can write itself an
   * `admins` or `moderators` document, which is what makes this the only place
   * a role can come from. A uid with neither document gets neither role.
   */
  async function resolveRoles(uid: string): Promise<{ isAdmin: boolean; isModerator: boolean }> {
    const [admin, moderator] = await Promise.all([
      store.getDocument(`${ADMINS}/${uid}`),
      store.getDocument(`${MODERATORS}/${uid}`),
    ]);
    const isAdmin = admin !== null;
    // An admin is also a moderator: the review surfaces are a subset of what an
    // admin may do, so it has to be one or the admin would be refused them.
    return { isAdmin, isModerator: isAdmin || moderator !== null };
  }

  function assertAuthorized(
    roles: { isAdmin: boolean; isModerator: boolean },
    path: string,
  ): void {
    if (!roles.isAdmin && !roles.isModerator) {
      throw new AdminError("Administrator or moderator privileges are required.", 403);
    }
    // `/v1/admin/me` is how the panel learns its own permissions, so it has to
    // be answerable before the per-role surface list applies.
    if (path === "/v1/admin/me") return;
    if (!roles.isAdmin && !MODERATOR_PREFIXES.some((prefix) => path.startsWith(prefix))) {
      throw new AdminError(
        "Moderator access is limited to scholar review, reports and audit logs.",
        403,
      );
    }
  }

  async function listApplications(): Promise<Record<string, unknown>[]> {
    const documents = await store.listDocuments(APPLICATIONS);
    return documents.map(redactApplication);
  }

  /** The audit trail. Appended by the service, never by a client. */
  async function writeAuditEntry(entry: {
    reviewerUid: string;
    action: string;
    applicantUid?: string;
    detail?: Record<string, unknown>;
  }): Promise<void> {
    await store.commitDocument({
      path: `${AUDIT_LOGS}/-`,
      fields: {
        reviewerUid: entry.reviewerUid,
        action: entry.action,
        applicantUid: entry.applicantUid ?? "",
        detail: entry.detail ?? {},
        createdAt: new Date(now()),
      },
      expectExists: false,
    });
  }

  /**
   * Records a decision on a pending application.
   *
   * The write is guarded by the document's `updateTime`, so two reviewers
   * pressing Approve at the same moment cannot both land: the second gets a 409
   * and re-reads, rather than silently overwriting the first decision.
   */
  async function decide(
    reviewerUid: string,
    applicantUid: string,
    decision: typeof DECISIONS[number],
    note: string,
  ): Promise<Record<string, unknown>> {
    const path = `${APPLICATIONS}/${applicantUid}`;
    const stored = await store.getDocument(path);
    if (stored === null) throw new AdminError("No such scholar application.", 404);
    // Firestore REST returns encoded values, so the status has to be decoded
    // before it can be compared.
    if (decodeFirestoreFields(stored.fields)["status"] !== PENDING) {
      throw new AdminError("That application has already been decided.", 409);
    }

    const decidedAt = new Date(now());
    const fields: Record<string, unknown> = {
      status: decision,
      reviewedBy: reviewerUid,
      reviewedAt: decidedAt,
    };
    const fieldMask = ["status", "reviewedBy", "reviewedAt"];
    if (note !== "") {
      fields["reviewNote"] = note;
      fieldMask.push("reviewNote");
    }

    try {
      await store.commitDocument({
        path,
        fields,
        fieldMask,
        ...(stored.updateTime === undefined ? {} : { expectUpdateTime: stored.updateTime }),
      });
    } catch (error) {
      if (error instanceof Error && error.name === "FirestorePreconditionError") {
        throw new AdminError(
          "Another reviewer decided this application first. Reload and try again.",
          409,
        );
      }
      throw error;
    }

    // The public scholar list is served from `users/{uid}.scholarVerified`, not
    // from this collection, so an approval that did not move that flag would
    // leave the app showing an unverified scholar the panel had approved. The
    // mirror is written by the service account, which bypasses the rules, so it
    // is this service's job to keep the two in step.
    await mirrorProfileDecision(applicantUid, decision, reviewerUid, decidedAt);

    await writeAuditEntry({
      reviewerUid,
      action: `scholar.${decision}`,
      applicantUid,
      detail: { note },
    });

    return { uid: applicantUid, decision, reviewedBy: reviewerUid, reviewedAt: decidedAt };
  }

  async function mirrorProfileDecision(
    applicantUid: string,
    decision: typeof DECISIONS[number],
    reviewerUid: string,
    decidedAt: Date,
  ): Promise<void> {
    const fields: Record<string, unknown> = decision === DECISION_APPROVED
      ? {
        scholarVerified: true,
        verifiedBy: reviewerUid,
        verifiedAt: decidedAt,
      }
      : {
        scholarVerified: false,
      };
    const fieldMask = decision === DECISION_APPROVED
      ? ["scholarVerified", "verifiedBy", "verifiedAt"]
      : ["scholarVerified"];
    try {
      await store.commitDocument({ path: `${USERS}/${applicantUid}`, fields, fieldMask });
    } catch (error) {
      // The decision itself is already recorded, so this is logged rather than
      // thrown: answering 500 here would tell the reviewer nothing was decided
      // when it was. The next reconcile pass fixes the mirror.
      console.error(
        `Could not mirror the scholar decision onto users/${applicantUid}:`,
        error instanceof Error ? error.message : "unknown error",
      );
    }
  }

  return async function handleAdmin(request: Request): Promise<Response | null> {
    const url = new URL(request.url);
    const path = url.pathname;
    // Not ours: the caller must fall through to its own routes.
    if (!path.startsWith("/v1/admin/")) return null;
    const method = request.method.toUpperCase();

    try {
      const uid = await authenticate(request);
      const roles = await resolveRoles(uid);

      // Everything below this line needs a role, so the check comes before any
      // route can read a document.
      assertAuthorized(roles, path);

      // Who the panel is talking to, and what it may offer them. The role comes
      // from the documents, never from the request, so this is a report of the
      // authorization decision rather than a way to influence it. It is reachable
      // by any role: a moderator has to be able to discover that suspending is
      // not one of their actions.
      if (method === "GET" && path === "/v1/admin/me") {
        return jsonResponse(200, { uid, isAdmin: roles.isAdmin, isModerator: roles.isModerator });
      }

      if (method === "GET" && path === `/v1/admin/${SCHOLAR_APPLICATIONS}`) {
        const applications = await listApplications();
        return jsonResponse(200, {
          applications,
          count: applications.length,
          counts: countByStatus(applications),
        });
      }

      if (method === "GET" && path === `/v1/admin/${REPORTS}`) {
        const documents = await store.listDocuments(REPORTS);
        return jsonResponse(200, {
          reports: documents.map((document) => ({
            id: document.name.split("/").pop() ?? "",
            ...decodeFirestoreFields(document.fields),
          })),
        });
      }

      if (method === "GET" && path === `/v1/admin/${AUDIT_LOG_ROUTE}`) {
        const documents = await store.listDocuments(AUDIT_LOGS);
        return jsonResponse(200, {
          entries: documents.map((document) => ({
            id: document.name.split("/").pop() ?? "",
            ...decodeFirestoreFields(document.fields),
          })),
        });
      }

      const decisionMatch = /^\/v1\/admin\/scholar-applications\/([^/]+)\/decision$/.exec(path);
      if (decisionMatch !== null && method === "POST") {
        const body = await readJsonBody(request);
        const decision = parseDecision(body["decision"]);
        // A suspend lifts a badge a moderator may have granted, so it stays on
        // the admin surface: a moderator can move an application forward or
        // turn it down, but only an administrator can take a scholar back down.
        if (decision === DECISION_SUSPENDED && !roles.isAdmin) {
          throw new AdminError("Only an administrator may suspend a verified scholar.", 403);
        }
        const note = parseReviewNote(body["reviewNote"]);
        const result = await decide(uid, parsePathUid(decisionMatch[1] ?? ""), decision, note);
        return jsonResponse(200, { ok: true, ...result });
      }

      throw new AdminError("Not found.", 404);
    } catch (error) {
      if (error instanceof AdminError) {
        return jsonResponse(error.status, { error: error.message });
      }
      // A token failure is a 401, not a 500: the caller is not authenticated,
      // whatever the underlying verifier said.
      if (error instanceof Error && isTokenFailure(error)) {
        return jsonResponse(401, { error: "Authentication is required." });
      }
      // The message is logged and never returned: an unexpected failure could
      // echo a project path or a document field back to the caller.
      console.error("Admin API error:", error instanceof Error ? error.message : "unknown error");
      return jsonResponse(500, { error: "Internal server error." });
    }
  };
}

function isTokenFailure(error: Error): boolean {
  return error.name === "MediaError" ||
    /token|bearer|authorization|header/i.test(error.message);
}

function createAdminStore(settings: AdminSettings, fetchImpl: typeof fetch): AdminStore {
  if (settings.serviceAccountJson === "") {
    throw new ConfigError(
      "The admin API needs FIREBASE_SERVICE_ACCOUNT_JSON to read role documents. Set it " +
        "in the server environment.",
    );
  }
  const credential = parseServiceAccountJson(settings.serviceAccountJson);
  // The token is verified against one project and the roles are read from
  // another if these ever disagree, which would mean authorizing against a
  // collection nobody audited. Refuse rather than quietly read the wrong one.
  if (credential.projectId !== settings.firebaseProjectId) {
    throw new ConfigError(
      "The service account belongs to a different Firebase project than the one this " +
        "server authenticates. The admin API reads role documents from the authenticated " +
        "project only.",
    );
  }
  const tokens = new GoogleAccessTokenProvider(credential, {
    scope: FIRESTORE_SCOPE,
    fetchImpl,
  });
  return new FirestoreRestGateway(credential.projectId, tokens, { fetchImpl });
}
