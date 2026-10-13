package com.tmplayer.ui.settings

import android.content.Context
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.tmplayer.data.LogExport
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.WatchedStore
import com.tmplayer.i18n.L
import com.tmplayer.platform.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * "Save logs" (CP59), beside "Report a problem": this app's own recent logcat, scrubbed by
 * [LogExport], in a text file the viewer attaches to the report.
 *
 * Saved the way a backup is (see [rememberBackupActions]): through the document picker, so the
 * viewer chooses where, or straight into the shared Download folder on a TV box that has no
 * picker. [say] is the toast.
 */
@Composable
internal fun rememberSaveLogs(say: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val text = logReport(context)
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                    }.onFailure { Logger.w(TAG, "Logs not written", it) }.isSuccess
                }
                say(if (saved) L.logsSaved else L.logsFailed)
            }
        }
    }

    return {
        val name = LogExport.fileName()
        val picked = runCatching { saver.launch(name) }
        if (picked.isFailure) {
            scope.launch {
                val place = withContext(Dispatchers.IO) {
                    runCatching { SharedDownloads.write(context, name, logReport(context), MIME) }
                        .onFailure { Logger.w(TAG, "Logs not written to Download", it) }
                        .getOrNull()
                }
                say(if (place != null) L.logsSavedTo(place) else L.logsFailed)
            }
        }
    }
}

/**
 * The file's text: the facts a bug report carries (version, model, Android version, TV or
 * phone), then this process's last [LogExport.MAX_LINES] logcat lines with the names this device
 * has seen, ids, phone numbers, usernames and file names taken out.
 *
 * An app may read its own log lines without any permission; `--pid` keeps it to this process,
 * which is all it could read anyway, and the player runs in the same one.
 */
private suspend fun logReport(context: Context): String {
    val lines = runCatching { ownLogcat() }
        .onFailure { Logger.w(TAG, "logcat could not be read", it) }
        .getOrDefault(emptyList())
    val known = LogExport.knownNames(SettingsStore(context), WatchedStore(context))
    return LogExport.report(feedbackFacts(context), lines, known)
}

private fun ownLogcat(): List<String> {
    val process = ProcessBuilder(
        "logcat", "-d", "-v", "threadtime", "-t", LogExport.MAX_LINES.toString(), "--pid=${Process.myPid()}",
    ).redirectErrorStream(true).start()
    return try {
        process.inputStream.bufferedReader().useLines { it.toList() }.takeLast(LogExport.MAX_LINES)
    } finally {
        process.waitFor(2, TimeUnit.SECONDS)
        process.destroy()
    }
}

private const val MIME = "text/plain"
private const val TAG = "Logs"
