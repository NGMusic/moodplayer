package xerus.music

import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import xerus.mpris.MPRISPlayer
import java.io.File

class Native : Natives() {

    override fun showNativeFileChooser(title: String, origin: File, extension: String): File? =
            if (extension == "directory") {
                val chooser = DirectoryChooser()
                chooser.initialDirectory = origin
                chooser.title = title
                chooser.showDialog(stage)
            } else {
                val chooser = FileChooser()
                chooser.initialDirectory = origin
                chooser.extensionFilters.add(FileChooser.ExtensionFilter(extension.toUpperCase(), "*.$extension"))
                chooser.title = title
                chooser.showOpenDialog(stage)
            }
    
    override fun adjustVolume(increase: Boolean): Boolean {
        return false
    }

    override fun init() {
        if (System.getProperty("os.name").startsWith("linux", true))
            MPRISPlayer()
    }

}
