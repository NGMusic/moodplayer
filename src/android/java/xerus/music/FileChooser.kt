package xerus.music

import android.app.Activity
import android.app.Dialog
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.util.*

private val PARENT_DIR = ".."

class FileChooser(private val activity: Activity) {

    private lateinit var listView: ListView
    private var dialog: Dialog? = null

    var currentPath: File? = null

    private var result: File? = null

    private var extension: String? = null

    private var isDirectoryChooser = false

    private var fileListener: FileSelectedListener? = null

    init {
        activity.runOnUiThread {
            dialog = Dialog(activity)
            listView = ListView(activity)
            listView.setOnItemClickListener { _, _, pos, _ ->
                val chosenFile = getChosenFile(listView.getItemAtPosition(pos) as String)
                if (chosenFile.isDirectory)
                    refresh(chosenFile)
                else
                    fileSelected(chosenFile)
            }
            dialog!!.setContentView(listView)
            dialog!!.window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }

    fun showDialog(fileListener: FileSelectedListener?) {
        if (currentPath == null || !currentPath!!.exists() || !currentPath!!.canRead())
            currentPath = Environment.getExternalStorageDirectory()
        this.fileListener = fileListener
        activity.runOnUiThread {
            refresh(currentPath!!)
            dialog!!.show()
        }
    }

    /**
     * shows the dialog and blocks until a file is selected or the dialog is closed
     * @return the selected file or null if the dialog was dismissed
     */
    fun showDialogAndWait(): File? {
        showDialog(null)
        try {
            while (dialog?.isShowing != true)
                Thread.sleep(200)
            while (dialog?.isShowing == true)
                Thread.sleep(200)
        } catch (e: InterruptedException) {
        }
        return result
    }

    // region Configuration

    fun setExtension(extension: String?) {
        this.extension = extension?.toLowerCase()
    }

    fun asDirectoryChooser() {
        isDirectoryChooser = true
        activity.runOnUiThread {
            listView.setOnItemLongClickListener { _, _, pos, _ ->
                val fileChosen = listView.getItemAtPosition(pos) as String
                val chosenFile = getChosenFile(fileChosen)
                fileSelected(chosenFile)
                true
            }
        }

    }

    private fun fileSelected(file: File) {
        if (fileListener != null)
            fileListener!!.fileSelected(file)
        else
            result = file
        dialog!!.dismiss()
    }

    interface FileSelectedListener {
        fun fileSelected(file: File)
    }

    //endregion

    // region Display

    /**
     * Sort, filter and display the files for the given path.
     */
    private fun refresh(path: File) {
        this.currentPath = path
        val fileList = ArrayList<String>()
        val parent = path.parentFile
        if (parent != null && (parent.parentFile != null || parent.listFiles().orEmpty().isNotEmpty()))
            fileList.add(PARENT_DIR)
        if (path.canRead()) {
            // find em all
            val dirs = TreeSet<String>()
            val files = TreeSet<String>()
            path.listFiles()
                    .forEach {
                        if (it.isDirectory) {
                            dirs.add(it.name)
                        } else {
                            println("${it.extension} und $extension")
                            if (!isDirectoryChooser && it.canRead() && (extension == null || it.extension.toLowerCase() == extension))
                                files.add(it.name)
                        }
                    }
            // add to ArrayList
            fileList.addAll(dirs)
            fileList.addAll(files)
        }
        // refresh the user interface
        dialog!!.setTitle(currentPath.toString())
        listView.adapter = MyAdapter(fileList)
    }

    private inner class MyAdapter<T> : ArrayAdapter<T> {

        internal constructor(vararg items: T) : super(activity, android.R.layout.simple_list_item_1, items)

        internal constructor(items: List<T>) : super(activity, android.R.layout.simple_list_item_1, items)

        override fun getView(pos: Int, view: View?, parent: ViewGroup): View {
            val textview = super.getView(pos, view, parent)
            (textview as TextView).setSingleLine(true)
            return textview
        }
    }

    //endregion

    /**
     * Convert a relative filename into an actual File object.
     */
    private fun getChosenFile(fileChosen: String): File =
            if (fileChosen == PARENT_DIR) currentPath!!.parentFile else File(currentPath, fileChosen)

}