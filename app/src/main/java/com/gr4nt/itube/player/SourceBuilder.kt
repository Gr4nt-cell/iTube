package com.gr4nt.itube.player

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.gr4nt.itube.data.best
import com.gr4nt.itube.net.Http
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream

/** Builds ExoPlayer media sources from NewPipe stream data. */
object SourceBuilder {

    data class Built(val source: MediaSource, val height: Int, val heights: List<Int>)

    private val av1Supported: Boolean by lazy { hasDecoder(MimeTypes.VIDEO_AV1) }
    private val vp9Supported: Boolean by lazy { hasDecoder(MimeTypes.VIDEO_VP9) }

    private fun hasDecoder(mime: String) = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) }
        }
    }.getOrDefault(false)

    private fun codecRank(v: VideoStream): Int {
        val codec = v.itagItem?.codec?.lowercase() ?: ""
        return when {
            codec.startsWith("avc") -> 0
            codec.startsWith("vp9") || codec.startsWith("vp09") -> if (vp9Supported) 1 else 9
            codec.startsWith("av01") -> if (av1Supported) 2 else 9
            v.format == MediaFormat.MPEG_4 -> 3
            else -> 4
        }
    }

    private fun usable(s: Stream) = s.isUrl &&
        (s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || s.deliveryMethod == DeliveryMethod.DASH)

    fun videoHeights(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams + info.videoStreams)
            .filter { usable(it) && it.height > 0 && codecRank(it) < 9 }
            .map { it.height }.distinct().sortedDescending()

    fun pickVideo(info: StreamInfo, maxHeight: Int): VideoStream? {
        val candidates = info.videoOnlyStreams.filter { usable(it) && it.itagItem != null && codecRank(it) < 9 }
        if (candidates.isEmpty()) return null
        val fitting = candidates.filter { it.height <= maxHeight }.ifEmpty { listOf(candidates.minBy { it.height }) }
        val bestHeight = fitting.maxOf { it.height }
        return fitting.filter { it.height == bestHeight }
            .sortedWith(compareBy<VideoStream> { codecRank(it) }.thenByDescending { it.fps }.thenByDescending { it.bitrate })
            .first()
    }

    fun pickAudio(info: StreamInfo, preferM4a: Boolean = false): AudioStream? {
        val all = info.audioStreams.filter { usable(it) && it.itagItem != null }
        if (all.isEmpty()) return null
        val original = all.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { all }
        val pool = if (preferM4a) original.filter { it.format == MediaFormat.M4A }.ifEmpty { original } else original
        return pool.maxBy { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
    }

    fun metadata(info: StreamInfo): MediaMetadata = MediaMetadata.Builder()
        .setTitle(info.name)
        .setArtist(info.uploaderName)
        .setDisplayTitle(info.name)
        .setArtworkUri(info.thumbnails.best(720)?.let(Uri::parse))
        .build()

    fun build(
        context: Context,
        info: StreamInfo,
        maxHeight: Int,
        audioOnly: Boolean,
        subtitle: SubtitlesStream?,
    ): Built {
        val mediaItem = MediaItem.Builder()
            .setMediaId(info.url)
            .setUri(info.url)
            .setMediaMetadata(metadata(info))
            .build()
        val plainHttp = DefaultHttpDataSource.Factory().setUserAgent(Http.USER_AGENT)
        val ytFactory = YoutubeDataSource.Factory()
        val heights = videoHeights(info)

        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live || (info.videoOnlyStreams.isEmpty() && info.audioStreams.isEmpty())) {
            if (!info.hlsUrl.isNullOrEmpty()) {
                val src = HlsMediaSource.Factory(plainHttp)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem.buildUpon().setUri(info.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                return Built(src, 0, emptyList())
            }
            if (!info.dashMpdUrl.isNullOrEmpty()) {
                val src = DashMediaSource.Factory(plainHttp)
                    .createMediaSource(mediaItem.buildUpon().setUri(info.dashMpdUrl).setMimeType(MimeTypes.APPLICATION_MPD).build())
                return Built(src, 0, emptyList())
            }
        }

        val sources = mutableListOf<MediaSource>()
        var chosenHeight = 0
        val duration = info.duration

        if (!audioOnly) {
            val video = pickVideo(info, maxHeight)
            val videoSource = video?.let { runCatching { dashSource(ytFactory, it, duration, mediaItem) }.getOrNull() }
            if (videoSource != null) {
                sources += videoSource
                chosenHeight = video.height
            } else {
                // Fallback: a muxed progressive stream (usually 360p).
                val muxed = info.videoStreams.filter { usable(it) && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .maxByOrNull { it.height }
                if (muxed != null) {
                    val src = ProgressiveMediaSource.Factory(ytFactory)
                        .createMediaSource(mediaItem.buildUpon().setUri(muxed.content).build())
                    var single: MediaSource = src
                    subtitle?.let { single = MergingMediaSource(single, subtitleSource(plainHttp, it, duration)) }
                    return Built(single, muxed.height, heights)
                }
            }
        }

        val audio = pickAudio(info)
        if (audio != null) {
            runCatching { dashSource(ytFactory, audio, duration, mediaItem) }.getOrNull()?.let { sources += it }
        }
        if (sources.isEmpty()) error("No playable streams found for this video")
        if (subtitle != null && !audioOnly) sources += subtitleSource(plainHttp, subtitle, duration)

        val merged = if (sources.size == 1) sources[0] else MergingMediaSource(true, *sources.toTypedArray())
        return Built(merged, chosenHeight, heights)
    }

    private fun dashSource(
        factory: YoutubeDataSource.Factory,
        stream: Stream,
        durationSec: Long,
        item: MediaItem,
    ): MediaSource {
        val itag = when (stream) {
            is VideoStream -> stream.itagItem
            is AudioStream -> stream.itagItem
            else -> null
        } ?: error("Missing itag info")
        val manifest = when (stream.deliveryMethod) {
            DeliveryMethod.DASH -> YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, durationSec)
            else -> YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itag, durationSec)
        }
        val parsed = DashManifestParser().parse(
            Uri.parse(stream.content),
            ByteArrayInputStream(manifest.toByteArray()),
        )
        return DashMediaSource.Factory(factory).createMediaSource(parsed, item)
    }

    private fun subtitleSource(
        http: DefaultHttpDataSource.Factory,
        sub: SubtitlesStream,
        durationSec: Long,
    ): MediaSource {
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.content))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(sub.languageTag)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        return SingleSampleMediaSource.Factory(http)
            .createMediaSource(config, if (durationSec > 0) durationSec * 1_000_000 else C.TIME_UNSET)
    }

    fun localSource(context: Context, uri: Uri, title: String, artist: String, artwork: String?): MediaSource {
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist)
                    .setArtworkUri(artwork?.let(Uri::parse)).build()
            )
            .build()
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(item)
    }

    /** VTT subtitles only (others need conversion). */
    fun subtitles(info: StreamInfo): List<SubtitlesStream> =
        info.subtitles.filter { it.format == MediaFormat.VTT && it.isUrl }
}
