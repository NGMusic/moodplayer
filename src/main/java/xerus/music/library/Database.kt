package xerus.music.library

import xerus.music.Settings
import java.io.File

object Database {
	
	val defaultDatabase
		get() = File(Settings.LIBRARY.get()).resolve("database")
	
	fun read(file: File = defaultDatabase) {
		
		file.inputStream().bufferedReader().use {
			val version = it.readLine()
			
			it.readLine()
		}
	}
	
	
}