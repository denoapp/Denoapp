# DeenoLink B2 media and push service

Server-side media service for the private Backblaze B2 bucket `deenolink-media`, plus FCM chat
push delivery.

The B2 credentials and the Firebase service-account key live here and nowhere else. This directory
is a sibling of `app/`, so Gradle never sees it and it cannot end up in the APK. There are no npm
dependencies at all, which keeps the credential path free of third-party supply chain.

## Endpoints

| Method   | Path                     | Auth   | Purpose                                              |
| -------- | ------------------------ | ------ | ---------------------------------------------------- |
| `GET`    | `/health`                | none   | Liveness plus a non-secret view of the configuration |
| `POST`   | `/v1/media/upload-url`   | Bearer | Presigned `PUT` for a key the **server** generates   |
| `POST`   | `/v1/media/download-url` | Bearer | Presigned `GET` for an existing key                  |
| `GET`    | `/v1/media/head?key=`    | Bearer | Object metadata, via a signed request to B2          |
| `DELETE` | `/v1/media?key=`         | Bearer | Delete, owner only (uploader-only for chat objects)  |
| `POST`   | `/v1/push/send`          | Bearer | Deliver a chat push for an already-stored message    |
| `POST`   | `/v1/groups`             | Bearer | Create a group, after checking every member id       |
| `POST`   | `/v1/groups/members/add`     | Bearer | Admin action: add real accounts to the roster    |
| `POST`   | `/v1/groups/members/remove`  | Bearer | Remove a member, or leave by naming yourself     |

Leaving a group is `members/remove` with your own uid in `memberId`. The service recomputes
`memberCount` on every membership write, and the Firestore rules refuse a client that tries to
write `memberIds` or `memberCount` itself, so the count and the list cannot drift apart.

Every `/v1` route requires `Authorization: Bearer <Firebase ID token>`. That gate is not optional in
production: without it, anyone on the internet could obtain an upload URL for the bucket.

## Entering the credentials

Do not paste the key into any source file.

```bash
cp .env.example .env
chmod 600 .env
$EDITOR .env          # fill B2_KEY_ID and B2_APPLICATION_KEY
```

In production, inject the same two names from your platform's secret manager (Docker `--env-file`,
systemd `EnvironmentFile`, Kubernetes Secret, Cloud Run secret, Fly secret) and do not create a
`.env` file at all.

`.env` is gitignored, and `deno.json` excludes it from `deno fmt`/`deno lint` so a stray run cannot
rewrite or print it.

## Running

```bash
deno task check   # type-check
deno task test    # unit tests, no network, no credentials needed
deno task start   # serve on 0.0.0.0:8787
deno task lint
deno task fmt
```

Permissions are already scoped in `deno.json`: the process can read the environment, listen on its
own port, and reach only `www.googleapis.com` (token keys), `oauth2.googleapis.com` and
`firestore.googleapis.com` (service-account token and reads), `fcm.googleapis.com` (push sends) and
`s3.us-east-005.backblazeb2.com`.

## Chat push

`POST /v1/push/send` is what makes a message show up on a phone that is not looking at the app. The
client calls it once, after its Firestore write is committed, naming only the message:

```bash
curl -sX POST http://localhost:8787/v1/push/send \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  -H 'content-type: application/json' \
  -d '{"kind":"direct","conversationId":"abe_zed","messageId":"m1"}'
```

`kind` is `direct` or `group`; `conversationId` is the 1-to-1 pair id or the group id.

**The client is never trusted.** A request carries no text, no recipient list and no token. The
server re-reads the stored message, requires the caller to be its own `senderId`, derives the
recipients from the conversation's own membership, and reads the preview text from the stored
message. A client that lies gets a `403` or an empty result, never somebody else's notification. A
withdrawn message (`unsent: true`) is never announced.

| answer | meaning |
| --- | --- |
| `delivered: n` | devices FCM accepted. Zero is legitimate: the recipient may have no registered device |
| `recipients: n` | accounts addressed, after membership and self-exclusion |
| `mutedRecipients: n` | accounts with that conversation muted; still delivered, on the silent channel |
| `removedTokens: n` | dead registrations deleted, so they are not retried forever |
| `503` | push is off, or Firestore/FCM could not be reached. **Never** reported as a success |

Push is off unless `PUSH_ENABLED=true`, and enabling it without `FIREBASE_SERVICE_ACCOUNT_JSON` is a
startup error rather than a silent no-op. A device that FCM calls unregistered is deleted from
`users/{uid}/devices`; a transient failure is not, because deleting a live token is how a user
quietly stops receiving messages.

Mute is read from `chatSettings/{uid}.mutedConversations` (an array of conversation ids) and is not
implemented by this service: the push layer only honours it, and a mute never loses a message - it
only silences it.

## Health check

```bash
curl -s http://localhost:8787/health
```

Returns the bucket, endpoint, region, size limits, `pushEnabled`, and
`credentialsPresent: { b2KeyId, b2ApplicationKey, firebaseServiceAccount }`. It reports only whether
a credential is set, never any part of its value, and the same view is printed once at startup.

## Client flow

Upload:

```bash
curl -sX POST http://localhost:8787/v1/media/upload-url \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  -H 'content-type: application/json' \
  -d '{"kind":"avatar","contentType":"image/jpeg","sizeBytes":204800}'
```

The response contains a `key` and a presigned `url`. Upload straight to B2 with the returned
headers, then confirm with `GET /v1/media/head?key=...`:

```bash
curl -X PUT "$UPLOAD_URL" -H 'content-type: image/jpeg' --data-binary @avatar.jpg
```

A presigned `PUT` URL is a bearer secret for its lifetime. Do not log it, store it, or let it reach
analytics or crash reporting. The default lifetime is 900 seconds and the server caps whatever the
client asks for.

Note that a presigned `PUT` cannot enforce a maximum body size in the signature. The server
validates the declared `sizeBytes` and returns `maxUploadBytes`; the `head` endpoint rejects an
object that exceeded it, which is how an oversized upload is caught.

## Local development without Firebase

```bash
B2_ALLOW_ANONYMOUS_DEV=true deno task dev
```

The server refuses to start with that flag when `DENO_ENV=production`, and logs a warning at
startup. All keys are then minted under `B2_DEV_UID`.

## Object keys

The server generates the key. A client chooses only the media kind and the content type:

```
media/users/{uid}/{kind}/{uuid}.{ext}
```

`kind` is one of `avatar`, `cover`, `post`, `reel`, `story`. The extension comes from a fixed table,
never from client input, and the layout matches the paths already enforced in `storage.rules`.

## Chat attachment keys

A 1-to-1 chat attachment is a different namespace, because it is readable by **two** people:

```
media/chats/{uploaderUid}/{peerUid}/{kind}/{uuid}.{ext}
```

`chatKind` is one of `photo`, `video`, `file`, `voice`. The uploader segment is always the
**authenticated** uid and never anything from the request body, so the key alone answers every
access question with no database and no state:

| | rule | why |
| --- | --- | --- |
| read | requester is `uploaderUid` **or** `peerUid` | one member of a conversation cannot read another's media |
| delete | requester is `uploaderUid` | a receiver can hide the message from itself, but the sender's history still points at the object |

`photo` must be an `image/*`, `video` a `video/*`, `voice` an `audio/*`; `file` is the catch-all a
document chooser produces and is bounded by the allow-list and by `B2_MAX_CHAT_UPLOAD_BYTES`.

The key is only reachable through a Firestore message, and `firestore.rules` independently requires
`mediaKey`'s two uid segments and its kind segment to equal the message's `senderId`, `receiverId`
and `mediaKind`. Both checks have to pass and neither alone is enough.

A presigned **URL** is never stored anywhere. URLs expire in minutes, so a message holding one
would look right and then fail to load; the durable reference is the key.

## Not implemented yet

Posts, reels and stories. Chat media is served by the primitives above; group attachments, an
offline send queue and unread counters do not exist. Push exists for 1-to-1 and group text
messages, but the trigger is the sending client's own call: a message written by something other
than the app (a Firestore console edit, an import script, or a client that dies between its commit
and the request) is stored and silent until the next message in that conversation. A Firestore
trigger would close that gap.
