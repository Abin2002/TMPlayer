package com.tmplayer.ui.browse

import com.tmplayer.data.MediaItem
import com.tmplayer.data.WatchLaterRecord
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * A page opened from a saved record's card (Continue watching, Watch later, Watched) is handed the
 * record's item, which keeps no thumbnail; [lacksArt] is what sends it to Telegram for the picture
 * the card showed, instead of drawing the generated cover.
 */
class RecordArtTest {

    private fun video(thumb: Int = 0, mini: ByteArray? = null, messageId: Long = 42) = MediaItem(
        chatId = 7,
        messageId = messageId,
        fileId = 9,
        title = "Show S05E02.mkv",
        sizeBytes = 1,
        durationSec = 60,
        mimeType = "video/x-matroska",
        thumbnailFileId = thumb,
        miniThumbnail = mini,
        date = 0,
    )

    @Test
    fun aRecordsItemHasNoPictureOfItsOwn() {
        val record = WatchLaterRecord(
            chatId = 7,
            messageId = 42,
            fileId = 9,
            title = "Show S05E02",
            fileName = "Show S05E02.mkv",
            chatTitle = "Shows",
            sizeBytes = 1,
            durationSec = 60,
            mimeType = "video/x-matroska",
            addedAt = 0,
        )
        assertTrue(lacksArt(record.toMediaItem()))
    }

    @Test
    fun aThumbnailOrAnInlinePreviewIsEnough() {
        assertFalse(lacksArt(video(thumb = 5)))
        assertFalse(lacksArt(video(mini = byteArrayOf(1, 2, 3))))
        assertTrue(lacksArt(video(mini = ByteArray(0))))
    }

    @Test
    fun anItemWithNoMessageIsNotLookedUp() {
        assertFalse(lacksArt(video(messageId = 0)))
    }
}
