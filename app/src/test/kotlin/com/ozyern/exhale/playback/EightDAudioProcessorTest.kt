/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EightDAudioProcessorTest {
    private val rate = 44_100

    @After
    fun off() {
        EightDAudioProcessor.enabled = false
    }

    /** A loud, wide master: a full-scale tone hard left and a different one hard right. */
    private fun loudWideStereo(seconds: Float): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * 2) { i ->
            val f = i / 2
            if (i % 2 == 0) 0.99f * sin(2 * PI * 220 * f / rate).toFloat()
            else 0.99f * sin(2 * PI * 330 * f / rate).toFloat()
        }
    }

    private fun run(processor: EightDAudioProcessor, samples: FloatArray): FloatArray {
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val out = ArrayList<Float>(samples.size)
        var i = 0
        while (i < samples.size) {
            val n = minOf(2048, samples.size - i)
            val buf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until n) buf.putFloat(samples[i + k])
            buf.flip()
            processor.queueInput(buf)
            val o = processor.output
            while (o.hasRemaining()) out.add(o.float)
            i += n
        }
        return out.toFloatArray()
    }

    @Test
    fun offIsBitExact() {
        EightDAudioProcessor.enabled = false
        val input = loudWideStereo(0.5f)
        val output = run(EightDAudioProcessor(), input)
        assertEquals(input.size, output.size)
        for (i in input.indices) assertEquals(input[i], output[i], 0f)
    }

    @Test
    fun loudWideMasterNeverClipsAndKeepsBothSides() {
        EightDAudioProcessor.enabled = true
        EightDAudioProcessor.rotationHz = EightDAudioProcessor.MAX_ROTATION_HZ
        val input = loudWideStereo(6f)
        val output = run(EightDAudioProcessor(), input)
        var peak = 0f
        output.forEach { if (it.isNaN()) throw AssertionError("NaN in output"); peak = maxOf(peak, abs(it)) }
        assertTrue("peak $peak over the limiter's ceiling", peak <= 0.971f)

        // The hard-right part must never vanish from the mix, whichever way the orbit swings:
        // measure the 330 Hz energy in each half-second window of the left+right sum.
        val window = rate / 2
        var w = 0
        while ((w + 1) * window * 2 <= output.size) {
            var re = 0.0
            var im = 0.0
            for (f in w * window until (w + 1) * window) {
                val s = (output[f * 2] + output[f * 2 + 1]).toDouble()
                re += s * kotlin.math.cos(2 * PI * 330 * f / rate)
                im += s * sin(2 * PI * 330 * f / rate)
            }
            val amp = kotlin.math.sqrt(re * re + im * im) / window
            assertTrue("hard-right part dropped to $amp in window $w", amp > 0.15)
            w++
        }
    }
}
