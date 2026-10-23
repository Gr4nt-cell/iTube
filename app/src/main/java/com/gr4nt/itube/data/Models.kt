package com.gr4nt.itube.data

import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@Serializable
sealed interface Item {
    val url: String
    val title: String
    val thumbnail: String?
}

@Serializable
data class VideoItem(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val uploader: String = "",
    val uploaderUrl: String? = null,
    val uploaderAvatar: String? = null,
    val durationSec: Long = -1,
    val views: Long = -1,
    val uploaded: String? = null,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val verified: Boolean = false,
    val timestamp: Long = 0,
) : Item

@Serializable
data class ChannelItem(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val subscribers: Long = -1,
    val videos: Long = -1,
    val description: String? = null,
    val verified: Boolean = false,
) : Item

@Serializable
data class PlaylistItem(
    override val url: String,
    override val title: String,
    override val thumbnail: String?,
    val uploader: String = "",
    val count: Long = -1,
) : Item

/** Picks a reasonably sized image (~ width 480) from a list. */
fun List<Image>.best(target: Int = 480): String? {
    if (isEmpty()) return null
    val sized = filter { it.width > 0 }
    val pick = if (sized.isNotEmpty()) {
        sized.minByOrNull { kotlin.math.abs(it.width - target) }
    } else {
        last()
    }
    return pick?.url?.let(::fixUrl)
}

fun List<Image>.largest(): String? = maxByOrNull { it.width * it.height }?.url?.let(::fixUrl)

private fun fixUrl(u: String) = if (u.startsWith("//")) "https:$u" else u

fun InfoItem.toItem(): Item? = when (this) {
    is StreamInfoItem -> VideoItem(
        url = url,
        title = name ?: "",
        thumbnail = thumbnails.best(),
        uploader = uploaderName ?: "",
        uploaderUrl = uploaderUrl,
        uploaderAvatar = uploaderAvatars.best(96),
        durationSec = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = isShortFormContent,
        verified = isUploaderVerified,
        timestamp = runCatching { uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L }.getOrDefault(0L),
    )
    is ChannelInfoItem -> ChannelItem(
        url = url,
        title = name ?: "",
        thumbnail = thumbnails.best(176),
        subscribers = subscriberCount,
        videos = streamCount,
        description = description,
        verified = isVerified,
    )
    is PlaylistInfoItem -> PlaylistItem(
        url = url,
        title = name ?: "",
        thumbnail = thumbnails.best(),
        uploader = uploaderName ?: "",
        count = streamCount,
    )
    else -> null
}

fun List<InfoItem>.toItems(): List<Item> = mapNotNull { runCatching { it.toItem() }.getOrNull() }
