/**
 * Group creation and membership changes, validated on the server.
 *
 * Why this exists: `firestore.rules` used to require that every id in a group's
 * `memberIds` named a real account, written as
 * `ids.every(uid, exists(/databases/$(database)/documents/users/$(uid)))`. Firestore
 * Rules have no list iteration and no `every`, so that check was not a
 * restriction at all - the expression raised "Function not found error" and the
 * whole rule branch was denied. Every group create and every membership change
 * failed, for everyone.
 *
 * The check cannot be repaired in the rules. It is an identity question about
 * every element of a list, and the only way a rule can ask it is one `exists()`
 * at a time, which a rule cannot loop over. So it moved here, where the service
 * account can read `users/{uid}` once per id and hold a loop.
 *
 * What this service is responsible for:
 * - Every id in a member list is a real account. A fabricated id is refused.
 * - The creator is the authenticated caller, never a value from the body.
 * - The admin list is derived, not accepted. A client cannot promote itself,
 *   promote a stranger, or hand the group to somebody else.
 * - `memberCount` is computed from the member list, so it cannot drift.
 * - Only a creator or admin may change membership, checked against the stored
 *   document rather than anything the caller asserts.
 * - The creator is never removable, because a group always keeps the person who
 *   made it.
 *
 * What this service deliberately does NOT do: it does not decide who may read a
 * group or post in it. Those stay in `firestore.rules`, which still governs
 * every client read and write. This service only makes the *writes that change
 * membership*, which the rules can no longer validate for themselves.
 *
 * The writes use the service account, so they bypass Firestore Rules entirely -
 * which is exactly why the authorization above is re-implemented here and why
 * the rules are changed to refuse client membership writes rather than to trust
 * a shape check. A rule and this service are two halves of one decision: the
 * rules refuse what this service performs, and this service refuses what it
 * cannot prove.
 */

import {
  FIRESTORE_SCOPE,
  GoogleAccessTokenProvider,
  FirestoreRestGateway,
  FirestorePreconditionError,
  assertDocumentId,
  parseServiceAccountJson,
  type FirestoreDocument,
  type FirestoreGateway,
  type FirestoreWriter,
  type ServiceAccountCredential,
} from "./push.ts";
import { ConfigError } from "./config.ts";

/** A group rejection. `status` is the HTTP status the route answers with. */
export class GroupError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "GroupError";
    this.status = status;
  }
}

const GROUPS = "groups";
const USERS = "users";

/** Mirrors MAX_GROUP_MEMBERS in `push.ts`, which sizes a push fan-out. */
const MAX_GROUP_MEMBERS = 500;
const MAX_NAME_LENGTH = 60;
const MAX_DESCRIPTION_LENGTH = 200;
const MAX_TOPIC_LENGTH = 60;
/** The rules cap the stored initial at four characters. */
const MAX_INITIAL_LENGTH = 4;

// ---------------------------------------------------------------------------
// Ports, so the rules above can be tested with no Firebase project and no key.
// ---------------------------------------------------------------------------

export interface GroupStore extends FirestoreGateway, FirestoreWriter {}

export interface GroupServiceDeps {
  store: GroupStore;
  now: () => number;
}

export interface GroupSettings {
  readonly serviceAccountJson: string;
}

// ---------------------------------------------------------------------------
// Pure validation, exported so it can be tested without any I/O.
// ---------------------------------------------------------------------------

/** A validated group-create request. Nothing here came from the caller's claims. */
export interface GroupCreateRequest {
  name: string;
  description: string;
  topic: string;
  /** Ids the caller asked to add. The creator is added by the server. */
  memberIds: string[];
}

/**
 * A group name: present, trimmed, and within the length the rules allow.
 */
export function normalizeGroupName(value: string): string {
  const name = value.trim();
  if (name.length === 0) {
    throw new GroupError("Enter a group name.", 400);
  }
  if (name.length > MAX_NAME_LENGTH) {
    throw new GroupError(`A group name may be at most ${MAX_NAME_LENGTH} characters.`, 400);
  }
  return name;
}

/**
 * The requested member ids, cleaned and bounded.
 *
 * Ids are validated for shape here and for *reality* in the service, because a
 * well-formed id is still a guess until a `users/{uid}` document answers for
 * it. Duplicates collapse, and a caller may not name somebody twice to pad the
 * list.
 */
export function normalizeRequestedMemberIds(values: readonly string[]): string[] {
  const seen = new Set<string>();
  for (const value of values) {
    if (typeof value !== "string") {
      throw new GroupError("A participant id was not a string.", 400);
    }
    // assertDocumentId rejects `..`, `/` and a leading dash, so an id can be
    // interpolated into a Firestore document path without escaping it.
    const uid = assertDocumentId(value, "memberIds");
    seen.add(uid);
  }
  if (seen.size > MAX_GROUP_MEMBERS) {
    throw new GroupError(`A group may have at most ${MAX_GROUP_MEMBERS} members.`, 400);
  }
  return [...seen];
}

/**
 * The two-letter initial the group list renders.
 *
 * Derived from the name rather than accepted, so it cannot be used to smuggle
 * arbitrary text into a document the list draws.
 */
export function groupInitial(name: string): string {
  return [...name].slice(0, 2).join("").toUpperCase();
}

/**
 * The document a group create writes.
 *
 * Every field is either validated, derived or server-owned. `creatorId`,
 * `adminIds` and `memberCount` are not parameters on purpose: they are decided
 * here, so no request can state them.
 */
export function buildGroupFields(input: {
  creatorId: string;
  memberIds: string[];
  name: string;
  description: string;
  topic: string;
  timestamp: Date;
}): Record<string, unknown> {
  const name = normalizeGroupName(input.name);
  const memberIds = [...input.memberIds];
  if (memberIds.length === 0) {
    throw new GroupError("A group needs at least one member.", 400);
  }
  if (memberIds.length > MAX_GROUP_MEMBERS) {
    throw new GroupError(`A group may have at most ${MAX_GROUP_MEMBERS} members.`, 400);
  }
  if (!memberIds.includes(input.creatorId)) {
    // The creator is always a member, so this is a server-side invariant rather
    // than something a client can get wrong.
    throw new GroupError("A group creator has to be one of its members.", 400);
  }
  const description = input.description.trim().slice(0, MAX_DESCRIPTION_LENGTH);
  const topic = input.topic.trim().slice(0, MAX_TOPIC_LENGTH);
  const stamp = input.timestamp;
  return {
    creatorId: input.creatorId,
    memberIds,
    // The creator is the sole admin of a group they just made. Promoting anybody
    // else is a separate, later, admin-only action.
    adminIds: [input.creatorId],
    // Computed from the list that is actually being written, so the number a
    // group shows cannot disagree with the members it has.
    memberCount: memberIds.length,
    name,
    description,
    topic,
    initial: groupInitial(name).slice(0, MAX_INITIAL_LENGTH),
    membersCanSend: true,
    membersCanAddParticipants: true,
    // The service's clock. A client cannot backdate or postdate a group.
    createdAt: stamp,
    updatedAt: stamp,
    lastMessage: "",
    lastMessageAt: stamp,
  };
}

/** A stored group, reduced to what a membership decision needs. */
export interface StoredGroup {
  id: string;
  creatorId: string;
  memberIds: string[];
  adminIds: string[];
  updateTime: string | undefined;
}

function readStringList(fields: Record<string, unknown>, key: string): string[] {
  const value = fields[key];
  if (!Array.isArray(value)) {
    throw new GroupError("The stored group is malformed.", 500);
  }
  return value.filter((item): item is string => typeof item === "string");
}

/** Parses a stored group document, refusing one that is not shaped like a group. */
export function parseStoredGroup(document: FirestoreDocument): StoredGroup {
  const fields = document.fields;
  const creatorId = fields["creatorId"];
  if (typeof creatorId !== "string" || creatorId === "") {
    throw new GroupError("The stored group is malformed.", 500);
  }
  return {
    id: document.name.split("/").pop() ?? "",
    creatorId,
    memberIds: readStringList(fields, "memberIds"),
    adminIds: readStringList(fields, "adminIds"),
    updateTime: document.updateTime,
  };
}

/** A creator or an admin. Read from the stored document, never from the caller. */
export function callerIsGroupAdmin(group: StoredGroup, uid: string): boolean {
  return group.creatorId === uid || group.adminIds.includes(uid);
}

// ---------------------------------------------------------------------------
// The service.
// ---------------------------------------------------------------------------

export class GroupService {
  readonly #store: GroupStore;
  readonly #now: () => number;

  constructor(deps: GroupServiceDeps) {
    this.#store = deps.store;
    this.#now = deps.now;
  }

  get enabled(): boolean {
    return true;
  }

  /**
   * Confirms that a uid names an account this deployment knows about.
   *
   * A `users/{uid}` document is the app's own record of a real Firebase Auth
   * account: the rules let a client create one only as itself, and every
   * profile the app can show is one of these. An id with no such document is
   * either a typo, a seeded demo id, or an attempt to invent a member, and all
   * three are refused identically so the refusal tells an attacker nothing
   * about which case it was.
   */
  async #assertRealAccounts(uids: readonly string[]): Promise<void> {
    // One read per id, in bounded batches: a large group must not turn into an
    // unbounded burst of requests.
    const BATCH = 20;
    for (let index = 0; index < uids.length; index += BATCH) {
      const batch = uids.slice(index, index + BATCH);
      const found = await Promise.all(
        batch.map((uid) => this.#store.getDocument(`${USERS}/${uid}`)),
      );
      const missing = batch.filter((_, position) => found[position] === null);
      if (missing.length > 0) {
        throw new GroupError("Some participants do not have an account yet.", 400);
      }
    }
  }

  async #readGroup(groupId: string): Promise<StoredGroup> {
    const path = `${GROUPS}/${assertDocumentId(groupId, "groupId")}`;
    const document = await this.#store.getDocument(path);
    if (document === null) {
      throw new GroupError("No such group.", 404);
    }
    return parseStoredGroup(document);
  }

  /**
   * Creates a group whose member list has been checked account by account.
   *
   * The caller is the creator. The requested ids are cleaned, the creator is
   * added to them, and only then is every id - including the creator's own -
   * confirmed to be a real account. Firestore mints the document id, so the
   * group is named by the service rather than by a client's clock.
   */
  async createGroup(creatorId: string, request: GroupCreateRequest): Promise<StoredGroup> {
    const name = normalizeGroupName(request.name);
    const requested = normalizeRequestedMemberIds(request.memberIds);
    // The creator is a member whether or not the client listed itself, and a
    // client cannot list somebody else in its place.
    const memberIds = [...new Set([creatorId, ...requested])];
    await this.#assertRealAccounts(memberIds);

    const fields = buildGroupFields({
      creatorId,
      memberIds,
      name,
      description: request.description,
      topic: request.topic,
      timestamp: new Date(this.#now()),
    });
    // `-` asks Firestore for the id, so two clients creating at the same instant
    // cannot collide, and `exists: false` means this create can never overwrite
    // a document that somehow already exists.
    const committed = await this.#store.commitDocument({
      path: `${GROUPS}/-`,
      fields,
      expectExists: false,
    });
    const id = (committed?.name ?? "").split("/").pop() ?? "";
    if (id === "" || id === "-") {
      throw new GroupError("The group could not be stored.", 502);
    }
    return { id, creatorId, memberIds, adminIds: [creatorId], updateTime: committed?.updateTime };
  }

  /**
   * Adds real accounts to a group. Creator/admin only.
   *
   * The new list is computed from the stored one, so an addition cannot drop
   * anybody, and the write carries the document's `updateTime` so two admins
   * acting at once cannot lose one of the two additions.
   */
  async addMembers(uid: string, groupId: string, requestedIds: readonly string[]): Promise<StoredGroup> {
    const group = await this.#readGroup(groupId);
    if (!callerIsGroupAdmin(group, uid)) {
      throw new GroupError("Only a group admin can add members.", 403);
    }
    const requested = normalizeRequestedMemberIds(requestedIds);
    if (requested.length === 0) {
      throw new GroupError("No participants were given.", 400);
    }
    const added = requested.filter((candidate) => !group.memberIds.includes(candidate));
    if (added.length === 0) {
      // Everyone asked for is already a member: a no-op is reported as a
      // success with the unchanged list, not as a failure.
      return group;
    }
    const memberIds = [...group.memberIds, ...added];
    if (memberIds.length > MAX_GROUP_MEMBERS) {
      throw new GroupError(`A group may have at most ${MAX_GROUP_MEMBERS} members.`, 400);
    }
    // Only the ids that are actually new have to be checked; the ones already
    // stored were checked when they were admitted.
    await this.#assertRealAccounts(added);
    return await this.#writeMembership(group, { memberIds });
  }

  /**
   * Removes one member. Creator/admin only, and never the creator.
   */
  async removeMember(uid: string, groupId: string, memberId: string): Promise<StoredGroup> {
    const group = await this.#readGroup(groupId);
    const target = assertDocumentId(memberId, "memberId");
    // Leaving is this endpoint too, not a second one: a member removing
    // themselves is the same write as an admin removing them, and it has to be
    // the same code so that `memberCount` is recomputed on the way out. Left as
    // a direct client write it was the one membership change that did not move
    // the count, and the count is what the group list sorts and shows.
    const isSelf = target === uid;
    if (!isSelf && !callerIsGroupAdmin(group, uid)) {
      throw new GroupError("Only a group admin can remove a member.", 403);
    }
    if (isSelf && !group.memberIds.includes(target)) {
      throw new GroupError("You are not in this group.", 404);
    }
    if (target === group.creatorId) {
      // The rule the client rules also enforce: a group always keeps the person
      // who made it, so there is no way to end up with a group nobody owns.
      throw new GroupError("The group creator cannot be removed.", 403);
    }
    if (!group.memberIds.includes(target)) {
      throw new GroupError("That person is not in this group.", 404);
    }
    const memberIds = group.memberIds.filter((member) => member !== target);
    // An admin that is not a member would let somebody keep administering a
    // group they were removed from, so the two lists are moved together.
    const adminIds = group.adminIds.filter((admin) => admin !== target);
    return await this.#writeMembership(group, { memberIds, adminIds });
  }

  /**
   * Writes a recomputed membership, guarded by the state it was read from.
   *
   * `memberCount` moves with the list, which the client rules forbid a client
   * from doing and which is why the count cannot drift: only this service writes
   * it, and it writes the size of the list it just wrote.
   */
  async #writeMembership(
    group: StoredGroup,
    next: { memberIds: string[]; adminIds?: string[] },
  ): Promise<StoredGroup> {
    const adminIds = next.adminIds ?? group.adminIds.filter((admin) => next.memberIds.includes(admin));
    const fields: Record<string, unknown> = {
      memberIds: next.memberIds,
      adminIds,
      memberCount: next.memberIds.length,
      updatedAt: new Date(this.#now()),
    };
    try {
      await this.#store.commitDocument({
        path: `${GROUPS}/${group.id}`,
        fields,
        fieldMask: ["memberIds", "adminIds", "memberCount", "updatedAt"],
        // Without this, two admins adding members at the same moment would
        // leave one of the two additions silently dropped.
        ...(group.updateTime === undefined ? {} : { expectUpdateTime: group.updateTime }),
      });
    } catch (error) {
      if (error instanceof FirestorePreconditionError) {
        throw new GroupError("The group changed while you were editing it. Try again.", 409);
      }
      throw error;
    }
    return {
      id: group.id,
      creatorId: group.creatorId,
      memberIds: next.memberIds,
      adminIds,
      updateTime: group.updateTime,
    };
  }
}

export interface GroupServiceFactoryOptions {
  fetchImpl?: typeof fetch;
  now?: () => number;
}

/**
 * Builds the group service from the same service account the push path uses.
 *
 * The credential's own project is authoritative, exactly as in
 * `createPushService`, so a group can never be written into a different project
 * than the one the token was minted for.
 */
export function createGroupService(
  settings: GroupSettings,
  options: GroupServiceFactoryOptions = {},
): GroupService {
  if (settings.serviceAccountJson === "") {
    throw new ConfigError(
      "Group writes need FIREBASE_SERVICE_ACCOUNT_JSON. Set the service account JSON in the " +
        "server environment, or turn GROUPS_ENABLED off.",
    );
  }
  const credential: ServiceAccountCredential = parseServiceAccountJson(settings.serviceAccountJson);
  const tokens = new GoogleAccessTokenProvider(credential, {
    scope: FIRESTORE_SCOPE,
    ...(options.fetchImpl === undefined ? {} : { fetchImpl: options.fetchImpl }),
    ...(options.now === undefined ? {} : { now: options.now }),
  });
  const gateway = new FirestoreRestGateway(credential.projectId, tokens, options);
  return new GroupService({
    store: gateway,
    now: options.now ?? Date.now,
  });
}
