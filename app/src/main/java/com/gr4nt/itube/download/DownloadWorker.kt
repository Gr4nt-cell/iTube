package com.gr4nt.itube.download

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gr4nt.itube.ITubeApp
import com.gr4nt.itube.R
import com.gr4nt.itube.data.DownloadRecord
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationId = (inputData.getString(KEY_ID) ?: "x").hashCode()
    private val title = inputData.getString(KEY_TITLE) ?: "Video"

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0f, "Starting")

    private fun foreground(progress: Float, stage: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, ITubeApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(stage)
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, n)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val videoUrl = inputData.getString(KEY_VIDEO_URL)
        val audioUrl = inputData.getString(KEY_AUDIO_URL)
        val ext = inputData.getString(KEY_EXT) ?: "mp4"
        val mime = inputData.getString(KEY_MIME) ?: "video/mp4"
        val audioOnly = inputData.getBoolean(KEY_AUDIO_ONLY, false)
        val videoSize = inputData.getLong(KEY_VIDEO_SIZE, -1)
        val audioSize = inputData.getLong(KEY_AUDIO_SIZE, -1)

        val work = File(applicationContext.cacheDir, "dl_$id").apply { mkdirs() }
        try {
            runCatching { setForeground(foreground(0f, "Starting")) }
            val result: File = when {
                videoUrl != null && audioUrl != null -> {
                    val total = videoSize.coerceAtLeast(0) + audioSize.coerceAtLeast(0)
                    val v = File(work, "video.$ext")
                    val a = File(work, "audio.$ext")
                    fetch(videoUrl, v, videoSize, 0, total, "Downloading video")
                    fetch(audioUrl, a, audioSize, v.length(), total, "Downloading audio")
                    report(1f, "Merging")
                    val out = File(work, "out.$ext")
                    Muxer.mux(v, a, out, ext == "webm")
                    v.delete(); a.delete()
                    out
                }
                videoUrl != null -> File(work, "out.$ext").also { fetch(videoUrl, it, videoSize, 0, videoSize, "Downloading") }
                audioUrl != null -> File(work, "out.$ext").also { fetch(audioUrl, it, audioSize, 0, audioSize, "Downloading audio") }
                else -> return@withContext Result.failure()
            }
            report(1f, "Saving")
            val name = sanitize(title) + (if (!audioOnly) " (${inputData.getString(KEY_LABEL) ?: ""})" else "") + ".$ext"
            val uri = save(result, name, mime, audioOnly)
            Store.addDownload(
                DownloadRecord(
                    id = id,
                    title = title,
                    uploader = inputData.getString(KEY_UPLOADER) ?: "",
                    thumbnail = inputData.getString(KEY_THUMB),
                    uri = uri.toString(),
                    mime = mime,
                    sizeBytes = result.length(),
                    audioOnly = audioOnly,
                    createdAt = System.currentTimeMillis(),
                )
            )
            result.delete()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2 && e is IOException) Result.retry() else Result.failure(workDataOf("error" to (e.message ?: "Failed")))
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun report(progress: Float, stage: String) {
        setProgress(workDataOf(PROGRESS to progress, STAGE to stage, KEY_TITLE to title))
        runCatching { setForeground(foreground(progress, stage)) }
    }

    /** Downloads [url] in range-parameter chunks (the way YouTube clients do), resuming partial files. */
    private suspend fun fetch(url: String, file: File, expected: Long, offsetDone: Long, total: Long, stage: String) {
        var position = if (file.exists()) file.length() else 0L
        var lastReport = 0L
        java.io.FileOutputStream(file, true).use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                if (expected > 0 && position >= expected) break
                val end = if (expected > 0) minOf(position + CHUNK, expected) - 1 else position + CHUNK - 1
                val requested = end - position + 1
                val isYt = Uri.parse(url).path?.startsWith("/videoplayback") == true
                val reqUrl = if (isYt) "$url&range=$position-$end" else url
                val builder = Request.Builder().url(reqUrl).header("User-Agent", Http.USER_AGENT)
                if (isYt) {
                    builder.post(byteArrayOf(0x78, 0).toRequestBody())
                    if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                        builder.header("Origin", "https://www.youtube.com").header("Referer", "https://www.youtube.com")
                    }
                } else {
                    builder.header("Range", "bytes=$position-$end")
                }
                var received = 0L
                Http.client.newCall(builder.build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val input = r.body?.byteStream() ?: throw IOException("Empty body")
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        position += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 700) {
                            lastReport = now
                            val p = if (total > 0) ((offsetDone + position).toFloat() / total).coerceIn(0f, 1f) else 0f
                            report(p, stage)
                        }
                    }
                }
                if (received < requested || !isYt) break
            }
        }
    }

    private fun save(file: File, name: String, mime: String, audioOnly: Boolean): Uri {
        val ctx = applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/iTube")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create file in Downloads")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not write file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "iTube").apply { mkdirs() }
            var target = File(dir, name)
            var i = 1
            while (target.exists()) target = File(dir, name.substringBeforeLast('.') + " ($i)." + name.substringAfterLast('.')).also { i++ }
            file.copyTo(target)
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
            Uri.fromFile(target)
        }
    }

    private fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(120).ifBlank { "video" }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_UPLOADER = "uploader"
        const val KEY_THUMB = "thumb"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_VIDEO_SIZE = "video_size"
        const val KEY_AUDIO_SIZE = "audio_size"
        const val KEY_EXT = "ext"
        const val KEY_MIME = "mime"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_LABEL = "label"
        const val PROGRESS = "progress"
        const val STAGE = "stage"
        private const val CHUNK = 8L * 1024 * 1024
    }
}
