/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * Whether timed lyrics could belong to the recording that is playing, judged by their timestamps
 * against its length — the same test the Windows app puts every source through.
 *
 * Timings from another cut of the song (a radio edit, a live take, the album version of a single, a
 * video with an intro) run past the end, start late, or stop far short, and scroll out of step from
 * the first verse. Those are turned away so another source's copy is shown instead; untimed lyrics,
 * and songs whose length is not known, always pass.
 */
object LyricsFit {
    /** Below this a length is a preview or unknown, and says nothing. */
    private const val MIN_DURATION_S = 30

    fun fits(lyrics: String, durationSeconds: Int): Boolean {
        if (durationSeconds < MIN_DURATION_S) return true
        val trimmed = lyrics.trim()
        val times = when {
            LyricsUtils.isTtml(trimmed) -> runCatching { LyricsUtils.parseTtml(trimmed).map { it.time } }.getOrNull()
            trimmed.startsWith("[") -> runCatching { LyricsUtils.parseLyrics(trimmed).filter { it.text.isNotBlank() }.map { it.time } }.getOrNull()
            else -> null
        }?.filter { it >= 0 }?.sorted() ?: return true
        if (times.size < 4) return true
        val first = times.first() / 1000.0
        val last = times.last() / 1000.0
        // Past the end: another, longer cut.
        if (last > durationSeconds + 10) return false
        // Starting most of the way through: timed for something else entirely.
        if (first > durationSeconds * 0.6) return false
        // Finished well before half-way through a full-length song: a shorter edit's timing.
        if (durationSeconds > 90 && last < durationSeconds * 0.4) return false
        return true
    }
}
