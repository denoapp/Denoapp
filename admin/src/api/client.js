/**
 * Authenticated API client for the staging backend.
 * Automatically attaches Authorization: Bearer <ID_TOKEN>.
 */

import { getValidToken, logout } from "./auth.js";

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || "https://deenolink-staging-b12k.denoapp.deno.net";

export async function apiRequest(path, options = {}) {
  const token = await getValidToken();
  const headers = {
    ...(options.headers || {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  if (options.body && typeof options.body === "object" && !(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(options.body);
  }

  const res = await fetch(`${BACKEND_URL}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 401) {
    logout();
    window.location.reload();
    throw new Error("Session expired. Please sign in again.");
  }

  const contentType = res.headers.get("content-type") || "";
  const data = contentType.includes("application/json") ? await res.json() : await res.text();

  if (!res.ok) {
    const msg = typeof data === "object" && data.error ? data.error : res.statusText;
    throw new Error(msg);
  }

  return data;
}
