package xerus.music.library

import javafx.application.Platform
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.SimpleRefresher
import xerus.ktutil.toByteArray
import xerus.ktutil.toInt
import xerus.music.Launcher
import xerus.music.Settings
import xerus.music.logger
import xerus.music.player.SongHistory
import xerus.music.view.SongViewer
import java.io.*
import java.lang.ref.WeakReference
import java.util.*

object Library {
	
	lateinit var path: File
		private set
	val cache: File
		get() = path.resolve(".lib").apply {
			if (!exists()) {
				mkdirs()
				if (System.getProperty("os.name").startsWith("Windows"))
					Runtime.getRuntime().exec("attrib +h " + this)
			}
		}
	
	val songs: Storage<Song> = Storage()
	
	// region Initialization
	
	var inited = false
	
	fun initialize(mainLib: String, vararg viewers: SongViewer) {
		inited = true
		working = true
		songs.clear()
		path = File(mainLib)
		ratingsPath = cache.resolve("ratings")
		ratingsBackup = cache.resolve(ratingsPath.name + ".bak")
		logger.config("Library initializing " + path)
		
		launch {
			addViews(*viewers)
			viewRefresher.refresh()
			if (ratingsEnabled)
				readRatings()
			if (path.exists() && path.canRead())
				addLibrary(path)
			else
				logger.warning("Library path $path not accessible")
		}
	}
	
	private var working = true
	private suspend fun addLibrary(path: File) {
		val queue = LinkedList<File>()
		queue.add(path)
		while (queue.isNotEmpty()) {
			val file = queue.poll()
			if (file.isDirectory) {
				logger.finest("Found directory $file")
				queue.addAll(file.listFiles())
			} else {
				addSong(file)
			}
		}
		working = false
		if (ratingsEnabled)
			SongHistory.createGenerator()
		else
			SongHistory.generateNextSong()
		launch {
			cache.resolve("songs").outputStream().bufferedWriter().use { file ->
				songs.forEach {
					file.write(it.id.toString())
				}
			}
		}
	}
	
	private fun addSong(file: File) {
		if (!arrayOf("mp3", "m4a", "wav").contains(file.extension.toLowerCase())) {
			return
			/*if (file.name[0] == '.' || arrayOf("jpg", "jpeg", "png", "db", "ini").contains(file.extension))
				return
			if (!Player.player.isPlayable(file))
				return*/
		}
		val song = Song(file)
		val id = song.id
		songs.add(id, song)
		if (id > lastID)
			lastID = id
		song.initRatings(lines[id])
		lines[id] = null
	}
	
	//endregion
	
	// region Views
	
	private val views = ArrayDeque<WeakReference<SongViewer>>()
	
	fun addViews(vararg viewers: SongViewer) {
		if (viewers.isEmpty())
			return
		viewers.mapTo(views) { WeakReference(it) }
		logger.fine("Added viewers: ${Arrays.toString(viewers)}")
		if (!inited)
			return
		if (working)
			viewRefresher.refresh()
		else
			for (viewer in viewers)
				populateView(viewer)
	}
	
	private val viewRefresher = SimpleRefresher {
		while (working)
			delay(200)
		System.gc()
		views.removeIf { it.get() == null }
		logger.config(String.format("Populating %s views with %s Songs", views.size, songs.trueSize))
		views.forEach { populateView(it.get()) }
	}
	
	private fun populateView(viewer: SongViewer?) {
		Platform.runLater { viewer?.populate(songs) }
	}
	
	//endregion
	
	// region Ratings
	
	var ratingsEnabled = Settings.ENABLERATINGS.getBool()
		set(enable) {
			field = enable
			Settings.ENABLERATINGS.put(enable)
			if (enable)
				launch {
					if (readRatings()) {
						for (i in lines.indices) {
							val s = songs[i]
							s?.initRatings(lines[i])
						}
						lines.clear()
					}
				}
		}
	
	private val version = 1
	private var lines: Storage<ByteArray> = Storage()
	private lateinit var ratingsPath: File
	private lateinit var ratingsBackup: File
	
	private val lineSeparator = 0
	
	private suspend fun readRatings(): Boolean {
		for (path in arrayOf(ratingsPath, ratingsBackup)) {
			if (!path.exists())
				continue
			try {
				FileInputStream(path).buffered().use { file ->
					@Suppress("UNUSED_VARIABLE")
					val version = file.read()
					lastID = file.readBytes(4).toInt()
					
					while (true) {
						val line = readLine(file) ?: break
						lines.add(line)
					}
				}
				logger.fine("Library read ratings from file")
				return true
			} catch (e: Exception) {
				lines.clear()
				logger.throwing("Library", "readRatings", e)
			}
		}
		return false
	}
	
	var lastSize = 128
	private fun readLine(inStream: InputStream): ByteArray? {
		var next = inStream.read()
		if (next == -1)
			return null
		val bytes = ByteArrayOutputStream(lastSize)
		while (next != lineSeparator) {
			bytes.write(next)
			next = inStream.read()
		}
		lastSize = lastSize.coerceAtLeast(bytes.size() + 1)
		return bytes.toByteArray()
	}
	
	fun writeRatings() {
		if (!ratingsEnabled || !inited)
			return
		launch {
			while (working)
				delay(300)
			logger.fine("Library is writing ratings to file...")
			if (ratingsPath.exists()) {
				if (ratingsBackup.exists())
					ratingsBackup.delete()
				ratingsPath.renameTo(ratingsBackup)
			}
			try {
				FileOutputStream(ratingsPath).buffered().use { out ->
					out.write(version)
					out.write(lastID.toByteArray())
					for (i in 0 until Math.max(songs.size, lines.size)) {
						songs[i]?.run { lines[i] = serialiseRatings() }
						lines[i]?.let { out.write(it) }
						out.write(lineSeparator)
					}
				}
				logger.config("Ratings written to " + ratingsPath)
			} catch (e: IOException) {
				Launcher.showError("Couldn't write database to " + ratingsPath, e)
			}
		}
	}
	
	//endregion
	
	private var lastID = -1
	val nextID: Int
		get() = ++lastID
	
	fun getSong(id: Int): Song? = songs[id]
	
}
