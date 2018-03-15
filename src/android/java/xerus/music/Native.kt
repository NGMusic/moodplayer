package xerus.music

import javafxports.android.FXActivity

import java.io.File

class Native : Natives() {

    public override fun showNativeFileChooser(title: String, origin: File, extension: String): File? {
        val chooser = FileChooser(FXActivity.getInstance())
        chooser.currentPath = origin
        if (extension == "directory")
            chooser.asDirectoryChooser()
        else
            chooser.setExtension(extension)
        return chooser.showDialogAndWait()
    }
    
    override fun adjustVolume(increase: Boolean): Boolean {
        return false
    }

}
