package com.gr4nt.itube

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.net.Http
import com.gr4nt.itube.net.NewPipeDownloader
import com.gr4nt.itube.net.potoken.PoTokenProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.Locale

class ITubeApp : Application(), SingletonImageLoader.Factory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Settings.init(this)
        Store.init(this)
        com.gr4nt.itube.data.Account.init(this)

        val locale = Locale.getDefault()
        val country = locale.country.ifBlank { "US" }
        NewPipeDownloader.restrictedMode = Settings.restrictedMode.value
        NewPipe.init(
            NewPipeDownloader,
            Localization(locale.language.ifBlank { "en" }, country),
            ContentCountry(country),
        )
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl(this))

        appScope.launch {
            Settings.restrictedMode.collect { NewPipeDownloader.restrictedMode = it }
        }

        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POPUP, "Pop-up player", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(Http.client)) }
            .crossfade(true)
            .build()

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_POPUP = "popup"
        lateinit var instance: ITubeApp
            private set

        val context: Context get() = instance
    }
}
