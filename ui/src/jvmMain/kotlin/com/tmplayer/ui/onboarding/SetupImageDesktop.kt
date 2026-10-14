package com.tmplayer.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.tmplayer.data.decodeImage
import com.tmplayer.ui.theme.LocalDarkTheme

/**
 * The picture of an answer on the desktop: a WebP on the classpath, `onboarding/<name>`, cropped
 * and marked by `scripts/annotate-shot.py` from the desktop promo fixture's shots
 * (`TMPLAYER_PROMO=<dir> ./gradlew :desktop:test --tests '*PromoShots*'`), so the setup shows the
 * window the viewer is about to use.
 */
@Composable
internal fun SetupShot(choice: SetupChoice, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val image = remember(choice, dark) { load(choice, dark) } ?: return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** The file name of an answer's picture, for the fixture that writes it and the setup that reads it. */
fun setupImageName(choice: SetupChoice, dark: Boolean): String? {
    val shot = when (choice) {
        SetupChoice.Everything -> "library-everything"
        SetupChoice.OnlyFolders -> "library-folders"
        SetupChoice.PostersOn -> "posters-on"
        SetupChoice.PostersOff -> "posters-off"
        SetupChoice.KeepWhilePlaying, SetupChoice.LowStorage, SetupChoice.ApplyRecommended, SetupChoice.NotNow -> return null
    }
    return "$shot-${if (dark) "dark" else "light"}.webp"
}

private fun load(choice: SetupChoice, dark: Boolean): ImageBitmap? {
    val name = setupImageName(choice, dark) ?: return null
    val bytes = SetupChoice::class.java.getResourceAsStream("/onboarding/$name")?.use { it.readBytes() } ?: return null
    return decodeImage(bytes)
}
