/**
 * Server-side configuration for the DeenoLink B2 media service.
 *
 * SECURITY RULES ENFORCED HERE:
 *  - Credentials are only ever read from the process environment.
 *  - No credential value is ever placed in an error message, a log line, or
 *    the health payload. Only booleans/lengths derived from them are exposed.
 *  - Missing or placeholder credentials fail closed at startup.
 *  - Nothing in this module is imported by the Android app.
 */

export class ConfigError extends Error {
  override name = "ConfigError";
}

export type EnvSource = { get(key: string): string | undefined };

export interface B2Settings {
  readonly keyId: string;
  readonly applicationKey: string;
  readonly bucket: string;
  /** Origin only, e.g. "https://s3.us-east-005.backblazeb2.com". Never has a trailing slash. */
  readonly endpoint: string;
  /** SigV4 region, e.g. "us-east-005". */
  readonly region: string;
  readonly presignTtlSeconds: number;
  readonly maxUploadBytes: number;
  /**
   * Chat attachments are a separate ceiling from profile media: a voice note or
   * a short video is legitimately larger than a 5 MB avatar, and the per-kind
   * rules in b2.ts bound the kind rather than the namespace.
   */
  readonly maxChatUploadBytes: number;
  readonly allowedContentTypes: readonly string[];
  /** All object keys are written beneath this prefix. */
  readonly objectPrefix: string;
}

export interface PushSettings {
  /**
   * Off unless the operator turns it on. A server with no service account
   * refuses to send rather than reporting a delivery that never happened.
   */
  readonly enabled: boolean;
  /** Raw service-account JSON. Read from the environment, never logged. */
  readonly serviceAccountJson: string;
  /** Present and parseable, reported as a boolean in the health view. */
  readonly serviceAccountPresent: boolean;
}

/**
 * Group creation and membership changes.
 *
 * These are on the service because `firestore.rules` cannot check that every id
 * in a member list names a real account - it has no list iteration, so the check
 * it used to express always failed closed and refused every group write. The
 * service account is what lets the server read `users/{uid}` once per id.
 *
 * Off unless the operator turns it on, and a hard startup error when it is on
 * without a service account, for the same reason push is: a server that cannot
 * check membership must refuse to change it rather than accept an unverified
 * member list.
 */
export interface GroupsConfig {
  readonly enabled: boolean;
  readonly serviceAccountJson: string;
  readonly serviceAccountPresent: boolean;
}

export interface ServerSettings {
  readonly port: number;
  readonly hostname: string;
  readonly firebaseProjectId: string;
  /** false only when the deliberate local-dev bypass is enabled. */
  readonly authRequired: boolean;
  /** uid used for object keys when the dev bypass is on. */
  readonly devUid: string;
  readonly corsAllowedOrigins: readonly string[];
  readonly b2: B2Settings;
  readonly push: PushSettings;
  readonly groups: GroupsConfig;
}

const DEFAULT_BUCKET = "deenolink-media";
const DEFAULT_REGION = "us-east-005";
const DEFAULT_OBJECT_PREFIX = "media";
const DEFAULT_PORT = 8787;
/** Matches the 5 MiB image ceiling already enforced in storage.rules. */
const DEFAULT_MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
/** Chat media ceiling. Bounded by a streaming upload, not by memory. */
const DEFAULT_MAX_CHAT_UPLOAD_BYTES = 25 * 1024 * 1024;
const DEFAULT_PRESIGN_TTL_SECONDS = 900;
/** AWS SigV4 / S3 hard limit for X-Amz-Expires. */
export const MAX_PRESIGN_TTL_SECONDS = 7 * 24 * 60 * 60;

const DEFAULT_CONTENT_TYPES = [
  // Images (profile photos, chat photos, camera captures).
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/heic",
  "image/heif",
  "image/gif",
  "image/bmp",
  // Video.
  "video/mp4",
  "video/quicktime",
  "video/webm",
  "video/3gpp",
  "video/x-matroska",
  // Voice notes. MediaRecorder writes AAC in an MP4 container, which is
  // audio/mp4, so it is the one that has to be allowed for a recording to work.
  "audio/mp4",
  "audio/aac",
  "audio/ogg",
  "audio/webm",
  "audio/mpeg",
  "audio/3gpp",
  "audio/amr",
  "audio/wav",
  "audio/x-wav",
  // Files chosen from a document chooser.
  "application/pdf",
  "application/zip",
  "application/x-zip-compressed",
  "application/json",
  "application/msword",
  "application/vnd.ms-excel",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "application/octet-stream",
  "text/plain",
  "text/csv",
] as const;

/**
 * Values that indicate the operator copied the example file but never filled
 * it in. Rejected so the server cannot start with a fake credential.
 */
const PLACEHOLDER_VALUES = new Set([
  "",
  "changeme",
  "change-me",
  "todo",
  "tbd",
  "none",
  "null",
  "undefined",
  "xxx",
  "xxxx",
  "your-key-id",
  "your-application-key",
  "your_key_id",
  "your_application_key",
  "b2_key_id",
  "b2_application_key",
  "paste-me",
  "replace-me",
]);

const BUCKET_PATTERN = /^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/;
const REGION_PATTERN = /^[a-z]{2}-[a-z]+-\d{3}$/;
const PREFIX_PATTERN = /^[a-z][a-z0-9-]{0,31}$/;
const CONTENT_TYPE_PATTERN = /^[a-z0-9][a-z0-9!#$&^_.+-]{0,126}\/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}$/;

function readString(env: EnvSource, key: string): string | undefined {
  const raw = env.get(key);
  if (raw === undefined) return undefined;
  const trimmed = raw.trim();
  return trimmed === "" ? undefined : trimmed;
}

/**
 * Reads a value that must be a real secret: present, non-empty, and not one of
 * the known placeholder strings. The thrown error names the variable but never
 * includes its value.
 */
function requireSecret(env: EnvSource, key: string): string {
  const value = readString(env, key);
  if (value === undefined) {
    throw new ConfigError(
      `Missing required environment variable ${key}. Set it in the server ` +
        `environment (see server/.env.example). Never place it in the Android app.`,
    );
  }
  if (PLACEHOLDER_VALUES.has(value.toLowerCase())) {
    throw new ConfigError(
      `Environment variable ${key} still holds a placeholder value. ` +
        `Provide the real credential in the server environment.`,
    );
  }
  // Guard against a stray newline/quote from copy-paste, which would silently
  // break HMAC signing in a way that is very hard to debug.
  if (/[\r\n]/.test(value)) {
    throw new ConfigError(
      `Environment variable ${key} contains a line break. Re-copy the value ` +
        `without surrounding whitespace or newlines.`,
    );
  }
  return value;
}

function optionalPattern(
  env: EnvSource,
  key: string,
  pattern: RegExp,
  hint: string,
): string | undefined {
  const value = readString(env, key);
  if (value === undefined) return undefined;
  if (!pattern.test(value)) {
    throw new ConfigError(`Environment variable ${key} is invalid. ${hint}`);
  }
  return value;
}

function intSetting(
  env: EnvSource,
  key: string,
  fallback: number,
  bounds: { min: number; max: number },
): number {
  const raw = readString(env, key);
  if (raw === undefined) return fallback;
  if (!/^\d+$/.test(raw)) {
    throw new ConfigError(`Environment variable ${key} must be a whole number.`);
  }
  const value = Number(raw);
  if (value < bounds.min || value > bounds.max) {
    throw new ConfigError(
      `Environment variable ${key} must be between ${bounds.min} and ${bounds.max}.`,
    );
  }
  return value;
}

function boolSetting(env: EnvSource, key: string, fallback: boolean): boolean {
  const raw = readString(env, key)?.toLowerCase();
  if (raw === undefined) return fallback;
  if (raw === "true" || raw === "1" || raw === "yes") return true;
  if (raw === "false" || raw === "0" || raw === "no") return false;
  throw new ConfigError(`Environment variable ${key} must be true or false.`);
}

function listSetting(env: EnvSource, key: string, fallback: readonly string[]): string[] {
  const raw = readString(env, key);
  if (raw === undefined) return [...fallback];
  const items = raw.split(",").map((item) => item.trim().toLowerCase()).filter((item) =>
    item !== ""
  );
  if (items.length === 0) {
    throw new ConfigError(`Environment variable ${key} must list at least one value.`);
  }
  for (const item of items) {
    if (!CONTENT_TYPE_PATTERN.test(item)) {
      throw new ConfigError(
        `Environment variable ${key} contains an invalid content type. Use a form like image/jpeg.`,
      );
    }
  }
  return items;
}

/**
 * Derives the B2 S3 endpoint from a region, or the region from an endpoint, so
 * that only the two credential variables plus the bucket have to be supplied.
 */
function resolveEndpointAndRegion(
  env: EnvSource,
): { endpoint: string; region: string } {
  const rawRegion = readString(env, "B2_REGION");
  const rawEndpoint = readString(env, "B2_ENDPOINT");

  if (rawEndpoint !== undefined) {
    let url: URL;
    try {
      url = new URL(rawEndpoint);
    } catch {
      throw new ConfigError(
        "Environment variable B2_ENDPOINT must be an absolute URL, for example " +
          "https://s3.us-east-005.backblazeb2.com",
      );
    }
    if (url.protocol !== "https:") {
      throw new ConfigError("Environment variable B2_ENDPOINT must use https.");
    }
    if (url.search !== "" || url.hash !== "" || (url.pathname !== "/" && url.pathname !== "")) {
      throw new ConfigError(
        "Environment variable B2_ENDPOINT must be a bare origin with no path, query, or fragment.",
      );
    }
    const derived = /^(?:https:\/\/)?s3\.([a-z0-9-]+)\.backblazeb2\.com$/i.exec(rawEndpoint);
    const region = rawRegion ?? derived?.[1];
    if (region === undefined) {
      throw new ConfigError(
        "Could not derive the B2 region from B2_ENDPOINT. Set B2_REGION explicitly, " +
          "for example us-east-005.",
      );
    }
    if (!REGION_PATTERN.test(region)) {
      throw new ConfigError("Environment variable B2_REGION has an unexpected format.");
    }
    return { endpoint: url.origin, region };
  }

  const region = rawRegion ?? DEFAULT_REGION;
  if (!REGION_PATTERN.test(region)) {
    throw new ConfigError("Environment variable B2_REGION has an unexpected format.");
  }
  return { endpoint: `https://s3.${region}.backblazeb2.com`, region };
}

export function loadSettings(env: EnvSource): ServerSettings {
  const keyId = requireSecret(env, "B2_KEY_ID");
  const applicationKey = requireSecret(env, "B2_APPLICATION_KEY");

  const bucket = readString(env, "B2_BUCKET") ?? DEFAULT_BUCKET;
  if (!BUCKET_PATTERN.test(bucket)) {
    throw new ConfigError("Environment variable B2_BUCKET is not a valid bucket name.");
  }

  const { endpoint, region } = resolveEndpointAndRegion(env);

  const objectPrefix = optionalPattern(
    env,
    "B2_OBJECT_PREFIX",
    PREFIX_PATTERN,
    "Use lowercase letters, digits, and hyphens, starting with a letter.",
  ) ?? DEFAULT_OBJECT_PREFIX;

  const presignTtlSeconds = intSetting(
    env,
    "B2_PRESIGN_TTL_SECONDS",
    DEFAULT_PRESIGN_TTL_SECONDS,
    { min: 60, max: MAX_PRESIGN_TTL_SECONDS },
  );

  const maxUploadBytes = intSetting(env, "B2_MAX_UPLOAD_BYTES", DEFAULT_MAX_UPLOAD_BYTES, {
    min: 1024,
    max: 5 * 1024 * 1024 * 1024,
  });

  const allowedContentTypes = Object.freeze(
    listSetting(env, "B2_ALLOWED_CONTENT_TYPES", DEFAULT_CONTENT_TYPES),
  );

  const maxChatUploadBytes = intSetting(
    env,
    "B2_MAX_CHAT_UPLOAD_BYTES",
    DEFAULT_MAX_CHAT_UPLOAD_BYTES,
    { min: 1024, max: 5 * 1024 * 1024 * 1024 },
  );
  if (maxChatUploadBytes < maxUploadBytes) {
    throw new ConfigError(
      "B2_MAX_CHAT_UPLOAD_BYTES cannot be smaller than B2_MAX_UPLOAD_BYTES.",
    );
  }

  // A signed request is meaningless without knowing which Firebase project the
  // caller's ID token belongs to, so this is required rather than defaulted.
  const firebaseProjectId = requireSecret(env, "FIREBASE_PROJECT_ID");
  if (!/^[a-z0-9][a-z0-9-]{4,29}[a-z0-9]$/.test(firebaseProjectId)) {
    throw new ConfigError("Environment variable FIREBASE_PROJECT_ID has an unexpected format.");
  }

  const isProduction = (readString(env, "DENO_ENV") ?? "development").toLowerCase() ===
    "production";
  const allowAnonymousDev = boolSetting(env, "B2_ALLOW_ANONYMOUS_DEV", false);
  if (allowAnonymousDev && isProduction) {
    throw new ConfigError(
      "B2_ALLOW_ANONYMOUS_DEV cannot be enabled when DENO_ENV=production. " +
        "Media endpoints always require a Firebase ID token in production.",
    );
  }

  const devUid = readString(env, "B2_DEV_UID") ?? "local-dev-user";
  if (allowAnonymousDev && !/^[A-Za-z0-9_-]{1,128}$/.test(devUid)) {
    throw new ConfigError(
      "Environment variable B2_DEV_UID may only contain letters, digits, underscores, and hyphens.",
    );
  }

  const port = intSetting(env, "PORT", DEFAULT_PORT, { min: 1, max: 65535 });
  const hostname = readString(env, "HOST") ?? "0.0.0.0";

  const origins = readString(env, "B2_CORS_ALLOWED_ORIGINS");
  const corsAllowedOrigins = origins === undefined ? Object.freeze<string[]>([]) : Object.freeze(
    origins.split(",").map((o) => o.trim()).filter((o) => o !== ""),
  );

  // Push is opt-in. Turning it on without a credential is a misconfiguration
  // that must fail at startup rather than at the first message, because the
  // alternative is a server that quietly drops every notification. The JSON is
  // parsed once, by createPushService, which is also where a malformed key is
  // reported as a configuration error.
  const pushEnabled = boolSetting(env, "PUSH_ENABLED", false);
  const serviceAccountJson = readString(env, "FIREBASE_SERVICE_ACCOUNT_JSON") ?? "";
  if (pushEnabled && serviceAccountJson === "") {
    throw new ConfigError(
      "PUSH_ENABLED is on but FIREBASE_SERVICE_ACCOUNT_JSON is missing. Set the service " +
        "account JSON in the server environment, or turn PUSH_ENABLED off.",
    );
  }

  // The group service reuses the same credential: reading users/{uid} to prove a
  // member is real needs exactly the identity a service account has and a client
  // does not.
  const groupsEnabled = boolSetting(env, "GROUPS_ENABLED", false);
  if (groupsEnabled && serviceAccountJson === "") {
    throw new ConfigError(
      "GROUPS_ENABLED is on but FIREBASE_SERVICE_ACCOUNT_JSON is missing. Set the service " +
        "account JSON in the server environment, or turn GROUPS_ENABLED off.",
    );
  }

  return Object.freeze({
    port,
    hostname,
    firebaseProjectId,
    authRequired: !allowAnonymousDev,
    devUid,
    corsAllowedOrigins,
    b2: Object.freeze({
      keyId,
      applicationKey,
      bucket,
      endpoint,
      region,
      presignTtlSeconds,
      maxUploadBytes,
      maxChatUploadBytes,
      allowedContentTypes,
      objectPrefix,
    }),
    push: Object.freeze({
      enabled: pushEnabled,
      serviceAccountJson,
      serviceAccountPresent: serviceAccountJson !== "",
    }),
    groups: Object.freeze({
      enabled: groupsEnabled,
      serviceAccountJson,
      serviceAccountPresent: serviceAccountJson !== "",
    }),
  });
}

/**
 * Non-secret projection of the configuration for the health endpoint.
 * Contains no credential material: only booleans and public identifiers.
 */
export function publicConfigView(settings: ServerSettings): Record<string, unknown> {
  return {
    bucket: settings.b2.bucket,
    endpoint: settings.b2.endpoint,
    region: settings.b2.region,
    objectPrefix: settings.b2.objectPrefix,
    presignTtlSeconds: settings.b2.presignTtlSeconds,
    maxUploadBytes: settings.b2.maxUploadBytes,
    maxChatUploadBytes: settings.b2.maxChatUploadBytes,
    allowedContentTypes: [...settings.b2.allowedContentTypes],
    firebaseProjectId: settings.firebaseProjectId,
    authRequired: settings.authRequired,
    pushEnabled: settings.push.enabled,
    groupsEnabled: settings.groups.enabled,
    credentialsPresent: {
      b2KeyId: settings.b2.keyId.length > 0,
      b2ApplicationKey: settings.b2.applicationKey.length > 0,
      // Presence only. The credential itself is a multi-kilobyte private key
      // and must never reach a response, a log line, or the startup banner.
      firebaseServiceAccount: settings.push.serviceAccountPresent,
    },
  };
}

/** Process environment adapter. */
export const systemEnv: EnvSource = {
  get: (key) => Deno.env.get(key),
};
