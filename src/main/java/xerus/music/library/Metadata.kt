package xerus.music.library

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import xerus.ktutil.XerusLogger
import xerus.music.isDesktop
import java.io.File
import java.util.*

const val ENABLETAGGING = true

private fun <T> AudioFile.get(key: Key<T>): T? = key.get(this).takeUnless { it is String && it.isEmpty() }

class Metadata(val id: Int) {
	
	val map = IdentityHashMap<Key<*>, Any>()
	
	var modified = false
	
	fun read(audioFile: AudioFile) {
		for (key in Key.values)
			audioFile.get(key)?.let { set(key, it) }
	}
	
	private fun <T> set(key: Key<T>, value: T) =
			map.put(key, value)
	
	@Suppress("UNCHECKED_CAST")
	operator fun <T> get(key: Key<T>) =
			map[key] as T?
	
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