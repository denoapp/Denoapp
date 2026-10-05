package com.deno.social.data.repository

import android.util.Log
import com.deno.social.data.remote.BackendApi
import com.deno.social.data.remote.BackendApis
import com.deno.social.data.remote.BackendClient
import com.deno.social.data.remote.PresignedDownload
import com.deno.social.data.remote.PresignedUpload
import com.deno.social.data.remote.backendErrorMessage
import kotlinx.coroutines.runBlocking

/**
 * Uploads local media to the Deno/B2 backend.
 *
 * The server mints a per-object key and a short-lived presigned PUT. This
 * object PUTs the file directly to that HTTPS URL and returns only the durable
 * remote reference ([StoredFile], whose `path` is the B2 object key). The B2
 * key id and application key never leave the server; the only credential the
 * app sends is the Firebase ID token.
 *
 * Wired up: profile avatars, scholar verification documents (certificate +
 * supporting). 1-to-1 chat attachments have their own flow in
 * [com.deno.social.data.media.ChatMediaStore] (upload, local adoption after
 * the message is stored, and cached resolution) because they need the message
 * id and the Firestore write to line up; posts, reels and stories keep their
 * existing behaviour until the backend exposes them.
 */
object MediaUploader {

    /**
     * The default API client. Swappable (as in the Phase 2A client) so a test
     * can point the uploader at a controlled origin without changing the app.
     */
    var api: BackendApi = BackendApis.default

    /**
     * The low-level client used for the direct B2 PUT. Defaults to the shared
     * backend client; a test can substitute its own.
     */
    var client: BackendClient = BackendClient()

/** Maximum avatar bytes, mirrored from the server's own limit. */
const val MAX_AVATAR_BYTES = 5L * 1024 * 1024

/** Maximum verification document bytes, mirrored from the server's own limit. */
const val MAX_VERIFICATION_DOC_BYTES = 10L * 1024 * 1024

/**
 * Uploads [bytes] as the current user's avatar.
     *
     * @throws com.deno.social.data.remote.BackendException for every failure
     * (no session, bad token, 400/401/403/413/5xx, timeout, offline or a
     * malformed response). A thrown failure means nothing was stored: an
     * existing avatar is left untouched.
     */
    suspend fun uploadAvatar(bytes: ByteArray, contentType: String): StoredFile {
        require(bytes.isNotEmpty()) { "The selected photo is empty" }
        require(bytes.size <= MAX_AVATAR_BYTES) { "The selected photo is larger than 5 MB" }
        require(contentType in ALLOWED_CONTENT_TYPES) {
            "Unsupported image type $contentType"
        }

        val target = presignAvatar(bytes.size.toLong(), contentType)
        client.putToPresigned(target.url, bytes, contentType)

        // The presigned URL is a short-lived bearer secret; it must not be
        // stored. Only the server-made object key is kept, and the avatar is
        // resolved again (to a fresh presigned GET) whenever it is displayed.
        return StoredFile(path = target.key, url = "")
    }

    /**
     * Requests a presigned avatar PUT for an already-validated size. Kept
     * public so callers can test the target without performing the upload.
     */
    suspend fun presignAvatar(sizeBytes: Long, contentType: String): PresignedUpload =
        api.presignUpload(AVATAR_KIND, contentType, sizeBytes)

    /**
     * Reads a cropped device image into memory within the avatar size cap. The
     * UI already crops to a square, so no huge original is ever decompressed.
     */
    fun readAvatarBytes(localPath: String): ByteArray {
        val file = java.io.File(localPath)
        // Safe facts only: the file name this app generated, whether it is
        // there, and its size. No parent directory, no URL, no credential.
        Log.d(
            "MediaUploader",
            "readAvatarBytes: name='${file.name}', isFile=${file.isFile}, exists=${file.exists()}, " +
                "bytes=${if (file.exists()) file.length() else -1L}, " +
                "readable=${file.canRead()}, isAbsolute=${file.isAbsolute}"
        )
        require(file.exists()) { "The selected photo is missing" }
        // TEMPORARY DIAGNOSTIC (safe to remove once the upload failure is fixed).
        // These two used to be one check, `file.length() in 1..MAX`, whose only
        // message claimed "larger than 5 MB". A 0-byte file therefore reported
        // the wrong reason entirely, which sent the investigation after the size
        // limit instead of after the write that produced the empty file. Split so
        // each failing condition has its own accurate message: a thrown
        // IllegalArgumentException from here now names the exact condition.
        require(file.length() <= MAX_AVATAR_BYTES) { "The selected photo is larger than 5 MB" }
        require(file.length() > 0) { "The selected photo is empty" }
        val bytes = file.readBytes()
        require(bytes.isNotEmpty()) { "The selected photo is empty" }
        return bytes
    }

    /**
     * Resolves a B2 object key to a usable, already-signed HTTPS GET for the
     * avatar loader. Returns null when the key is not ours or any step fails,
     * so the UI simply falls back to the initials avatar. The returned URL
     * expires quickly and is used immediately, never persisted.
     */
    fun avatarDisplayUrlBlocking(key: String): String? {
        if (!isAvatarKey(key)) return null
        val download: PresignedDownload = try {
            runBlocking { api.presignDownload(key) }
        } catch (error: Throwable) {
            val errMessage = backendErrorMessage(error)
            val errorClass = error.javaClass.simpleName
            Log.e("MediaUploader", "Photo download/display URL failed for key '$key' at stage 'presign-download' ($errorClass): $errMessage", error)
            return null
        }
        return download.url.takeIf { it.startsWith("https://") }
    }

    /** True for a key shaped like `media/users/{uid}/avatar/{id}.{ext}`. */
    private fun isAvatarKey(key: String): Boolean {
        if (key.isBlank()) return false
        val segments = key.split('/')
        return segments.size == 5 &&
            segments[0] == "media" &&
            segments[1] == "users" &&
            segments[2].isNotBlank() &&
            segments[3] == "avatar" &&
            segments[4].isNotBlank()
    }

    private const val AVATAR_KIND = "avatar"
    private const val CERTIFICATE_KIND = "certificate"
    private const val SUPPORTING_KIND = "supporting"
    private val ALLOWED_CONTENT_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic"
    )
    private val VERIFICATION_DOC_CONTENT_TYPES = setOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic"
    )

    /**
     * Uploads a verification document (certificate or supporting) for the
     * current user.
     *
     * The server mints a per-object key under `media/users/{uid}/<kind>/...`
     * and returns a presigned PUT. The file is uploaded directly to B2. The
     * returned [StoredFile.path] is the B2 object key, which is the canonical
     * reference stored in Firestore.
     */
    suspend fun uploadVerificationDocument(
        bytes: ByteArray,
        contentType: String,
        kind: String
    ): StoredFile {
        require(bytes.isNotEmpty()) { "The selected document is empty" }
        require(bytes.size <= MAX_VERIFICATION_DOC_BYTES) {
            "The selected document is larger than 10 MB"
        }
        require(contentType in VERIFICATION_DOC_CONTENT_TYPES) {
            "Unsupported document type $contentType"
        }
        require(kind == CERTIFICATE_KIND || kind == SUPPORTING_KIND) {
            "Unknown verification document kind $kind"
        }

        val target = presignVerificationDocument(bytes.size.toLong(), contentType, kind)
        client.putToPresigned(target.url, bytes, contentType)

        return StoredFile(path = target.key, url = "")
    }

    /**
     * Requests a presigned verification document PUT for an already-validated size.
     */
    suspend fun presignVerificationDocument(
        sizeBytes: Long,
        contentType: String,
        kind: String
    ): PresignedUpload =
        api.presignUpload(kind, contentType, sizeBytes)

    /**
     * Reads a document from a content URI into memory within the size cap.
     *
     * The URI comes from the system document picker and can be a `content://`
     * URI. This opens an input stream through the ContentResolver and reads the
     * bytes directly.
     */
    fun readVerificationDocumentBytes(
        context: android.content.Context,
        uri: android.net.Uri
    ): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open document")
        val bytes = inputStream.readBytes()
        inputStream.close()
        require(bytes.isNotEmpty()) { "The selected document is empty" }
        require(bytes.size <= MAX_VERIFICATION_DOC_BYTES) {
            "The selected document is larger than 10 MB"
        }
        return bytes
    }

    /**
     * Determines the content type of a document from its URI.
     *
     * Falls back to application/pdf if the content resolver cannot determine
     * the type.
     */
    fun getDocumentContentType(
        context: android.content.Context,
        uri: android.net.Uri
    ): String {
        val type = context.contentResolver.getType(uri)
        return type ?: "application/pdf"
    }
}