package xerus.music.library

import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.SimpleRefresher
import xerus.ktutil.helpers.WeakCollection
import xerus.ktutil.javafx.onJFX
import xerus.ktutil.toByteArray
import xerus.ktutil.toInt
import xerus.music.Launcher
import xerus.music.Settings
import xerus.music.logger
import xerus.music.player.SongHistory
import xerus.music.view.SongViewer
import java.io.*
import java.util.*

object Library {
	
	lateinit var main: File
		private set
	val cache: File
		get() = main.resolve(".lib").apply {
			if (!exists()) {
				mkdirs()
				if (System.getProperty("os.name").startsWith("Windows"))
					Runtime.getRuntime().exec("attrib +h " + this)
			}
		}
	
	private val _songs: Storage<Song> = Storage()
	val songsRaw = FXCollections.observableArrayList<Song>()
	val songs = FilteredList<Song>(songsRaw)
	
	// region Initialization
	
	var inited = false
	
	fun initialize(mainLib: String, vararg viewers: SongViewer) {
		inited = true
		working = true
		_songs.clear()
		songs.clear()
		main = File(mainLib)
		ratingsPath = cache.resolve("ratings")
		ratingsBackup = cache.resolve(ratingsPath.name + ".bak")
		logger.config("Library initializing " + mainLib)
		
		launch {
			addViews(*viewers)
			viewRefresher.refresh()
			if (ratingsEnabled)
				readRatings()
			if (main.exists() && main.canRead())
				addLibrary(main)
			else
				logger.warning("Library path $main not accessible")
		}
	}
	
	private var working = true
	suspend fun addLibrary(path: File) {
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
		songsRaw.setAll(_songs)
		working = false
		if (ratingsEnabled)
			SongHistory.createGenerator()
		else
			SongHistory.generateNextSong()
		launch {
			logger.finer("Writing song cache with ${_songs.size}")
			cache.resolve("songs").outputStream().bufferedWriter().use { file ->
				_songs.forEach {
					file.write(it.id.toString() + " " + it.file.relativeTo(main))
				}
			}
		}
		logger.finer("Library $path added")
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
		_songs.add(id, song)
		if (id > lastID)
			lastID = id
		song.initRatings(lines[id])
		lines[id] = null
	}
	
	//endregion
	
	// region Views
	
	private val views = WeakCollection<SongViewer>()
	
	fun addViews(vararg viewers: SongViewer) {
		if (viewers.isEmpty())
			return
		views.addAll(viewers)
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
		views.clean(2)
		logger.config(String.format("Populating %s views with %s Songs", views.size, _songs.trueSize))
		views.forEach { populateView(it) }
	}
	
	private fun populateView(viewer: SongViewer) {
		onJFX { viewer.populate(_songs) }
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
							val s = _songs[i]
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
					for (i in 0 until Math.max(_songs.size, lines.size)) {
						_songs[i]?.run { lines[i] = serialiseRatings() }
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
	
	fun getSong(id: Int): Song? = _songs[id]
	
}
