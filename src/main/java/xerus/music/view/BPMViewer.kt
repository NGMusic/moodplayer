package xerus.music.view

import javafx.beans.Observable
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.bind
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.ui.controls.FilteredTableView
import xerus.music.library.Library
import xerus.music.library.Song
import java.util.function.Predicate
import kotlin.math.roundToInt

class BPMViewer : VBox(5.0), SongViewer {

	private val view = FilteredTableView<Song>()
	private var filterField: TextInputControl? = null
	
	private val enable = ViewSettings.create("bpmEnable", true)
	private val speed = ViewSettings.create("bpm", 60)
	private val tolerance = ViewSettings.create("bpmTolerance", 0.1)
	private val enableMultiples = ViewSettings.create("bpmEnableMultiples", true)
	
	init {
		addLabeled("Speed", intSpinner(0, 999, 60).apply { speed.dependOn(valueProperty(), { it }) })
		addRow(Label("Range"),
				Spinner(SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 1.0, 0.1, 0.02).apply {
					converter = object : StringConverter<Double>() {
						override fun fromString(string: String): Double =
								string.substring(0, string.length - 1).toDouble() / 100.0
						
						override fun toString(`object`: Double): String =
								(`object` * 100).toInt().toString() + "%"
					}
					valueProperty().bindBidirectional(tolerance)
				}),
				CheckBox("enable multiples").bind(enableMultiples)
		)
		refreshFilter()

		view.addColumn("Name", { it.toString() })
		view.addColumn("BPM", { it.bpm })
		fill(view)
		
		enable.addListener { _, _, nv ->
			if(nv) {
				Library.songs.predicateProperty().bind(view.predicate)
			} else {
				Library.songs.predicateProperty().unbind()
				Library.songs.predicate = null
			}
		}
	}
	
	override fun populateView(songs: Iterable<Song>) {
		view.data.clear()
		view.data.addAll(songs)
		onJFX { view.refresh() }
	}
	
	override fun bindFilter(field: TextInputControl) {
		filterField = field
		refreshFilter()
	}
	
	private fun refreshFilter() {
		val dependencies = mutableListOf<Observable>(speed, tolerance, enableMultiples)
		filterField?.run { dependencies.add(textProperty()) }
		view.predicate.bind({
			Predicate<Song> { song ->
				(filterField == null || song.matches(filterField!!.text))
				&& song.bpm?.let { bpm ->
					val mult = bpm.toDouble().div(speed.get())
					val rounded = if(enableMultiples())
						if(mult > 1) mult.roundToInt().toDouble() else 1 / (1/mult).roundToInt().toDouble()
					else 1.0
					rounded.times(1 - tolerance.get()).rangeTo(rounded.times(1 + tolerance.get())).contains(mult)
				} ?: false
			}
		}, *dependencies.toTypedArray())
	}
	
}