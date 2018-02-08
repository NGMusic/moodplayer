package xerus.music.view

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.TextInputControl
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import xerus.ktutil.javafx.properties.bind
import xerus.ktutil.javafx.properties.bindSoft
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.ui.FilteredTableView
import xerus.music.Settings
import xerus.music.library.Song
import java.util.function.Predicate

class TableViewer : FilteredTableView<Song>(FXCollections.observableArrayList<Song>()), SongViewer {

    init {
        setOnMouseClicked { handleClick(it, selectionModel.selectedItem) }

        placeholder = Label("No Songs to show!")
        
        setRowFactory {
            object : TableRow<Song>() {
                override fun updateItem(item: Song?, empty: Boolean) {
                    super.updateItem(item, empty)
                    backgroundProperty().unbind()
                    if (item != null && Settings.ENABLERATINGCOLORS.getBool()) {
                        backgroundProperty().bind({
                            Background(BackgroundFill(item.color.get(), CornerRadii.EMPTY, Insets.EMPTY))
                        }, item.color)
                    }
                }
            }
        }
    }

    override fun populateView(songs: Iterable<Song>) {
        data.clear()
        data.addAll(songs)
        Platform.runLater { refresh() }
    }

    override fun bindFilter(field: TextInputControl) {
        predicate.dependOn(field.textProperty(), { text -> Predicate<Song> { song -> song.matches(text) } })
    }

}