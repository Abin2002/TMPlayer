package com.tmplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Auto volume level: videos brought to one loudness, so a quiet upload no longer sends the
 * viewer to the volume keys and a loud one no longer shouts over it.
 *
 * [VolumeBoostProcessor] lifts by a fixed amount and squeezes the peaks; it cannot know that
 * one video was mastered whispering and the next at full blast. This measures the sound as it
 * plays (the RMS over a window of about two seconds, which is close enough to loudness for a
 * level) and walks a slow gain towards [TARGET_RMS] over the measurement: quiet videos come
 * up, loud ones go down, and the walk is slow enough (the whole span over about two seconds)
 * that a scene's own loud and quiet moments are left alone. The gain is clamped to
 * [MIN_GAIN]..[MAX_GAIN], and capped by the window's peak so the lift never pushes the
 * loudest recent frame past [CEILING]. Silence does not move the gain: there is nothing to
 * level in a quiet stretch, and chasing the noise floor would boom when the sound returns.
 *
 * Always in the chain, so it can be switched while the video plays: the sink's processors are
 * fixed when the player is built. Off, the gain walks back to one and then the sound is copied
 * straight through. Before [VolumeBoostProcessor] in the chain, so its compressor still hears
 * the levelled sound and holds the peaks the measurement window has not seen yet.
 *
 * Handles 16 bit and float PCM, which is what the decoders here hand over. Anything else, and
 * a Dolby or DTS track sent to the receiver untouched, passes it by.
 */
@UnstableApi
class AutoLevelProcessor : BaseAudioProcessor() {

    /** Whether the levelling is wanted. Set from the main thread, read on the playback thread. */
    @Volatile
    var enabled: Boolean = false

    /** The gain actually applied, walking towards [desired]. */
    private var gain = 1f

    /** The gain the last finished window asked for. One while off. */
    private var desired = 1f

    private var rampStep = 0f
    private var windowTarget = 0
    private var windowFrames = 0
    private var sumSquares = 0.0
    private var windowPeak = 0f
    private var frame = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val rate = inputAudioFormat.sampleRate.toFloat()
        rampStep = (MAX_GAIN - MIN_GAIN) / (RAMP_SECONDS * rate)
        windowTarget = (WINDOW_SECONDS * rate).toInt()
        frame = FloatArray(inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        if (!enabled && gain == 1f) {
            resetWindow()
            out.put(inputBuffer).flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val channels = frame.size
        val frames = remaining / inputAudioFormat.bytesPerFrame
        repeat(frames) {
            for (c in 0 until channels) {
                frame[c] = if (float) input.getFloat() else input.getShort() / SHORT_SCALE
            }
            // Measured before the gain: levelling the output would chase its own tail.
            var peak = 0f
            for (c in 0 until channels) {
                val sample = frame[c]
                sumSquares += sample * sample
                peak = maxOf(peak, abs(sample))
            }
            windowPeak = maxOf(windowPeak, peak)
            if (++windowFrames >= windowTarget) settleWindow()
            gain = if (gain < desired) minOf(desired, gain + rampStep) else maxOf(desired, gain - rampStep)
            for (c in 0 until channels) {
                val sample = (frame[c] * gain).coerceIn(-1f, 1f)
                if (float) {
                    out.putFloat(sample)
                } else {
                    out.putShort((sample * SHORT_SCALE).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }
        out.flip()
    }

    /** A window's worth of sound is in: ask for the gain it points to, and start the next one. */
    private fun settleWindow() {
        val rms = sqrt(sumSquares / (windowFrames * frame.size)).toFloat()
        val peak = windowPeak
        resetWindow()
        desired = if (enabled) desiredGain(rms, peak, desired) else 1f
    }

    private fun resetWindow() {
        windowFrames = 0
        sumSquares = 0.0
        windowPeak = 0f
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // The same video, a step along it: the level it had is the level it still wants.
        resetWindow()
    }

    override fun onReset() {
        resetWindow()
        gain = 1f
        desired = 1f
    }

    companion object {
        /** The loudness aimed for: an RMS of about -22 dBFS, where dialogue usually sits. */
        const val TARGET_RMS = 0.08f

        /** The least the levelling will turn a video down: about -12 dB. */
        const val MIN_GAIN = 0.25f

        /** The most it will lift one: about +14 dB. */
        const val MAX_GAIN = 5f

        /** The gain never pushes the window's loudest frame past this. */
        const val CEILING = 0.99f

        /** Under about -54 dBFS a window counts as silence, and the gain holds. */
        const val SILENCE_RMS = 0.002f

        /**
         * The gain a window measured at [rms] with its loudest frame at [peak] asks for,
         * starting from [current]: the level over the measurement, kept inside the clamps and
         * under the ceiling the peak sets. A silent window keeps [current]. The ceiling is
         * what stops a quiet window with one loud bang in it (dialogue, then a door slam)
         * being lifted by the full amount the average asks for.
         */
        fun desiredGain(rms: Float, peak: Float, current: Float): Float {
            if (rms < SILENCE_RMS) return current
            var wanted = (TARGET_RMS / rms).coerceIn(MIN_GAIN, MAX_GAIN)
            if (peak > 0f) wanted = minOf(wanted, CEILING / peak)
            return wanted.coerceIn(0.1f, MAX_GAIN)
        }

        private const val WINDOW_SECONDS = 2f
        private const val RAMP_SECONDS = 2f
        private const val SHORT_SCALE = 32768f
    }
}
