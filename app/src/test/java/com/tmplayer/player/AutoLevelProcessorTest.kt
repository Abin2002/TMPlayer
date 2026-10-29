package com.tmplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AutoLevelProcessorTest {

    /** 48 kHz mono 16 bit. */
    private val format = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)

    private fun processor(on: Boolean) = AutoLevelProcessor().apply {
        configure(format)
        flush()
        enabled = on
    }

    /**
     * A steady square wave at [level] of full scale: its RMS is the level itself, which keeps
     * the levelling arithmetic simple.
     */
    private fun square(level: Float, frames: Int) =
        ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putShort(((if (it % 2 == 0) level else -level) * 32767).toInt().toShort()) }
            flip()
        }

    private fun silence(frames: Int) =
        ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder()).apply {
            repeat(frames) { putShort(0) }
            flip()
        }

    private fun run(processor: AutoLevelProcessor, input: ByteBuffer): List<Float> {
        processor.queueInput(input)
        val out = processor.output
        return buildList { while (out.hasRemaining()) add(out.short / 32768f) }
    }

    @Test
    fun `off passes the sound through untouched`() {
        val out = run(processor(false), square(0.5f, 4800))
        assertEquals(4800, out.size)
        assertEquals(0.5f, abs(out.last()), 0.001f)
    }

    @Test
    fun `a quiet video is lifted to the target level`() {
        // Six seconds: a two second window to measure, then the walk up, then settled.
        val out = run(processor(true), square(0.02f, 48_000 * 6))
        assertEquals(AutoLevelProcessor.TARGET_RMS, abs(out.last()), 0.005f)
    }

    @Test
    fun `a loud video is turned down, but only to the floor`() {
        val out = run(processor(true), square(0.5f, 48_000 * 6))
        assertEquals(0.5f * AutoLevelProcessor.MIN_GAIN, abs(out.last()), 0.005f)
    }

    @Test
    fun `a full scale video never clips`() {
        val out = run(processor(true), square(1f, 48_000 * 6))
        assertTrue(out.all { abs(it) <= 1f })
    }

    @Test
    fun `the gain walks rather than jumps`() {
        val out = run(processor(true), square(0.02f, 48_000 * 6))
        // The first window is still being measured at the input's own level.
        assertEquals(0.02f, abs(out.first()), 0.001f)
    }

    @Test
    fun `switching off walks the gain back to one`() {
        val p = processor(true)
        run(p, square(0.02f, 48_000 * 6))
        p.enabled = false
        val out = run(p, square(0.02f, 48_000 * 6))
        assertEquals(0.02f, abs(out.last()), 0.002f)
    }

    @Test
    fun `silence does not move the gain`() {
        val p = processor(true)
        run(p, square(0.02f, 48_000 * 6))
        val out = run(p, silence(48_000 * 4))
        assertTrue(out.all { it == 0f })
        // And the level is still where the quiet video left it when sound returns.
        val back = run(p, square(0.02f, 48_000))
        assertEquals(AutoLevelProcessor.TARGET_RMS, abs(back.last()), 0.01f)
    }

    @Test
    fun `desired gain levels quiet and loud towards the target`() {
        assertEquals(4f, AutoLevelProcessor.desiredGain(0.02f, 0.02f, 1f), 0.001f)
        assertEquals(
            AutoLevelProcessor.MIN_GAIN,
            AutoLevelProcessor.desiredGain(0.5f, 0.5f, 1f),
            0.001f,
        )
        assertEquals(
            AutoLevelProcessor.MAX_GAIN,
            AutoLevelProcessor.desiredGain(0.01f, 0.01f, 1f),
            0.001f,
        )
    }

    @Test
    fun `desired gain is capped by the window's peak`() {
        // A quiet average with one loud frame in it: the lift stops at the ceiling.
        assertEquals(1.1f, AutoLevelProcessor.desiredGain(0.02f, 0.9f, 1f), 0.001f)
    }

    @Test
    fun `desired gain holds through silence`() {
        assertEquals(2f, AutoLevelProcessor.desiredGain(0f, 0f, 2f), 0.001f)
        assertEquals(2f, AutoLevelProcessor.desiredGain(0.001f, 0.001f, 2f), 0.001f)
    }
}
