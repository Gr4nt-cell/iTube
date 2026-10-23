package com.gr4nt.itube.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.gr4nt.itube.data.Account
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.net.Innertube
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.ui.glass.GlassIconButton
import com.gr4nt.itube.ui.glass.liquidGlass
import com.gr4nt.itube.ui.theme.AppTheme
import com.gr4nt.itube.ui.theme.ITubeTheme
import com.gr4nt.itube.ui.theme.isDark
import kotlinx.coroutines.launch

/** Google sign-in for YouTube, done on Google's own page inside a WebView. */
class LoginActivity : ComponentActivity() {

    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by Settings.theme.collectAsState()
            ITubeTheme(isDark(theme)) {
                var loading by remember { mutableStateOf(true) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                BackHandler {
                    val wv = webView
                    if (wv != null && wv.canGoBack()) wv.goBack() else finish()
                }
                Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    ) {
                        GlassIconButton(Icons.Rounded.Close, "Close", { finish() }, state = null)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f).liquidGlass(RoundedCornerShape(22.dp), state = null).padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.label)
                        }
                    }
                    if (loading) {
                        LinearProgressIndicator(color = AppTheme.colors.accent, modifier = Modifier.fillMaxWidth())
                    }
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        factory = { ctx -> createWebView(ctx) { loading = it }.also { webView = it } },
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: android.content.Context, onLoading: (Boolean) -> Unit): WebView {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        return WebView(ctx).apply {
            cookies.setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // Present as a regular mobile browser; Google refuses sign-in from "embedded" views.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onLoading(true)

                override fun onPageFinished(view: WebView, url: String?) {
                    onLoading(false)
                    checkSignedIn(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Keep web pages in the sign-in view; drop intent:// and app deep links.
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http"
                }
            }
            loadUrl(SIGN_IN_URL)
        }
    }

    private fun checkSignedIn(url: String?) {
        if (finished || url == null) return
        val host = Uri.parse(url).host.orEmpty()
        if (!host.endsWith("youtube.com")) return
        val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return
        if (Account.sapisid(cookie) == null) return
        finished = true
        CookieManager.getInstance().flush()
        Account.signIn(cookie)
        lifecycleScope.launch {
            runCatching { Innertube.fetchProfile() }
                .onSuccess {
                    Account.setProfile(it)
                    PlaybackEngine.emit("Signed in as ${it.name}")
                }
                .onFailure { PlaybackEngine.emit("Signed in") }
            // Bring the account's subscriptions into the app.
            runCatching { Innertube.subscribedChannels() }.onSuccess { channels ->
                val added = com.gr4nt.itube.data.Store.importSubscriptions(channels)
                if (added > 0) PlaybackEngine.emit("Imported $added subscriptions")
            }
            finish()
        }
    }

    companion object {
        private const val SIGN_IN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
                "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"
    }
}
