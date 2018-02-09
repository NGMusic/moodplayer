package xerus.music.view

import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import xerus.ktutil.javafx.CheckMenuItem
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.addMenuItem
import xerus.ktutil.javafx.properties.bind
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.music.Settings
import xerus.music.library.Library
import xerus.music.library.Song
import xerus.music.library.hsv
import java.util.*

var foldersOnTop = Settings.FOLDERSONTOP.getBool()

class FolderViewer : TreeView<Any>(), SongViewer {
	
	private val root = FilterableTreeItem("Root")
	
	init {
		setRoot(root as TreeItem<Any>)
		isShowRoot = false
		
		FilterableTreeItem.autoLeaf = false
		//scrolsetHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		setOnMouseClicked {
			val selected = (selectionModel.selectedItem as TreeItem<*>?)?.value
			if (selected is Song)
				handleClick(it, selected)
		}
		
		addMenuItem(CheckMenuItem("Sort Folders to the Top", { sel: Boolean ->
			foldersOnTop = sel
			Settings.FOLDERSONTOP.put(sel)
			sort()
		}, foldersOnTop))
		
		FilterableTreeItem.autoExpand = true
		val expand = Menu("Expand/Collapse", null,
				CheckMenuItem("Expand/Collapse upon searching", { FilterableTreeItem.autoExpand = it }, FilterableTreeItem.autoExpand),
				MenuItem("Expand all", { expand(root as TreeItem<*>, true) }),
				MenuItem("Collapse all", { expand(root as TreeItem<*>, false) }))
		addMenuItem(expand)
		
		setCellFactory { _ ->
			object : TreeCell<Any>() {
				override fun updateItem(item: Any?, empty: Boolean) {
					super.updateItem(item, empty)
					textProperty().set(item?.toString().orEmpty())
					backgroundProperty().unbind()
					if (!Settings.ENABLERATINGCOLORS.getBool())
						return
					if (item is Song) {
						backgroundProperty().bind({
							Background(BackgroundFill(item.color.get(), CornerRadii.EMPTY, Insets.EMPTY))
						}, item.color)
					} else if (treeItem != null) {
						val s = treeItem.children.map { it.value as Song }
						backgroundProperty().bind({
							Background(BackgroundFill(hsv(s.map { it.color.get().hue }.average()),
									CornerRadii.EMPTY, Insets.EMPTY))
						}, *s.map { it.color }.toTypedArray())
					}
				}
			}
		}
	}
	
	// region Manage TreeItems
	
	override fun populateView(songs: Iterable<Song>) {
		root.internalChildren.clear()
		
		val nodes = HashMap<String, FilterableTreeItem<String>>()
		nodes.put(Library.main.name, root)
		for (s in songs) {
			val parent = s.parentFile.name
			var node: FilterableTreeItem<String>? = nodes[parent]
			if (node == null) {
				node = FilterableTreeItem(parent)
				nodes.put(parent, node)
				root.internalChildren.add(node)
			}
			node.internalChildren.add(FilterableTreeItem(s) as TreeItem<String>)
		}
		sort()
	}
	
	override fun bindFilter(field: TextInputControl) {
		root.bindPredicate(field.textProperty())
	}
	
	private fun sort() {
		root.internalChildren.sortWith(Comparator comp@ { t1: TreeItem<*>, t2: TreeItem<*> ->
			if (foldersOnTop) {
				val c1 = (t1 as FilterableTreeItem<*>).internalChildren.size
				val c2 = (t2 as FilterableTreeItem<*>).internalChildren.size
				if (c1 > 0 && c2 == 0)
					return@comp -1000
				if (c2 > 0 && c1 == 0)
					return@comp 1000
			}
			t1.value.toString().compareTo(t2.value.toString())
		})
	}
	
	private fun expand(t: TreeItem<*>, value: Boolean) {
		if (t !== root)
			t.isExpanded = value
		for (child in t.children)
			expand(child, value)
	}
	
	//endregion
	
}
