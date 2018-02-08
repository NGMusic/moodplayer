package xerus.music.player;

import xerus.music.library.Song;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import java.util.*;
import java.util.function.Consumer;

public class SongEvent {
	
	private static EventType state = EventType.STOP;
	public static final EventType getState() {
		return state;
	}
	public static final Property<Song> curSong = new SimpleObjectProperty<>();
	public static final Song getCurrentSong() {
	
	}
	
	private static final int maxHistory = 10;
	/**
	 * Do not modify this List directly!<br>
	 * Instead create a new SongEvent, which will automatically be pushed into the history
	 */
	public static final List<SongChange> songHistory = new LinkedList<SongChange>() {
		@Override
		public boolean add(SongChange song) {
			if (size() == maxHistory)
				remove();
			return super.add(song);
		}
	};
	
	public final EventType type;
	
	public static final void fireContinue() {
		
	}
	
	/** Fires a new Event depending on the current state */
	public SongEvent(Song song) {
		if(state == EventType.PLAY)
		curSong = song;
		type = EventType.PLAY;
		songHistory.add(new SongChange(curSong));
	}
	
	/** Fires a new SKIP-Event */
	public SongEvent(Song song, float playedFraction) {
		type = EventType.SKIP;
		songHistory.add(new SongChange(curSong, playedFraction, false));
	}
	
	private static final Map<EventType, Consumer<SongEvent>> subscribers = new HashMap<>();
	
	public static final void subscribe(Consumer<SongEvent> consumer) {
		subscribe(consumer, EventType.ALL);
	}
	
	public static final void subscribe(Consumer<SongEvent> consumer, EventType type) {
		
	}
	
	public class SongChange {
		
		public float ratingMultiplier;
		public float ratingInfluence;
	    
	    public SongChange(Song song, float playedFraction, boolean skipped) {
		    ratingMultiplier = playedFraction - 0.4f;
		    ratingInfluence = playedFraction - 0.4f;
		    if(skipped) {
			    ratingMultiplier -= 0.3f;
		    }
	    }
	    
	}
	
	public enum EventType {
		PLAY, PAUSE, CONTINUE, STOP, SKIP, ALL
	}
	
}
