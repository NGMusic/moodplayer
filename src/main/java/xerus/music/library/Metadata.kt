package xerus.music.library

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import xerus.ktutil.XerusLogger
import xerus.ktutil.nullIfEmpty
import xerus.music.isDesktop
import java.io.File
import java.util.*
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

private fun Tag.get(key: TagKey): String? = (key.supplier ?: { it.getFirst(FieldKey.valueOf(key.name)) }).invoke(this).nullIfEmpty()

enum class TagKey(val supplier: ((Tag) -> String?)? = null) {
    ARTIST({ it.getAll(FieldKey.ARTIST).joinToString(", ") }),
    TITLE, ALBUM, GENRE, BPM({ it.getFirst(FieldKey.BPM).toIntOrNull()?.toString() }),
    TRACKGAIN({ it.trackGain?.toString() });

}

class Metadata(val id: Int) {

    val map = EnumMap<TagKey, String>(TagKey::class.java)

    fun read(tag: Tag) {
        for (key in TagKey.values())
            tag.get(key)?.let { set(key, it) }
    }

    fun read(header: AudioHeader) {
        header.trackLength
    }

    operator fun set(key: TagKey, value: String) = map.put(key, value)

    operator fun get(key: TagKey) = map[key]

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
            if (tag?.isEmpty == false)
                metadata.read(tag)
            return metadata
        }

    }

}