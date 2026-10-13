package com.tmplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tmplayer.ui.theme.Tone

/**
 * A media preview that never leaves a hole in the grid: the blurred inline preview paints
 * immediately, the real thumbnail replaces it when it arrives, and a stand-in fills the frame
 * when a message has no art at all. The loading is [MediaArt] in `:ui`; the stand-in is chosen
 * here.
 *
 * A video passes its [cover], and a file with no picture then gets its [GeneratedCover]. A chat's
 * or an account's picture passes none and keeps the letter of [fallbackLabel], which is what a
 * missing face looks like everywhere else on the device.
 */
@Composable
fun MediaPreview(
    miniThumbnail: ByteArray?,
    thumbnailFileId: Int,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    cover: Cover? = null,
) {
    if (cover != null) {
        MediaArt(miniThumbnail, thumbnailFileId, modifier, cover = cover, compactCover = isTouch())
        return
    }
    MediaArt(miniThumbnail, thumbnailFileId, modifier) {
        Text(
            fallbackLabel.take(1).uppercase(),
            // The tile this letter stands in for is a third of the size on a phone, where a
            // headline glyph fills it corner to corner and reads as a mistake.
            style = if (isTouch()) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineLarge
            },
            color = if (isTouch()) Tone.muted else Color.Unspecified,
        )
    }
}
