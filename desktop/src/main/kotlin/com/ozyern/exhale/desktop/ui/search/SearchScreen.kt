/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.LyricSearch
import com.ozyern.exhale.desktop.data.MusicLinks
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.ui.typingField
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongRow
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.glass.LocalPageBackdrop
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.desktop.ui.subtitle
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.SearchSummary
import com.ozyern.exhale.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** YouTube Music's own result filters, plus All (its summary page). */
enum class SearchTab(val label: String, val filter: YouTube.SearchFilter?) {
    All("All", null),
    Songs("Songs", YouTube.SearchFilter.FILTER_SONG),
    Artists("Artists", YouTube.SearchFilter.FILTER_ARTIST),
    Albums("Albums", YouTube.SearchFilter.FILTER_ALBUM),
    Playlists("Playlists", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
    Videos("Videos", YouTube.SearchFilter.FILTER_VIDEO),
}

/** State behind the Search tab: recent searches, live suggestions while typing, results per filter on Enter. */
class SearchModel(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    /** The query the results belong to; results are stale once the text moves on from it. */
    var searchedQuery by mutableStateOf<String?>(null)
        private set
    var tab by mutableStateOf(SearchTab.All)
        private set
    var summary by mutableStateOf<SearchSummaryPage?>(null)
        private set
    var items by mutableStateOf<List<YTItem>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private val summaries = HashMap<String, SearchSummaryPage>()
    private val filtered = HashMap<String, List<YTItem>>()

    val showingResults: Boolean
        get() = searchedQuery != null && searchedQuery == query.trim()

    fun updateQuery(value: String) {
        query = value
        suggestJob?.cancel()
        val q = value.trim()
        if (q.isEmpty()) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(220)
            withContext(Dispatchers.IO) { YouTube.searchSuggestions(q) }
                .onSuccess { suggestions = it.queries.take(12) }
        }
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = text
        suggestJob?.cancel()
        suggestions = emptyList()
        searchedQuery = q
        DesktopPrefs.recordSearch(q)
        run(q)
    }

    fun selectTab(value: SearchTab) {
        if (value == tab) return
        tab = value
        searchedQuery?.let(::run)
    }

    private fun run(q: String) {
        searchJob?.cancel()
        error = null
        val current = tab
        val key = "$q|${current.name}"
        val filter = current.filter
        if (filter == null) {
            summaries[key]?.let {
                summary = it
                searching = false
                return
            }
        } else {
            filtered[key]?.let {
                items = it
                searching = false
                return
            }
        }
        searching = true
        searchJob = scope.launch {
            if (filter == null && MusicLinks.isLink(q)) {
                // A Spotify, Apple Music, Deezer, Tidal, SoundCloud or YouTube link: the same song or
                // album here as the top result, and the ordinary results for it underneath — searching
                // the raw address found only pages about the address.
                val found = MusicLinks.resolve(q)
                if (found == null) {
                    error = "Couldn't find the music in that link here."
                } else {
                    val by = when (found) {
                        is SongItem -> found.artists.joinToString(" ") { it.name }
                        is AlbumItem -> found.artists?.joinToString(" ") { it.name }.orEmpty()
                        else -> ""
                    }
                    val more = withContext(Dispatchers.IO) { YouTube.searchSummary("${found.title} $by".trim()) }.getOrNull()
                    val page = SearchSummaryPage(
                        listOf(SearchSummary("Top result", listOf(found))) +
                            more?.summaries.orEmpty().filterNot { it.title.contains("top", ignoreCase = true) },
                    )
                    val cleaned = page.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)
                    summaries[key] = cleaned
                    summary = cleaned
                }
            } else if (filter == null) {
                // A line of a song — "his palms are sweaty" — is asked of Genius alongside YouTube, whose
                // own search reads titles and so turns up nothing much for it.
                val fromLyrics: kotlinx.coroutines.Deferred<List<LyricSearch.Match>>? = if (LyricSearch.looksLikeLyrics(q)) {
                    async { runCatching { LyricSearch.search(q) }.getOrDefault(emptyList()) }
                } else {
                    null
                }
                withContext(Dispatchers.IO) { YouTube.searchSummary(q) }
                    .onSuccess { page ->
                        val cleaned = page.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)
                        summaries[key] = cleaned
                        summary = cleaned
                    }
                    .onFailure { if (it is CancellationException) throw it else error = it.message ?: "Search failed" }
                searching = false
                val matches = fromLyrics?.await().orEmpty()
                if (matches.isNotEmpty() && searchedQuery == q && tab == SearchTab.All) {
                    val merged = withLyricMatches(summary, matches.map { it.song })
                    summaries[key] = merged
                    summary = merged
                    error = null
                }
            } else {
                withContext(Dispatchers.IO) { YouTube.search(q, filter) }
                    .onSuccess { result ->
                        val cleaned = result.items.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)
                        filtered[key] = cleaned
                        items = cleaned
                    }
                    .onFailure { if (it is CancellationException) throw it else error = it.message ?: "Search failed" }
            }
            searching = false
        }
    }

    /**
     * The song the line came from takes the top spot, unless YouTube's top result already is one of
     * them; the other matches sit in their own shelf, first, so they are seen before the rest.
     */
    private fun withLyricMatches(page: SearchSummaryPage?, songs: List<SongItem>): SearchSummaryPage {
        val summaries = page?.summaries.orEmpty()
        val top = summaries.firstOrNull { it.title.contains("top", ignoreCase = true) }
        val topIsMatch = top?.items?.firstOrNull()?.id in songs.map { it.id }
        val lead = if (topIsMatch) top!! else SearchSummary("Top result", listOf(songs.first()))
        val others = songs.filterNot { it.id == lead.items.firstOrNull()?.id }
        return SearchSummaryPage(
            listOf(lead) +
                (if (others.isNotEmpty()) listOf(SearchSummary("From the lyrics", others)) else emptyList()) +
                summaries.filterNot { it === top },
        )
    }

    fun clear() {
        suggestJob?.cancel()
        searchJob?.cancel()
        query = ""
        suggestions = emptyList()
        searchedQuery = null
        summary = null
        items = emptyList()
        searching = false
        error = null
    }
}

private enum class SearchMode { Recent, Suggest, Results }

/**
 * The Search tab, laid out across the whole width: recent searches as pills, suggestions in columns,
 * and results the way Spotify sets them — a big top result beside the best songs, then a shelf for
 * each kind of thing found — with YouTube Music's filters along the top.
 */
@Composable
fun SearchScreen(model: SearchModel, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets, showField: Boolean = true) {
    val history by DesktopPrefs.searchHistory.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(showField) { if (showField) runCatching { focus.requestFocus() } }
    val start = insets.start + 20.dp

    Column(Modifier.fillMaxSize().padding(start = start, end = 28.dp, top = 30.dp)) {
        LargeTitle(if (showField || model.query.isBlank()) "Search" else "“${model.query.trim()}”")
        if (showField) {
            Spacer(Modifier.height(16.dp))
            SearchField(
                query = model.query,
                onChange = model::updateQuery,
                onSearch = { model.search() },
                onClear = model::clear,
                focus = focus,
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
            )
        }

        val mode = when {
            model.showingResults -> SearchMode.Results
            model.query.isNotBlank() -> SearchMode.Suggest
            else -> SearchMode.Recent
        }
        if (mode == SearchMode.Results) {
            Spacer(Modifier.height(14.dp))
            TabChips(model.tab, model::selectTab)
        }
        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = if (mode == SearchMode.Results) "results:${model.tab.name}" else mode.name,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it / 40 }) togetherWith fadeOut(tween(110)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "search_mode",
        ) { target ->
            val bottom = PaddingValues(bottom = insets.bottom + 16.dp)
            when {
                target == SearchMode.Recent.name -> RecentSearches(history, bottom, onPick = { model.search(it) })
                target == SearchMode.Suggest.name -> Suggestions(model.suggestions, model.query, bottom, onPick = { model.search(it) })
                model.searching -> Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
                model.error != null -> Hint(model.error!!)
                model.tab == SearchTab.All -> AllResults(model.summary, model.searchedQuery.orEmpty(), player, state, bottom)
                else -> FilteredResults(model.tab, model.items, model.searchedQuery.orEmpty(), player, state, bottom)
            }
        }
    }
}

@Composable
private fun TabChips(selected: SearchTab, onSelect: (SearchTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchTab.entries.forEach { tab ->
            val chosen = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val background by animateColorAsState(
                when {
                    chosen -> MaterialTheme.colorScheme.primary
                    hovered -> Color.White.copy(alpha = 0.13f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
                label = "search_tab_bg",
            )
            val content by animateColorAsState(if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "search_tab_fg")
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(history: List<String>, padding: PaddingValues, onPick: (String) -> Unit) {
    if (history.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Find songs, artists, albums and playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Type in the search field — Ctrl+F gets you there from anywhere.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Clear all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(CircleShape).clickable { DesktopPrefs.clearSearchHistory() }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            history.forEach { q -> RecentPill(q, onClick = { onPick(q) }, onRemove = { DesktopPrefs.removeSearch(q) }) }
        }
    }
}

@Composable
private fun RecentPill(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "recent_pill")
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassPlate(CircleShape, base = if (hovered) Color.White.copy(alpha = 0.06f) else Color.Unspecified)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Recent, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(24.dp)
                .graphicsLayer { alpha = if (hovered) 1f else 0.35f }
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, query: String, padding: PaddingValues, onPick: (String) -> Unit) {
    if (suggestions.isEmpty()) {
        Hint("Press Enter to search for “${query.trim()}”.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(suggestions, key = { "suggest:$it" }) { suggestion ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onPick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun rememberIntro(key: Any?): () -> Float {
    val intro = remember(key) { Animatable(0f) }
    LaunchedEffect(intro) { intro.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
    return { intro.value }
}

@Composable
private fun AllResults(summary: SearchSummaryPage?, query: String, player: DesktopPlayer, state: PlayerState, padding: PaddingValues) {
    val summaries = summary?.summaries.orEmpty()
    val topSummary = summaries.firstOrNull { it.title.contains("top", ignoreCase = true) }
    val top = topSummary?.items?.firstOrNull() ?: summaries.firstOrNull()?.items?.firstOrNull()
    val songSummary = summaries.firstOrNull { it.title.equals("Songs", ignoreCase = true) }
    val songs = (songSummary?.items ?: summaries.flatMap { it.items })
        .filterIsInstance<SongItem>()
        .filterNot { it.id == top?.id }
        .distinctBy { it.id }
        .take(5)
    val shelves = summaries.filter { it !== topSummary && it !== songSummary && it.items.isNotEmpty() }
    val intro = rememberIntro(query)
    val open = rememberItemOpener(player)

    if (top == null && shelves.isEmpty()) {
        Hint("Nothing found for “$query”.")
        return
    }

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (top != null) {
            item(key = "top") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 900.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(0.85f).feedIntro(0f, intro)) {
                                ResultsHeader("Top result")
                                TopResultCard(top, active = top.id == state.current?.id, playing = state.isPlaying, onPlay = { player.playNow(top) }, onOpen = { open(top) })
                            }
                            Column(Modifier.weight(1.35f).feedIntro(1f, intro)) {
                                ResultsHeader("Songs") {
                                    HeaderAction("Play all") { player.play(songs, 0) }
                                    HeaderAction("Add all") { player.addToQueue(songs) }
                                }
                                songs.forEach { song -> SongRow(song, isActive = song.id == state.current?.id, onClick = { player.playItem(song) }, player = player) }
                            }
                        }
                    } else {
                        Column {
                            ResultsHeader("Top result")
                            TopResultCard(top, active = top.id == state.current?.id, playing = state.isPlaying, onPlay = { player.playNow(top) }, onOpen = { open(top) })
                            if (songs.isNotEmpty()) {
                                ResultsHeader("Songs")
                                songs.forEach { song -> SongRow(song, isActive = song.id == state.current?.id, onClick = { player.playItem(song) }, player = player) }
                            }
                        }
                    }
                }
            }
        }
        shelves.forEachIndexed { index, shelf ->
            item(key = "shelf:$index:${shelf.title}") {
                Column(Modifier.feedIntro(index + 2f, intro)) {
                    ResultsHeader(shelf.title)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(shelf.items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                            ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, onPlay = { player.playNow(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilteredResults(tab: SearchTab, items: List<YTItem>, query: String, player: DesktopPlayer, state: PlayerState, padding: PaddingValues) {
    val intro = rememberIntro("$query|$tab")
    val open = rememberItemOpener(player)
    if (items.isEmpty()) {
        Hint("No ${tab.label.lowercase()} found for “$query”.")
        return
    }
    if (tab == SearchTab.Songs || tab == SearchTab.Videos) {
        val songs = items.filterIsInstance<SongItem>()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(520.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ResultsHeader("${songs.size} ${tab.label.lowercase()}") }
            itemsIndexed(songs, key = { i, song -> "$i:${song.id}" }) { i, song ->
                Box(Modifier.feedIntro(i.coerceAtMost(14) * 0.5f, intro)) {
                    SongRow(song, isActive = song.id == state.current?.id, onClick = { player.playItem(song) }, player = player)
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ResultsHeader("${items.size} ${tab.label.lowercase()}") }
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { i, item ->
                Box(Modifier.feedIntro(i.coerceAtMost(18) * 0.4f, intro)) {
                    ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = 176.dp, onPlay = { player.playNow(item) })
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** A quiet word beside a section heading: play the lot, or put the lot in the queue. */
@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun typeLabel(item: YTItem): String = when (item) {
    is SongItem -> "Song"
    is ArtistItem -> "Artist"
    is AlbumItem -> "Album"
    is PlaylistItem -> "Playlist"
}

/** The best match, set large on its own artwork's colour, with a play button that rises in under the pointer. */
@Composable
private fun TopResultCard(item: YTItem, active: Boolean, playing: Boolean, onPlay: () -> Unit, onOpen: () -> Unit) {
    val colors by rememberArtworkColors(item.thumbnail?.resizedThumbnail(120))
    val base by animateColorAsState(colors.firstOrNull() ?: MaterialTheme.colorScheme.surfaceVariant, tween(500), label = "top_result_base")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 320f), label = "top_result_lift")
    val press by animateFloatAsState(if (pressed) 0.985f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "top_result_press")
    val shape = RoundedCornerShape(24.dp)
    val artShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(14.dp)
    val accent = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationY = -4.dp.toPx() * lift
                scaleX = press
                scaleY = press
            }
            .shadow(if (hovered) 26.dp else 8.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(base, Color.Black, 0.3f), lerp(base, Color.Black, 0.78f))))
            .border(0.8.dp, Color.White.copy(alpha = 0.10f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(26.dp),
    ) {
        Column {
            AsyncImage(
                item.thumbnail?.resizedThumbnail(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).shadow(18.dp, artShape).clip(artShape).background(Color.White.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.height(20.dp))
            Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    typeLabel(item),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                item.subtitle()?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val shown by animateFloatAsState(if (hovered || active) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "top_result_play")
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    alpha = shown
                    translationY = (1f - shown) * 12.dp.toPx()
                }
                .size(58.dp)
                .shadow(14.dp, CircleShape)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (active && playing) {
                EqualizerBars(MaterialTheme.colorScheme.onPrimary, Modifier.size(22.dp))
            } else {
                Icon(TransportIcons.Play, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalPageBackdrop.current
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .height(56.dp)
            .then(if (backdrop != null) Modifier.liquidGlass(shape, backdrop, tintAlpha = 0.38f, blurRadius = 56.dp) else Modifier.glassPlate(shape))
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Songs, artists, albums", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .typingField()
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            onSearch()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Text("✕", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp))
}

@Suppress("unused")
private val UnusedIcon: ImageVector? = null
