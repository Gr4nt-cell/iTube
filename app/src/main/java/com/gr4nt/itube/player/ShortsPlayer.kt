package com.gr4nt.itube.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.gr4nt.itube.ITubeApp
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.data.best
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Separate looping player for the Shorts feed so it never disturbs the main queue. */
object ShortsPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(ITubeApp.context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _info = MutableStateFlow<StreamInfo?>(null)
    val info: StateFlow<StreamInfo?> = _info.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val cache = HashMap<String, StreamInfo>()

    fun play(video: VideoItem) {
        if (_currentUrl.value == video.url) {
            player.play()
            return
        }
        job?.cancel()
        _currentUrl.value = video.url
        _info.value = null
        _error.value = null
        player.stop()
        player.clearMediaItems()
        if (PlaybackEngine.player.isPlaying) PlaybackEngine.player.pause()
        job = scope.launch {
            try {
                val info = cache[video.url] ?: Repo.stream(video.url).also { cache[video.url] = it }
                _info.value = info
                val built = SourceBuilder.build(ITubeApp.context, info, 1080, audioOnly = false, subtitle = null)
                player.setMediaSource(built.source)
                player.prepare()
                player.play()
                Store.addHistory(video.copy(title = video.title.ifEmpty { info.name }, thumbnail = video.thumbnail ?: info.thumbnails.best()))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.message ?: "Could not play this Short"
            }
        }
    }

    /** Warms the extractor cache for the next Short so swiping feels instant. */
    fun prefetch(video: VideoItem) {
        if (cache.containsKey(video.url)) return
        scope.launch { runCatching { cache[video.url] = Repo.stream(video.url) } }
    }

    fun pause() = player.pause()

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        job?.cancel()
        _currentUrl.value = null
        player.stop()
    }
}
