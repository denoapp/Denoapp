/**
 * Admin API service connecting React Admin UI screens to Staging Backend routes.
 */

import { apiRequest } from "./client.js";

export async function fetchScholarApplications() {
  return apiRequest("/v1/admin/scholar-applications");
}

/**
 * The caller's own role, resolved by the backend from the role documents. The
 * panel uses it only to decide which buttons to offer; the backend refuses
 * anything the caller has no role for regardless of what is rendered.
 */
export async function fetchAdminRole() {
  return apiRequest("/v1/admin/me");
}

export async function decideScholarApplication(uid, decisionData) {
  return apiRequest(`/v1/admin/scholar-applications/${uid}/decision`, {
    method: "POST",
    body: decisionData,
  });
}

export async function fetchUsers() {
  return apiRequest("/v1/admin/users");
}

export async function updateUserStatus(uid, statusData) {
  return apiRequest(`/v1/admin/users/${uid}/status`, {
    method: "POST",
    body: statusData,
  });
}

export async function fetchReports() {
  return apiRequest("/v1/admin/reports");
}

export async function actionReport(id, actionData) {
  return apiRequest(`/v1/admin/reports/${id}/action`, {
    method: "POST",
    body: actionData,
  });
}

export async function fetchPosts() {
  return apiRequest("/v1/admin/posts");
}

export async function fetchReels() {
  return apiRequest("/v1/admin/reels");
}

export async function fetchStories() {
  return apiRequest("/v1/admin/stories");
}

export async function fetchGroups() {
  return apiRequest("/v1/admin/groups");
}

export async function fetchFlaggedMessages() {
  return apiRequest("/v1/admin/messages/flagged");
}

export async function fetchAdmins() {
  return apiRequest("/v1/admin/admins");
}

export async function addAdmin(adminData) {
  return apiRequest("/v1/admin/admins", {
    method: "POST",
    body: adminData,
  });
}

export async function removeAdmin(uid) {
  return apiRequest(`/v1/admin/admins/${uid}`, {
    method: "DELETE",
  });
}

export async function fetchAuditLogs() {
  return apiRequest("/v1/admin/audit-logs");
}
