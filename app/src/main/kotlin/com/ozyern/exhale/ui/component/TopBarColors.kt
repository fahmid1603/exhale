/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The page's own top bar: no band of its own, the page and its moving artwork showing straight
 * through behind the back button and the title, as the tabs' bar already does.
 *
 * Material's default paints the bar in the surface colour — pure black under the AMOLED theme — so
 * every screen that never said otherwise wore a black strip across its top while the rest of the app
 * was transparent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
)
