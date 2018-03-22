package xerus.music.library

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import xerus.ktutil.XerusLogger
import xerus.ktutil.nullIfEmpty
import xerus.music.isDesktop
import java.io.File
import java.util.*


private fun AudioFile.get(key: Key): String? = key.get(this).nullIfEmpty()

class Metadata(val id: Int) {
	
	val map = IdentityHashMap<Key, String>()
	
	var modified = false
	
	fun read(audioFile: AudioFile) {
		for (key in Key.values)
			audioFile.get(key)?.let { set(key, it) }
	}
	
	private operator fun set(key: Key, value: String) {
		map.put(key, value)
	}
	
	operator fun get(key: Key) = map[key]
	
	companion object {
		
		fun read(file: File): Metadata {
			val audioFile =
					(if (isDesktop)
						try {
							AudioFileIO.read(file)
						} catch (e: Exception) {
							null
						}
					else
						null) ?: return Metadata(Library.nextID)
			
			val tag = audioFile.tagOrCreateAndSetDefault
			val id = if (tag?.hasField(FieldKey.CUSTOM4) == true) {
				Integer.parseInt(tag.getFirst(FieldKey.CUSTOM4))
			} else {
				Library.nextID.also {
					if (ENABLETAGGING) {
						try {
							tag.setField(FieldKey.CUSTOM4, it.toString())
							audioFile.commit()
						} catch (e: Exception) {
							XerusLogger.throwing("Song", "Writing ID to Tag", e)
						}
					}
				}
			}
			
			val metadata = Metadata(id)
			metadata.read(audioFile)
			return metadata
		}
		
	}
	
}