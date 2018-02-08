package xerus.music.player

import javafx.util.Duration
import xerus.music.library.Song
import java.io.File


/** interface for music playing functionality */
interface IPlayer {

    fun getCurrentSong(): Song?

    fun isPlaying(): Boolean
    val isReady: Boolean

    fun setVolume(vol: Int)
    fun adjustVolume(dif: Int): Boolean
    fun increaseVolume() = adjustVolume(volumeDif)
    fun decreaseVolume() = adjustVolume(-volumeDif)

    fun play(song: Song)
    fun play()
    fun pause()
    fun stop()

    fun playPause() {
        if (isPlaying())
            pause()
        else
            play()
    }

    fun seek(time: Duration)
    fun repeat(repeat: Boolean)

    fun isPlayable(file: File): Boolean

}
