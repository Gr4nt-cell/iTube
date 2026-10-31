package com.gr4nt.itube.data

import java.net.URI

/** Shorts are identified by source metadata or an explicit Shorts link, never duration alone. */
fun isShortUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host.orEmpty().lowercase()
    (host == "youtube.com" || host.endsWith(".youtube.com")) &&
        uri.path.orEmpty().startsWith("/shorts/", ignoreCase = true)
}.getOrDefault(false)

val VideoItem.isShortContent: Boolean get() = isShort || isShortUrl(url)

fun List<VideoItem>.regularVideos(): List<VideoItem> = filterNot { it.isShortContent }

fun List<Item>.regularItems(): List<Item> = filterNot { it is VideoItem && it.isShortContent }

/** Preserve continuation loaders: a page containing only Shorts is not the end of the feed. */
fun PageResult<Item>.regularPage(): PageResult<Item> = PageResult(
    items.regularItems(),
    next?.let { loader -> suspend { loader().regularPage() } },
)
