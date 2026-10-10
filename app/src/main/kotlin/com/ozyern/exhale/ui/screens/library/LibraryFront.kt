/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquidGlassSurface

/** One of the library's own collections: Liked, Downloaded, Cached, Top. */
internal data class LibraryCollection(
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    val colors: List<Color>,
    val onClick: () -> Unit,
)

/** A place in the library: Playlists, Artists, Albums, Songs. */
internal data class LibraryDestination(
    val title: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val onClick: () -> Unit,
)

/**
 * The top of the library: the first collection — Liked, normally — as a wide card of its own
 * colour with the count and a play button, the way a music app puts the thing you open most at
 * the top; the rest beside each other under it as small glass tiles.
 */
@Composable
internal fun LibraryCollectionsHero(
    collections: List<LibraryCollection>,
    gutter: Dp,
    onPlayFirst: (() -> Unit)?,
) {
    if (collections.isEmpty()) return
    val lead = collections.first()
    val rest = collections.drop(1)
    val likedCount by LocalDatabase.current.likedSongsCount().collectAsState(initial = 0)

    Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(lead.colors))
                .clickable(onClick = lead.onClick),
        ) {
            // The glyph, large and faint, off the card's right edge: the card is the collection.
            Icon(
                painter = painterResource(lead.icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 26.dp, y = 10.dp)
                    .size(150.dp)
                    .graphicsLayer { rotationZ = -12f },
            )
            // A light from the top left, so the card reads as a lit object rather than a fill.
            Box(
                Modifier
                    .matchParentSizeCompat()
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                            radius = 520f,
                            center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp, end = 90.dp),
            ) {
                Text(
                    text = lead.title,
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.02).em),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (lead.icon == R.drawable.favorite && likedCount > 0) {
                        "$likedCount song${if (likedCount == 1) "" else "s"}"
                    } else {
                        lead.subtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
            if (onPlayFirst != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onPlayFirst),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = "Play",
                        tint = lead.colors.last(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rest.forEach { c ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .liquidGlassSurface(RoundedCornerShape(20.dp), tint = c.colors.first())
                            .clickable(onClick = c.onClick)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(c.colors)),
                        ) {
                            Icon(painterResource(c.icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = c.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 13.sp,
                            letterSpacing = (-0.2).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = c.subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The places in the library, as one glass group of rows: a coloured disc with its glyph, the
 * name, a chevron, hairlines inset to the text between them.
 */
@Composable
internal fun LibraryDestinationList(destinations: List<LibraryDestination>, gutter: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter)
            .padding(top = 22.dp)
            .liquidGlassSurface(RoundedCornerShape(22.dp)),
    ) {
        destinations.forEachIndexed { i, d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = d.onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(d.color, d.color.copy(red = d.color.red * 0.8f, green = d.color.green * 0.8f, blue = d.color.blue * 0.8f)))),
                ) {
                    Icon(painterResource(d.icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = d.title,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = (-0.01).em),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (i < destinations.lastIndex) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    modifier = Modifier.padding(start = 60.dp),
                )
            }
        }
    }
}

/** `matchParentSize` outside a BoxScope receiver, for the light laid over the hero card. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(132.dp))
