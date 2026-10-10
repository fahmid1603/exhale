/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import com.ozyern.exhale.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Each version's own release notes, one Markdown file per version.
 *
 * They live in `app/src/main/assets/changelogs/<version>.md`, so every build carries its own notes
 * and every earlier version's: readable offline, on day one, before any network call. The same
 * files are on GitHub at the same path, and when the network is there the copy from there wins — a
 * typo fixed after release reaches everyone without an app update. Writing a release's notes is
 * adding one file; nothing else changes.
 */
object VersionChangelog {
    private const val DIR = "changelogs"
    private const val REMOTE = "https://raw.githubusercontent.com/ozyern/Exhale/master/app/src/main/assets/changelogs"

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    data class Entry(val version: String, val markdown: String)

    /** The version this build is. */
    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Every version with notes in this build, newest first. */
    fun versions(context: Context): List<String> =
        runCatching { context.assets.list(DIR)?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.endsWith(".md") }
            .map { it.removeSuffix(".md") }
            .sortedWith { a, b -> compareVersions(b, a) }

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    /** The notes bundled for [version], or null when this build has none. */
    fun bundled(context: Context, version: String): String? =
        runCatching { context.assets.open("$DIR/$version.md").bufferedReader().use { it.readText() } }.getOrNull()

    /** The notes on GitHub for [version], or null when they can't be reached. */
    suspend fun remote(version: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url("$REMOTE/$version.md").build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    /** Everything this build has notes for, its own version first — bundled copies. */
    fun all(context: Context): List<Entry> {
        val list = versions(context)
        val ordered = listOfNotNull(list.firstOrNull { Updater.isSameVersion(it, currentVersion) }) +
            list.filterNot { Updater.isSameVersion(it, currentVersion) }
        return ordered.mapNotNull { v -> bundled(context, v)?.let { Entry(v, it) } }
    }
}
