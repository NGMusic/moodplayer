package xerus.music.player

import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import xerus.music.library.Song
import xerus.music.logger
import java.io.File
import java.util.logging.Level

abstract class GeneralPlayer {
	
	val playedMillis = SimpleIntegerProperty()
	val totalMillis = SimpleIntegerProperty()
	
	val playing: BooleanProperty = object : SimpleBooleanProperty(false) {
		override fun set(nv: Boolean) {
			if (nv)
				play()
			else
				pause()
			super.set(nv)
		}
	}
	
	abstract fun isPlayable(file: File): Boolean
	
	abstract fun setVolume(volume: Float)
	
	fun playSong(song: Song) {
		loadSong(song)
		playing.set(true)
	}
	
	protected abstract fun loadSong(song: Song)
	protected abstract fun play()
	protected abstract fun pause()
	fun stop() {
		playing.set(false)
		releasePlayer()
		playedMillis.set(0)
		totalMillis.set(0)
	}
	
	fun songFinished() {
		log("Autoplay triggered")
		Player.playNext()
	}
	
	protected abstract fun releasePlayer()
	
	abstract fun seek(millis: Int)
	abstract fun repeat(repeat: Boolean)
	
	fun log(msg: String, level: Level = Level.FINER) = logger.log(level, "PLAYER " + msg)
	
}
