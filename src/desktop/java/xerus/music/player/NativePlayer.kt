package xerus.music.player

import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import xerus.ktutil.javafx.properties.dependOn
import xerus.music.library.Song
import java.io.File
import java.util.*

class NativePlayer : GeneralPlayer() {

    private var player: MediaPlayer? = null

    override fun loadSong(song: Song) {
        player?.dispose()
        player = MediaPlayer(Media(song.uri)).apply {
            setOnReady {
                playedMillis.dependOn(currentTimeProperty(), { it.toMillis() })
                totalMillis.set(stopTime.toMillis().toInt())
                for (consumer in playerListeners)
                    consumer.invoke(this)
            }
            setOnEndOfMedia { songFinished() }
        }
    }

    // Player listeners

    private val playerListeners = ArrayDeque<(MediaPlayer) -> Unit>(8)

    private fun addListener(listener: (MediaPlayer) -> Unit) {
        player?.apply(listener)
        playerListeners.add(listener)
    }

    private val repeater: (MediaPlayer) -> Unit = { it.cycleCount = Integer.MAX_VALUE; it.onEndOfMedia = null }
    override fun repeat(repeat: Boolean) {
        if (repeat) {
            addListener(repeater)
        } else {
            player?.setOnEndOfMedia { songFinished() }
            player?.cycleCount = 1
            playerListeners.remove(repeater)
        }
        println(player?.cycleCount)
    }

    // Basic functionality

    override fun setVolume(volume: Float) {
        player?.volume = volume.toDouble()
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun releasePlayer() {
        player?.dispose()
        player = null
    }

    override fun seek(millis: Int) {
        player?.seek(Duration.millis(millis.toDouble()))
    }

    override fun isPlayable(file: File): Boolean {
        if (arrayOf("mp3", "m4a", "wav", "aif", "aiff").contains(file.extension.toLowerCase()))
            return true
        return try {
            Media(file.toURI().toString())
            true
        } catch (e: Exception) {
            log("Not playable: $file")
            false
        }
    }

}
