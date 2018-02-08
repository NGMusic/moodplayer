package xerus.music.library;

import javafx.application.Platform;
import javafx.scene.media.Media;
import xerus.music.Main;
import xerus.music.MusicPlayer;
import xerus.music.view.SongViewer;
import xerus.ktutil.DistributedRandom;
import xerus.ktutil.tools.Tools;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class LibraryCopy {

	public static String path;
	private static Storage<Song> songs;

	// region Initialization

	public static void initialize(String mainlib, SongViewer... views) {
		songs = new Storage<>();
		path = mainlib;
		ratingsPath = new File(path + File.separator + ".ratings");
		ratingsBackup = new File(path + File.separator + ".ratings.bak");

		activeThreads++;
		for(SongViewer view : views)
			addView(view);
		Platform.runLater(() -> {
			readRatings();
			addLibrary(path);
			activeThreads--;
			refreshViews();
		});
	}

	private static void addLibrary(String path) {
		activeThreads++;
		File f = new File(path);
		if (f.exists()) {
			if (f.isFile())
				addSong(f);
			else
				new LibraryThread(path).start();
		}
		activeThreads--;
	}

	//endregion

	// region Views

	private static final Collection<WeakReference<SongViewer>> views = new ArrayDeque<>();
	public static void addView(SongViewer viewer) {
		Main.logger.config("Library added " + viewer);
		views.add(new WeakReference<>(viewer));
		if(activeThreads == 0)
			populateView(viewer);
		else
			refreshViews();
	}

	private static Thread refresher;
	public static void refreshViews() {
		if(refresher != null)
			return;
		refresher = new Thread(() -> {
			awaitSongLoading();
			views.removeIf(w -> (w.get() == null));
			for (WeakReference<SongViewer> reference : views)
				populateView(reference.get());
			refresher = null;
		});
		refresher.start();
	}

	private static void populateView(SongViewer viewer) {
		Platform.runLater(() -> viewer.populate(songs));
	}

	//endregion

	// region Ratings

	private static boolean enableRatings = false;

	public static void enableRatings(boolean enable) {
		enableRatings = enable;
		if(!enable)
			return;
		for(int i=0; i<lines.size(); i++) {
			Song s = songs.get(i);
			if (s != null)
				s.initRatings(lines.get(i));
		}
	}

	private static final int version = 1;
	private static Storage<byte[]> lines;
	private static File ratingsPath;
	private static File ratingsBackup;

	private static final int lineSeparator = 0;

	private static void readRatings() {
		lines = new Storage<>();
		if(!enableRatings)
			return;
		for (File path : new File[]{ratingsPath, ratingsBackup}) {
			try(InputStream in = new BufferedInputStream(new FileInputStream(path))) {
				@SuppressWarnings("unused")
				int version = in.read();

				byte[] b = new byte[4];
				in.read(b);
				lastID = Tools.byteArrayToInt(b);

				byte[] line;
				while ((line = readLine(in)) != null)
					lines.add(line);
				Main.logger.fine("Library read ratings from file");
				break;
			} catch (Exception e) {
				lines.clear();
				Main.logger.throwing("Library", "readRatings", e);
			}
		}
	}

	private static byte[] readLine(InputStream in) throws IOException {
		int next = in.read();
		if (next == -1)
			return null;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
		while (next != lineSeparator) {
			bytes.write(next);
			next = in.read();
		}
		return bytes.toByteArray();
	}

	public static void writeRatings() {
		if(!enableRatings)
			return;
		new Thread(() -> {
			awaitSongLoading();
			Main.logger.fine("Library is writing ratings to file...");
			if (ratingsPath.exists()) {
				if(ratingsBackup.exists())
					ratingsBackup.delete();
				ratingsPath.renameTo(ratingsBackup);
			}
			try (OutputStream out = new BufferedOutputStream(new FileOutputStream(ratingsPath))) {
				out.write(version);
				out.write(Tools.intToByteArray(lastID));
				int size = Math.max(songs.size(), lines.size());
				for (int i = 0; i < size; i++) {
					Song s = songs.get(i);
					if (s != null)
						lines.set(i, s.serialiseRatings());
					byte[] b = lines.get(i);
					if(b != null)
						out.write(b);
					out.write(lineSeparator);
				}
			} catch (IOException e) {
				Main.showError("Couldn't write database to " + ratingsPath, e);
			}
		}).start();
	}

	//endregion

	// region Song management

	private static DistributedRandom<Song> rand = new DistributedRandom();
	private static Song distSong;

	public static Song getRandomSong() {
		Song cur = MusicPlayer.player.getCurrentSong();
		if (cur != distSong || distSong == null) {
			rand.clear();
			awaitSongLoading();
			if (cur == null)
				for (Song s : songs)
					rand.add(s, 1 << (int) (s.getRating()));
			else
				for (Song s : songs)
					rand.add(s, 1 << (int) (s.getRating() + cur.getRating(s.getId())));
			distSong = cur;
		}
		return rand.generate();
	}

	public static Song getSong(int id) {
		return songs.get(id);
	}

	public static Storage<Song> getSongs() {
		return songs;
	}

	// Song initializing

	static void addSong(File f) {
		try {
			new Media(f.toURI().toString());
		} catch(Exception e) {
			return;
		}
		Song song = new Song(f);
		int id = song.getId();
		songs.add(id, song);
		if (id > lastID)
			lastID = id;
		song.initRatings(lines.get(id));
	}

	private static int lastID = -1;

	public static int getNextID() {
		return ++lastID;
	}

	//endregion

	// region Threading

	private static int activeThreads;

	private static void awaitSongLoading() {
		try {
			while (activeThreads > 0)
				Thread.sleep(100);
		} catch (InterruptedException e) { }
	}

	private static class LibraryThread extends Thread {

		String path;

		public LibraryThread(String path) {
			activeThreads++;
			this.path = path;
		}

		@Override
		public void run() {
			try {
				Queue<File> queue = new LinkedList<>();
				//queue.addAll(new File(path).listFiles());
			} catch (IOException e) {
				Main.logger.throwing(toString(), "run", e);
			}
			activeThreads--;
		}

		@Override
		public String toString() {
			return String.format("%s processing %s", getClass().getName(), path);
		}

	}

	//endregion

}
