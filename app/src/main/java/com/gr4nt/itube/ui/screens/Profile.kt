package com.gr4nt.itube.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.data.Account
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.net.Innertube
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.ui.LocalAppState
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.LoginActivity
import com.gr4nt.itube.ui.Screen
import com.gr4nt.itube.ui.components.Avatar
import com.gr4nt.itube.ui.components.PagedList
import com.gr4nt.itube.ui.components.SectionLabel
import com.gr4nt.itube.ui.glass.GlassPill
import com.gr4nt.itube.ui.glass.frosted
import com.gr4nt.itube.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
private fun ProfileRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = c.label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .frosted(RoundedCornerShape(24.dp), elevation = 10.dp),
    ) { content() }
}

@Composable
fun ProfileScreen() {
    val app = LocalAppState.current
    val chrome = LocalChrome.current
    val ctx = LocalContext.current
    val c = AppTheme.colors
    val cookies by Account.cookies.collectAsState()
    val profile by Account.profile.collectAsState()
    val subs by Store.subscriptions.collectAsState()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }

    // Refresh the name/photo in case they changed.
    LaunchedEffect(cookies) {
        if (cookies != null) runCatching { Innertube.fetchProfile() }.onSuccess { Account.setProfile(it) }
    }

    LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom), modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            ) {
                if (cookies != null && profile?.avatar != null) {
                    Avatar(profile?.avatar, 96.dp)
                } else {
                    Icon(Icons.Rounded.AccountCircle, null, tint = c.secondaryLabel, modifier = Modifier.size(96.dp))
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    if (cookies != null) profile?.name ?: "Signed in" else "Not signed in",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.label,
                    fontWeight = FontWeight.Bold,
                )
                val sub = if (cookies != null) listOfNotNull(profile?.handle, profile?.email).joinToString(" · ") else null
                if (!sub.isNullOrBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
                }
                if (cookies == null) {
                    Text(
                        "Sign in with your Google account to get your recommendations, subscriptions, history, Watch later and liked videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                    GlassPill(
                        modifier = Modifier.padding(top = 6.dp),
                        height = 50.dp,
                        onClick = { ctx.startActivity(Intent(ctx, LoginActivity::class.java)) },
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = c.accent)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google", color = c.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        if (cookies != null) {
            item { SectionLabel("Your YouTube") }
            item {
                Card {
                    profile?.channelUrl?.let { url ->
                        ProfileRow(Icons.Rounded.Person, "Your channel") { app.push(Screen.Channel(url)) }
                    }
                    ProfileRow(Icons.Rounded.History, "History", "Synced with your account") { app.push(Screen.AccountFeed("history")) }
                    ProfileRow(Icons.Rounded.WatchLater, "Watch later") { app.push(Screen.AccountFeed("watch_later")) }
                    ProfileRow(Icons.Rounded.ThumbUp, "Liked videos") { app.push(Screen.AccountFeed("liked")) }
                    ProfileRow(
                        Icons.Rounded.Subscriptions,
                        if (importing) "Importing subscriptions…" else "Import subscriptions",
                        "${subs.size} channels in the app",
                    ) {
                        if (importing) return@ProfileRow
                        importing = true
                        scope.launch {
                            runCatching { Innertube.subscribedChannels() }
                                .onSuccess { PlaybackEngine.emit("Imported ${Store.importSubscriptions(it)} new subscriptions") }
                                .onFailure { PlaybackEngine.emit(it.message ?: "Could not import subscriptions") }
                            importing = false
                        }
                    }
                }
            }
        }

        item { SectionLabel("App") }
        item {
            Card {
                ProfileRow(Icons.Rounded.Download, "Downloads") { app.selectTab(3) }
                ProfileRow(Icons.Rounded.Settings, "Settings") { app.push(Screen.Settings) }
                if (cookies != null) {
                    ProfileRow(Icons.AutoMirrored.Rounded.Logout, "Sign out") {
                        Account.signOut()
                        PlaybackEngine.emit("Signed out")
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Your Google password is entered on Google's own sign-in page; iTube only keeps the YouTube session on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.tertiaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun accountFeedTitle(kind: String) = when (kind) {
    "history" -> "History"
    "watch_later" -> "Watch later"
    "liked" -> "Liked videos"
    else -> "YouTube"
}

@Composable
fun AccountFeedScreen(kind: String) {
    val chrome = LocalChrome.current
    PagedList(
        key = "account_$kind",
        load = {
            when (kind) {
                "history" -> Innertube.history()
                "watch_later" -> Innertube.watchLater()
                else -> Innertube.liked()
            }
        },
        contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom),
        large = false,
    )
}
