package xerus.music.library

import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.paint.Color
import xerus.music.library.TagKey.*
import xerus.music.logger
import java.io.File

const val ENABLETAGGING = true
const val DEFAULTGAIN = -8.0
const val DEFAULTRATING = 6f

var brightness: Double = 0.0
fun hsv(hue: Double): Color = Color.hsb(hue, 0.6, brightness)


class Song(filename: String) {

    constructor(file: Any) : this(file.toString())

    val file = File(filename)
    lateinit var tags: Metadata

    val id
        get() = tags.id

    // region Tags

    val gain: Float?
        get() = tags[TRACKGAIN]?.toFloat()
    val artist: String?
        get() = tags[ARTIST]
    val title: String?
        get() = tags[TITLE]
    val album: String?
        get() = tags[ALBUM]
    val genre: String?
        get() = tags[GENRE]
    val bpm: Int?
        get() = tags[BPM]?.toInt()

    val name = file.name

    //endregion

    // region Ratings

    private val ratings: Storage<MutableFloat> = Storage(MutableFloat(DEFAULTRATING))

    fun getRating(): Float = ratings.getOrDefault(id).toFloat()
    fun getRating(id: Int): Float = getRatingInternal(id).toFloat()

    private fun getRatingInternal(id: Int): MutableFloat = ratings.getOptional(id).orElseGet {
        val other = Library.getSong(id)
        val rating = MutableFloat(if (other != null) Rating.get(this, other) else DEFAULTRATING)
        other?.ratings?.set(id, rating)
        ratings[id] = rating
        rating
    }

    private fun editRating(id: Int, change: Float) {
        val f = getRatingInternal(id)
        // apply a function to softly cap off the sides
        val temp = f.toFloat() / 8 - 1
        f.add((-temp * temp + 1) * change)
    }

    fun updateRating(s: Song, change: Float) {
        editRating(s.id, change)
        s.editRating(id, change / 2)
        logger.finer("$this updated Rating to $s by $change")
    }

    fun updateRating(change: Float) {
        editRating(id, change)
        logger.finer("$this updated Rating by $change")
    }

    fun initRatings(line: ByteArray?) {
        if (line == null)
            return
        ratings.ensureCapacity(line.size)
        line.mapTo(ratings) {
            MutableFloat((it.toInt() and 0x0FF) / 16f)
        }
    }

    fun serialiseRatings(): ByteArray? {
        val res = ByteArray(ratings.size)
        for (i in ratings.indices)
            res[i] = (16 * getRating(i)).toByte()
        return res
    }

    //endregion

    val uri
        get() = file.toURI().toString()

    val color: ObjectProperty<Color> = SimpleObjectProperty<Color>()

    fun computeColor(prob: Double) = color.set(hsv(prob * 8))

    fun matches(filter: String): Boolean =
            arrayOf(name, artist, title, album, genre).any { it?.contains(filter, true) == true }

    /** returns "[artist] - [title]" if present or alternatively [nameWithoutExtension] */
    override fun toString(): String = if (artist != null && title != null) "$artist - $title" else file.nameWithoutExtension

    fun verboseString() = "Song $name, ID: $id, Rating: ${getRating()}"

    override fun hashCode() = toString().hashCode()
    fun compareTo(other: Song): Int = toString().compareTo(other.toString())

}


/*
interface Ratable {
	
	val id: Int
	val ratings: Storage<MutableFloat>

	fun getRating(): Float = ratings.getOrDefault(id).toFloat()
	fun getRating(id: Int): Float = getRatingInternal(id).toFloat()

	private fun getRatingInternal(id: Int): MutableFloat = ratings.getOptional(id).orElseGet {
		val other = Library.getSong(id)
		val rating = MutableFloat(if(other != null) Rating.get(this as Song, other as Song) else DEFAULTRATING)
		if(other != null) other.ratings.set(id, rating)
		ratings.set(id, rating)
		rating
	}

	private fun editRating(id: Int, change: Float) {
		val f = getRatingInternal(id)
		// apply a function to softly cap off the sides
		val temp = f.toFloat() / 8 - 1
		f.add((-temp * temp + 1) * change)
	}

	fun updateRating(s: Ratable, change: Float) {
		editRating(s.id, change)
		s.editRating(id, change / 2)
	}

	fun updateRating(change: Float) {
		editRating(id, change)
	}

	fun initRatings(line: ByteArray?) {
		if(line == null)
			return
		ratings.ensureCapacity(line.size)
		for (b in line)
			ratings.add(MutableFloat((b.toInt() and 0x0FF) / 16f))
	}

	fun serialiseRatings(): ByteArray? {
		val res = ByteArray(ratings.size)
		for (i in ratings.indices)
			res[i] = (16 * getRating(i)).toByte()
		return res
	}
}
*/

private enum class Rating(bonus: Float, methodName: String?, comp: (String, String) -> Boolean = String::equals) {
    GENRE(1f, "getGenre"),
    ARTISTCONTAINS(1f, "getArtist", { s1, s2 -> if (s1.length > s2.length) s1.contains(s2) else s2.contains(s1) }),
    ARTIST(1f, "getArtist"),
    ALBUM(1f, "getAlbum");

    private val condition: (Song, Song) -> Boolean
    private val bonus: Float

    init {
        try {
            val m = Song::class.java.getMethod(methodName)
            condition = { song1: Song, song2: Song ->
                val s1 = m.invoke(song1) as String?
                val s2 = m.invoke(song2) as String?
                s1 != null && s2 != null && comp(s1, s2)
            }
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }
        this.bonus = bonus
    }

    companion object {
        fun get(s1: Song, s2: Song): Float {
            return DEFAULTRATING + values()
                    .filter { it.condition.invoke(s1, s2) }
                    .map { it.bonus }
                    .sum()
        }
    }

}