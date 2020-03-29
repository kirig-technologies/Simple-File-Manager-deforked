package com.simplemobiletools.filemanager.pro.activities

import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.extensions.config
import com.simplemobiletools.filemanager.pro.helpers.RootHelpers
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupCustomizeColors()
        setupUseEnglish()
        setupShowHidden()
        setupKeepLastModified()
        setupEnableRootAccess()
        updateTextColors(settings_holder)
        setupSectionColors()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupSectionColors() {
        val adjustedPrimaryColor = getAdjustedPrimaryColor()
        arrayListOf(visibility_label, file_operations_label, security_label).forEach {
            it.setTextColor(adjustedPrimaryColor)
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    private fun setupShowHidden() {
        settings_show_hidden.isChecked = config.showHidden
        settings_show_hidden_holder.setOnClickListener {
            if (config.showHidden) {
                toggleShowHidden()
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleShowHidden()
                }
            }
        }
    }

    private fun toggleShowHidden() {
        settings_show_hidden.toggle()
        config.showHidden = settings_show_hidden.isChecked
    }

    private fun setupKeepLastModified() {
        settings_keep_last_modified.isChecked = config.keepLastModified
        settings_keep_last_modified_holder.setOnClickListener {
            settings_keep_last_modified.toggle()
            config.keepLastModified = settings_keep_last_modified.isChecked
        }
    }

    private fun setupEnableRootAccess() {
        settings_enable_root_access_holder.beVisibleIf(config.isRootAvailable)
        settings_enable_root_access.isChecked = config.enableRootAccess
        settings_enable_root_access_holder.setOnClickListener {
            if (!config.enableRootAccess) {
                RootHelpers(this).askRootIfNeeded {
                    toggleRootAccess(it)
                }
            } else {
                toggleRootAccess(false)
            }
        }
    }

    private fun toggleRootAccess(enable: Boolean) {
        settings_enable_root_access.isChecked = enable
        config.enableRootAccess = enable
    }
}
