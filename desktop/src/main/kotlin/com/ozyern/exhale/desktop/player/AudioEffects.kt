/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.EqualizerPresets
import com.ozyern.exhale.desktop.data.Settings

/**
 * Player & audio settings as an mpv audio filter chain (ffmpeg lavfi): the equalizer, skip silence
 * and the spatial upmix. What Android does with AudioEffect and ExoPlayer, done by the decoder here.
 */
object AudioEffects {
    fun filterChain(settings: Settings): String {
        val filters = buildList {
            if (settings.equalizerEnabled) {
                val gains = EqualizerPresets.frequencies.indices.map { settings.equalizerBands.getOrElse(it) { 0 } }
                // Headroom for the boost, so raised bands don't clip.
                val peak = gains.maxOrNull() ?: 0
                if (peak > 0) add("volume=-${peak}dB")
                EqualizerPresets.frequencies.forEachIndexed { index, frequency ->
                    val gain = gains[index]
                    if (gain != 0) add("equalizer=f=$frequency:t=o:w=1:g=$gain")
                }
            }
            if (settings.skipSilence) {
                add("silenceremove=start_periods=1:start_duration=0.05:start_threshold=-50dB:stop_periods=-1:stop_duration=1.5:stop_threshold=-50dB")
            }
            // YouTube only ever sends stereo. Spread into a surround bed, it gives Dolby Atmos for
            // Headphones (or Windows Sonic) channels to place around the listener instead of two.
            //
            // A placement, not a decode. This used to be ffmpeg's `surround` filter, which is a
            // Dolby Pro Logic *decoder*: it assumes the stereo it is given was matrix-encoded and
            // recovers the rear channels from the out-of-phase content. Ordinary music is not
            // matrix-encoded, so what it recovers is whatever the mix happens to have out of phase
            // - reverb tails, wide synths, half the hi-hat - thrown behind the listener while the
            // centre is pulled out of the front pair. That is the hollow, swirling sound the
            // toggles produced, and no amount of "quality" settings fixes it, because the filter
            // is answering a different question.
            //
            // So the fronts are passed through untouched, the centre is a level-matched sum, and
            // the rears and sides are quiet copies delayed by a few milliseconds. The delay is the
            // point: identical audio from front and back combs, while a 10-16ms offset reads as
            // room rather than as a second copy of the track (Haas). Nothing is invented and
            // nothing is subtracted, so a stereo mix stays the mix - it just has somewhere to sit
            // when Windows Sonic or Atmos places it.
            //
            // LFE is left silent on purpose: a full-range copy of the music in a channel the
            // receiver expects to be low-passed is how an upmix ends up boomy.
            when (settings.spatialAudio) {
                "SURROUND_71" -> {
                    add(
                        "pan=7.1|FL=FL|FR=FR|FC=0.4*FL+0.4*FR|LFE=0" +
                            "|BL=0.38*FL|BR=0.38*FR|SL=0.30*FL|SR=0.30*FR"
                    )
                    add("adelay=0|0|0|0|16|16|10|10")
                }

                "SURROUND_51" -> {
                    add("pan=5.1|FL=FL|FR=FR|FC=0.4*FL+0.4*FR|LFE=0|BL=0.40*FL|BR=0.40*FR")
                    add("adelay=0|0|0|0|14|14")
                }
            }
        }
        return if (filters.isEmpty()) "" else "lavfi=[${filters.joinToString(",")}]"
    }

    /** What mpv asks Windows for — a list, so a device that can't take surround falls back to stereo. */
    fun outputChannels(settings: Settings): String = when (settings.spatialAudio) {
        "SURROUND_71" -> "7.1,5.1,stereo"
        "SURROUND_51" -> "5.1,stereo"
        else -> "auto-safe"
    }

    /**
     * For when the device doesn't run at the stream's rate (AAC is 44.1 kHz, most Windows devices 48):
     * SoX's very-high-quality resampler, a steep filter that keeps the top octave, and noise-shaped
     * dither whenever the output drops to integer samples (exclusive mode on a 16- or 24-bit device).
     */
    fun resamplerOptions(settings: Settings): String =
        if (settings.hqResampler) "resampler=soxr,precision=28,cutoff=0.97,dither_method=triangular_hp" else ""
}
