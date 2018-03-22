package xerus.music.library

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import xerus.ktutil.XerusLogger
import java.util.regex.Pattern

val gainPattern: Pattern = Pattern.compile("-?[.\\d]+")
val Tag.trackGain
	get() = run {
		var trackGain: Double? = null
		for (field in fields) {
			if ((field.id + field.toString()).contains("replaygain_track_gain")) {
				val matcher = gainPattern.matcher(field.toString())
				if (matcher.find())
					trackGain = matcher.group().toDouble()
				else
					XerusLogger.finer("Couldn't extract ReplayGain of Tag ${field.id}$field")
				break
			}
		}
		trackGain?.toGain()
	}

private fun Double.toGain() = Math.pow(10.0, this / 20).toFloat()
val DEFAULTGAIN = (-8.0).toGain()

const val DEFAULTRATING = 6f

open class Key<out T>(private val function: (AudioFile) -> T?) {
	
	init {
		values.add(this)
	}
	
	fun get(audioFile: AudioFile) = function(audioFile)
	
	companion object {
		val values = ArrayList<Key<*>>()
		val tags = ArrayList<TagKey>()
		
		val ARTIST = TagKey(FieldKey.ARTIST, { it.tag.getAll(FieldKey.ARTIST).joinToString(", ") })
		val TITLE = TagKey(FieldKey.TITLE)
		val ALBUM = TagKey(FieldKey.ALBUM)
		val GENRE = TagKey(FieldKey.GENRE)
		val BPM = TagKey(FieldKey.BPM)
		val TRACKGAIN = Key { it.tag.trackGain }
		val LENGTH = headerKey { it.trackLength }
		val BITRATE = headerKey { it.bitRateAsNumber }
	}
}

class TagKey(val fieldKey: FieldKey, function: (AudioFile) -> String = { it.tag.getFirst(fieldKey) }) : Key<String>(function) {
	
	init {
		Key.tags.add(this)
	}
	
	fun set(audioFile: AudioFile, value: String, commit: Boolean = true) {
		audioFile.tag.setField(fieldKey, value)
		if (commit)
			audioFile.commit()
	}
	
}

inline fun <T> headerKey(crossinline function: (AudioHeader) -> T) = Key({ function(it.audioHeader) })