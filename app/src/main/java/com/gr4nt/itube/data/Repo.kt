package com.gr4nt.itube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

/** One page of results plus a loader for the next page (null when exhausted). */
class PageResult<T>(
    val items: List<T>,
    val next: (suspend () -> PageResult<T>)?,
)

data class ChannelDetails(
    val info: ChannelInfo,
    val tabs: List<ListLinkHandler>,
)

data class Comment(
    val author: String,
    val authorAvatar: String?,
    val authorUrl: String?,
    val text: String,
    val likes: Int,
    val date: String?,
    val pinned: Boolean,
    val hearted: Boolean,
    val replyCount: Int,
    val replies: Page?,
)

data class Kiosk(val id: String, val label: String)

object Repo {
    private val yt get() = ServiceList.YouTube

    val kiosks = listOf(
        Kiosk("trending_music", "Music"),
        Kiosk("trending_gaming", "Gaming"),
        Kiosk("live", "Live"),
        Kiosk("trending_movies_and_shows", "Movies"),
        Kiosk("trending_podcasts_episodes", "Podcasts"),
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    suspend fun kiosk(id: String): PageResult<Item> = io {
        val extractor = yt.kioskList.getExtractorById(id, null)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        PageResult(info.relatedItems.map { it as InfoItem }.toItems(), nextKiosk(info.url, info.nextPage))
    }

    private fun nextKiosk(url: String, page: Page?): (suspend () -> PageResult<Item>)? {
        if (page == null || !Page.isValid(page)) return null
        return {
            io {
                val p = KioskInfo.getMoreItems(yt, url, page)
                PageResult(p.items.map { it as InfoItem }.toItems(), nextKiosk(url, p.nextPage))
            }
        }
    }

    private var trendingCache: Pair<Long, List<VideoItem>>? = null
    private var trendingKey: Set<String> = emptySet()

    /**
     * "Trending" built from your subscriptions: recent uploads from every subscribed channel,
     * ranked by how quickly they're gaining views (views ÷ age^1.4). Without subscriptions it
     * falls back to a mix of YouTube's music, gaming and movie charts.
     */
    suspend fun trending(subs: List<ChannelItem>, force: Boolean = false): PageResult<Item> = io {
        val key = subs.map { it.url }.toSet()
        val cached = trendingCache
        if (!force && cached != null && key == trendingKey && System.currentTimeMillis() - cached.first < 10 * 60_000) {
            return@io PageResult<Item>(cached.second, null)
        }
        val list = if (subs.isEmpty()) chartsMix() else rankSubscriptions(subs)
        trendingCache = System.currentTimeMillis() to list
        trendingKey = key
        PageResult<Item>(list, null)
    }

    private suspend fun rankSubscriptions(subs: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(8)
        val now = System.currentTimeMillis()
        val all = subs.map { ch ->
            async {
                gate.withPermit {
                    runCatching { feed(ch.url) }.getOrDefault(emptyList()).map {
                        it.copy(uploaderAvatar = it.uploaderAvatar ?: ch.thumbnail, uploaderUrl = it.uploaderUrl ?: ch.url)
                    }
                }
            }
        }.awaitAll().flatten().distinctBy { it.url }

        val windowMs = 10L * 24 * 3_600_000
        val recent = all.filter { it.timestamp == 0L || now - it.timestamp < windowMs }.ifEmpty { all }
        recent.map { v ->
            val ageHours = if (v.timestamp > 0) ((now - v.timestamp) / 3_600_000.0).coerceAtLeast(0.5) else 96.0
            val views = v.views.coerceAtLeast(0).toDouble()
            v to (views + 50) / Math.pow(ageHours + 2, 1.4)
        }.sortedByDescending { it.second }
            .map { it.first }
            // Keep a single channel from flooding the top.
            .let { ranked ->
                val perChannel = HashMap<String, Int>()
                ranked.filter { v -> (perChannel.merge(v.uploader, 1) { a, b -> a + b } ?: 1) <= 3 } +
                    ranked.filter { v -> (perChannel[v.uploader] ?: 0) > 3 }.drop(0)
            }
            .distinctBy { it.url }
            .take(150)
    }

    private suspend fun chartsMix(): List<VideoItem> = coroutineScope {
        val lists = listOf("trending_music", "trending_gaming", "trending_movies_and_shows", "trending_podcasts_episodes").map { id ->
            async { runCatching { kiosk(id).items.filterIsInstance<VideoItem>() }.getOrDefault(emptyList()) }
        }.awaitAll()
        // Interleave the charts.
        val out = mutableListOf<VideoItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        out.distinctBy { it.url }
    }

    // ---------------- Shorts ----------------

    private fun isShortLike(v: VideoItem) = !v.isLive && (v.isShort || v.durationSec in 1..180)

    private val shortQueries = listOf("#shorts", "shorts funny", "shorts music", "shorts trending", "shorts gaming", "shorts sports")

    /** Shorts feed: shorts from your subscriptions mixed with popular shorts, endlessly paged. */
    suspend fun shorts(subs: List<ChannelItem>): PageResult<Item> = coroutineScope {
        val query = shortQueries.random()
        val searchDef = async { runCatching { search(query, "videos") }.getOrNull() }
        val subsDef = async { subscriptionShorts(subs.shuffled().take(12)) }
        val first = searchDef.await()
        val fromSearch = first?.items.orEmpty().filterIsInstance<VideoItem>().filter(::isShortLike)
        val fromSubs = subsDef.await().shuffled()
        val merged = mutableListOf<VideoItem>()
        val n = maxOf(fromSearch.size, fromSubs.size)
        for (i in 0 until n) {
            fromSubs.getOrNull(i)?.let(merged::add)
            fromSearch.getOrNull(i)?.let(merged::add)
        }
        PageResult<Item>(merged.distinctBy { it.url }, first?.next?.let { shortsNext(it, 0) })
    }

    private fun shortsNext(next: suspend () -> PageResult<Item>, empties: Int): suspend () -> PageResult<Item> = {
        val p = next()
        val items = p.items.filterIsInstance<VideoItem>().filter(::isShortLike)
        val more = p.next
        val nextEmpties = if (items.isEmpty()) empties + 1 else 0
        // Fall back to a fresh query when a search runs dry.
        val following: (suspend () -> PageResult<Item>)? = when {
            more != null && nextEmpties < 3 -> shortsNext(more, nextEmpties)
            else -> suspend { shorts(emptyList()) }
        }
        PageResult<Item>(items, following)
    }

    private suspend fun subscriptionShorts(subs: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val gate = Semaphore(6)
        subs.map { ch ->
            async {
                gate.withPermit {
                    runCatching {
                        val info = ChannelInfo.getInfo(yt, ch.url)
                        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.SHORTS) } ?: return@runCatching emptyList<VideoItem>()
                        ChannelTabInfo.getInfo(yt, tab).relatedItems.toItems().filterIsInstance<VideoItem>().take(8)
                            .map { it.copy(isShort = true, uploader = it.uploader.ifEmpty { ch.title }, uploaderUrl = it.uploaderUrl ?: ch.url, uploaderAvatar = it.uploaderAvatar ?: ch.thumbnail) }
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun suggestions(query: String): List<String> = io {
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    /** filter: "all", "videos", "channels", "playlists", "music_songs" ... */
    suspend fun search(query: String, filter: String = "all"): PageResult<Item> = io {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            if (filter == "all") emptyList() else listOf(filter),
            "",
        )
        val info = SearchInfo.getInfo(yt, handler)
        fun next(page: Page?): (suspend () -> PageResult<Item>)? {
            if (page == null || !Page.isValid(page)) return null
            return {
                io {
                    val p = SearchInfo.getMoreItems(yt, handler, page)
                    PageResult(p.items.toItems(), next(p.nextPage))
                }
            }
        }
        PageResult(info.relatedItems.toItems(), next(info.nextPage))
    }

    suspend fun stream(url: String): StreamInfo = io { StreamInfo.getInfo(yt, url) }

    suspend fun channel(url: String): ChannelDetails = io {
        val info = ChannelInfo.getInfo(yt, url)
        ChannelDetails(info, info.tabs)
    }

    suspend fun channelTab(tab: ListLinkHandler): PageResult<Item> = io {
        val info = ChannelTabInfo.getInfo(yt, tab)
        fun next(page: Page?): (suspend () -> PageResult<Item>)? {
            if (page == null || !Page.isValid(page)) return null
            return {
                io {
                    val p = ChannelTabInfo.getMoreItems(yt, tab, page)
                    PageResult(p.items.toItems(), next(p.nextPage))
                }
            }
        }
        PageResult(info.relatedItems.toItems(), next(info.nextPage))
    }

    suspend fun playlist(url: String): Pair<PlaylistInfo, PageResult<Item>> = io {
        val info = PlaylistInfo.getInfo(yt, url)
        fun next(page: Page?): (suspend () -> PageResult<Item>)? {
            if (page == null || !Page.isValid(page)) return null
            return {
                io {
                    val p = PlaylistInfo.getMoreItems(yt, url, page)
                    PageResult(p.items.map { it as InfoItem }.toItems(), next(p.nextPage))
                }
            }
        }
        info to PageResult(info.relatedItems.map { it as InfoItem }.toItems(), next(info.nextPage))
    }

    suspend fun comments(videoUrl: String): Pair<Boolean, PageResult<Comment>> = io {
        val info = CommentsInfo.getInfo(yt, videoUrl)
        fun next(page: Page?): (suspend () -> PageResult<Comment>)? {
            if (page == null || !Page.isValid(page)) return null
            return {
                io {
                    val p = CommentsInfo.getMoreItems(yt, videoUrl, page)
                    PageResult(p.items.map(::toComment), next(p.nextPage))
                }
            }
        }
        info.isCommentsDisabled to PageResult(info.relatedItems.map(::toComment), next(info.nextPage))
    }

    suspend fun replies(videoUrl: String, page: Page): PageResult<Comment> = io {
        val p = CommentsInfo.getMoreItems(yt, videoUrl, page)
        PageResult(p.items.map(::toComment), null)
    }

    private fun toComment(c: CommentsInfoItem) = Comment(
        author = c.uploaderName ?: "",
        authorAvatar = c.uploaderAvatars.best(96),
        authorUrl = c.uploaderUrl,
        text = c.commentText?.content ?: "",
        likes = c.likeCount,
        date = c.textualUploadDate,
        pinned = c.isPinned,
        hearted = c.isHeartedByUploader,
        replyCount = c.replyCount,
        replies = c.replies,
    )

    /** Latest uploads of a channel through YouTube's lightweight feed. */
    suspend fun feed(channelUrl: String): List<VideoItem> = io {
        FeedInfo.getInfo(yt, channelUrl).relatedItems.map { it as InfoItem }.toItems()
            .filterIsInstance<VideoItem>()
    }

    /** Normalizes a shared link/ID into a watch URL, or null if it isn't a video. */
    fun videoUrlFrom(text: String): String? = runCatching {
        val urlInText = Regex("https?://\\S+").find(text)?.value ?: text.trim()
        val id = yt.streamLHFactory.getId(urlInText)
        "https://www.youtube.com/watch?v=$id"
    }.getOrNull()

    fun isChannelUrl(text: String) = runCatching { yt.channelLHFactory.acceptUrl(text) }.getOrDefault(false)
    fun isPlaylistUrl(text: String) = runCatching { yt.playlistLHFactory.acceptUrl(text) }.getOrDefault(false)

    fun videoId(url: String): String? = runCatching { yt.streamLHFactory.getId(url) }.getOrNull()
}
