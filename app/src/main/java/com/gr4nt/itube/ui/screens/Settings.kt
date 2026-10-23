package com.gr4nt.itube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gr4nt.itube.BuildConfig
import com.gr4nt.itube.data.LeaveAction
import com.gr4nt.itube.data.SbCategory
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.ThemeMode
import com.gr4nt.itube.player.PlaybackEngine
import com.gr4nt.itube.player.PopupPlayerService
import com.gr4nt.itube.ui.LocalChrome
import com.gr4nt.itube.ui.components.SectionLabel
import com.gr4nt.itube.ui.glass.frosted
import com.gr4nt.itube.ui.theme.AppTheme

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .frosted(RoundedCornerShape(22.dp), elevation = 8.dp)
            .padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = c.secondaryLabel, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, color: Color? = null, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        if (color != null) {
            Box(Modifier.padding(end = 12.dp).size(12.dp).clip(CircleShape).background(color))
        }
        Text(title, color = c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.accent)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.chip)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (i == selected) c.elevated else Color.Transparent)
                    .clickable { onSelect(i) },
            ) {
                Text(label, color = c.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val ctx = LocalContext.current
    val theme by Settings.theme.collectAsState()
    val quality by Settings.quality.collectAsState()
    val speed by Settings.defaultSpeed.collectAsState()
    val autoplay by Settings.autoplay.collectAsState()
    val leave by Settings.leaveAction.collectAsState()
    val sb by Settings.sbEnabled.collectAsState()
    val cats by Settings.sbCategories.collectAsState()
    val restricted by Settings.restrictedMode.collectAsState()
    val dislikes by Settings.showDislikes.collectAsState()

    LazyColumn(contentPadding = PaddingValues(top = chrome.top, bottom = chrome.bottom), modifier = Modifier.fillMaxSize()) {
        item { SectionLabel("Appearance") }
        item {
            Segmented(listOf("System", "Light", "Dark"), ThemeMode.entries.indexOf(theme)) { Settings.setTheme(ThemeMode.entries[it]) }
        }

        item { SectionLabel("When I leave the app while watching") }
        item {
            Card {
                LeaveAction.entries.forEach { a ->
                    ChoiceRow(a.label, a == leave) {
                        Settings.setLeaveAction(a)
                        if (a == LeaveAction.POPUP && !PopupPlayerService.canDrawOverlays(ctx)) {
                            ctx.startActivity(PopupPlayerService.overlayPermissionIntent(ctx))
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Background play keeps audio going with the screen off or in other apps, controlled from the notification. " +
                    "Pop-up shows a floating, draggable mini-player over other apps.",
                color = c.secondaryLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        item { SectionLabel("Playback") }
        item {
            Card {
                ToggleRow("Autoplay next video", checked = autoplay, onChange = Settings::setAutoplay)
                ToggleRow("Show dislike counts", "From Return YouTube Dislike", checked = dislikes, onChange = Settings::setShowDislikes)
            }
        }
        item { SectionLabel("Default quality") }
        item {
            val qualities = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
            Card {
                qualities.forEach { q -> ChoiceRow("${q}p", q == quality) { Settings.setQuality(q) } }
            }
        }
        item { SectionLabel("Default speed") }
        item {
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            Segmented(speeds.map { if (it == 1f) "1×" else "${it}×" }, speeds.indexOf(speed).coerceAtLeast(0)) {
                Settings.setDefaultSpeed(speeds[it])
                PlaybackEngine.setSpeed(speeds[it])
            }
        }

        item { SectionLabel("SponsorBlock") }
        item {
            Card {
                ToggleRow("Skip sponsored segments", "Community-submitted segments from sponsor.ajay.app", checked = sb, onChange = Settings::setSbEnabled)
                if (sb) {
                    SbCategory.entries.forEach { cat ->
                        ChoiceRow(cat.label, cat.key in cats, Color(cat.color)) { Settings.toggleSbCategory(cat.key) }
                    }
                }
            }
        }

        item { SectionLabel("Ad blocking") }
        item {
            Text(
                "Videos are streamed directly from YouTube's servers without the ad system, so there are no pre-roll, mid-roll or banner ads.",
                color = c.secondaryLabel,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        item { SectionLabel("Content") }
        item {
            Card {
                ToggleRow("Restricted mode", "Hide potentially mature videos", checked = restricted, onChange = Settings::setRestricted)
            }
        }

        item { SectionLabel("Data") }
        item {
            Card {
                ChoiceRow("Clear watch history", false) { Store.clearHistory(); PlaybackEngine.emit("Watch history cleared") }
                ChoiceRow("Clear search history", false) { Store.clearSearches(); PlaybackEngine.emit("Search history cleared") }
            }
        }
        item {
            Text(
                "iTube ${BuildConfig.VERSION_NAME}\nPowered by NewPipe Extractor, SponsorBlock and Haze.",
                color = c.tertiaryLabel,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }
}
