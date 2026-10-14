package com.tmplayer.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tmplayer.data.DeviceForm
import com.tmplayer.ui.components.deviceForm
import com.tmplayer.ui.theme.LocalDarkTheme

/**
 * Where the Android pictures come from. They are the app's own drawables, which this library cannot
 * name, so the app hands over the lookup when it starts (App.kt).
 */
object SetupImages {
    /** The drawable for [choice] on a television or a phone, light or dark; 0 when it has none. */
    @Volatile
    var drawable: (choice: SetupChoice, tv: Boolean, dark: Boolean) -> Int = { _, _, _ -> 0 }
}

/**
 * The picture of an answer: a cropped, marked shot of the part of the app it changes, from the
 * television or the phone, light or dark to match what the reader is looking at. It fills
 * [modifier]'s box, which the caller gives the shots' square shape.
 */
@Composable
internal fun SetupShot(choice: SetupChoice, modifier: Modifier = Modifier) {
    val tv = deviceForm() == DeviceForm.Tv
    val id = SetupImages.drawable(choice, tv, LocalDarkTheme.current)
    if (id == 0) return
    Image(
        painter = painterResource(id),
        // The answer's name under it is the description; a second reading is noise.
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
