package com.gr4nt.itube.download

import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gr4nt.itube.data.best
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

data class DownloadOption(
    val label: String,
    val detail: String,
    val audioOnly: Boolean,
    val height: Int,
    val videoUrl: String?,
    val audioUrl: String?,
    val videoSize: Long,
    val audioSize: Long,
    val ext: String,
    val mime: String,
) {
    val sizeBytes get() = (videoSize.coerceAtLeast(0) + audioSize.coerceAtLeast(0))
}

object Downloads {
    const val TAG = "download"

    private fun AudioStream.isOriginal() = audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL
    private fun AudioStream.rate() = averageBitrate.takeIf { it > 0 } ?: bitrate
    private fun VideoStream.codec() = itagItem?.codec?.lowercase() ?: ""
    private fun size(s: VideoStream) = s.itagItem?.contentLength ?: -1L
    private fun size(s: AudioStream) = s.itagItem?.contentLength ?: -1L

    fun options(info: StreamInfo): List<DownloadOption> {
        val audios = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val pool = audios.filter { it.isOriginal() }.ifEmpty { audios }
        val m4a = pool.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.rate() }
        val opus = pool.filter { it.format == MediaFormat.WEBMA || it.format == MediaFormat.WEBMA_OPUS }.maxByOrNull { it.rate() }

        val out = mutableListOf<DownloadOption>()
        val videos = info.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val heights = videos.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
        for (h in heights) {
            val atH = videos.filter { it.height == h }
            val avc = atH.filter { it.format == MediaFormat.MPEG_4 && it.codec().startsWith("avc") }.maxByOrNull { it.bitrate }
            val vp9 = atH.filter { it.format == MediaFormat.WEBM && (it.codec().startsWith("vp9") || it.codec().startsWith("vp09")) }
                .maxByOrNull { it.bitrate }
            when {
                avc != null && m4a != null -> out += DownloadOption(
                    label = "${h}p" + if (avc.fps > 30) avc.fps.toString() else "",
                    detail = "MP4 · H.264 + AAC",
                    audioOnly = false, height = h,
                    videoUrl = avc.content, audioUrl = m4a.content,
                    videoSize = size(avc), audioSize = size(m4a),
                    ext = "mp4", mime = "video/mp4",
                )
                vp9 != null && opus != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> out += DownloadOption(
                    label = "${h}p" + if (vp9.fps > 30) vp9.fps.toString() else "",
                    detail = "WebM · VP9 + Opus",
                    audioOnly = false, height = h,
                    videoUrl = vp9.content, audioUrl = opus.content,
                    videoSize = size(vp9), audioSize = size(opus),
                    ext = "webm", mime = "video/webm",
                )
            }
        }
        // Single-file muxed stream (no merge needed)
        info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 }
            .maxByOrNull { it.height }?.let {
                out += DownloadOption(
                    label = "${it.height}p", detail = "MP4 · single file",
                    audioOnly = false, height = it.height,
                    videoUrl = it.content, audioUrl = null,
                    videoSize = size(it), audioSize = -1,
                    ext = "mp4", mime = "video/mp4",
                )
            }
        m4a?.let {
            out += DownloadOption(
                label = "Audio ${it.rate() / 1000}kbps", detail = "M4A · AAC",
                audioOnly = true, height = 0, videoUrl = null, audioUrl = it.content,
                videoSize = -1, audioSize = size(it), ext = "m4a", mime = "audio/mp4",
            )
        }
        opus?.let {
            out += DownloadOption(
                label = "Audio ${it.rate() / 1000}kbps", detail = "WebM · Opus",
                audioOnly = true, height = 0, videoUrl = null, audioUrl = it.content,
                videoSize = -1, audioSize = size(it), ext = "webm", mime = "audio/webm",
            )
        }
        return out
    }

    fun enqueue(context: Context, info: StreamInfo, option: DownloadOption) {
        val id = "${info.id}_${option.label}_${option.ext}".replace(Regex("[^A-Za-z0-9_]"), "_")
        val data = workDataOf(
            DownloadWorker.KEY_ID to id,
            DownloadWorker.KEY_TITLE to info.name,
            DownloadWorker.KEY_UPLOADER to (info.uploaderName ?: ""),
            DownloadWorker.KEY_THUMB to info.thumbnails.best(),
            DownloadWorker.KEY_VIDEO_URL to option.videoUrl,
            DownloadWorker.KEY_AUDIO_URL to option.audioUrl,
            DownloadWorker.KEY_VIDEO_SIZE to option.videoSize,
            DownloadWorker.KEY_AUDIO_SIZE to option.audioSize,
            DownloadWorker.KEY_EXT to option.ext,
            DownloadWorker.KEY_MIME to option.mime,
            DownloadWorker.KEY_AUDIO_ONLY to option.audioOnly,
            DownloadWorker.KEY_LABEL to option.label,
        )
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .addTag(TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, request)
    }
}
