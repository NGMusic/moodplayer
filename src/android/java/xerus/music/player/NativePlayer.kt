package xerus.music.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.music.library.Song
import java.io.File
import java.util.logging.Level


class NativePlayer : GeneralPlayer() {

    var player: MediaPlayer = MediaPlayer()

    init {
        player.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
        player.setOnErrorListener { _, errorType, extra -> log("Android Mediaplayer Error: $errorType $extra", Level.WARNING); true }
        player.setOnCompletionListener { songFinished() }
        player.setOnPreparedListener { totalMillis.set(it.duration) }
        launch {
            while (true) {
                playedMillis.set(player.currentPosition)
                delay(200)
            }
        }
        /*
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 20, 0)
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        */
    }

    override fun play() = player.start()
    override fun pause() = player.pause()

    override fun loadSong(song: Song) {
        releasePlayer()
        player.setDataSource(song.source)
        player.prepare()
    }

    override fun setVolume(volume: Float) = player.setVolume(volume, volume)

    override fun releasePlayer() {
        player.stop()
        player.reset()
    }

    override fun seek(millis: Int) = player.seekTo(millis)

    override fun repeat(repeat: Boolean) = player.setLooping(repeat)

    override fun isPlayable(file: File): Boolean {
        if(arrayOf("mp3", "m4a", "wav", "mkv", "flac").contains(file.extension.toLowerCase()))
            return true
        return try {
            player.setDataSource(file.toURI().toString())
            player.reset()
            true
        } catch (e: Exception) {
            log("Not playable: $file")
            false
        }
    }

}