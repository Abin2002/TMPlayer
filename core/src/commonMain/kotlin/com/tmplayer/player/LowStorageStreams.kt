package com.tmplayer.player

import com.tmplayer.data.OfflineDownloads

/**
 * The videos streaming in Low storage mode right now, and what each has on disk.
 *
 * The player decides, once per video, whether the mode applies: the setting is on, and the video
 * is only being watched, not one the viewer downloaded or is downloading. It says so with [allow]
 * and takes it back with [revoke]. [TdByteWindow] asks here before every download request, so
 * the state outlives the reopen at every seek, and a video the viewer starts downloading halfway
 * through is never deleted again from that moment on.
 *
 * Held per file id rather than per reader: the player opens a fresh reader at each seek, and the
 * ranges on disk belong to the file.
 */
object LowStorageStreams {

    private class State(val window: Long, var ranges: List<LowStorageWindow.Range>)

    private val files = HashMap<Int, State>()

    /** Lets [fileId] stream through a window of [windowBytes]. */
    @Synchronized
    fun allow(fileId: Int, windowBytes: Long) {
        files[fileId] = State(windowBytes, emptyList())
    }

    /** Back to streaming the whole file, as without the mode. */
    @Synchronized
    fun revoke(fileId: Int) {
        files.remove(fileId)
    }

    @Synchronized
    fun isRolling(fileId: Int): Boolean = fileId in files && !OfflineDownloads.isDownloading(fileId)

    /**
     * The request for a download of [fileId] from [offset], or null when the mode does not apply
     * and the download goes to the end of the file as it always has. Records what the request
     * leaves on disk, so call it only for a request that is about to be made.
     */
    @Synchronized
    fun plan(fileId: Int, offset: Long, size: Long): LowStorageWindow.Request? {
        val state = files[fileId] ?: return null
        // The download queue shares this file in TDLib: it is the viewer's now, and not ours to
        // delete or to cut short.
        if (OfflineDownloads.isDownloading(fileId)) return null
        val request = LowStorageWindow.plan(state.ranges, offset, size, state.window)
        state.ranges = request.ranges
        return request
    }

    /** Whether a read at [position] should roll [fileId]'s window on before it is served. */
    @Synchronized
    fun rollDue(fileId: Int, position: Long, size: Long): Boolean {
        val state = files[fileId] ?: return false
        if (OfflineDownloads.isDownloading(fileId)) return false
        return LowStorageWindow.shouldRoll(position, state.ranges, size)
    }
}
