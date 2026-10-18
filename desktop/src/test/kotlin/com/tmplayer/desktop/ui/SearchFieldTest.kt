package com.tmplayer.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.unit.dp
import com.tmplayer.ui.theme.TmMaterialTheme
import androidx.compose.ui.graphics.toAwtImage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The Chats page's search field: one height, empty or typed in, so typing never moves the
 * heading's row (it was Material's 56 dp outlined field, which grew a clear button as you typed).
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldTest {

    @Test
    fun `typing keeps the field's height`() = runComposeUiTest {
        setContent {
            TmMaterialTheme(dark = true) {
                var query by remember { mutableStateOf("") }
                Box(Modifier.padding(16.dp)) {
                    SearchField(query, { query = it }, "Search chats", remember { FocusRequester() }, Modifier.width(280.dp).testTag("field"))
                }
            }
        }
        waitForIdle()
        val empty = onNodeWithTag("field").fetchSemanticsNode().size
        save("search-field-empty.png")
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performTextInput("harbour")
        waitForIdle()
        val typed = onNodeWithTag("field").fetchSemanticsNode().size
        save("search-field-typed.png")
        assertEquals(empty, typed)
        assertEquals(40f, empty.height / density.density, 0.5f)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.save(name: String) {
        val out = System.getenv("TMPLAYER_SHOTS")?.takeIf { it.isNotBlank() } ?: return
        val image = onNodeWithTag("field").captureToImage().toAwtImage()
        javax.imageio.ImageIO.write(image, "png", File(out).apply { mkdirs() }.resolve(name))
    }
}
