package com.simplemobiletools.filemanager.pro.interfaces

import com.simplemobiletools.filemanager.pro.models.FileDirItem
import java.util.*

interface ItemOperationsListener {
    fun refreshItems()

    fun deleteFiles(files: ArrayList<FileDirItem>)

    fun selectedPaths(paths: ArrayList<String>)
}
