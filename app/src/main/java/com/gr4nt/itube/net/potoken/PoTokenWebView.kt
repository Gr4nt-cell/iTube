package com.gr4nt.itube.net.potoken

// Ported from NewPipe (GPL-3.0). Runs YouTube's BotGuard in an offline WebView to mint PoTokens,
// which YouTube requires for reliable stream playback.

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.gr4nt.itube.net.NewPipeDownloader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
class PoTokenWebView private constructor(
    context: Context,
    private val ready: CompletableDeferred<PoTokenWebView>,
) {
    private val webView = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = mutableMapOf<String, CompletableDeferred<String>>()
    private var expiresAt: Long = 0

    init {
        webView.settings.javaScriptEnabled = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, false)
        }
        webView.settings.userAgentString = USER_AGENT
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    val e = BadWebViewException("${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    fail(e)
                    failAll(e)
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    private fun load(context: Context) {
        scope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                }
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"),
                    "text/html",
                    "utf-8",
                    null,
                )
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        request("https://www.youtube.com/api/jnn/v1/Create", "[ \"$REQUEST_KEY\" ]") { body ->
            val data = parseChallengeData(body)
            webView.evaluateJavascript(
                """try {
                    data = $data
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null,
            )
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) = fail(exceptionForJsError(error))

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        request(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { body ->
            val (token, ttlSeconds) = parseIntegrityTokenData(body)
            expiresAt = System.currentTimeMillis() + (ttlSeconds - 600) * 1000
            webView.evaluateJavascript("this.integrityToken = $token") { ready.complete(this) }
        }
    }

    suspend fun generatePoToken(identifier: String): String {
        val deferred = CompletableDeferred<String>()
        synchronized(pending) { pending[identifier] = deferred }
        withContext(Dispatchers.Main) {
            val u8 = stringToU8(identifier)
            webView.evaluateJavascript(
                """try {
                    identifier = "$identifier"
                    u8Identifier = $u8
                    poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                    poTokenU8String = ""
                    for (i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ","
                        poTokenU8String += poTokenU8[i]
                    }
                    $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                }""",
                null,
            )
        }
        return deferred.await()
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        pop(identifier)?.completeExceptionally(exceptionForJsError(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val d = pop(identifier) ?: return
        try {
            d.complete(u8ToBase64(poTokenU8))
        } catch (t: Throwable) {
            d.completeExceptionally(t)
        }
    }

    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt

    private fun pop(id: String) = synchronized(pending) { pending.remove(id) }

    private fun failAll(e: Exception) {
        val all = synchronized(pending) { pending.values.toList().also { pending.clear() } }
        all.forEach { it.completeExceptionally(e) }
    }

    private fun request(url: String, data: String, onBody: (String) -> Unit) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    NewPipeDownloader.post(
                        url,
                        mapOf(
                            "User-Agent" to listOf(USER_AGENT),
                            "Accept" to listOf("application/json"),
                            "Content-Type" to listOf("application/json+protobuf"),
                            "x-goog-api-key" to listOf(GOOGLE_API_KEY),
                            "x-user-agent" to listOf("grpc-web-javascript/0.1"),
                        ),
                        data.toByteArray(),
                    )
                }
                if (response.responseCode() != 200) {
                    fail(PoTokenException("Invalid response code: ${response.responseCode()}"))
                } else {
                    onBody(response.responseBody())
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun fail(e: Throwable) {
        Handler(Looper.getMainLooper()).post {
            if (!ready.isCompleted) {
                close()
                ready.completeExceptionally(e)
            }
        }
    }

    fun close() {
        Handler(Looper.getMainLooper()).post {
            scope.cancel()
            runCatching {
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    companion object {
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        suspend fun create(context: Context): PoTokenWebView {
            val ready = CompletableDeferred<PoTokenWebView>()
            withContext(Dispatchers.Main) {
                PoTokenWebView(context, ready).load(context)
            }
            return ready.await()
        }
    }
}
