/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

/**
 * Switches for measuring what a frame costs, read once per process from `debug.exhale.*` system
 * properties (`adb shell setprop debug.exhale.nofade 1`, then restart the app). Unset — the only
 * state anyone outside a profiling session will ever see — every one of them is off.
 */
object PerfFlags {
    private fun prop(name: String): Boolean = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            .invoke(null, name) as String == "1"
    }.getOrDefault(false)

    /** No fade of the tab content under the top bar. */
    val noFade: Boolean by lazy { prop("debug.exhale.nofade") }

    /** No refraction in in-page glass: the tonal recipe everywhere. */
    val noGlass: Boolean by lazy { prop("debug.exhale.noglass") }

    /** Show the after-update welcome screen on every launch, to look at it. */
    val showWelcome: Boolean by lazy { prop("debug.exhale.welcome") }

    /** The moving artwork behind the pages held still. */
    val stillAmbient: Boolean by lazy { prop("debug.exhale.stillambient") }
}
