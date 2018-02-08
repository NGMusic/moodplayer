package xerus.music.view;

import xerus.music.library.Song;
import xerus.ktutil.swing.table.MyTable;

public class JTableViewer extends MyTable implements SongViewer {

	public JTableViewer(String... columns) {
		super(columns);
	}

	@Override
	public void populateView(Iterable<Song> songs) {
		for (Song s : songs) {
			addRow(s.getAbsolutePath(), s.getArtist(), s.getTitle());
		}
	}

}
