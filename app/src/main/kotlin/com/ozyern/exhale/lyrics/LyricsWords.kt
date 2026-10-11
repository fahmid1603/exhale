/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

object LyricsWords {
    /** See [sameWords]: below this, two copies are different songs. */
    private const val SAME_WORDS_SHARE = 0.3

    /**
     * Whether two copies of lyrics are the same song's words, however each is timed or transcribed:
     * the share of distinct words (three letters and up) the two have in common. Two transcriptions
     * of one song share most of theirs; two different songs, little beyond the commonest words.
     */
    fun sameWords(a: String, b: String): Boolean {
        val first = wordsOf(a)
        val second = wordsOf(b)
        if (first.size < 8 || second.size < 8) return true
        val shared = first.intersect(second).size.toDouble()
        return shared / (first.size + second.size - shared) >= SAME_WORDS_SHARE
    }

    private fun wordsOf(lyrics: String): Set<String> {
        val text = when {
            LyricsUtils.isTtml(lyrics.trimStart()) -> LyricsUtils.parseTtml(lyrics).joinToString(" ") { it.text }
            lyrics.trimStart().startsWith("[") -> LyricsUtils.parseLyrics(lyrics).joinToString(" ") { it.text }
            else -> lyrics
        }
        return SongQuery.normalized(text).split(' ').filter { it.length >= 3 }.toSet()
    }
}
