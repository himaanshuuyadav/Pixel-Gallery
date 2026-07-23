package com.prantiux.pixelgallery.ui.screens.edit.refra.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri

fun Context.getEditImageCapableApps(): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*")
    }
    val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
    return resolveInfoList.filterNot { it.activityInfo.packageName == packageName }
}

fun Context.launchEditImageIntent(packageName: String, uri: Uri) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(packageName)
    }
    startActivity(intent)
}

fun Context.getEditVideoCapableApps(): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(Uri.parse("content://media/external/video/media/1"), "video/*")
    }
    val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
    return resolveInfoList.filterNot { it.activityInfo.packageName == packageName }
}

fun Context.launchEditVideoIntent(packageName: String, uri: Uri) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "video/*")
        putExtra("mimeType", "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(packageName)
    }
    startActivity(intent)
}
