import { assertEquals, assertNotEquals } from "./assert.ts";
import { createAdminHandler } from "../src/admin.ts";

const SETTINGS = {
  firebaseProjectId: "demo-project",
  authRequired: true,
  devUid: "dev-user",
};

Deno.test("admin handler returns 401 when authorization header is missing", async () => {
  const handler = createAdminHandler(SETTINGS, {
    verifier: {
      verify: () => { throw new Error("Authorization header missing."); },
    } as any,
  });

  const req = new Request("https://localhost/v1/admin/users", { method: "GET" });
  const res = await handler(req);
  assertEquals(res?.status, 401);
});

Deno.test("super admin can access admin-only and moderator-only endpoints", async () => {
  const handler = createAdminHandler(SETTINGS, {
    verifier: {
      verify: async () => ({ uid: "admin-uid" }),
    } as any,
    roleResolver: async () => ({ isAdmin: true, isModerator: true }),
  });

  for (const path of ["/v1/admin/users", "/v1/admin/scholar-applications", "/v1/admin/reports"]) {
    const req = new Request(`https://localhost${path}`, {
      method: "GET",
      headers: { authorization: "Bearer valid-token" },
    });
    const res = await handler(req);
    assertEquals(res?.status, 200);
  }
});

Deno.test("moderator can access scholarship and reports, but forbidden on users", async () => {
  const handler = createAdminHandler(SETTINGS, {
    verifier: {
      verify: async () => ({ uid: "mod-uid" }),
    } as any,
    roleResolver: async () => ({ isAdmin: false, isModerator: true }),
  });

  // Allowed for moderator
  const res1 = await handler(new Request("https://localhost/v1/admin/scholar-applications", {
    method: "GET",
    headers: { authorization: "Bearer valid-token" },
  }));
  assertEquals(res1?.status, 200);

  // Forbidden for moderator (admin-only)
  const res2 = await handler(new Request("https://localhost/v1/admin/users", {
    method: "GET",
    headers: { authorization: "Bearer valid-token" },
  }));
  assertEquals(res2?.status, 403);
});

Deno.test("normal user is forbidden on all admin endpoints", async () => {
  const handler = createAdminHandler(SETTINGS, {
    verifier: {
      verify: async () => ({ uid: "user-uid" }),
    } as any,
    roleResolver: async () => ({ isAdmin: false, isModerator: false }),
  });

  const res = await handler(new Request("https://localhost/v1/admin/users", {
    method: "GET",
    headers: { authorization: "Bearer valid-token" },
  }));
  assertEquals(res?.status, 403);
});
