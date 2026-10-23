package com.gr4nt.itube.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Item
import com.gr4nt.itube.data.PlaylistItem
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.glass.frosted
import com.gr4nt.itube.ui.theme.AppTheme

@Composable
fun Thumb(
    url: String?,
    modifier: Modifier = Modifier,
    duration: String? = null,
    live: Boolean = false,
    progress: Float = 0f,
    corner: Dp = 18.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.aspectRatio(16f / 9f).clip(shape).background(AppTheme.colors.chip)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val badge = if (live) "LIVE" else duration
        if (!badge.isNullOrEmpty()) {
            Text(
                badge,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(if (live) Color(0xE6FF2D55) else Color(0xB3000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (progress > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(AppTheme.colors.accent)
            )
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape).background(AppTheme.colors.chip),
    )
}

@Composable
private fun progressOf(video: VideoItem): Float {
    if (video.durationSec <= 0) return 0f
    val pos = Store.positions.value[video.url] ?: return 0f
    return pos / 1000f / video.durationSec
}

/** Big card used in feeds: glass-framed thumbnail with title row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: VideoItem, queue: List<VideoItem>? = null, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .frosted(shape, elevation = 10.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { app.openVideo(video, queue) },
                onLongClick = { app.showSheet { VideoActionsSheet(video) } },
            )
            .padding(8.dp),
    ) {
        Thumb(
            video.thumbnail,
            duration = Format.duration(video.durationSec),
            live = video.isLive,
            progress = progressOf(video),
        )
        Row(Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            if (video.uploaderAvatar != null) {
                Avatar(video.uploaderAvatar, 36.dp, Modifier.padding(end = 10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    Format.meta(video.uploader, Format.views(video.views), video.uploaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { app.showSheet { VideoActionsSheet(video) } }) {
                Icon(Icons.Rounded.MoreVert, "More", tint = c.secondaryLabel)
            }
        }
    }
}

/** Compact row used in related lists, playlists, history and search. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(
    video: VideoItem,
    queue: List<VideoItem>? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { app.openVideo(video, queue) },
                onLongClick = { app.showSheet { VideoActionsSheet(video) } },
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(
            video.thumbnail,
            modifier = Modifier.width(164.dp),
            duration = Format.duration(video.durationSec),
            live = video.isLive,
            progress = progressOf(video),
            corner = 14.dp,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            Text(video.uploader, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Format.meta(Format.views(video.views), video.uploaded),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                maxLines = 1,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = { app.showSheet { VideoActionsSheet(video) } }) {
                Icon(Icons.Rounded.MoreVert, "More", tint = c.secondaryLabel)
            }
        }
    }
}

@Composable
fun ChannelRow(channel: ChannelItem, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .frosted(RoundedCornerShape(22.dp), elevation = 6.dp)
            .combinedClickable(onClick = { app.push(Screen.Channel(channel.url)) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(channel.thumbnail, 56.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (channel.verified) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
            Text(
                Format.meta(Format.subs(channel.subscribers), if (channel.videos > 0) "${channel.videos} videos" else null),
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
            )
        }
        SubscribeButton(channel)
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistItem, modifier: Modifier = Modifier) {
    val app = LocalAppState.current
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { app.push(Screen.Playlist(playlist.url)) })
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(164.dp)) {
            Thumb(playlist.thumbnail, corner = 14.dp)
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(if (playlist.count >= 0) "${playlist.count}" else "", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.title, style = MaterialTheme.typography.titleSmall, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(playlist.uploader, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1)
        }
    }
}

@Composable
fun ItemView(item: Item, queue: List<VideoItem>?, large: Boolean) {
    when (item) {
        is VideoItem -> if (large) VideoCard(item, queue) else VideoRow(item, queue)
        is ChannelItem -> ChannelRow(item)
        is PlaylistItem -> PlaylistRow(item)
    }
}
