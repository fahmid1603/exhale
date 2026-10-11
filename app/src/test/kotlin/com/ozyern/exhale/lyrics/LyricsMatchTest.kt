package com.ozyern.exhale.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchTest {
    @Test
    fun sameSongNeedsTheTitleAndAnArtist() {
        assertTrue(SongQuery.sameSong("Don’t Smile", "Sabrina Carpenter", "Don't Smile", "Sabrina Carpenter"))
        assertTrue(SongQuery.sameSong("Don't Smile", "Sabrina Carpenter", "Don't Smile", "Sabrina Carpenter & Someone"))
        assertTrue(SongQuery.sameSong("Don't Smile (Official Video)", "Sabrina Carpenter", "Don't Smile", null))
        // A namesake by somebody else.
        assertFalse(SongQuery.sameSong("Don't Smile", "Sabrina Carpenter", "don't smile", "louisette, émilienne, fernande"))
        // Another song by the same artist.
        assertFalse(SongQuery.sameSong("Don't Smile", "Sabrina Carpenter", "Bed Chem", "Sabrina Carpenter"))
    }

    @Test
    fun creditLinesAreRecognised() {
        assertTrue(isCreditLine("JulianBunetta/JohnRyan/ SabrinaCarpenter/ AmyAllen/StephJones"))
        assertTrue(isCreditLine("作词 : Someone"))
        assertTrue(isCreditLine("Written by: A, B"))
        assertFalse(isCreditLine("we walked home in the rain tonight"))
        assertFalse(isCreditLine("either/or"))
    }

    @Test
    fun sameWordsTellsSongsApart() {
        val song = "the river runs silver under the morning light, carry me over the water tonight, " +
            "golden horizon calling my name, nothing between us will ever be the same"
        val retranscribed = song.replace("carry me", "carry us").replace("tonight", "tonight, oh")
        val other = "neon signals flicker downtown, taxis are humming a restless sound, " +
            "pocket of quarters, a jukebox prayer, dancing alone in an empty square"
        assertTrue(LyricsWords.sameWords(song, retranscribed))
        assertFalse(LyricsWords.sameWords(song, other))
    }
}
