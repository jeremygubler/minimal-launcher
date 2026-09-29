package dev.minimal.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.minimal.launcher.service.NotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val packageName: String,
)

/**
 * Beobachtet laufende Medien-Sitzungen (Musik, Podcasts, Videos).
 * Funktioniert nur mit Benachrichtigungszugriff, deshalb wird es vom NotificationListener gestartet.
 */
object NowPlaying {
    private val _state = MutableStateFlow<MediaInfo?>(null)
    val state: StateFlow<MediaInfo?> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    fun start(context: Context) {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, NotificationListener::class.java)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            manager = msm
            setControllers(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            stop()
        }
    }

    fun stop() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        manager = null
        setControllers(emptyList())
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, handler) } }
        publish()
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun MediaController.isPaused() = playbackState?.state == PlaybackState.STATE_PAUSED

    private fun publish() {
        val controller = controllers.firstOrNull { it.isPlaying() }
            ?: controllers.firstOrNull { it.isPaused() && it.metadata != null }
        current = controller
        val meta = controller?.metadata
        _state.value = if (controller == null || meta == null) {
            null
        } else {
            MediaInfo(
                title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
                artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                playing = controller.isPlaying(),
                packageName = controller.packageName,
            ).takeIf { it.title.isNotBlank() }
        }
    }

    fun playPause() {
        val c = current ?: return
        if (c.isPlaying()) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        current?.transportControls?.skipToPrevious()
    }

    fun open(context: Context) {
        val c = current ?: return
        val pi = c.sessionActivity
        if (pi != null) {
            try {
                val options = if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else {
                    null
                }
                pi.send(context, 0, null, null, null, null, options)
                return
            } catch (_: Exception) {
            }
        }
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            dev.minimal.launcher.util.SystemActions.start(context, it)
        }
    }
}
