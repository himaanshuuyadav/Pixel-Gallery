package com.prantiux.pixelgallery

import android.app.Application
import com.prantiux.pixelgallery.startup.StartupTrace

class PixelGalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupTrace.markStage("Application started", once = true)
    }
}
