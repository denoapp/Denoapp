/**
 * Dependency-free Firebase Web Auth client using Google Identity Toolkit REST API.
 * Avoids npm package installation issues in offline/restricted environments.
 */

const API_KEY = import.meta.env.VITE_FIREBASE_API_KEY || "";
const SIGN_IN_URL = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`;
const REFRESH_URL = `https://securetoken.googleapis.com/v1/token?key=${API_KEY}`;

const STORAGE_KEY = "deno_admin_auth";

export function getStoredAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function setStoredAuth(authData) {
  if (!authData) {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(authData));
  }
}

export async function loginWithEmailPassword(email, password) {
  if (!API_KEY) {
    throw new Error("Missing VITE_FIREBASE_API_KEY environment variable.");
  }
  const res = await fetch(SIGN_IN_URL, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error?.message || "Authentication failed.");
  }
  const authData = {
    token: data.idToken,
    refreshToken: data.refreshToken,
    expiresAt: Date.now() + Number(data.expiresIn || 3600) * 1000,
    uid: data.localId,
    email: data.email,
  };
  setStoredAuth(authData);
  return authData;
}

export async function getValidToken() {
  const auth = getStoredAuth();
  if (!auth || !auth.token) return null;

  // If token is close to expiry (within 2 minutes), refresh it
  if (auth.expiresAt && Date.now() > auth.expiresAt - 120000 && auth.refreshToken) {
    try {
      const res = await fetch(REFRESH_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "refresh_token", refresh_token: auth.refreshToken }),
      });
      const data = await res.json();
      if (res.ok && data.id_token) {
        auth.token = data.id_token;
        auth.refreshToken = data.refresh_token || auth.refreshToken;
        auth.expiresAt = Date.now() + Number(data.expires_in || 3600) * 1000;
        setStoredAuth(auth);
        return auth.token;
      }
    } catch {
      // fallback to existing token if refresh fails
    }
  }
  return auth.token;
}

export function logout() {
  setStoredAuth(null);
}
