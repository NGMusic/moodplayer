package xerus.music

import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser

import java.io.File

class Native : Natives() {

    public override fun showNativeFileChooser(title: String, origin: File, extension: String): File? =
            if (extension == "directory") {
                val chooser = DirectoryChooser()
                chooser.initialDirectory = origin
                chooser.title = title
                chooser.showDialog(stage)
            } else {
                val chooser = FileChooser()
                chooser.initialDirectory = origin
                chooser.extensionFilters.add(FileChooser.ExtensionFilter(extension.toUpperCase(), "*." + extension))
                chooser.title = title
                chooser.showOpenDialog(stage)
            }

}
