package com.gr4nt.itube.net.potoken

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** Supplies web-client PoTokens to NewPipeExtractor. Called from extractor (IO) threads. */
class PoTokenProviderImpl(private val appContext: Context) : PoTokenProvider {

    private val lock = Any()
    private var generator: PoTokenWebView? = null
    private var visitorData: String? = null
    private var streamingPot: String? = null
    private var webViewBroken = false

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (webViewBroken) return null
        return try {
            runBlocking { withTimeout(25_000) { obtain(videoId, false) } }
        } catch (e: BadWebViewException) {
            Log.e(TAG, "WebView cannot run BotGuard", e)
            webViewBroken = true
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain poToken", e)
            null
        }
    }

    private suspend fun obtain(videoId: String, forceRecreate: Boolean): PoTokenResult {
        val recreated: Boolean
        val gen: PoTokenWebView
        val vd: String
        val sp: String
        synchronized(lock) {
            recreated = generator == null || forceRecreate || generator!!.isExpired()
        }
        if (recreated) {
            val info = InnertubeClientRequestInfo.ofWebClient()
            info.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
            val newVisitor = YoutubeParsingHelper.getVisitorDataFromInnertube(
                info,
                NewPipe.getPreferredLocalization(),
                NewPipe.getPreferredContentCountry(),
                YoutubeParsingHelper.getYouTubeHeaders(),
                YoutubeParsingHelper.YOUTUBEI_V1_URL,
                null,
                false,
            )
            generator?.close()
            val newGen = PoTokenWebView.create(appContext)
            val newStreaming = newGen.generatePoToken(newVisitor)
            synchronized(lock) {
                generator = newGen
                visitorData = newVisitor
                streamingPot = newStreaming
            }
        }
        synchronized(lock) {
            gen = generator!!
            vd = visitorData!!
            sp = streamingPot!!
        }
        val playerPot = try {
            gen.generatePoToken(videoId)
        } catch (t: Throwable) {
            if (recreated) throw t
            return obtain(videoId, true)
        }
        return PoTokenResult(vd, playerPot, sp)
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    private companion object {
        const val TAG = "PoToken"
    }
}
