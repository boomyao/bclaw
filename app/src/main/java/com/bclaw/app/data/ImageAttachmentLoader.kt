package com.bclaw.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.bclaw.app.domain.v2.ImageAttachment
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies an image URI (content:// from the photo picker, typically) into app-private
 * storage and encodes the bytes for the ACP `image` content block in one pass.
 *
 * Returns [LoadedAttachment.persisted] so the timeline can render the thumbnail from a
 * stable path, and [LoadedAttachment.base64Data] so the controller can stuff it into the
 * prompt without re-reading the bytes.
 *
 * Caller decides the cap; we reject anything larger by returning null so the composer can
 * surface "image too big" without swallowing the error.
 */
interface ImageAttachmentLoader {
    suspend fun load(sourceUri: String, maxBytes: Long = MAX_BYTES_DEFAULT): LoadedAttachment?

    /**
     * Persist a pre-fetched image byte buffer (e.g. pulled from the bridge) to app-private
     * storage. Used when the user picked an image via Tools→files → the content already
     * travelled the wire, we just need a stable file URI for the timeline + a base64 copy
     * for the ACP `image` content block.
     */
    suspend fun loadBytes(
        bytes: ByteArray,
        mimeType: String,
        maxBytes: Long = MAX_BYTES_DEFAULT,
    ): LoadedAttachment?

    companion object {
        const val MAX_BYTES_DEFAULT: Long = 5_000_000L
    }
}

data class LoadedAttachment(
    val persisted: ImageAttachment,
    val base64Data: String,
)

class AndroidImageAttachmentLoader(context: Context) : ImageAttachmentLoader {
    private val appContext = context.applicationContext

    override suspend fun load(sourceUri: String, maxBytes: Long): LoadedAttachment? =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext null
            val resolver = appContext.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val bytes = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null
            persistImage(bytes, mime, maxBytes)
        }

    override suspend fun loadBytes(
        bytes: ByteArray,
        mimeType: String,
        maxBytes: Long,
    ): LoadedAttachment? = withContext(Dispatchers.IO) {
        persistImage(bytes, mimeType, maxBytes)
    }

    private fun persistImage(bytes: ByteArray, mime: String, maxBytes: Long): LoadedAttachment? {
        if (bytes.size > maxBytes) return null
        val dir = File(appContext.filesDir, "attachments").apply { mkdirs() }
        val ext = when (mime) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            "image/bmp" -> "bmp"
            "image/svg+xml" -> "svg"
            else -> "jpg"
        }
        val file = File(dir, "${UUID.randomUUID()}.$ext")
        file.writeBytes(bytes)
        return LoadedAttachment(
            persisted = ImageAttachment(
                uri = file.toURI().toString(),
                mimeType = mime,
                sizeBytes = bytes.size.toLong(),
            ),
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }
}
