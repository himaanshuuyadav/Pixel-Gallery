package com.prantiux.pixelgallery.ui.screens.edit.refra.components.trim

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoTrimmerTimeline(
    modifier: Modifier = Modifier,
    videoUri: Uri,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimChanged: (startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val frameCount = 8

    LaunchedEffect(videoUri, durationMs) {
        if (durationMs <= 0) return@LaunchedEffect
        coroutineScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val extracted = mutableListOf<Bitmap>()
                val intervalMs = durationMs / frameCount
                for (i in 0 until frameCount) {
                    val timeUs = (i * intervalMs + intervalMs / 2) * 1000L
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bitmap != null) {
                        extracted.add(bitmap)
                    }
                }
                withContext(Dispatchers.Main) {
                    frames = extracted
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }
        }
    }

    var timelineWidth by remember { mutableIntStateOf(0) }
    
    // Convert Ms to Px
    val startPx = if (durationMs > 0 && timelineWidth > 0) (trimStartMs.toFloat() / durationMs) * timelineWidth else 0f
    val currentEndMs = if (trimEndMs < 0) durationMs else trimEndMs
    val endPx = if (durationMs > 0 && timelineWidth > 0) (currentEndMs.toFloat() / durationMs) * timelineWidth else timelineWidth.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .onSizeChanged { timelineWidth = it.width }
    ) {
        // Draw Thumbnails
        if (frames.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                frames.forEach { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Draw Trimmer Overlay
        val handleWidth = 24f // pixels
        val overlayColor = Color.Black.copy(alpha = 0.6f)
        val handleColor = MaterialTheme.colorScheme.primary

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(timelineWidth, durationMs) {
                    var draggingStart = false
                    var draggingEnd = false
                    
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val x = offset.x
                            if (kotlin.math.abs(x - startPx) < handleWidth * 2) {
                                draggingStart = true
                            } else if (kotlin.math.abs(x - endPx) < handleWidth * 2) {
                                draggingEnd = true
                            }
                        },
                        onDragEnd = {
                            draggingStart = false
                            draggingEnd = false
                        },
                        onDragCancel = {
                            draggingStart = false
                            draggingEnd = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (durationMs <= 0 || timelineWidth <= 0) return@detectHorizontalDragGestures
                            
                            val pxToMs = durationMs.toFloat() / timelineWidth.toFloat()
                            
                            if (draggingStart) {
                                val newStartPx = (startPx + dragAmount).coerceIn(0f, endPx - handleWidth)
                                onTrimChanged((newStartPx * pxToMs).toLong(), currentEndMs)
                            } else if (draggingEnd) {
                                val newEndPx = (endPx + dragAmount).coerceIn(startPx + handleWidth, timelineWidth.toFloat())
                                onTrimChanged(trimStartMs, (newEndPx * pxToMs).toLong())
                            }
                        }
                    )
                }
        ) {
            // Darken unselected areas
            drawRect(
                color = overlayColor,
                topLeft = Offset(0f, 0f),
                size = Size(startPx, size.height)
            )
            drawRect(
                color = overlayColor,
                topLeft = Offset(endPx, 0f),
                size = Size(size.width - endPx, size.height)
            )

            // Draw handles
            drawRoundRect(
                color = handleColor,
                topLeft = Offset(startPx, 0f),
                size = Size(handleWidth, size.height),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = handleColor,
                topLeft = Offset(endPx - handleWidth, 0f),
                size = Size(handleWidth, size.height),
                cornerRadius = CornerRadius(8f, 8f)
            )
            
            // Draw borders
            drawRect(
                color = handleColor,
                topLeft = Offset(startPx, 0f),
                size = Size(endPx - startPx, 4f)
            )
            drawRect(
                color = handleColor,
                topLeft = Offset(startPx, size.height - 4f),
                size = Size(endPx - startPx, 4f)
            )
        }
    }
}
