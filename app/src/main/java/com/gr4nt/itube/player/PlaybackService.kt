package com.gr4nt.itube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gr4nt.itube.ui.MainActivity

/**
 * Background playback: hosts the media session around the shared player, which gives a media
 * notification with controls, lock-screen controls and Bluetooth/headset buttons, and keeps the
 * audio going while the screen is off or another app is open.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, QueueAwarePlayer(PlaybackEngine.player))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            // The ExoPlayer itself is app-wide; only release the session wrapper.
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Exposes next/previous in the notification by routing them to the engine's queue. */
private class QueueAwarePlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM || super.isCommandAvailable(command)

    override fun seekToNext() = PlaybackEngine.next()
    override fun seekToNextMediaItem() = PlaybackEngine.next()
    override fun seekToPrevious() = PlaybackEngine.previous()
    override fun seekToPreviousMediaItem() = PlaybackEngine.previous()
}
