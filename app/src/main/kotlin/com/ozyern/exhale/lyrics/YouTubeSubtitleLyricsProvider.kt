/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.innertube.YouTube

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    /**
     * YouTube turns the transcript request down outright (400, FAILED_PRECONDITION) when it no
     * longer accepts the request's shape, and then it does so for every song. Asked anyway, that
     * was two wasted round trips per song; once refused like that, it sits out the session.
     */
    @Volatile
    private var refused = false

    override fun isEnabled(context: Context) = !refused

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        if (refused) return Result.failure(IllegalStateException("Lyrics unavailable"))
        return YouTube.transcript(id).onFailure { error ->
            val message = error.message.orEmpty()
            if ("FAILED_PRECONDITION" in message || "invalid: 400" in message) refused = true
        }
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }
}
