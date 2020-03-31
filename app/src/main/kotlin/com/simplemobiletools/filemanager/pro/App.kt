package com.simplemobiletools.filemanager.pro

import android.app.Application
import com.facebook.stetho.Stetho

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            //Stetho.initializeWithDefaults(this);
        }
    }
}
