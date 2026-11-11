package com.kinetic.app.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.kinetic.app.model.Layer
import com.kinetic.app.model.LayerType

/**
 * Timeline-aware audio transport.
 *
 * ExoPlayer owns decoding and buffering. The editor only changes the requested
 * transport position when a clip starts or when drift becomes large; it does
 * not issue a seek on every UI frame.
 */
class AudioEngine(context: Context) {
    private val appContext = context.applicationContext
    private val players = HashMap<String, ExoPlayer>()
    private var released = false

    fun prepare(layers: List<Layer>) {
        if (released) return
        for (layer in layers) {
            val uri = layer.uri ?: continue
            if (layer.type != LayerType.AUDIO && layer.type != LayerType.VIDEO) continue
            if (players.containsKey(layer.id)) continue
            players[layer.id] = createPlayer(uri)
        }
    }

    fun sync(layers: List<Layer>, time: Float, playing: Boolean) {
        if (released) return

        val activeIds = HashSet<String>()
        for (layer in layers) {
            val uri = layer.uri ?: continue
            if (layer.type != LayerType.AUDIO && layer.type != LayerType.VIDEO) continue
            activeIds.add(layer.id)

            val shouldPlay = playing && layer.visible && layer.activeAt(time)
            var player = players[layer.id]
            if (player == null && shouldPlay) {
                player = createPlayer(uri)
                players[layer.id] = player
                player.seekTo(sourcePositionMs(layer, time))
                player.playWhenReady = true
                continue
            }
            if (player == null) continue

            if (!shouldPlay) {
                if (player.playWhenReady) player.playWhenReady = false
                continue
            }

            player.setPlaybackSpeed(layer.speed.coerceIn(0.1f, 4f))
            player.volume = layer.volume.coerceIn(0f, 2f)
            val targetMs = sourcePositionMs(layer, time)
            if (!player.playWhenReady) {
                // Seek before resuming so clips entered mid-source never begin at zero.
                player.seekTo(targetMs)
                player.playWhenReady = true
            }
            val currentMs = player.currentPosition
            if (player.playbackState == androidx.media3.common.Player.STATE_READY &&
                kotlin.math.abs(currentMs - targetMs) > DRIFT_CORRECTION_MS
            ) {
                player.seekTo(targetMs)
            }
        }

        val stale = players.keys.filter { it !in activeIds }
        for (id in stale) {
            players.remove(id)?.release()
        }
    }

    private fun createPlayer(uri: String): ExoPlayer {
        return ExoPlayer.Builder(appContext).build().apply {
            trackSelectionParameters = TrackSelectionParameters.Builder(appContext)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            volume = 1f
            prepare()
        }
    }

    private fun sourcePositionMs(layer: Layer, time: Float): Long =
        (((time - layer.start).coerceAtLeast(0f) * layer.speed + layer.srcOffset).coerceAtLeast(0f) * 1000f).toLong()

    fun pauseAll() {
        for (player in players.values) {
            player.playWhenReady = false
        }
    }

    fun release() {
        if (released) return
        released = true
        for (player in players.values) player.release()
        players.clear()
    }

    companion object {
        private const val DRIFT_CORRECTION_MS = 750L
    }
}
