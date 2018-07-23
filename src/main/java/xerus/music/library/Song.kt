package xerus.music.library

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.paint.Color
import mu.KotlinLogging
import xerus.ktutil.ifNull
import xerus.ratings.AbstractRatingsHelper
import xerus.ratings.Rating
import xerus.ratings.Ratings
import java.io.File
import kotlin.reflect.KProperty1

var brightness: Double = 0.0
fun hsv(hue: Double): Color = Color.hsb(hue, 0.6, brightness)

private val logger = KotlinLogging.logger {}

class Song(filename: String) {
	
	constructor(file: Any) : this(file.toString())
	
	val file = File(filename)
	lateinit var tags: Metadata
	lateinit var ratings: Ratings
	
	val id
		get() = tags.id
	
	// region Tags
	
	val gain: Float
		get() = tags[Key.TRACKGAIN] ?: DEFAULTGAIN
	val artist
		get() = tags[Key.ARTIST]
	val title
		get() = tags[Key.TITLE]
	val album
		get() = tags[Key.ALBUM]
	val genre
		get() = tags[Key.GENRE]
	val bpm
		get() = tags[Key.BPM]?.let { it.toIntOrNull().ifNull { logger.warn("Invalid BPM: \"$it\"") } }
	
	val name = file.name
	
	val uri
		get() = file.toURI().toString()
	
	val color: ObjectProperty<Color> = SimpleObjectProperty<Color>()
	
	fun computeColor(prob: Double) = color.set(hsv(prob * 8))
	
	fun matches(filter: String): Boolean =
			arrayOf(name, artist, title, album, genre).any { it?.contains(filter, true) == true }
	
	fun initRatings(line: ByteArray?) {
		if(line != null)
			ratings.deserialize(line)
	}
	
	/** returns "[artist] - [title]" if present or alternatively [nameWithoutExtension] */
	override fun toString(): String = if (artist != null && title != null) "$artist - $title" else file.nameWithoutExtension
	
	fun verboseString() = "Song $name, ID: $id, Rating: ${ratings.getRating()}"
	
	override fun hashCode() = toString().hashCode()
	fun compareTo(other: Song): Int = toString().compareTo(other.toString())
	
}

class RatingsHelper : AbstractRatingsHelper() {
	
	init {
		instance = this
	}
	
	override val defaultRating = 8.0f
	
	override fun calculateRating(r1: Ratings, r2: Ratings): Rating {
		val s1 = Library.getSong(r1.id) ?: return defaultRating
		val s2 = Library.getSong(r2.id) ?: return defaultRating
		return RatingComparators.get(s1, s2)
	}
	
	override fun getRatableById(id: Int): Ratings? =
			Library.getSong(id)?.ratings
	
}

private enum class RatingComparators(val bonus: Float, val field: KProperty1<Song, String?>, val comp: (String, String) -> Boolean = { s1, s2 -> s1.equals(s2, true) }) {
	GENRE(1f, Song::genre),
	ARTISTCONTAINS(1f, Song::artist, { s1, s2 -> if (s1.length > s2.length) s1.contains(s2) else s2.contains(s1) }),
	ARTIST(1f, Song::artist),
	ALBUM(1f, Song::album);
	
	val condition: (Song, Song) -> Boolean
	
	init {
		condition = { song1, song2 ->
			val s1 = field.get(song1)
			val s2 = field.get(song2)
			if (s1 == null || s2 == null)
				false
			else
				comp(s1, s2)
		}
	}
	
	companion object {
		fun get(s1: Song, s2: Song): Float {
			return AbstractRatingsHelper.instance.defaultRating + values()
					.filter { it.condition.invoke(s1, s2) }
					.map { it.bonus }
					.sum()
		}
	}
	
}