# DeenoLink B2 media service

Server-side media service for the private Backblaze B2 bucket `deenolink-media`.

The B2 credentials live here and nowhere else. This directory is a sibling of `app/`, so Gradle
never sees it and it cannot end up in the APK. There are no npm dependencies at all, which keeps the
credential path free of third-party supply chain.

## Endpoints

| Method   | Path                     | Auth   | Purpose                                              |
| -------- | ------------------------ | ------ | ---------------------------------------------------- |
| `GET`    | `/health`                | none   | Liveness plus a non-secret view of the configuration |
| `POST`   | `/v1/media/upload-url`   | Bearer | Presigned `PUT` for a key the **server** generates   |
| `POST`   | `/v1/media/download-url` | Bearer | Presigned `GET` for an existing key                  |
| `GET`    | `/v1/media/head?key=`    | Bearer | Object metadata, via a signed request to B2          |
| `DELETE` | `/v1/media?key=`         | Bearer | Delete, owner only                                   |

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
own port, and reach only `www.googleapis.com` (token keys) and `s3.us-east-005.backblazeb2.com`.

## Health check

```bash
curl -s http://localhost:8787/health
```

Returns the bucket, endpoint, region, size limits, and
`credentialsPresent: { b2KeyId: true, b2ApplicationKey: true }`. It reports only whether a
credential is set, never any part of its value, and the same view is printed once at startup.

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

## Not implemented yet

Posts, reels, stories, and chat. Only the media primitives above exist.
