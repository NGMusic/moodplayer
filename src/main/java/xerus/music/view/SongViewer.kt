package xerus.music.view

import javafx.scene.control.Control
import javafx.scene.control.TextInputControl
import javafx.scene.input.MouseEvent
import xerus.music.isDesktop
import xerus.music.library.Song
import xerus.music.logger
import xerus.music.musicPlayer
import xerus.music.player.Player
import xerus.util.Storage

interface SongViewer {
	
	/** the library calls this method to populate the View<br></br>
	 * @param songs all songs the library has right now, which may also include null-elements at this point */
	fun populate(songs: Storage<Song>) {
		populateView(songs)
		logger.fine("${javaClass.simpleName} populated")
	}
	
	/** Override this to display the Songs.
	 * Whenever the Library changes, this method will be called again with all Songs */
	fun populateView(songs: Iterable<Song>)
	
	fun bindFilter(field: TextInputControl)
	
	fun handleClick(me: MouseEvent, selected: Song?) {
		if (selected == null)
			return
		if (me.clickCount == if (isDesktop) 2 else 1) {
			if (Player.curSong.get() != selected)
				musicPlayer.skipTo(selected)
		}
	}
	
	fun asControl(): Control = this as Control
	
}
