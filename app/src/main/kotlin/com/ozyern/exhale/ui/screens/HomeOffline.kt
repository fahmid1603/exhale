/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Most songs listed on the offline Home before "and N more". */
private const val OFFLINE_LIST_LIMIT = 60

/**
 * Home with no internet: what the phone can play on its own.
 *
 * Every song in the library that is downloaded, saved or cached whole — the same test playback
 * uses when it skips to something it can reach — most played first, with Play and Shuffle across
 * all of them. Nothing on this page needs the network, artwork included: covers come from the
 * image cache they were shown from before.
 */
@Composable
fun HomeOfflineSection(manual: Boolean, modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val current by playerConnection.mediaMetadata.collectAsState()
    var songs by remember { mutableStateOf<List<Song>?>(null) }

    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) {
            val service = playerConnection.service
            database.allSongs().first()
                .filter { runCatching { service.isPlayableOffline(it.id) }.getOrDefault(false) }
                .sortedByDescending { it.song.totalPlayTime }
        }
    }

    fun play(list: List<Song>, start: Int, shuffle: Boolean = false) {
        val ordered = if (shuffle) list.shuffled() else list
        playerConnection.playQueue(
            ListQueue(
                title = "Offline",
                items = ordered.map { it.toMediaItem() },
                startIndex = if (shuffle) 0 else start,
            ),
        )
    }

    Column(modifier.fillMaxWidth()) {
        // The banner: where things stand, and what this page is instead.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFF9F0A)),
            ) {
                Icon(painterResource(R.drawable.offline), null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (manual) "Offline mode" else "You're offline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when (val list = songs) {
                        null -> "Finding what's on your phone…"
                        else -> if (list.isEmpty()) {
                            "Nothing downloaded yet. Download songs to play them without internet."
                        } else {
                            "${list.size} song${if (list.size == 1) "" else "s"} you can play without internet"
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val list = songs.orEmpty()
        if (list.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                OfflinePill("Play", R.drawable.play, filled = true, modifier = Modifier.weight(1f)) { play(list, 0) }
                OfflinePill("Shuffle", R.drawable.shuffle, filled = false, modifier = Modifier.weight(1f)) { play(list, 0, shuffle = true) }
            }
            Spacer(Modifier.height(6.dp))
            list.take(OFFLINE_LIST_LIMIT).forEachIndexed { i, song ->
                OfflineSongRow(song, playing = song.id == current?.id) { play(list, i) }
            }
            if (list.size > OFFLINE_LIST_LIMIT) {
                Text(
                    text = "and ${list.size - OFFLINE_LIST_LIMIT} more — Play or Shuffle includes them all",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OfflinePill(text: String, icon: Int, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
    ) {
        val ink = if (filled) Color.Black else Color.White
        Icon(painterResource(icon), null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OfflineSongRow(song: Song, playing: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        AsyncImage(
            model = song.song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
