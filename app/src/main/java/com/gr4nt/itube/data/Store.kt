package com.gr4nt.itube.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryEntry(val video: VideoItem, val watchedAt: Long)

@Serializable
data class LocalPlaylist(val id: String, val name: String, val videos: List<VideoItem> = emptyList())

@Serializable
data class DownloadRecord(
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val uri: String,
    val mime: String,
    val sizeBytes: Long,
    val audioOnly: Boolean,
    val createdAt: Long,
)

/** Small JSON-file backed persistence for the app's local library. */
object Store {
    const val WATCH_LATER = "watch_later"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File

    private class Persisted<T>(
        val name: String,
        val serializer: KSerializer<T>,
        val default: T,
    ) {
        val flow = MutableStateFlow(default)
    }

    private val subsP = Persisted("subscriptions.json", ListSerializer(ChannelItem.serializer()), emptyList())
    private val historyP = Persisted("history.json", ListSerializer(HistoryEntry.serializer()), emptyList())
    private val positionsP = Persisted("positions.json", MapSerializer(String.serializer(), Long.serializer()), emptyMap())
    private val playlistsP = Persisted(
        "playlists.json",
        ListSerializer(LocalPlaylist.serializer()),
        listOf(LocalPlaylist(WATCH_LATER, "Watch later")),
    )
    private val searchesP = Persisted("searches.json", ListSerializer(String.serializer()), emptyList())
    private val downloadsP = Persisted("downloads.json", ListSerializer(DownloadRecord.serializer()), emptyList())

    val subscriptions: StateFlow<List<ChannelItem>> get() = subsP.flow.asStateFlow()
    val history: StateFlow<List<HistoryEntry>> get() = historyP.flow.asStateFlow()
    val positions: StateFlow<Map<String, Long>> get() = positionsP.flow.asStateFlow()
    val playlists: StateFlow<List<LocalPlaylist>> get() = playlistsP.flow.asStateFlow()
    val searches: StateFlow<List<String>> get() = searchesP.flow.asStateFlow()
    val downloads: StateFlow<List<DownloadRecord>> get() = downloadsP.flow.asStateFlow()

    fun init(context: Context) {
        dir = File(context.filesDir, "library").apply { mkdirs() }
        listOf(subsP, historyP, positionsP, playlistsP, searchesP, downloadsP).forEach { load(it) }
    }

    private fun <T> load(p: Persisted<T>) {
        val f = File(dir, p.name)
        if (f.exists()) {
            runCatching { p.flow.value = json.decodeFromString(p.serializer, f.readText()) }
        }
    }

    private fun <T> mutate(p: Persisted<T>, transform: (T) -> T) {
        p.flow.update(transform)
        val snapshot = p.flow.value
        scope.launch {
            synchronized(p) {
                runCatching {
                    val tmp = File(dir, p.name + ".tmp")
                    tmp.writeText(json.encodeToString(p.serializer, snapshot))
                    tmp.renameTo(File(dir, p.name))
                }
            }
        }
    }

    // Subscriptions
    fun isSubscribed(url: String) = subsP.flow.value.any { it.url == url }
    fun toggleSubscription(channel: ChannelItem) = mutate(subsP) { list ->
        if (list.any { it.url == channel.url }) list.filterNot { it.url == channel.url } else list + channel
    }

    /** Adds channels that aren't subscribed yet; returns how many were added. */
    fun importSubscriptions(channels: List<ChannelItem>): Int {
        val existing = subsP.flow.value.map { it.url }.toSet()
        val fresh = channels.filter { it.url !in existing }.distinctBy { it.url }
        if (fresh.isNotEmpty()) mutate(subsP) { it + fresh }
        return fresh.size
    }

    // History & resume
    fun addHistory(video: VideoItem) = mutate(historyP) { list ->
        (listOf(HistoryEntry(video, System.currentTimeMillis())) + list.filterNot { it.video.url == video.url }).take(500)
    }
    fun removeHistory(url: String) = mutate(historyP) { list -> list.filterNot { it.video.url == url } }
    fun clearHistory() = mutate(historyP) { emptyList() }

    fun savePosition(url: String, ms: Long) = mutate(positionsP) { m ->
        if (ms < 5_000) m - url else (m + (url to ms)).let { if (it.size > 1000) it.entries.drop(it.size - 1000).associate { e -> e.key to e.value } else it }
    }

    // Playlists
    fun createPlaylist(name: String): String {
        val id = "pl_" + System.currentTimeMillis()
        mutate(playlistsP) { it + LocalPlaylist(id, name) }
        return id
    }
    fun deletePlaylist(id: String) = mutate(playlistsP) { list -> list.filterNot { it.id == id && id != WATCH_LATER } }
    fun renamePlaylist(id: String, name: String) = mutate(playlistsP) { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    fun addToPlaylist(id: String, video: VideoItem) = mutate(playlistsP) { list ->
        list.map { if (it.id == id && it.videos.none { v -> v.url == video.url }) it.copy(videos = it.videos + video) else it }
    }
    fun removeFromPlaylist(id: String, url: String) = mutate(playlistsP) { list ->
        list.map { if (it.id == id) it.copy(videos = it.videos.filterNot { v -> v.url == url }) else it }
    }
    fun inWatchLater(url: String) = playlistsP.flow.value.firstOrNull { it.id == WATCH_LATER }?.videos?.any { it.url == url } == true
    fun toggleWatchLater(video: VideoItem) {
        if (inWatchLater(video.url)) removeFromPlaylist(WATCH_LATER, video.url) else addToPlaylist(WATCH_LATER, video)
    }

    // Searches
    fun addSearch(q: String) = mutate(searchesP) { list -> (listOf(q) + list.filterNot { it.equals(q, true) }).take(30) }
    fun removeSearch(q: String) = mutate(searchesP) { list -> list - q }
    fun clearSearches() = mutate(searchesP) { emptyList() }

    // Downloads
    fun addDownload(r: DownloadRecord) = mutate(downloadsP) { listOf(r) + it.filterNot { d -> d.id == r.id } }
    fun removeDownload(id: String) = mutate(downloadsP) { list -> list.filterNot { it.id == id } }
}
