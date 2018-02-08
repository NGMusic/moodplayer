package xerus.music.player;

import static com.sun.media.jfxmedia.MediaManager.canPlayContentType;
import static com.sun.media.jfxmediaimpl.MediaUtils.filenameToContentType;

import xerus.music.library.Library;
import xerus.music.library.Song;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.util.Duration;

public final class JFXPlayer extends XPlayer {

	private MediaPlayer player;

	@Override
	void initPlayer(Song song) {
		if(isReady())
			player.dispose();
		player = new MediaPlayer(new Media(song.toURI().toString()));
		for (Consumer<MediaPlayer> consumer : playerListeners)
			consumer.accept(player);
	}

	// Player listeners

	private Queue<Consumer<MediaPlayer>> playerListeners = new ArrayDeque<>(8);

	private void addListener(Consumer<MediaPlayer> c) {
		playerListeners.add(c);
	}

	public void addDurationListener(Consumer<Duration> c) {
		addListener(
				player -> player.stopTimeProperty().addListener((o, ov, nv) -> {
					if(!nv.isUnknown())
						c.accept(nv);
				}));
	}

	public void addPlaybackTimeListener(Consumer<Duration> c) {
		addListener(player -> player.currentTimeProperty().addListener((o, ov, nv) -> c.accept(nv)));
	}

	// Status checks

	@Override
	public boolean isPlaying() {
		return checkStatus(Status.PLAYING);
	}

	@Override
	public boolean isReady() {
		return player != null && !checkStatus(Status.DISPOSED);
	}

	private boolean checkStatus(Status tocheck) {
		return player != null && player.statusProperty().getValue() == tocheck;
	}

	// Play actions

	@Override
	public void setPlayerVolume(double volume) {
		player.setVolume(volume);
	}

	@Override
	public void play() {
		if(!isReady())
			loadSong(Library.getRandomSong());
		player.play();
	}

	@Override
	public void pause() {
		if(!isReady())
			return;
		player.pause();
	}

	@Override
	public void stop() {
		if(!isReady())
			return;
		player.dispose();
		currentSong.setValue(null);
	}

	@Override
	public void seek(Duration time) {
		player.seek(time);
	}

	// Other checks

	public float playedPercentage() {
		return (float) (player.getCurrentTime().toMillis() / player.getTotalDuration().toMillis());
	}

	@Override
	public boolean isPlayable(String filename) {
		return canPlayContentType(filenameToContentType(filename));
	}

}
