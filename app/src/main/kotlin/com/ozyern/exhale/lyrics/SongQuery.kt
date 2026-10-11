/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

/**
 * What to search the lyrics sites for. YouTube titles carry things no lyrics database has in its
 * title — "(feat. Don Toliver)", "[Official Video]", "- 2011 Remaster" — and a search that includes
 * them often finds nothing, or a different song. The same cleaning the Windows app uses.
 */
internal object SongQuery {
    private val bracketed = Regex("""\s*[(\[]([^)\]]*)[)\]]""")
    private val noise = Regex("""(?i)\b(feat\.?|ft\.?|featuring|with|official|video|audio|lyrics?|lyrical|visuali[sz]er|m/?v|hd|hq|4k|explicit|clean|remaster(ed)?|prod\.?|song|full|teaser|promo|title\s+track)\b""")
    private val trailingFeature = Regex("""(?i)\s+(feat\.?|ft\.?|featuring)\s.*$""")
    private val trailingRemaster = Regex("""(?i)\s+-\s+[^-]*remaster[^-]*$""")

    /** An upload's tag after a dash rather than in brackets: "Kesariya – Full Video". */
    private val trailingDashNoise =
        Regex("""(?i)\s+[-–—]\s+[^-–—]*\b(full|official|video|audio|lyrics?|lyrical|song|teaser|promo|visuali[sz]er)\b[^-–—]*$""")

    /**
     * What to search for. A song uploaded as a plain video is titled the way a channel titles a video —
     * "Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | T-Series" — where everything after
     * the first bar is credits, and searching the whole string finds nothing at all.
     */
    fun cleanTitle(title: String): String {
        val head = title.substringBefore('|').trim().ifBlank { title }
        var cleaned = bracketed.replace(head) { match -> if (noise.containsMatchIn(match.groupValues[1])) "" else match.value }
        cleaned = trailingFeature.replace(cleaned, "")
        cleaned = trailingRemaster.replace(cleaned, "")
        cleaned = trailingDashNoise.replace(cleaned, "")
        return cleaned.replace(Regex("""\s+"""), " ").trim().ifBlank { head.trim() }
    }

    /**
     * The artist as a lyrics site would have it. Uploads credit the label ("T-Series"), and a bar-
     * separated title usually names the singer itself — the segment after the song is a better guess
     * than the channel.
     */
    fun creditedArtist(title: String, artists: List<String>): String? {
        val lead = artists.firstOrNull()?.takeIf { it.isNotBlank() }
        val segments = title.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 2) return lead
        return segments.drop(1).firstOrNull { segment ->
            segment.length in 2..40 && !segment.equals(lead, ignoreCase = true) && !noise.containsMatchIn(segment)
        } ?: lead
    }

    private val bracketedPart = Regex("""[(\[][^)\]]*[)\]]""")
    private val combiningMarks = Regex("""\p{Mn}+""")
    private val artistSeparators = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Letters and digits only, lower case, accents dropped: "Don’t Smile" and "Don't Smile" agree. */
    fun normalized(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(java.util.Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun coreOf(title: String): String =
        normalized(cleanTitle(title).replace(bracketedPart, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    /**
     * Whether a lyrics index's hit is the song asked for: the same title outside its brackets, and
     * an artist in common. An index that searches by name ranks by its own idea of close, and
     * "Don't Smile" by somebody else, the same length give or take, came back first and was shown
     * as Sabrina Carpenter's. A hit that names no artist is judged on the title alone.
     */
    fun sameSong(title: String, artist: String, hitTitle: String?, hitArtist: String?): Boolean {
        val wanted = coreOf(title)
        val got = coreOf(hitTitle ?: return false)
        if (wanted.isEmpty() || got.isEmpty()) return false
        val titleMatches = wanted == got ||
            (minOf(wanted.length, got.length) >= 4 && (wanted.startsWith(got) || got.startsWith(wanted)))
        if (!titleMatches) return false
        if (hitArtist.isNullOrBlank() || artist.isBlank()) return true
        val wantedArtists = artist.split(artistSeparators).map(::normalized).filter { it.isNotEmpty() }
        val gotArtists = hitArtist.split(artistSeparators).map(::normalized).filter { it.isNotEmpty() }
        return wantedArtists.any { a -> gotArtists.any { b -> a == b || a.contains(b) || b.contains(a) } }
    }
}
