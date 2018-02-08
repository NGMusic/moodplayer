package xerus.music.player

import javafx.beans.property.FloatProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleFloatProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import xerus.ktutil.javafx.checkJFX
import xerus.music.Settings
import xerus.music.library.Library
import xerus.music.library.Song
import xerus.music.logger

// volume functions: 1 - log(100-x)/log(100), (x/100)^4, exp(6.9*x/100)/1000, (x/100)^2

object Player {

    val player: GeneralPlayer

    private val volumeDif = 0.05f
    private val volumeStart = Settings.VOLUME.get().toFloat()
    init {
        try {
            player = Class.forName("xerus.music.player.NativePlayer").newInstance() as GeneralPlayer
        } catch (e: Exception) {
            throw RuntimeException("Couldn't create player!", e)
        }
        logger.fine("Loaded volume " + volumeStart)
    }

    val curSong: ObjectProperty<Song> = object : SimpleObjectProperty<Song>() {
        override fun set(nv: Song?) {
            val ov = get()
            if(nv == null && ov == null) return

            if (ov != null && Library.ratingsEnabled) {
                SongHistory.addSong(ov, if(player.totalMillis.get() == 0) 0f else player.playedMillis.get().toFloat() / player.totalMillis.get(), nv != null)
            } else {
                SongHistory.generateNextSong()
            }

            if (nv == null) {
                player.stop()
                logger.fine("Player stopping")
            } else {
                player.playSong(nv)
                logger.fine("New current Song: " + nv.verboseString())
            }
            checkJFX {
                super.set(nv)
                if (nv != null)
                    setPlayerVolume()
            }
        }
    }
    val nextSong: ObjectProperty<Song?> = object : SimpleObjectProperty<Song?>() {
        override fun set(newValue: Song?) {
            checkJFX { super.set(newValue) }
        }
    }
    val volumeCur: FloatProperty = object : SimpleFloatProperty(volumeStart) {
        override fun set(nv: Float) {
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
        volumeCur.setValue(cur + dif)
        return true
    }

    fun adjustVolume(increase: Boolean): Boolean {
        return adjustVolume(if (increase) volumeDif else -volumeDif)
    }

    fun handleVolumeButtons(event: KeyEvent) {
        var handled = false
        when (event.code) {
            KeyCode.VOLUME_UP -> handled = adjustVolume(true)
            KeyCode.VOLUME_DOWN -> handled = adjustVolume(false)
        }
        if (handled)
            event.consume()
        else {
            // TODO native android volume control
        }
    }

}
