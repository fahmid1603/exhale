/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.spotify

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.DefaultDialog
import kotlinx.coroutines.launch

/**
 * Paste a Spotify playlist link, get the playlist in the library. Shows how far the matching has
 * got while it works, and opens the new playlist when it is done.
 */
@Composable
fun SpotifyImportDialog(
    initialLink: String = "",
    onDismiss: () -> Unit,
    onImported: (playlistId: String) -> Unit,
) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(initialLink) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val id = SpotifyImport.playlistId(link)

    fun start() {
        val playlistId = id ?: return
        working = true
        status = "Reading the playlist…"
        scope.launch {
            val source = SpotifyImport.fetch(playlistId)
            if (source == null || source.tracks.isEmpty()) {
                working = false
                status = "Couldn't read that playlist. Is it public?"
                return@launch
            }
            total = source.tracks.size
            status = "Finding “${source.name}” on YouTube Music…"
            val result = runCatching {
                SpotifyImport.import(database, source) { d, t -> done = d; total = t }
            }.getOrNull()
            working = false
            if (result == null) {
                status = "The import stopped. Check the connection and try again."
                return@launch
            }
            Toast.makeText(
                context,
                "Imported “${result.name}”: ${result.matched} of ${result.total} songs",
                Toast.LENGTH_LONG,
            ).show()
            onImported(result.playlistId)
        }
    }

    DefaultDialog(
        onDismiss = { if (!working) onDismiss() },
        icon = { Icon(painterResource(R.drawable.spotify_icon), null, tint = androidx.compose.ui.graphics.Color(0xFF1ED760), modifier = Modifier.size(32.dp)) },
        title = { Text("Import from Spotify") },
        buttons = {
            TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") }
            TextButton(enabled = !working && id != null, onClick = ::start) { Text("Import") }
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Paste a link to a public Spotify playlist. Each song is found on YouTube Music and the playlist is saved in your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                enabled = !working,
                singleLine = true,
                placeholder = { Text("open.spotify.com/playlist/…") },
                isError = link.isNotBlank() && id == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (working && total > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (working && total > 0) "$it  $done of $total" else it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
