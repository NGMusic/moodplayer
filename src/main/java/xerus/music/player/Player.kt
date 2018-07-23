package xerus.music.player

import javafx.beans.property.FloatProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import mu.KotlinLogging
import xerus.ktutil.javafx.checkFx
import xerus.music.Natives
import xerus.music.Settings
import xerus.music.library.Library
import xerus.music.library.Song

// volume functions: 1 - log(100-x)/log(100), (x/100)^4, exp(6.9*x/100)/1000, (x/100)^2

object Player {
    private val logger = KotlinLogging.logger {}

    val player: GeneralPlayer

    private val volumeDif = 0.05f
    private val volumeStart = Settings.VOLUME.get().toFloat()

    init {
        try {
            player = Class.forName("xerus.music.player.NativePlayer").newInstance() as GeneralPlayer
        } catch (e: Exception) {
            throw RuntimeException("Couldn't create player!", e)
        }
        logger.debug("Loaded volume $volumeStart")
    }

    val curSong: ObjectProperty<Song> = object : SimpleObjectProperty<Song>() {
        override fun set(nv: Song?) {
            val ov = get()
            if (nv == null && ov == null) return

            if (ov != null && Library.ratingsEnabled) {
                SongHistory.addSong(ov, if (player.totalMillis.get() == 0) 0f else player.playedMillis.get().toFloat() / player.totalMillis.get(), nv != null)
            } else {
                SongHistory.generateNextSong()
            }

            if (nv == null) {
                player.stop()
                logger.debug("Player stopping")
            } else {
                player.playSong(nv)
                logger.debug("New current Song: " + nv.verboseString())
            }
            checkFx {
                super.set(nv)
                if (nv != null)
                    setPlayerVolume()
            }
        }
    }
    val nextSong: ObjectProperty<Song?> = object : SimpleObjectProperty<Song?>() {
        override fun set(newValue: Song?) {
            checkFx { super.set(newValue) }
        }
    }
    val volumeCur: FloatProperty = object : SimpleFloatProperty(volumeStart) {
        override fun set(nv: Float) {
            if (nv == value)
                return
            super.set(nv.coerceIn(0f, 1f))
            if (curSong.get() != null)
                setPlayerVolume()
        }
    }

    val isPlaying: Boolean
        get() = player.playing.get()

    fun setPlayerVolume() {
        var volume = Math.pow(volumeCur.get().toDouble(), 2.0).toFloat() * curSong.get().gain
        volume *= curSong.get().gain
        player.setVolume(volume)
    }

    fun playPause() {
        play(!isPlaying)
    }

    fun play(play: Boolean) {
        if (play && curSong.get() == null)
            playNext()
        else
            player.playing.set(play)
    }

    fun playNext() {
        if (nextSong.get() != null) {
            curSong.set(nextSong.get())
            nextSong.set(null)
        }
    }

    fun adjustVolume(dif: Float): Boolean {
        val cur = volumeCur.get()
        if (cur >= 1 && dif > 0 || cur <= 0 && dif < 0)
            return false
        volumeCur.set(cur + dif)
        return true
    }

    fun adjustVolume(increase: Boolean): Boolean {
        return adjustVolume(if (increase) volumeDif else -volumeDif)
    }

    fun handleVolumeButtons(event: KeyEvent) {
        val adjustment = when(event.code) {
            KeyCode.VOLUME_UP -> true
            KeyCode.VOLUME_DOWN -> false
            else -> return
        }
        val handled = adjustVolume(adjustment) || Natives.instance.adjustVolume(true)
        if (handled)
            event.consume()
    }

}
