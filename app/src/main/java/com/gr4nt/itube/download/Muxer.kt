package com.gr4nt.itube.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Merges a video-only file and an audio-only file into one MP4/WebM without re-encoding. */
object Muxer {

    fun mux(video: File, audio: File, out: File, webm: Boolean) {
        val format = if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        val muxer = MediaMuxer(out.absolutePath, format)
        val vEx = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val aEx = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        try {
            val vTrack = firstTrack(vEx, "video/")
            val aTrack = firstTrack(aEx, "audio/")
            vEx.selectTrack(vTrack)
            aEx.selectTrack(aTrack)
            val vOut = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val aOut = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()

            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            // Interleave by timestamp so the result streams well.
            var vDone = false
            var aDone = false
            while (!vDone || !aDone) {
                val takeVideo = !vDone && (aDone || vEx.sampleTime <= aEx.sampleTime)
                val ex = if (takeVideo) vEx else aEx
                val track = if (takeVideo) vOut else aOut
                buffer.clear()
                val size = ex.readSampleData(buffer, 0)
                if (size < 0) {
                    if (takeVideo) vDone = true else aDone = true
                    continue
                }
                info.offset = 0
                info.size = size
                info.presentationTimeUs = ex.sampleTime
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(track, buffer, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vEx.release()
            aEx.release()
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        error("No $prefix track found")
    }
}
