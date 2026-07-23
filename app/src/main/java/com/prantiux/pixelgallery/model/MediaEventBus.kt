package com.prantiux.pixelgallery.model
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

object MediaEventBus {
    val lastSavedUri = MutableStateFlow<Uri?>(null)
}
