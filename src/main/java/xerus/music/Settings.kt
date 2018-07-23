package xerus.music

import com.gluonhq.charm.down.Services
import com.gluonhq.charm.down.plugins.SettingsService

enum class Settings constructor(private val key: String, defaultValue: Any = "") {
	FOLDERSONTOP("viewFoldersontop", true),
	LOGLEVEL("loglevel", "FINE"),
	LIBRARY("library"),
	ENABLERATINGS("ratingsEnable", isDesktop),
	ENABLERATINGCOLORS("viewRatingcolors", false),
	VOLUME("volume", 0.9),
	
	FXML("fxml"),
	CSS("css", "black");
	
	companion object {
		private val PREFS = Services.get(SettingsService::class.java).get()
	}
	
	private val defaultVal: String = defaultValue.toString()
	
	fun isDefined() =
			PREFS.retrieve(key) != null
	
	fun get() =
			PREFS.retrieve(key) ?: defaultVal
	
	fun getOr(alternative: String) =
			PREFS.retrieve(key) ?: alternative
	
	fun getBool(): Boolean =
			get() == "true"
	
	fun put(value: String) =
			PREFS.store(key, value)
	
	fun put(value: Any) =
			PREFS.store(key, value.toString())
	
	fun reset() = PREFS.remove(key)
	
}
