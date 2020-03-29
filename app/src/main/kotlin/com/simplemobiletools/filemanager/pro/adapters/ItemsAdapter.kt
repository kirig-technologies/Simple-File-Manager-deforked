package com.simplemobiletools.filemanager.pro.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.activities.BaseSimpleActivity
import com.simplemobiletools.filemanager.pro.dialogs.PropertiesDialog
import com.simplemobiletools.filemanager.pro.extensions.*
import com.simplemobiletools.filemanager.pro.helpers.ensureBackgroundThread
import com.simplemobiletools.filemanager.pro.interfaces.ItemOperationsListener
import com.simplemobiletools.filemanager.pro.models.FileDirItem
import com.simplemobiletools.filemanager.pro.models.ListItem
import com.simplemobiletools.filemanager.pro.views.FastScroller
import com.simplemobiletools.filemanager.pro.views.MyRecyclerView
import kotlinx.android.synthetic.main.item_list_file_dir.view.*
import kotlinx.android.synthetic.main.item_list_section.view.*
import java.io.File
import java.util.*

class ItemsAdapter(activity: BaseSimpleActivity, var listItems: MutableList<ListItem>, val listener: ItemOperationsListener?, recyclerView: MyRecyclerView,
                   val isPickMultipleIntent: Boolean, fastScroller: FastScroller, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private val TYPE_FILE_DIR = 1
    private val TYPE_SECTION = 2
    private lateinit var folderDrawable: Drawable
    private lateinit var fileDrawable: Drawable
    private var currentItemsHash = listItems.hashCode()
    private var textToHighlight = ""
    private val hasOTGConnected = activity.hasOTGConnected()
    private var fontSize = 0f
    private var smallerFontSize = 0f

    var adjustedPrimaryColor = activity.getAdjustedPrimaryColor()

    init {
        setupDragListener(true)
        initDrawables()
        updateFontSizes()
    }

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_confirm_selection).isVisible = isPickMultipleIntent
            findItem(R.id.cab_copy_path).isVisible = isOneItemSelected()

            checkHideBtnVisibility(this)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_properties -> showProperties()
            R.id.cab_share -> shareFiles()
            R.id.cab_hide -> toggleFileVisibility(true)
            R.id.cab_unhide -> toggleFileVisibility(false)
            R.id.cab_copy_path -> copyPath()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = listItems.filter { !it.isSectionTitle }.size

    override fun getIsItemSelectable(position: Int) = !listItems[position].isSectionTitle

    override fun getItemSelectionKey(position: Int) = listItems.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst { it.path.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun getItemViewType(position: Int): Int {
        return if (listItems[position].isSectionTitle) {
            TYPE_SECTION
        } else {
            TYPE_FILE_DIR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == TYPE_SECTION) R.layout.item_list_section else R.layout.item_list_file_dir
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val fileDirItem = listItems[position]
        holder.bindView(fileDirItem, !fileDirItem.isSectionTitle, !fileDirItem.isSectionTitle) { itemView, layoutPosition ->
            setupView(itemView, fileDirItem)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = listItems.size

    private fun getItemWithKey(key: Int): FileDirItem? = listItems.firstOrNull { it.path.hashCode() == key }

    fun initDrawables() {
        folderDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_folder_vector, textColor)
        fileDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_file_vector, textColor)
        folderDrawable.alpha = 180
        fileDrawable.alpha = 180
    }

    private fun checkHideBtnVisibility(menu: Menu) {
        var hiddenCnt = 0
        var unhiddenCnt = 0
        getSelectedFileDirItems().map { it.name }.forEach {
            if (it.startsWith(".")) {
                hiddenCnt++
            } else {
                unhiddenCnt++
            }
        }

        menu.findItem(R.id.cab_hide).isVisible = unhiddenCnt > 0
        menu.findItem(R.id.cab_unhide).isVisible = hiddenCnt > 0
    }

    private fun confirmSelection() {
        if (selectedKeys.isNotEmpty()) {
            val paths = getSelectedFileDirItems().asSequence().filter { !it.isDirectory }.map { it.path }.toMutableList() as ArrayList<String>
            if (paths.isEmpty()) {
                finishActMode()
            } else {
                listener?.selectedPaths(paths)
            }
        }
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            PropertiesDialog(activity, getFirstSelectedItemPath(), activity.config.shouldShowHidden)
        } else {
            val paths = getSelectedFileDirItems().map { it.path }
            PropertiesDialog(activity, paths, activity.config.shouldShowHidden)
        }
    }

    private fun shareFiles() {
        val selectedItems = getSelectedFileDirItems()
        val paths = ArrayList<String>(selectedItems.size)
        selectedItems.forEach {
            addFileUris(it.path, paths)
        }
        activity.sharePaths(paths)
    }

    private fun toggleFileVisibility(hide: Boolean) {
        ensureBackgroundThread {
            getSelectedFileDirItems().forEach {
                activity.toggleItemVisibility(it.path, hide)
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun addFileUris(path: String, paths: ArrayList<String>) {
        if (activity.getIsPathDirectory(path)) {
            val shouldShowHidden = activity.config.shouldShowHidden
            if (activity.isPathOnOTG(path)) {
                activity.getDocumentFile(path)?.listFiles()?.filter { if (shouldShowHidden) true else !it.name!!.startsWith(".") }?.forEach {
                    addFileUris(it.uri.toString(), paths)
                }
            } else {
                File(path).listFiles()?.filter { if (shouldShowHidden) true else !it.isHidden }?.forEach {
                    addFileUris(it.absolutePath, paths)
                }
            }
        } else {
            paths.add(path)
        }
    }

    private fun copyPath() {
        val clip = ClipData.newPlainText(activity.getString(R.string.app_name), getFirstSelectedItemPath())
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        finishActMode()
        activity.toast(R.string.path_copied)
    }

    private fun getFirstSelectedItemPath() = getSelectedFileDirItems().first().path

    private fun getSelectedFileDirItems() = listItems.filter { selectedKeys.contains(it.path.hashCode()) } as ArrayList<FileDirItem>

    fun updateItems(newItems: ArrayList<ListItem>, highlightText: String = "") {
        if (newItems.hashCode() != currentItemsHash) {
            currentItemsHash = newItems.hashCode()
            textToHighlight = highlightText
            listItems = newItems.clone() as ArrayList<ListItem>
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
        fastScroller?.measureRecyclerView()
    }

    fun updateFontSizes() {
        fontSize = activity.getTextSize()
        smallerFontSize = fontSize * 0.8f
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val icon = holder.itemView.item_icon
            if (icon != null) {
                Glide.with(activity).clear(icon)
            }
        }
    }

    private fun setupView(view: View, listItem: ListItem) {
        val isSelected = selectedKeys.contains(listItem.path.hashCode())
        view.apply {
            if (listItem.isSectionTitle) {
                item_section.text = listItem.mName
                item_section.setTextColor(textColor)
                item_section.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            } else {
                item_frame.isSelected = isSelected
                val fileName = listItem.name
                item_name.text = if (textToHighlight.isEmpty()) fileName else fileName.highlightTextPart(textToHighlight, adjustedPrimaryColor)
                item_name.setTextColor(textColor)
                item_name.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                item_details.setTextColor(textColor)
                item_details.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

                item_date.setTextColor(textColor)
                item_date.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallerFontSize)

                if (listItem.isDirectory) {
                    item_icon.setImageDrawable(folderDrawable)
                    item_details.text = getChildrenCnt(listItem)
                    item_date.beGone()
                } else {
                    item_details.text = listItem.size.formatSize()
                    item_date.beVisible()
                    item_date.text = listItem.modified.formatDate(activity)

                    val options = RequestOptions()
                            .signature(listItem.mPath.getFileSignature())
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .error(fileDrawable)
                            .centerCrop()

                    val itemToLoad = getImagePathToLoad(listItem.path)
                    if (!activity.isDestroyed) {
                        Glide.with(activity).load(itemToLoad).transition(DrawableTransitionOptions.withCrossFade()).apply(options).into(item_icon)
                    }
                }
            }
        }
    }

    private fun getChildrenCnt(item: FileDirItem): String {
        val children = item.children
        return activity.resources.getQuantityString(R.plurals.items, children, children)
    }

    private fun getOTGPublicPath(itemToLoad: String) = "${baseConfig.OTGTreeUri}/document/${baseConfig.OTGPartition}%3A${itemToLoad.substring(baseConfig.OTGPath.length).replace("/", "%2F")}"

    private fun getImagePathToLoad(path: String): Any {
        var itemToLoad = if (path.endsWith(".apk", true)) {
            val packageInfo = activity.packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
            if (packageInfo != null) {
                val appInfo = packageInfo.applicationInfo
                appInfo.sourceDir = path
                appInfo.publicSourceDir = path
                appInfo.loadIcon(activity.packageManager)
            } else {
                path
            }
        } else {
            path
        }

        if (hasOTGConnected && itemToLoad is String && activity.isPathOnOTG(itemToLoad) && baseConfig.OTGTreeUri.isNotEmpty() && baseConfig.OTGPartition.isNotEmpty()) {
            itemToLoad = getOTGPublicPath(itemToLoad)
        }

        return itemToLoad
    }
}
