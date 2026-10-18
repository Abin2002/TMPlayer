package com.tmplayer.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tmplayer.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a finished download into the phone's public Movies folder (CP82), where the gallery and
 * every other app find it. Only for a video whose chat allows saving (the caller checks
 * [KeepVideo.mayExport]); the download in TMPlayer's own folder stays where it is.
 *
 * Android 10 and later go through MediaStore, which needs no permission for the app's own new
 * files. Older versions write to the shared Movies folder directly, which this app does not hold
 * the storage permission for, so there it is refused with a reason rather than tried.
 */
object GalleryExport {

    /** Null when the copy is in Movies/TMPlayer, or why it is not. */
    suspend fun copy(context: Context, file: File, displayName: String, mimeType: String): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext L.keepExportFailed(L.errorsNoAnswer)
        if (!file.isFile) return@withContext L.keepExportFailed(L.downloadsFileGone)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, nameFor(displayName, file))
            put(MediaStore.Video.Media.MIME_TYPE, mimeType.ifBlank { "video/*" })
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TMPlayer")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            ?: return@withContext L.keepExportFailed(L.errorsNoAnswer)
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, BUFFER) } } != null
        }
        if (copied.getOrNull() != true) {
            runCatching { resolver.delete(uri, null, null) }
            val reason = copied.exceptionOrNull()?.let { Failures.humanise(it) } ?: L.errorsNoAnswer
            return@withContext L.keepExportFailed(reason)
        }
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
        null
    }

    /** The file's own name where it has one, so the gallery shows what the chat called it. */
    internal fun nameFor(displayName: String, file: File): String {
        val extension = file.extension.ifBlank { "mp4" }
        val base = displayName.trim().ifBlank { file.nameWithoutExtension }
            .replace(Regex("""[\\/:*?"<>|]"""), " ").trim().take(NAME_MAX)
        return if (base.endsWith(".$extension", ignoreCase = true)) base else "$base.$extension"
    }

    private const val BUFFER = 1 shl 16
    private const val NAME_MAX = 120
}
