/**
 * Group service tests: the real-account check, the authorization decisions, and
 * the shape of the document that gets written.
 *
 * The Firestore store is a fake, so this suite needs no Firebase project, no
 * credentials and no network. That is the point: the properties that matter
 * here are "a fabricated uid is refused" and "a non-admin cannot change a
 * membership", and those must be provable without a network.
 */

import { assert, assertEquals, assertMatch, assertRejects } from "./assert.ts";
import {
  buildGroupFields,
  callerIsGroupAdmin,
  createGroupService,
  groupInitial,
  GroupError,
  GroupService,
  normalizeGroupName,
  normalizeRequestedMemberIds,
  parseStoredGroup,
  type GroupStore,
} from "../src/groups.ts";
import { encodeFirestoreFields, FirestorePreconditionError, type FirestoreDocument, type FirestoreWrite } from "../src/push.ts";

const NOW = () => Date.parse("2026-02-03T04:05:06.000Z");
const CREATOR = "creator-uid";

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/** A store whose contents are the whole world, and which records every write. */
class FakeGroupStore implements GroupStore {
  readonly documents = new Map<string, FirestoreDocument>();
  readonly writes: FirestoreWrite[] = [];
  private updateTimeCounter = 0;

  #nextUpdateTime(): string {
    this.updateTimeCounter += 1;
    return `2026-01-01T00:00:${String(this.updateTimeCounter).padStart(2, "0")}Z`;
  }

  seed(path: string, fields: Record<string, unknown>, updateTime?: string): FirestoreDocument {
    const document: FirestoreDocument = {
      name: path,
      fields,
      updateTime: updateTime ?? this.#nextUpdateTime(),
    };
    this.documents.set(path, document);
    return document;
  }

  async getDocument(path: string): Promise<FirestoreDocument | null> {
    return this.documents.get(path) ?? null;
  }

  async listDocuments(path: string): Promise<FirestoreDocument[]> {
    const prefix = `${path}/`;
    return [...this.documents.values()].filter((document) => document.name.startsWith(prefix));
  }

  async deleteDocument(path: string): Promise<void> {
    this.documents.delete(path);
  }

  async commitDocument(write: FirestoreWrite): Promise<FirestoreDocument | null> {
    this.writes.push(write);
    if (write.path.endsWith("/-")) {
      const generated = `groups/generated-${this.documents.size + 1}`;
      return this.seed(generated, write.fields);
    }
    const existing = this.documents.get(write.path);
    if (write.expectExists === false && existing !== undefined) {
      // The typed error the service translates into a 409, not a bare Error:
      // the service only maps FirestorePreconditionError, so a plain Error here
      // would surface as a 500 and the test would pass for the wrong reason.
      throw new FirestorePreconditionError("Firestore refused the write: ALREADY_EXISTS");
    }
    if (write.expectUpdateTime !== undefined && existing?.updateTime !== write.expectUpdateTime) {
      throw new FirestorePreconditionError("Firestore refused the write: write conflict");
    }
    // A field mask replaces only the named fields, exactly as Firestore does.
    const fields: Record<string, unknown> = { ...(existing?.fields ?? {}) };
    for (const name of write.fieldMask ?? Object.keys(write.fields)) {
      const value = write.fields[name];
      if (value === undefined) delete fields[name];
      else fields[name] = value;
    }
    return this.seed(write.path, fields);
  }
}

function storeWithAccounts(accounts: readonly string[]): FakeGroupStore {
  const store = new FakeGroupStore();
  for (const account of accounts) store.seed(`users/${account}`, { uid: account });
  return store;
}

function serviceFor(store: FakeGroupStore): GroupService {
  return new GroupService({ store, now: NOW });
}

function seedGroup(
  store: FakeGroupStore,
  overrides: {
    creatorId?: string;
    memberIds?: string[];
    adminIds?: string[];
    memberCount?: number;
  } = {},
): void {
  const memberIds = overrides.memberIds ?? [CREATOR, "member-a", "member-b"];
  const adminIds = overrides.adminIds ?? [CREATOR];
  store.seed("groups/g1", {
    creatorId: overrides.creatorId ?? CREATOR,
    memberIds,
    adminIds,
    memberCount: overrides.memberCount ?? memberIds.length,
    name: "Existing group",
    description: "",
    topic: "",
    initial: "EX",
    membersCanSend: true,
    membersCanAddParticipants: true,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    lastMessage: "",
    lastMessageAt: "2026-01-01T00:00:00.000Z",
  });
}

/** `assertRejects` hands back an unknown; these narrow it to the type under test. */
async function rejectsGroupError(fn: () => unknown | Promise<unknown>): Promise<GroupError> {
  const error = await assertRejects(fn);
  assert(error instanceof GroupError, `Expected a GroupError, got ${String(error)}`);
  return error as GroupError;
}

// ---------------------------------------------------------------------------
// (a) A fabricated uid cannot become a member.
// ---------------------------------------------------------------------------

Deno.test("createGroup refuses a member id with no account", async () => {
  const store = storeWithAccounts([CREATOR, "real-a"]);
  const failure = await rejectsGroupError(() =>
    serviceFor(store).createGroup(CREATOR, {
      name: "Trip",
      description: "",
      topic: "",
      memberIds: ["real-a", "attacker-invented"],
    })
  );
  assertEquals(failure.status, 400);
  assertMatch(failure.message, /do not have an account/);
});

Deno.test("createGroup writes nothing when one id is not a real account", async () => {
  const store = storeWithAccounts([CREATOR, "real-a"]);
  await rejectsGroupError(() =>
    serviceFor(store).createGroup(CREATOR, {
      name: "Trip",
      description: "",
      topic: "",
      memberIds: ["real-a", "attacker-invented"],
    })
  );
  assertEquals(store.writes.length, 0);
  // Only the two user documents exist; no group was written.
  assertEquals([...store.documents.keys()].sort(), ["users/creator-uid", "users/real-a"]);
});

Deno.test("addMembers refuses a fabricated id and leaves the group untouched", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "member-b", "real-c"]);
  seedGroup(store);
  const before = store.documents.get("groups/g1")?.fields["memberIds"];

  const failure = await rejectsGroupError(() =>
    serviceFor(store).addMembers(CREATOR, "g1", ["real-c", "attacker-invented"])
  );
  assertEquals(failure.status, 400);
  assertEquals(store.writes.length, 0);
  assertEquals(store.documents.get("groups/g1")?.fields["memberIds"], before);
});

Deno.test("a seeded demo id is refused just like an invented one", async () => {
  // A local-only group id has no users/ document, so it cannot be smuggled into
  // a stored group. The refusal is identical, so it discloses nothing.
  const store = storeWithAccounts([CREATOR, "u1"]);
  const failure = await rejectsGroupError(() =>
    serviceFor(store).createGroup(CREATOR, {
      name: "Mixed",
      description: "",
      topic: "",
      memberIds: ["u1", "s1"],
    })
  );
  assertEquals(failure.status, 400);
  assertMatch(failure.message, /do not have an account/);
});

// ---------------------------------------------------------------------------
// (b) A real uid can.
// ---------------------------------------------------------------------------

Deno.test("createGroup admits ids that are real accounts", async () => {
  const store = storeWithAccounts([CREATOR, "real-a", "real-b"]);
  const created = await serviceFor(store).createGroup(CREATOR, {
    name: "Trip",
    description: "beach",
    topic: "summer",
    memberIds: ["real-a", "real-b"],
  });
  assertEquals(created.creatorId, CREATOR);
  assertEquals([...created.memberIds].sort(), [CREATOR, "real-a", "real-b"]);

  // createGroup writes and returns, so the document is in the fake store.
  const stored = store.documents.get(`groups/${created.id}`)!;
  assertEquals(stored.fields["creatorId"], CREATOR);
  assertEquals(stored.fields["memberCount"], 3);
  assertEquals(stored.fields["adminIds"], [CREATOR]);
  assertEquals(stored.fields["name"], "Trip");
  assertEquals(stored.fields["initial"], "TR");
  assertEquals(stored.fields["lastMessage"], "");
});

Deno.test("addMembers admits a real account and keeps memberCount in step", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "member-b", "real-c"]);
  seedGroup(store);
  const updated = await serviceFor(store).addMembers(CREATOR, "g1", ["real-c"]);
  assertEquals(updated.memberIds.length, 4);
  // The membership write is the last one the service made.
  const write = store.writes[store.writes.length - 1]!;
  // (f) The count is recomputed from the list that was written, never accepted.
  assertEquals(write.fields["memberCount"], 4);
  assertEquals(write.fields["memberIds"], updated.memberIds);
});

Deno.test("adding somebody already in the group is a no-op, not a failure", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "member-b"]);
  seedGroup(store);
  const updated = await serviceFor(store).addMembers(CREATOR, "g1", ["member-a"]);
  assertEquals(updated.memberIds.length, 3);
  assertEquals(store.writes.length, 0);
});

Deno.test("the creator is a member even when the client does not list itself", async () => {
  const store = storeWithAccounts([CREATOR, "real-a"]);
  const created = await serviceFor(store).createGroup(CREATOR, {
    name: "Trip",
    description: "",
    topic: "",
    memberIds: ["real-a"],
  });
  assert(created.memberIds.includes(CREATOR));
});

Deno.test("Firestore mints the group id, so the client cannot choose it", async () => {
  const store = storeWithAccounts([CREATOR, "real-a"]);
  await serviceFor(store).createGroup(CREATOR, {
    name: "Trip",
    description: "",
    topic: "",
    memberIds: ["real-a"],
  });
  // The create is the only write this test made.
  const write = store.writes[0]!;
  assertEquals(write.path, "groups/-");
  assertEquals(write.expectExists, false);
});

// ---------------------------------------------------------------------------
// (c) A non-admin cannot add or remove members.
// ---------------------------------------------------------------------------

Deno.test("a plain member cannot add members", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "real-c"]);
  seedGroup(store);
  const failure = await rejectsGroupError(() => serviceFor(store).addMembers("member-a", "g1", ["real-c"]));
  assertEquals(failure.status, 403);
  assertEquals(store.writes.length, 0);
});

Deno.test("a plain member cannot remove a member", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "member-b"]);
  seedGroup(store);
  const failure = await rejectsGroupError(() =>
    serviceFor(store).removeMember("member-a", "g1", "member-b")
  );
  assertEquals(failure.status, 403);
  assertEquals(store.writes.length, 0);
});

Deno.test("a non-member cannot change a membership", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "real-c"]);
  seedGroup(store);
  const failure = await rejectsGroupError(() => serviceFor(store).addMembers("stranger", "g1", ["real-c"]));
  assertEquals(failure.status, 403);
});

Deno.test("an admin who is not the creator may change membership", async () => {
  const store = storeWithAccounts([CREATOR, "admin-2", "member-a", "real-c"]);
  seedGroup(store, {
    memberIds: [CREATOR, "admin-2", "member-a"],
    adminIds: [CREATOR, "admin-2"],
  });
  const updated = await serviceFor(store).addMembers("admin-2", "g1", ["real-c"]);
  assertEquals(updated.memberIds.length, 4);
});

// ---------------------------------------------------------------------------
// (d) The creator cannot be removed.
// ---------------------------------------------------------------------------

Deno.test("the creator cannot be removed, not even by an admin", async () => {
  const store = storeWithAccounts([CREATOR, "admin-2", "member-a"]);
  seedGroup(store, { memberIds: [CREATOR, "admin-2", "member-a"], adminIds: [CREATOR, "admin-2"] });
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember("admin-2", "g1", CREATOR));
  assertEquals(failure.status, 403);
  assertMatch(failure.message, /creator cannot be removed/);
  assertEquals(store.writes.length, 0);
});

Deno.test("the creator cannot remove itself either", async () => {
  const store = storeWithAccounts([CREATOR, "member-a"]);
  seedGroup(store);
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember(CREATOR, "g1", CREATOR));
  assertEquals(failure.status, 403);
});

Deno.test("removing a member also drops their admin entry", async () => {
  const store = storeWithAccounts([CREATOR, "admin-2", "member-a"]);
  seedGroup(store, { memberIds: [CREATOR, "admin-2", "member-a"], adminIds: [CREATOR, "admin-2"] });
  const updated = await serviceFor(store).removeMember(CREATOR, "g1", "admin-2");
  assertEquals(updated.memberIds, [CREATOR, "member-a"]);
  assertEquals(updated.adminIds, [CREATOR]);
});

Deno.test("removing somebody who is not in the group is refused", async () => {
  const store = storeWithAccounts([CREATOR, "member-a"]);
  seedGroup(store);
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember(CREATOR, "g1", "stranger"));
  assertEquals(failure.status, 404);
});

Deno.test("a member leaving is the same endpoint, and moves memberCount", async () => {
  const store = storeWithAccounts([CREATOR, "member-a"]);
  seedGroup(store, { memberIds: [CREATOR, "member-a"], adminIds: [CREATOR] });
  const updated = await serviceFor(store).removeMember("member-a", "g1", "member-a");
  assertEquals(updated.memberIds, [CREATOR]);
  // memberCount is not part of the returned group: it is derived from the list
  // and written as a stored field, so the write is what proves it moved.
  const write = store.writes[store.writes.length - 1]!;
  assertEquals(write.fields["memberCount"], 1);
});

Deno.test("a member cannot remove somebody else by calling it a leave", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "member-b"]);
  seedGroup(store, { memberIds: [CREATOR, "member-a", "member-b"], adminIds: [CREATOR] });
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember("member-a", "g1", "member-b"));
  assertEquals(failure.status, 403);
});

Deno.test("leaving drops the caller's own admin entry", async () => {
  const store = storeWithAccounts([CREATOR, "admin-2"]);
  seedGroup(store, { memberIds: [CREATOR, "admin-2"], adminIds: [CREATOR, "admin-2"] });
  const updated = await serviceFor(store).removeMember("admin-2", "g1", "admin-2");
  assertEquals(updated.adminIds, [CREATOR]);
});

Deno.test("the creator cannot leave, so a group always has an owner", async () => {
  const store = storeWithAccounts([CREATOR, "member-a"]);
  seedGroup(store, { memberIds: [CREATOR, "member-a"], adminIds: [CREATOR] });
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember(CREATOR, "g1", CREATOR));
  assertEquals(failure.status, 403);
});

Deno.test("a non-member cannot leave a group it was never in", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "stranger"]);
  seedGroup(store, { memberIds: [CREATOR, "member-a"], adminIds: [CREATOR] });
  const failure = await rejectsGroupError(() => serviceFor(store).removeMember("stranger", "g1", "stranger"));
  assertEquals(failure.status, 404);
});

// ---------------------------------------------------------------------------
// (e) A non-member cannot reach a group that is not theirs.
// ---------------------------------------------------------------------------

Deno.test("a group that does not exist is a 404, not a silent success", async () => {
  const store = storeWithAccounts([CREATOR, "stranger"]);
  const failure = await rejectsGroupError(() =>
    serviceFor(store).addMembers("stranger", "no-such-group", ["x"])
  );
  assertEquals(failure.status, 404);
});

Deno.test("a traversal-shaped group id is refused before any write", async () => {
  const store = storeWithAccounts([CREATOR]);
  await assertRejects(() => serviceFor(store).addMembers(CREATOR, "../../users/someone", ["x"]));
  assertEquals(store.writes.length, 0);
});

Deno.test("a traversal-shaped member id is refused", async () => {
  const store = storeWithAccounts([CREATOR, "member-a"]);
  seedGroup(store);
  // The id is refused by the shared document-id validation, which raises a
  // PushError rather than a GroupError; either way the write is refused, and
  // both are answered with the same status by the handler.
  await assertRejects(() => serviceFor(store).addMembers(CREATOR, "g1", ["../elsewhere"]));
  assertEquals(store.writes.length, 0);
});

// ---------------------------------------------------------------------------
// (f) memberCount stays consistent, and identity fields are server-owned.
// ---------------------------------------------------------------------------

Deno.test("memberCount equals the member list length after add and after remove", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "real-c"]);
  seedGroup(store);
  await serviceFor(store).addMembers(CREATOR, "g1", ["real-c"]);
  // The seed holds three members, so adding one makes four.
  const added = store.documents.get("groups/g1");
  assertEquals(added?.fields["memberCount"], 4);
  assertEquals(added?.fields["memberIds"], [CREATOR, "member-a", "member-b", "real-c"]);

  // Removing it again returns the group to the three it was seeded with.
  await serviceFor(store).removeMember(CREATOR, "g1", "real-c");
  const removed = store.documents.get("groups/g1");
  assertEquals(removed?.fields["memberCount"], 3);
  assertEquals(removed?.fields["memberIds"], [CREATOR, "member-a", "member-b"]);
});

Deno.test("a membership write never touches creatorId, createdAt or the name", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "real-c"]);
  seedGroup(store);
  const before = store.documents.get("groups/g1")?.fields;
  await serviceFor(store).addMembers(CREATOR, "g1", ["real-c"]);
  const after = store.documents.get("groups/g1")?.fields;
  assertEquals(after?.["creatorId"], before?.["creatorId"]);
  assertEquals(after?.["createdAt"], before?.["createdAt"]);
  assertEquals(after?.["name"], before?.["name"]);
  const write = store.writes[store.writes.length - 1];
  assertEquals(write?.fieldMask, ["memberIds", "adminIds", "memberCount", "updatedAt"]);
});

Deno.test("a membership change that lands on a moved document is reported, not lost", async () => {
  const store = storeWithAccounts([CREATOR, "member-a", "real-c"]);
  seedGroup(store);
  // Somebody else commits between our read and our write: the write carries the
  // updateTime it was computed against, so Firestore refuses it instead of
  // dropping the other admin's addition.
  const service = serviceFor(store);
  const realGet = store.getDocument.bind(store);
  store.getDocument = async (path: string) => {
    const document = await realGet(path);
    if (path === "groups/g1" && document !== null) {
      return { ...document, updateTime: "2020-01-01T00:00:00.000Z" };
    }
    return document;
  };
  const failure = await rejectsGroupError(() => service.addMembers(CREATOR, "g1", ["real-c"]));
  assertEquals(failure.status, 409);
  assertMatch(failure.message, /changed while you were editing/);
});

// ---------------------------------------------------------------------------
// Field validation
// ---------------------------------------------------------------------------

Deno.test("groupInitial takes two characters and uppercases them", () => {
  assertEquals(groupInitial("trip"), "TR");
  assertEquals(groupInitial("a"), "A");
});

Deno.test("a group name is trimmed and length-checked", () => {
  assertEquals(normalizeGroupName("  Trip  "), "Trip");
  const blank = (() => {
    try {
      normalizeGroupName("   ");
      return null;
    } catch (error) {
      return error;
    }
  })();
  assert(blank instanceof GroupError);
  const tooLong = (() => {
    try {
      normalizeGroupName("x".repeat(61));
      return null;
    } catch (error) {
      return error;
    }
  })();
  assert(tooLong instanceof GroupError);
});

Deno.test("requested ids are deduplicated, validated and bounded", () => {
  assertEquals(normalizeRequestedMemberIds(["a", "a", " b "]), ["a", "b"]);
  const many = Array.from({ length: 501 }, (_, index) => `u${index}`);
  let refused = false;
  try {
    normalizeRequestedMemberIds(many);
  } catch (error) {
    refused = error instanceof GroupError;
  }
  assert(refused, "501 members should be refused");
});

Deno.test("requested ids that are not document ids are refused", () => {
  for (const bad of ["a/b", "..", "a".repeat(129), ""]) {
    let refused = false;
    try {
      normalizeRequestedMemberIds([bad]);
    } catch {
      refused = true;
    }
    assert(refused, `"${bad}" should be refused`);
  }
});

Deno.test("buildGroupFields derives adminIds and memberCount, never accepts them", () => {
  const fields = buildGroupFields({
    creatorId: CREATOR,
    memberIds: [CREATOR, "member-a"],
    name: "Trip",
    description: "beach",
    topic: "summer",
    timestamp: new Date("2026-02-03T04:05:06.000Z"),
  });
  assertEquals(fields["adminIds"], [CREATOR]);
  assertEquals(fields["memberCount"], 2);
  assertEquals(fields["lastMessage"], "");
  assert(fields["createdAt"] instanceof Date);
});

Deno.test("buildGroupFields refuses a creator that is not a member", () => {
  let refused = false;
  try {
    buildGroupFields({
      creatorId: CREATOR,
      memberIds: ["someone-else"],
      name: "Trip",
      description: "",
      topic: "",
      timestamp: new Date(0),
    });
  } catch (error) {
    refused = error instanceof GroupError;
  }
  assert(refused, "a creator outside the member list should be refused");
});

Deno.test("callerIsGroupAdmin reads the stored lists", () => {
  const group = {
    id: "g1",
    creatorId: CREATOR,
    memberIds: [CREATOR, "admin-2", "member-a"],
    adminIds: [CREATOR, "admin-2"],
    updateTime: undefined,
  };
  assert(callerIsGroupAdmin(group, CREATOR));
  assert(callerIsGroupAdmin(group, "admin-2"));
  assert(!callerIsGroupAdmin(group, "member-a"));
  assert(!callerIsGroupAdmin(group, "stranger"));
});

Deno.test("a stored group with a malformed member list is refused, not guessed at", () => {
  let refused = false;
  try {
    parseStoredGroup({ name: "groups/g1", fields: { creatorId: CREATOR, memberIds: "nope" } });
  } catch (error) {
    refused = error instanceof GroupError;
  }
  assert(refused, "a non-list memberIds should be refused");
});

Deno.test("encodeFirestoreFields round-trips the values a group document holds", () => {
  const encoded = encodeFirestoreFields({
    memberIds: [CREATOR],
    memberCount: 1,
    name: "Trip",
    membersCanSend: true,
    createdAt: new Date("2026-02-03T04:05:06.000Z"),
  });
  assertEquals(encoded["memberCount"], { integerValue: "1" });
  assertEquals(encoded["name"], { stringValue: "Trip" });
  assertEquals(encoded["membersCanSend"], { booleanValue: true });
  assertEquals(encoded["createdAt"], { timestampValue: "2026-02-03T04:05:06.000Z" });
  assertEquals(encoded["memberIds"], { arrayValue: { values: [{ stringValue: CREATOR }] } });
});

Deno.test("createGroupService refuses to build without a service account", () => {
  let failure: unknown = null;
  try {
    createGroupService({ serviceAccountJson: "" });
  } catch (error) {
    failure = error;
  }
  assert(failure !== null, "building without a credential should fail loudly");
  assertMatch(String(failure), /FIREBASE_SERVICE_ACCOUNT_JSON/);
});
