package xerus.music.archive;

import static org.jaudiotagger.tag.FieldKey.*;

import xerus.ktutil.tools.StringTools;
import xerus.music.Main;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.mutable.MutableFloat;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.id3.ID3v22Tag;

public class Song extends File {

	private static final boolean enableTagging = false;
	private static final float defaultGain = -8;
	private static final float defaultRating = 6;

	static Storage<Song> songs;

	int id;
	float gain;

	Tag tag;

	// CONSTRUCTION

	public Song(String filename) {
		super(filename);
		ratings = new Storage<>(new MutableFloat(defaultRating));
		try {
			AudioFile audiofile = AudioFileIO.read(this);
			tag = audiofile.getTag();

			gain = (float) Math.pow(10, extractReplayGain() / 20);
			if (tag.hasField(CUSTOM4)) {
				id = Integer.parseInt(tag.getFirst(CUSTOM4));
				Main.logger.finer(String.format("%s loaded ID %s from Tag", toString(), id));
			} else {
				id = Library.getNextID();
				Main.logger.finer(String.format("%s was assigned ID %s", toString(), id));
				if (enableTagging)
					try {
						tag.setField(CUSTOM4, String.valueOf(id));
						audiofile.commit();
					} catch (FieldDataInvalidException | CannotWriteException e) {
						Main.logger.throwing("Song", "Constructor", e);
					}
			}
		} catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException ex) {
			tag = new ID3v22Tag();
		}
	}

	public Song(Object file) {
		this(file.toString());
	}

	// RATINGS

	protected Storage<MutableFloat> ratings;

	public float getRating() {
		return ratings.getOrDefault(id).floatValue();
	}

	public float getRating(int id) {
		return getRatingInternal(id).floatValue();
	}

	MutableFloat getRatingInternal(int id) {
		MutableFloat rating = ratings.get(id);
		return rating != null ? rating : calcDefault.apply(id);
	}

	private final Function<Integer, MutableFloat> calcDefault = (id) -> {
		Optional<Song> s = songs.getOptional(id);
		MutableFloat rating = new MutableFloat(s.isPresent() ? Rating.get(this, s.get()) : defaultRating);
		Main.logger.finer(String.format("%s and %s calculated rating %s", this, s.orElseGet(() -> null), rating));
		s.ifPresent(song -> song.ratings.set(id, rating));
		ratings.set(id, rating);
		return rating;
	};

	public void updateRating(Song s, float change) {
		editRating(s.id, change);
		s.editRating(id, change / 2);
	}

	public void updateRating(float change) {
		editRating(id, change);
	}

	protected void editRating(int id, float change) {
		MutableFloat f = getRatingInternal(id);
		// do some magic to cap off the sides
		float temp = f.floatValue() / 8 - 1;
		f.add((-temp * temp + 1) * change);
	}

	void initRatings(byte[] line) {
		for (byte b : line)
			ratings.add(new MutableFloat((b & 0x0FF) / 16f));
		if (ratings.size() < id)
			ratings.set(id, new MutableFloat(defaultRating));
	}

	byte[] serialiseRatings() {
		byte[] res = new byte[ratings.size()];
		for (int i = 0; i < ratings.size(); i++)
			res[i] = (byte) (16 * getRating(i));
		return res;
	}

	private enum Rating {
		GENRE(1, "getGenre"),
		ARTISTCONTAINS(1, "getArtist", StringTools::containsEach),
		ARTIST(1, "getArtist"),
		ALBUM(1, "getAlbum"),;

		private final BiPredicate<Song, Song> condition;
		private final float bonus;

		Rating(float bonus, String methodname) {
			this(bonus, methodname, StringTools.equals);
		}

		Rating(float bonus, String methodname, BiPredicate<String, String> comp) {
			try {
				Method m = Song.class.getMethod(methodname);
				condition = (song1, song2) -> {
					try {
						String s1 = (String) m.invoke(song1);
						String s2 = (String) m.invoke(song2);
						return comp.test(s1, s2);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						return false;
					}
				};
			} catch(NoSuchMethodException e) {
				throw new RuntimeException(e);
			}
			this.bonus = bonus;

		}

		static float get(Song s1, Song s2) {
			float res = defaultRating;
			for (Rating r : Rating.values()) {
				if (r.condition.test(s1, s2))
					res += r.bonus;
			}
			return res;
		}

	}

	// TAG INFO

	public String getArtist() {
		return String.join(", ", tag.getAll(ARTIST));
	}

	public String getAlbum() {
		return tag.getFirst(ALBUM);
	}

	public String getGenre() {
		return tag.getFirst(GENRE);
	}

	public String getTitle() {
		String title = tag.getFirst(TITLE);
		return title.isEmpty() ? getName() : title;
	}

	private static final Pattern gainPattern = Pattern.compile("-?[.\\d]+");

	private float extractReplayGain() {
		try {
			Iterator<TagField> fields = tag.getFields();
			while (fields.hasNext()) {
				TagField field = fields.next();
				if ((field.getId() + field.toString()).contains("replaygain_track_gain")) {
					Matcher m = gainPattern.matcher(field.toString());
					m.find();
					float replaygain = Float.parseFloat(m.group());
					return replaygain;
				}
			}
		} catch (Exception e) {
		}
		return defaultGain;
	}

	public float getGain() {
		return gain;
	}

	// COMPARING

	@Override
	public String toString() {
		return String.format("%s - %s", getArtist(), getTitle());
	}

	public String verboseString() {
		return String.format("Song %s with ID %s, by Tags: %s", getName(), id, toString());
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public int compareTo(File f) {
		if (f.getClass() == Song.class)
			return toString().compareTo(f.toString());
		return super.toString().compareTo(f.toString());
	}

}
