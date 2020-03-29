package com.simplemobiletools.filemanager.pro.interfaces

import java.util.*

interface ItemOperationsListener {
    fun refreshItems()
    fun selectedPaths(paths: ArrayList<String>)
}
