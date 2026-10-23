package com.gr4nt.itube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
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
