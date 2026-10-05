/**
 * Admin API service connecting React Admin UI screens to Staging Backend routes.
 */

import { apiRequest } from "./client.js";

export async function fetchScholarApplications() {
  return apiRequest("/v1/admin/scholar-applications");
}

/**
 * One application in full, for a reviewer who has opened it.
 *
 * This is a separate call from the list rather than a wider list: the answer
 * carries the applicant's email, phone number and document state, so it is only
 * fetched for an application actually being reviewed, and only ever from an
 * authorized-admin endpoint.
 */
export async function fetchScholarApplication(uid) {
  return apiRequest(`/v1/admin/scholar-applications/${encodeURIComponent(uid)}`);
}

/**
 * A short-lived signed link for one submitted document.
 *
 * The backend checks the caller's role and the object's ownership and mints the
 * link; this returns the URL and its expiry. The document itself is never
 * fetched here - opening it is the browser's job, and the link stops working on
 * its own.
 */
export async function fetchScholarDocumentUrl(uid, documentId) {
  return apiRequest(
    `/v1/admin/scholar-applications/${encodeURIComponent(uid)}/documents/${encodeURIComponent(documentId)}`,
  );
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
