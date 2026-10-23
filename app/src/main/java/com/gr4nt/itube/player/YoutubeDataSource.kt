package com.gr4nt.itube.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.gr4nt.itube.net.Http
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.InputStream

/**
 * Fetches googlevideo "videoplayback" URLs the way YouTube's own clients do: bounded chunks
 * requested through the `range` query parameter, a request counter (`rn`) and a tiny POST body.
 * Plain GET requests are used for everything else. Unbounded reads are split into chunks so
 * YouTube does not throttle or reject long open-ended transfers.
 */
class YoutubeDataSource : BaseDataSource(true) {

    private var spec: DataSpec? = null
    private var response: Response? = null
    private var stream: InputStream? = null
    private var opened = false

    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var chunkRequested = 0L
    private var chunkReceived = 0L
    private var youtubeMode = false
    private var requestNumber = 0

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        transferInitializing(dataSpec)
        position = dataSpec.position
        remaining = dataSpec.length
        chunkRequested = 0L
        youtubeMode = dataSpec.uri.path?.startsWith("/videoplayback") == true &&
            !dataSpec.uri.toString().contains("/range/") // DASH-live segment URLs use path ranges

        if (youtubeMode) openChunk() else openPlain(dataSpec)

        opened = true
        transferStarted(dataSpec)
        return dataSpec.length
    }

    private fun openPlain(dataSpec: DataSpec) {
        val range = if (dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) {
            null
        } else if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            "bytes=${dataSpec.position}-"
        } else {
            "bytes=${dataSpec.position}-${dataSpec.position + dataSpec.length - 1}"
        }
        val req = Request.Builder()
            .url(dataSpec.uri.toString())
            .header("User-Agent", Http.USER_AGENT)
            .apply { if (range != null) header("Range", range) }
            .apply { dataSpec.httpRequestHeaders.forEach { (k, v) -> header(k, v) } }
            .build()
        val r = Http.client.newCall(req).execute()
        if (!r.isSuccessful) {
            r.close()
            throw IOException("HTTP ${r.code} for ${dataSpec.uri.host}")
        }
        response = r
        stream = r.body?.byteStream()
        // Server ignored the range: skip to the requested position.
        if (range != null && r.code == 200 && dataSpec.position > 0) {
            stream?.skipFully(dataSpec.position)
        }
    }

    private fun openChunk() {
        closeConnection()
        // Whole-resource requests (e.g. OTF DASH segments addressed by `sq`) must not carry a range.
        val unranged = position == 0L && remaining == C.LENGTH_UNSET.toLong() && chunkRequested == 0L
        val want = when {
            unranged -> Long.MAX_VALUE
            remaining == C.LENGTH_UNSET.toLong() -> CHUNK
            else -> minOf(CHUNK, remaining)
        }
        val base = spec!!.uri.toString()
        val url = buildString {
            append(base)
            if (!base.contains("&rn=")) append("&rn=").append(requestNumber++)
            if (!unranged) append("&range=").append(position).append('-').append(position + want - 1)
        }
        val builder = Request.Builder()
            .url(url)
            .post(POST_BODY.toRequestBody())
            .header("Accept-Encoding", "identity")
            .header("TE", "trailers")
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            builder.header("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
        } else {
            builder.header("User-Agent", Http.USER_AGENT)
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            builder.header("Origin", "https://www.youtube.com")
            builder.header("Referer", "https://www.youtube.com")
            builder.header("Sec-Fetch-Dest", "empty")
            builder.header("Sec-Fetch-Mode", "cors")
            builder.header("Sec-Fetch-Site", "cross-site")
        }
        val r = Http.client.newCall(builder.build()).execute()
        if (!r.isSuccessful) {
            r.close()
            throw IOException("HTTP ${r.code} while streaming")
        }
        response = r
        stream = r.body?.byteStream()
        chunkRequested = want
        chunkReceived = 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            val s = stream ?: return C.RESULT_END_OF_INPUT
            val toRead = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
            val n = s.read(buffer, offset, toRead)
            if (n > 0) {
                position += n
                chunkReceived += n
                if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
                bytesTransferred(n)
                return n
            }
            // Current connection is exhausted.
            if (!youtubeMode || chunkReceived < chunkRequested) return C.RESULT_END_OF_INPUT
            openChunk()
        }
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        closeConnection()
        if (opened) {
            opened = false
            transferEnded()
        }
        spec = null
    }

    private fun closeConnection() {
        runCatching { stream?.close() }
        runCatching { response?.close() }
        stream = null
        response = null
    }

    private fun InputStream.skipFully(n: Long) {
        var left = n
        val tmp = ByteArray(8192)
        while (left > 0) {
            val r = read(tmp, 0, minOf(tmp.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = YoutubeDataSource()
    }

    companion object {
        private const val CHUNK = 5L * 1024 * 1024
        private val POST_BODY = byteArrayOf(0x78, 0)
    }
}
