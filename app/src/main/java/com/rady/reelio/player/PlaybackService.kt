package com.rady.reelio.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rady.reelio.MainActivity
import com.rady.reelio.R
import com.rady.reelio.data.MediaEntry
import com.rady.reelio.video.VideoActivity

/**
 * A thin "player" that mirrors [PlayerHub] for Android's media session — this is what drives
 * the notification, the lock screen, Bluetooth buttons and Android Auto/Wear controls.
 */
@OptIn(UnstableApi::class)
class ReelioSessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var currentWindowIndex = 0

    fun refresh() = invalidateState()

    override fun getState(): State {
        val h = PlayerHub
        val q = h.queue
        val cmds = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_SET_SPEED_AND_PITCH, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE,
        ).build()
        val b = State.Builder()
            .setAvailableCommands(cmds)
            .setRepeatMode(
                when (q.repeat) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                },
            )
            .setShuffleModeEnabled(q.shuffle)
            .setPlaybackParameters(PlaybackParameters(h.state.value.speed))
        val cur = q.current
        if (cur == null) {
            currentWindowIndex = 0
            return b.setPlaylist(emptyList())
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }
        val list = ArrayList<MediaItemData>(3)
        val prev = if (q.hasPrevious()) q.peekPrevious() else null
        if (prev != null) list += itemData("p:" + prev.uri, prev, false)
        currentWindowIndex = list.size
        list += itemData("c:" + cur.uri, cur, true)
        if (q.hasNext()) {
            val next = q.peekNext() ?: cur
            list += itemData("n:" + next.uri, next, false)
        }
        return b.setPlaylist(list)
            .setCurrentMediaItemIndex(currentWindowIndex)
            .setPlaybackState(
                when {
                    h.isEnded -> Player.STATE_ENDED
                    h.isBuffering -> Player.STATE_BUFFERING
                    else -> Player.STATE_READY
                },
            )
            .setPlayWhenReady(h.isPlayWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(PositionSupplier { h.positionMs() })
            .build()
    }

    private fun itemData(uid: String, e: MediaEntry, isCurrent: Boolean): MediaItemData {
        val meta = MediaMetadata.Builder()
            .setTitle(e.title)
            .setArtist(e.artist.ifBlank { if (e.isVideo) "Video" else null })
            .setAlbumTitle(e.album.ifBlank { null })
            .setIsPlayable(true)
            .setIsBrowsable(false)
        if (isCurrent) {
            val art = PlayerHub.artwork
            if (art != null && art.first == e.uri && art.second != null) meta.setArtworkData(art.second, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val item = MediaItem.Builder().setMediaId(e.uri).setMediaMetadata(meta.build()).build()
        val dur = if (isCurrent) PlayerHub.durationMs() else e.durationMs
        return MediaItemData.Builder(uid)
            .setMediaItem(item)
            .setDurationUs(if (dur > 0) dur * 1000 else C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) PlayerHub.play() else PlayerHub.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        PlayerHub.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when {
            mediaItemIndex > currentWindowIndex -> PlayerHub.next()
            mediaItemIndex < currentWindowIndex && mediaItemIndex >= 0 -> PlayerHub.previousItem()
            else -> PlayerHub.seekTo(if (positionMs == C.TIME_UNSET) 0 else positionMs)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        PlayerHub.setRepeat(
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        PlayerHub.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        PlayerHub.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }
}

/** Keeps music and videos playing with the screen off or while you use other apps. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = MediaSession.Builder(this, PlayerHub.sessionPlayer)
            .setSessionActivity(activityIntent(PlayerHub.queue.current?.isVideo == true))
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_reelio) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    fun setVideoMode(video: Boolean) {
        session?.setSessionActivity(activityIntent(video))
    }

    private fun activityIntent(video: Boolean): PendingIntent {
        val i = Intent(this, if (video) VideoActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        return PendingIntent.getActivity(
            this, if (video) 1 else 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHub.persistNow()
        if (!PlayerHub.isPlayWhenReady) stopSelf()
    }

    override fun onDestroy() {
        instance = null
        PlayerHub.persistNow()
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        var instance: PlaybackService? = null
            private set
    }
}
