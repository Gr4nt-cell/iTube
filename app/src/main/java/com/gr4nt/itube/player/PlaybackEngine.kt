package com.gr4nt.itube.player

import com.gr4nt.itube.data.regularVideos
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.gr4nt.itube.ITubeApp
import com.gr4nt.itube.data.DownloadRecord
import com.gr4nt.itube.data.Repo
import com.gr4nt.itube.data.SbCategory
import com.gr4nt.itube.data.Segment
import com.gr4nt.itube.data.Settings
import com.gr4nt.itube.data.SponsorBlock
import com.gr4nt.itube.data.Store
import com.gr4nt.itube.data.VideoItem
import com.gr4nt.itube.data.best
import com.gr4nt.itube.data.toItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream

enum class SurfaceOwner { MAIN, POPUP }

/**
 * Single app-wide player. The UI, the background media session and the floating pop-up all
 * drive this same ExoPlayer, so playback continues seamlessly between them.
 */
object PlaybackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val context: Context get() = ITubeApp.context

    val player: ExoPlayer by lazy { createPlayer() }

    private val _current = MutableStateFlow<VideoItem?>(null)
    val current: StateFlow<VideoItem?> = _current.asStateFlow()

    private val _info = MutableStateFlow<StreamInfo?>(null)
    val info: StateFlow<StreamInfo?> = _info.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _height = MutableStateFlow(0)
    val height: StateFlow<Int> = _height.asStateFlow()

    private val _heights = MutableStateFlow<List<Int>>(emptyList())
    val heights: StateFlow<List<Int>> = _heights.asStateFlow()

    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _subtitle = MutableStateFlow<SubtitlesStream?>(null)
    val subtitle: StateFlow<SubtitlesStream?> = _subtitle.asStateFlow()

    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue.asStateFlow()

    private val _surfaceOwner = MutableStateFlow(SurfaceOwner.MAIN)
    val surfaceOwner: StateFlow<SurfaceOwner> = _surfaceOwner.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short user-facing notices (e.g. "Skipped sponsor"). */
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var localRecord: DownloadRecord? = null
    private var loadJob: Job? = null
    private var controller: MediaController? = null
    private val skipped = mutableSetOf<String>()
    private var backgrounded = false

    private fun createPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_000, 2_500)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (!isPlaying) savePosition()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) onEnded()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _error.value = "Playback error: ${error.errorCodeName}"
                    }
                })
                startTickers(p)
            }
    }

    /** Connects a MediaController so the media session service runs (notification + background play). */
    fun ensureSession(ctx: Context) {
        if (controller != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(ctx))
    }

    fun play(video: VideoItem, queue: List<VideoItem>? = null, audioOnly: Boolean = false) {
        if (queue != null) _queue.value = queue.regularVideos()
        _audioOnly.value = audioOnly
        localRecord = null
        _current.value = video
        _info.value = null
        _error.value = null
        _segments.value = emptyList()
        _subtitle.value = null
        skipped.clear()
        ensureSession(context)
        load(video.url, resume = true, playWhenReady = true)
    }

    /** Audio-only playback that keeps going with the app closed or the screen off. */
    fun playInBackground(video: VideoItem) {
        play(video, audioOnly = true)
        emit("Playing in background")
    }

    fun playUrl(url: String) = play(VideoItem(url = url, title = "", thumbnail = null))

    private fun load(url: String, resume: Boolean, playWhenReady: Boolean, keepPositionMs: Long? = null) {
        loadJob?.cancel()
        _loading.value = true
        loadJob = scope.launch {
            try {
                val info = Repo.stream(url)
                _info.value = info
                val item = _current.value?.takeIf { it.title.isNotEmpty() } ?: VideoItem(
                    url = info.url,
                    title = info.name,
                    thumbnail = info.thumbnails.best(),
                    uploader = info.uploaderName ?: "",
                    uploaderUrl = info.uploaderUrl,
                    uploaderAvatar = info.uploaderAvatars.best(96),
                    durationSec = info.duration,
                    views = info.viewCount,
                    uploaded = info.textualUploadDate,
                )
                _current.value = item.copy(uploaderAvatar = item.uploaderAvatar ?: info.uploaderAvatars.best(96))
                Store.addHistory(_current.value!!)

                if (_queue.value.none { it.url == info.url }) {
                    // Auto-queue related videos so "next" and autoplay work.
                    _queue.value = listOf(_current.value!!) +
                        info.relatedItems.toItems().filterIsInstance<VideoItem>().regularVideos().filter { !it.isLive }
                }

                val built = SourceBuilder.build(context, info, Settings.quality.value, _audioOnly.value, _subtitle.value)
                _height.value = built.height
                _heights.value = built.heights
                player.setMediaSource(built.source)
                player.prepare()
                val start = keepPositionMs ?: if (resume) Store.positions.value[info.url] ?: 0L else 0L
                if (start > 0) player.seekTo(start)
                player.playbackParameters = PlaybackParameters(Settings.defaultSpeed.value)
                applyBackgroundVideoState()
                player.playWhenReady = playWhenReady
                fetchSegments(info)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _loading.value = false
            }
        }
    }

    private fun fetchSegments(info: StreamInfo) {
        if (!Settings.sbEnabled.value) return
        scope.launch {
            val id = Repo.videoId(info.url) ?: return@launch
            _segments.value = SponsorBlock.segments(id, Settings.sbCategories.value)
        }
    }

    private fun reload() {
        val url = _info.value?.url ?: return
        load(url, resume = false, playWhenReady = player.playWhenReady, keepPositionMs = player.currentPosition)
    }

    fun setQuality(height: Int) {
        Settings.setQuality(height)
        reload()
    }

    fun setAudioOnly(enabled: Boolean) {
        _audioOnly.value = enabled
        reload()
    }

    fun setSubtitle(sub: SubtitlesStream?) {
        _subtitle.value = sub
        reload()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        val q = _queue.value
        val idx = q.indexOfFirst { it.url == _current.value?.url }
        q.getOrNull(idx + 1)?.let { play(it, audioOnly = _audioOnly.value) }
    }

    fun previous() {
        if (player.currentPosition > 5_000) {
            player.seekTo(0); return
        }
        val q = _queue.value
        val idx = q.indexOfFirst { it.url == _current.value?.url }
        q.getOrNull(idx - 1)?.let { play(it, audioOnly = _audioOnly.value) }
    }

    fun hasNext(): Boolean {
        val q = _queue.value
        val idx = q.indexOfFirst { it.url == _current.value?.url }
        return idx >= 0 && idx < q.size - 1
    }

    private fun onEnded() {
        _info.value?.url?.let { Store.savePosition(it, 0) }
        if (Settings.autoplay.value && localRecord == null && hasNext()) next()
    }

    fun playLocal(record: DownloadRecord) {
        loadJob?.cancel()
        savePosition()
        localRecord = record
        _info.value = null
        _error.value = null
        _segments.value = emptyList()
        _heights.value = emptyList()
        _current.value = VideoItem(url = record.uri, title = record.title, thumbnail = record.thumbnail, uploader = record.uploader)
        ensureSession(context)
        player.setMediaSource(SourceBuilder.localSource(context, Uri.parse(record.uri), record.title, record.uploader, record.thumbnail))
        player.prepare()
        player.play()
    }

    fun stop() {
        savePosition()
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _current.value = null
        _info.value = null
        _queue.value = emptyList()
        _segments.value = emptyList()
        localRecord = null
        if (_surfaceOwner.value == SurfaceOwner.POPUP) PopupPlayerService.stop(context)
    }

    fun setSurfaceOwner(owner: SurfaceOwner) {
        _surfaceOwner.value = owner
    }

    /** App went to background (BACKGROUND mode) or came back: drop / restore the video track to save data. */
    fun setBackgrounded(value: Boolean) {
        backgrounded = value
        applyBackgroundVideoState()
    }

    private fun applyBackgroundVideoState() {
        val disableVideo = backgrounded && _surfaceOwner.value == SurfaceOwner.MAIN
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableVideo)
            .build()
    }

    private fun savePosition() {
        if (localRecord != null) return
        val url = _info.value?.url ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (dur > 0 && pos > dur - 10_000) Store.savePosition(url, 0) else Store.savePosition(url, pos)
    }

    private fun startTickers(p: ExoPlayer) {
        // SponsorBlock auto-skip
        scope.launch {
            while (isActive) {
                delay(250)
                if (!p.isPlaying || !Settings.sbEnabled.value) continue
                val pos = p.currentPosition
                val seg = _segments.value.firstOrNull {
                    pos >= it.startMs && pos < it.endMs - 300 && it.category in Settings.sbCategories.value
                } ?: continue
                if (seg.uuid in skipped && pos > seg.startMs + 1500) continue
                skipped += seg.uuid
                p.seekTo(seg.endMs)
                _events.tryEmit("Skipped ${SbCategory.of(seg.category)?.label ?: seg.category}")
            }
        }
        // Periodic resume-position save
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (p.isPlaying) savePosition()
            }
        }
    }

    fun emit(message: String) {
        _events.tryEmit(message)
    }
}
