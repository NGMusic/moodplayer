package xerus.music

import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.*
import xerus.ktutil.XerusLogger
import xerus.ktutil.javafx.StylingTools
import xerus.ktutil.javafx.onJFX
import xerus.ktutil.javafx.properties.UnmodifiableObservableList
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.ui.controls.LogTextArea
import xerus.ktutil.javafx.ui.controls.SlideBar
import xerus.music.library.Library
import xerus.music.library.Song
import xerus.music.player.Player
import xerus.music.player.Player.adjustVolume
import xerus.music.player.Player.curSong
import xerus.music.player.Player.isPlaying
import xerus.music.player.Player.nextSong
import xerus.music.player.Player.playNext
import xerus.music.player.Player.player
import xerus.music.player.Player.volumeCur
import xerus.music.player.SongHistory
import java.io.OutputStream
import java.lang.reflect.Method

lateinit var musicPlayer: MusicPlayer

class MusicPlayer {

    @FXML
    var playPause: ToggleButton? = null
    @FXML
    var volumeSlider: Slider? = null
    @FXML
    var seekSlider: SlideBar? = null
    @FXML
    var log: LogTextArea? = null
    @FXML
    var searchField: TextInputControl? = null
    @FXML
    var loglevel: ComboBox<String>? = null

    var repeatState: Boolean = false

    init {
        musicPlayer = this
    }

    @FXML
    fun initialize() {
        playPause?.run {
            isSelected = true
            player.playing.addListener { _ -> isSelected = !isPlaying }
        }
        volumeSlider?.run {
            max = 1.0
            value = volumeCur.get().toDouble()
            valueProperty().bind(volumeCur)
        }
        seekSlider?.slider?.run {
            isDisable = true
            disableProperty().dependOn(player.totalMillis, {
                max = it.toDouble()
                it == 0
            })
            var playerAdjusting = false
            var seeking = false
            player.playedMillis.addListener { _ ->
                logger.finest("playedMillis: " + player.playedMillis.get())
                if (!seeking) {
                    playerAdjusting = true
                    value = player.playedMillis.get().toDouble()
                    playerAdjusting = false
                }
            }
            setOnMousePressed { _ -> seeking = true }
            setOnMouseReleased { _ -> seeking = false; logger.finer("Stopped seeking") }
            valueProperty().addListener({ _, _, new ->
                if (!playerAdjusting && Math.abs(new.toInt() - player.playedMillis.get()) > 1000)
                    player.seek(new.toInt())
            })
        }
        log?.run {
            //SysoutListener.addObserver(log::appendText);
            XerusLogger.addOutputStream(object : OutputStream() {
                override fun write(b: Int) {
                    appendText(Character.toString(b.toChar()))
                }
            })
        }
        loglevel?.run {
            items = UnmodifiableObservableList("SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST")
            selectionModel.select(Settings.LOGLEVEL.get())
            setOnAction {
                XerusLogger(value)
            }
        }
        onJFX {
            for (name in arrayOf("enableRatings", "enableRatingColors")) {
                val nodes = StylingTools.find(root) { node -> name == node.id }
                if (nodes.isEmpty()) continue
                val node = nodes.iterator().next()

                val onAction: Method = try {
                    node.javaClass.getMethod("setOnAction", EventHandler::class.java)
                } catch (e: NoSuchMethodException) {
                    logger.warning(node.toString() + " has no onAction property: " + e.toString())
                    logger.throwing("MusicPlayer", "initialize", e)
                    continue
                }

                val setting = Settings.valueOf(name.toUpperCase())
                try {
                    val myMethod = javaClass.getMethod(name, ActionEvent::class.java)
                    onAction.invoke(node, myMethod::invoke)
                } catch (ignored: Exception) {
                    try {
                        onAction.invoke(node, EventHandler<ActionEvent> { evt -> setting.put(getSelected(evt)!!) })
                    } catch (e: Exception) {
                        logger.throwing("MusicPlayer", "initialize", e)
                    }
                }

                try {
                    val select = node.javaClass.getMethod("setSelected", Boolean::class.javaPrimitiveType)
                    val selected = setting.getBool()
                    select.invoke(node, selected)
                    logger.fine("Selectable $node set to $selected")
                } catch (e: Exception) {
                    logger.throwing("MusicPlayer", "initialize", e)
                }

            }
        }
    }

    @FXML
    fun playPause() {
        Player.playPause()
    }

    @FXML
    fun play() {
        Player.play(true)
    }

    @FXML
    fun pause() {
        Player.play(false)
    }

    @FXML
    fun stop() {
        curSong.set(null)
    }

    @FXML
    fun skip() {
        playNext()
    }

    fun skipTo(s: Song) {
        SongHistory.updateRatings(s, 0.3f, 0.2f)
        curSong.set(s)
    }

    @FXML
    fun repeat() {
        // todo save repeatState in player
        repeatState = !repeatState
        logger.fine("Repeat: " + repeatState)
        player.repeat(repeatState)
    }

    @FXML
    fun regenerateNextSong() {
        SongHistory.updateRatings(nextSong.get(), -0.3f, -0.2f)
        SongHistory.generateNextSong()
    }

    @FXML
    fun selectLibraryFolder() {
        selectLibrary(if (Library.inited) Library.main.toString() else Settings.LIBRARY.get())
    }

    @FXML
    fun enableRatings(evt: ActionEvent) {
        val result = getSelected(evt)
        if (result != null) {
            Library.ratingsEnabled = result
            SongHistory.createGenerator()
        }
    }

    @FXML
    fun like() {
        SongHistory.updateRatings(curSong.get(), 0.2f, 0.8f)
    }

    @FXML
    fun dislike() {
        SongHistory.updateRatings(curSong.get(), -0.2f, -0.8f)
    }

    @FXML
    fun saveRatings() {
        Library.writeRatings()
    }

    @FXML
    fun about() {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "About"
        alert.headerText = null
        alert.contentText = "My $TITLE (alpha)"
        alert.show()
    }

    @FXML
    fun restart() {
        quit()
        Launcher()
    }

    @FXML
    fun quit() {
        logger.info("Shutting down")
        if (stage.isShowing)
            stage.close()
        Library.writeRatings()
        player.stop()
        Settings.VOLUME.put(volumeCur.get())
        executor.shutdown()
        Platform.exit()
    }

    @FXML
    fun selectLayout() {
        val f = Natives.showFileChooser("Select Layout File", Settings.FXML.get(), "fxml")
        if (f != null) {
            Settings.FXML.put(f)
            Launcher.loadFXML(f.toString())
        }
    }

    @FXML
    fun selectCSS() {
        val f = Natives.showFileChooser("Select Style File", Settings.CSS.get(), "css")
        if (f != null) {
            Settings.CSS.put(f)
            Launcher.applyCSS()
        }
    }

    @FXML
    fun resetLayouts() {
        Settings.FXML.reset()
        Settings.CSS.reset()
        Launcher.loadFXML(null)
    }

    @FXML
    fun aboutLayouts() {
        val info = Alert(Alert.AlertType.NONE)
        info.title = "About Layouts"
        info.contentText = "FXML-files define the Layout of the application, every control and container and it's position. It also allows you to freely configure Views like the TableView\n" +
                "The Style of the application is defined by a *css* file. It controls the Color schemes and graphics.\n" +
                "Although most often a specific Layout has a matching style, these can be independent."
        info.buttonTypes.add(ButtonType.CLOSE)
        info.show()
    }

    @FXML
    fun increaseVolume() {
        adjustVolume(true)
    }

    @FXML
    fun decreaseVolume() {
        adjustVolume(false)
    }

    private fun getSelected(evt: ActionEvent): Boolean? {
        return getSelected(evt.source)
    }

    private fun getSelected(source: Any): Boolean? {
        try {
            val isSelected = source.javaClass.getDeclaredMethod("isSelected")
            return isSelected.invoke(source) as Boolean
        } catch (e: NoSuchMethodException) {
            logger.warning(source.toString() + " is not selectable!")
        } catch (e: Exception) {
            Launcher.showError("getSelected", e)
        }

        return null
    }

    fun selectLibrary(origin: String) {
        val selected = Natives.showFileChooser("Select your Library", origin, "directory")
        logger.finer("Selected $selected as new Library folder")
        if (selected != null) {
            Settings.LIBRARY.put(selected)
            Library.initialize(selected.toString())
        }
    }

}