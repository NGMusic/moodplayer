package xerus.mpris

import org.freedesktop.dbus.Variant
import org.jaudiotagger.tag.FieldKey
import org.mpris.MediaPlayer2.LoopStatus
import org.mpris.MediaPlayer2.PlaybackStatus
import xerus.ktutil.javafx.properties.addListener
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.music.TITLE
import xerus.music.musicPlayer
import xerus.music.player.Player

operator fun <K, V> HashMap<K, V>.set(k: K, value: V) = put(k, value)

class MPRISPlayer : AbstractMPRISPlayer() {
    override var rate by DBusProperty(1.0)
    override var loopStatus = LoopStatus.None
    override var shuffle by DBusProperty(true)
    override var metadata: Map<String, Variant<*>> by DBusProperty(HashMap())
    override var volume by DBusProperty(1.0) {
        Player.volumeCur.set(it.toFloat())
    }
    override var position by DBusProperty(0L)

    override val minimumRate by DBusConstant(1.0)
    override val maximumRate by DBusConstant(1.0)

    override val canGoNext by DBusConstant(true)
    override val canGoPrevious by DBusConstant(false)
    override val canPlay by DBusConstant(true)
    override val canPause by DBusConstant(true)
    override val canSeek by DBusConstant(true)
    override val canControl by DBusConstant(true)
    override val canQuit by DBusConstant(true)
    override val canSetFullscreen by DBusConstant(true)
    override val canRaise by DBusConstant(true)

    override val supportedUriSchemes by DBusConstant(arrayOf("file"))
    override val supportedMimeTypes by DBusConstant(arrayOf("audio/mpeg", "audio/x-wav", "audio/x-aiff", "audio/mp4", "video/mp4", "audio/x-m4a", "video/x-m4v", "video/x-javafx", "video/x-flv", "application/vnd.apple.mpegurl", "audio/mpegurl"))

    override var fullscreen by DBusProperty(App.stage.isFullScreen) {
        App.stage.isFullScreen = it
    }
    override val identity by DBusProperty(TITLE)
    override val desktopEntry = ""

    override var playbackStatus by DBusProperty(PlaybackStatus.Stopped)

    init {

        Player.player.playedMillis.listen {
            position = it.toLong() * 1000
        }

        arrayOf(Player.curSong, Player.player.playing).addListener {
            playbackStatus = when {
                Player.curSong.get() == null -> PlaybackStatus.Stopped
                Player.player.playing.get() -> PlaybackStatus.Playing
                else -> PlaybackStatus.Paused
            }
        }

        Player.curSong.listen { song ->
            if (song == null)
                metadata = emptyMap()
            metadata = PropertyMap {
                put("mpris:trackid", "/$TITLE/songs/" + song.toString().replace(Regex("\\W"), ""))
                put("mpris:length", Player.player.totalMillis.get() * 1000)
                put("xesam:artist", song.artist ?: "")
                put("xesam:title", song.title.orEmpty())
            }
        }
    }

    override fun PlayPause() =
            musicPlayer.playPause()

    override fun Play() =
            musicPlayer.play()

    override fun Pause() =
            musicPlayer.pause()

    override fun Stop() =
            musicPlayer.stop()

    override fun Seek(x: Long) =
            Player.player.seek(Player.player.playedMillis.get() + x.div(1000).toInt())

    override fun Previous() {
        println("Previous requested")
    }

    override fun Next() =
            musicPlayer.skip()

    override fun Raise() =
            App.stage.requestFocus()

    override fun Quit() {
        connection.disconnect()
        musicPlayer.quit()
    }

    override fun OpenUri(uri: String) {
        TODO("OpenUri called with $uri")
    }

    override fun getObjectPath() = "/org/mpris/MediaPlayer2"

}