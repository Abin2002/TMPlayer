package com.tmplayer.player

import com.tmplayer.data.valueOrNull
import dev.g000sha256.tdl.TdlClient

/**
 * Fast start, the honest version: the first bytes of the next video are already on their way
 * before it is asked for, so opening it finds headers on disk instead of an empty file.
 *
 * Best effort and silent throughout: a prefetch that fails leaves exactly what not prefetching
 * would have, and nothing here may ever surface an error or take bandwidth from the video
 * playing now, which is why the request goes at the lowest priority TDLib takes.
 */
object PrefetchHeads {
    /** How much of the head to warm: the container headers and the first keyframes. */
    const val HEAD_BYTES = 2L * 1024 * 1024

    /** Below playback's 32 ([TdByteWindow]), so a prefetch never queues ahead of watching. */
    private const val PREFETCH_PRIORITY = 1

    /** Starts [fileId]'s first [HEAD_BYTES] downloading, unless there is nothing to do. */
    suspend fun warm(td: TdlClient, fileId: Int) {
        runCatching {
            if (fileId <= 0) return
            val file = td.getFile(fileId).valueOrNull ?: return
            if (file.local.isDownloadingCompleted) return
            // Already on its way, by the player or an earlier warm: TDLib merges the requests.
            if (file.local.isDownloadingActive) return
            td.downloadFile(
                fileId = fileId,
                priority = PREFETCH_PRIORITY,
                offset = 0,
                limit = HEAD_BYTES,
                synchronous = false,
            )
        }
    }
}
