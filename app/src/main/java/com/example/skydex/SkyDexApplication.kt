package com.example.skydex

import android.app.Application

class SkyDexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
