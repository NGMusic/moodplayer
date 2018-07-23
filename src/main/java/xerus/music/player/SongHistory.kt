package xerus.music.player

import javafx.application.Platform
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.DistributedRandom
import xerus.music.library.Library
import xerus.music.library.Song
import xerus.music.logger
import java.util.*

object SongHistory {

    private val size = 10
    private var cursor = 0
    private val history = arrayOfNulls<PlayedSong>(size)

    private val nextGenerator = DistributedRandom<Song>()
    private var generating: Boolean = false

    class PlayedSong internal constructor(internal val song: Song, playedFraction: Float, skipped: Boolean) {
        private val timestamp = System.currentTimeMillis()

        /** the multiplier to apply when a new song is pushed into the history  */
        private var ratingMultiplier: Float = 0.toFloat()
        internal val multiplier: Float
            get() = timeDilation() * ratingMultiplier

        /**
         * the influence of this PlayedSong on future Song generations<br></br>
         * currently equal to [.ratingMultiplier]
         */
        private val ratingInfluence: Float
        internal var influence: Float = 0.toFloat()

        init {
            ratingMultiplier = when {
                playedFraction > 0.9 -> 1f
                skipped -> playedFraction - 0.6f
                else -> playedFraction / 2 - 0.2f
            }
            ratingInfluence = ratingMultiplier
            updateRatings(song, ratingMultiplier)
            // prevent duplicates
            (0 until size).forEach { if (history[it]?.song === song) history[it] = null }
        }

        fun getInfluence(): Float {
            influence = timeDilation() * ratingInfluence
            return influence
        }

        private fun timeDilation(): Float {
            return persistence / (System.currentTimeMillis() - timestamp + persistence)
        }

        internal fun getRating(s: Song): Float {
            return song.ratings.getRating(s.id) * influence
        }

        override fun toString(): String {
            return String.format("%s with an influence of %s", song, influence)
        }

        companion object {
            /** amount of milliseconds until the influence is halved  */
            private val persistence = 400_000f
        }
    }

    fun updateRatings(s: Song?, change: Float, vararg flatchange: Float) {
        if (noRatings() || s == null)
            return
        s.ratings.updateRating(if (flatchange.isNotEmpty()) change + flatchange[0] else change)
        history.filterNotNull().forEach {
            it.song.ratings.updateRating(s.ratings, it.multiplier * change)
        }
    }

    private fun noRatings() = !Library.ratingsEnabled

    fun generateNextSong() {
        if (generating || !Library.inited)
            return
        generating = true
        launch {
            var s: Song? = null
            if (noRatings()) {
                val size = Library.songs.size
                val rand = Random()
                while (s == null)
                    s = Library.getSong(rand.nextInt(size))
            } else
                s = nextGenerator.generate()
            val res = s
            Platform.runLater {
                Player.nextSong.set(res)
                generating = false
            }
        }
    }

    fun addSong(song: Song, playedFraction: Float, skipped: Boolean) {
        history[cursor++] = PlayedSong(song, playedFraction, skipped)
        if (cursor == 10)
            cursor = 0
        createGenerator()
    }

    fun createGenerator() {
        launch {
            nextGenerator.clear()
            var influenceSum = 1f
            val historyList = ArrayList<PlayedSong>()
            // sortiert aktuellen Song aus
            //if (curSong.get() != null)
            //	historyList.add(new PlayedSong(curSong.get()));
            history.filterNotNull().forEach {
                historyList.add(it)
                influenceSum += it.getInfluence()
            }
            while (influenceSum < 0.5) {
                influenceSum = 1f
                for (playedSong in historyList) {
                    if (playedSong.influence < 0)
                        playedSong.influence = playedSong.influence / 2
                    influenceSum += playedSong.influence
                }
            }
            
            logger.finer("History: $historyList")
            val historyArray = historyList.toTypedArray()
            for (s in Library.songs) {
                val rating = s.ratings.getRating() + historyArray.map { it.getRating(s) }.sum().coerceAtLeast(0f)
                s.computeColor((rating / influenceSum).toDouble())
                nextGenerator.add(s, (1 shl rating.toInt()).toFloat())
            }

            generateNextSong()
        }
    }

}
