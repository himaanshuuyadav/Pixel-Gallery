package com.prantiux.pixelgallery.image

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaThumbnailRequest(
    val uri: Uri,
    val isVideo: Boolean
)

class MediaStoreThumbnailFetcher(
    private val data: MediaThumbnailRequest,
    private val options: Options,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return withContext(Dispatchers.IO) {
                try {
                    // Extract exact pixel dimensions if possible, otherwise default to 512
                    val width = options.size.width.let { if (it is coil.size.Dimension.Pixels) it.px else 512 }
                    val height = options.size.height.let { if (it is coil.size.Dimension.Pixels) it.px else 512 }
                    
                    val cancellationSignal = CancellationSignal()
                    
                    // This is the magic bullet for Google Photos performance.
                    // It bypasses decoding the original file and fetches the OS-level micro-thumbnail.
                    val bitmap = context.contentResolver.loadThumbnail(
                        data.uri,
                        Size(width, height),
                        cancellationSignal
                    )
                    
                    DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                } catch (e: Exception) {
                    // If the OS thumbnail fails (e.g. file deleted or corrupted), throw to trigger error state
                    throw e
                }
            }
        }
        
        throw IllegalStateException("MediaStoreThumbnailFetcher should only be used on Android Q and above.")
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumbnailRequest> {
        override fun create(
            data: MediaThumbnailRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return MediaStoreThumbnailFetcher(data, options, context)
        }
    }
}
