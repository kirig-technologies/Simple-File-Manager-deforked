package com.simplemobiletools.filemanager.pro.activities

import com.simplemobiletools.filemanager.pro.R

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
            R.mipmap.ic_launcher
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)
}
