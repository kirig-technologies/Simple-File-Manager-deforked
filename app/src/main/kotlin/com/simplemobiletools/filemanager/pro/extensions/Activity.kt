package com.simplemobiletools.filemanager.pro.extensions

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.TransactionTooLargeException
import android.provider.DocumentsContract
import android.text.Html
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.simplemobiletools.filemanager.pro.BuildConfig
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.activities.BaseSimpleActivity
import com.simplemobiletools.filemanager.pro.dialogs.WritePermissionDialog
import com.simplemobiletools.filemanager.pro.helpers.*
import kotlinx.android.synthetic.main.dialog_title.view.*
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*
import kotlin.collections.HashMap

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}

fun Activity.tryOpenPathIntent(path: String, forceChooser: Boolean, openAsType: Int = OPEN_AS_DEFAULT) {
    if (!forceChooser && path.endsWith(".apk", true)) {
        val uri = if (isNougatPlus()) {
            FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", File(path))
        } else {
            Uri.fromFile(File(path))
        }

        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, getMimeTypeFromUri(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (resolveActivity(packageManager) != null) {
                startActivity(this)
            } else {
                toast(R.string.no_app_found)
            }
        }
    } else {
        openPath(path, forceChooser, openAsType)
    }
}

fun Activity.openPath(path: String, forceChooser: Boolean, openAsType: Int = OPEN_AS_DEFAULT) {
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, getMimeType(openAsType))
}

private fun getMimeType(type: Int) = when (type) {
    OPEN_AS_DEFAULT -> ""
    OPEN_AS_TEXT -> "text/*"
    OPEN_AS_IMAGE -> "image/*"
    OPEN_AS_AUDIO -> "audio/*"
    OPEN_AS_VIDEO -> "video/*"
    else -> "*/*"
}

fun AppCompatActivity.updateActionBarTitle(text: String, color: Int = baseConfig.primaryColor) {
    supportActionBar?.title = Html.fromHtml("<font color='${color.getContrastColor().toHex()}'>$text</font>")
}

fun Activity.appLaunched(appId: String) {
    baseConfig.internalStoragePath = getInternalStoragePath()
    updateSDCardPath()
    baseConfig.appId = appId

    if (baseConfig.navigationBarColor == INVALID_NAVIGATION_BAR_COLOR && (window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0)) {
        baseConfig.defaultNavigationBarColor = window.navigationBarColor
        baseConfig.navigationBarColor = window.navigationBarColor
    }
}

fun BaseSimpleActivity.isShowingSAFDialog(path: String): Boolean {
    return if (isPathOnSD(path) && !isSDCardSetAsDefaultStorage() && (baseConfig.treeUri.isEmpty() || !hasProperStoredTreeUri(false))) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                WritePermissionDialog(this, false) {
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)
                        if (resolveActivity(packageManager) == null) {
                            type = "*/*"
                        }

                        if (resolveActivity(packageManager) != null) {
                            checkedDocumentPath = path
                            startActivityForResult(this, OPEN_DOCUMENT_TREE)
                        } else {
                            toast(R.string.unknown_error_occurred)
                        }
                    }
                }
            }
        }
        true
    } else {
        false
    }
}

fun BaseSimpleActivity.isShowingOTGDialog(path: String): Boolean {
    return if (isPathOnOTG(path) && (baseConfig.OTGTreeUri.isEmpty() || !hasProperStoredTreeUri(true))) {
        showOTGPermissionDialog(path)
        true
    } else {
        false
    }
}

fun BaseSimpleActivity.showOTGPermissionDialog(path: String) {
    runOnUiThread {
        if (!isDestroyed && !isFinishing) {
            WritePermissionDialog(this, true) {
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    if (resolveActivity(packageManager) == null) {
                        type = "*/*"
                    }

                    if (resolveActivity(packageManager) != null) {
                        checkedDocumentPath = path
                        startActivityForResult(this, OPEN_DOCUMENT_TREE_OTG)
                    } else {
                        toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }
}

fun Activity.sharePathIntent(path: String, applicationId: String) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, newUri)
            type = getUriMimeType(path, newUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            try {
                if (resolveActivity(packageManager) != null) {
                    startActivity(Intent.createChooser(this, getString(R.string.share_via)))
                } else {
                    toast(R.string.no_app_found)
                }
            } catch (e: RuntimeException) {
                if (e.cause is TransactionTooLargeException) {
                    toast(R.string.maximum_share_reached)
                } else {
                    showErrorToast(e)
                }
            }
        }
    }
}

fun Activity.sharePathsIntent(paths: ArrayList<String>, applicationId: String) {
    ensureBackgroundThread {
        if (paths.size == 1) {
            sharePathIntent(paths.first(), applicationId)
        } else {
            val uriPaths = ArrayList<String>()
            val newUris = paths.map {
                val uri = getFinalUriFromPath(it, applicationId) ?: return@ensureBackgroundThread
                uriPaths.add(uri.path!!)
                uri
            } as ArrayList<Uri>

            var mimeType = uriPaths.getMimeType()
            if (mimeType.isEmpty() || mimeType == "*/*") {
                mimeType = paths.getMimeType()
            }

            Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, newUris)

                try {
                    if (resolveActivity(packageManager) != null) {
                        startActivity(Intent.createChooser(this, getString(R.string.share_via)))
                    } else {
                        toast(R.string.no_app_found)
                    }
                } catch (e: RuntimeException) {
                    if (e.cause is TransactionTooLargeException) {
                        toast(R.string.maximum_share_reached)
                    } else {
                        showErrorToast(e)
                    }
                }
            }
        }
    }
}

fun Activity.openPathIntent(path: String, forceChooser: Boolean, applicationId: String, forceMimeType: String = "", extras: HashMap<String, Boolean> = HashMap()) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, applicationId) ?: return@ensureBackgroundThread
        val mimeType = if (forceMimeType.isNotEmpty()) forceMimeType else getUriMimeType(path, newUri)
        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(newUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (applicationId == "com.simplemobiletools.gallery.pro" || applicationId == "com.simplemobiletools.gallery.pro.debug") {
                putExtra(IS_FROM_GALLERY, true)
            }

            for ((key, value) in extras) {
                putExtra(key, value)
            }

            putExtra(REAL_FILE_PATH, path)

            if (resolveActivity(packageManager) != null) {
                val chooser = Intent.createChooser(this, getString(R.string.open_with))
                try {
                    startActivity(if (forceChooser) chooser else this)
                } catch (e: NullPointerException) {
                    showErrorToast(e)
                }
            } else {
                if (!tryGenericMimeType(this, mimeType, newUri)) {
                    toast(R.string.no_app_found)
                }
            }
        }
    }
}

fun Activity.getFinalUriFromPath(path: String, applicationId: String): Uri? {
    val uri = try {
        ensurePublicUri(path, applicationId)
    } catch (e: Exception) {
        showErrorToast(e)
        return null
    }

    if (uri == null) {
        toast(R.string.unknown_error_occurred)
        return null
    }

    return uri
}

fun Activity.tryGenericMimeType(intent: Intent, mimeType: String, uri: Uri): Boolean {
    var genericMimeType = mimeType.getGenericMimeType()
    if (genericMimeType.isEmpty()) {
        genericMimeType = "*/*"
    }

    intent.setDataAndType(uri, genericMimeType)
    return if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
        true
    } else {
        false
    }
}

fun Activity.scanPathRecursively(path: String, callback: (() -> Unit)? = null) {
    applicationContext.scanPathRecursively(path, callback)
}

fun Activity.scanPathsRecursively(paths: ArrayList<String>, callback: (() -> Unit)? = null) {
    applicationContext.scanPathsRecursively(paths, callback)
}

fun Activity.rescanPaths(paths: ArrayList<String>, callback: (() -> Unit)? = null) {
    applicationContext.rescanPaths(paths, callback)
}

fun BaseSimpleActivity.renameFile(oldPath: String, newPath: String, callback: ((success: Boolean) -> Unit)? = null) {
    if (needsStupidWritePermissions(newPath)) {
        handleSAFDialog(newPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val document = getSomeDocumentFile(oldPath)
            if (document == null || (File(oldPath).isDirectory != document.isDirectory)) {
                runOnUiThread {
                    callback?.invoke(false)
                }
                return@handleSAFDialog
            }

            try {
                try {
                    DocumentsContract.renameDocument(applicationContext.contentResolver, document.uri, newPath.getFilenameFromPath())
                } catch (ignored: FileNotFoundException) {
                    // FileNotFoundException is thrown in some weird cases, but renaming works just fine
                }
                updateInMediaStore(oldPath, newPath)
                rescanPaths(arrayListOf(oldPath, newPath)) {
                    if (!baseConfig.keepLastModified) {
                        updateLastModified(newPath, System.currentTimeMillis())
                    }
                    deleteFromMediaStore(oldPath)
                    runOnUiThread {
                        callback?.invoke(true)
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
                runOnUiThread {
                    callback?.invoke(false)
                }
            }
        }
    } else if (File(oldPath).renameTo(File(newPath))) {
        if (File(newPath).isDirectory) {
            deleteFromMediaStore(oldPath)
            rescanPaths(arrayListOf(newPath)) {
                runOnUiThread {
                    callback?.invoke(true)
                }
                scanPathRecursively(newPath)
            }
        } else {
            if (!baseConfig.keepLastModified) {
                File(newPath).setLastModified(System.currentTimeMillis())
            }
            deleteFromMediaStore(oldPath)
            scanPathsRecursively(arrayListOf(newPath)) {
                runOnUiThread {
                    callback?.invoke(true)
                }
            }
        }
    } else {
        runOnUiThread {
            callback?.invoke(false)
        }
    }
}

fun BaseSimpleActivity.getFileInputStreamSync(path: String): InputStream? {
    return if (isPathOnOTG(path)) {
        val fileDocument = getSomeDocumentFile(path)
        applicationContext.contentResolver.openInputStream(fileDocument?.uri!!)
    } else {
        FileInputStream(File(path))
    }
}

fun Activity.setupDialogStuff(view: View, dialog: AlertDialog, titleId: Int = 0, titleText: String = "", callback: (() -> Unit)? = null) {
    if (isDestroyed || isFinishing) {
        return
    }

    var title: TextView? = null
    if (titleId != 0 || titleText.isNotEmpty()) {
        title = layoutInflater.inflate(R.layout.dialog_title, null) as TextView
        title.dialog_title_textview.apply {
            if (titleText.isNotEmpty()) {
                text = titleText
            } else {
                setText(titleId)
            }
            setTextColor(baseConfig.textColor)
        }
    }

    dialog.apply {
        setView(view)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCustomTitle(title)
        setCanceledOnTouchOutside(true)
        show()
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(baseConfig.textColor)
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(baseConfig.textColor)
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(baseConfig.textColor)

        val bgDrawable = resources.getColoredDrawableWithColor(R.drawable.dialog_bg, baseConfig.backgroundColor)
        window?.setBackgroundDrawable(bgDrawable)
    }
    callback?.invoke()
}

fun Activity.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText(getString(R.string.simple_commons), text)
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    toast(R.string.value_copied_to_clipboard)
}
