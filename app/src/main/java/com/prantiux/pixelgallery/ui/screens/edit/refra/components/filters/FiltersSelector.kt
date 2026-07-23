package com.prantiux.pixelgallery.ui.screens.edit.refra.components.filters

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.*
import androidx.graphics.shapes.*
import androidx.graphics.shapes.Morph
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.prantiux.pixelgallery.domain.model.editor.Adjustment
import com.prantiux.pixelgallery.domain.model.editor.ImageFilter
import com.prantiux.pixelgallery.ui.screens.edit.refra.adjustments.filters.ImageFilterTypes
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.HorizontalScrubber
import com.prantiux.pixelgallery.ui.screens.edit.refra.components.core.SupportiveLazyLayout
import com.prantiux.pixelgallery.ui.screens.edit.refra.util.safeSystemGesturesPadding

@Composable
fun WindowInsets.Companion.horizontalSystemGesturesPadding(): PaddingValues {
    val padding = WindowInsets.systemGestures.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return remember(padding, layoutDirection) {
        PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            end = padding.calculateEndPadding(layoutDirection)
        )
    }
}



@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterItem(
    filter: ImageFilterTypes,
    thumbnailBitmap: Bitmap,
    activeFilterName: String?,
    noFilterActive: Boolean,
    onClick: (ImageFilter) -> Unit,
    isSupportingPanel: Boolean
) {
    val imageFilter = remember(filter) { filter.createImageFilter() }
    val isSelected = imageFilter.name == activeFilterName || (noFilterActive && filter == ImageFilterTypes.Original)
    
    val strokeSize by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        label = "strokeSize_${filter.name}"
    )
    val strokeAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        label = "strokeAlpha_${filter.name}"
    )
    val filterShape = if (isSelected) MaterialShapes.Cookie9Sided.toShape() else androidx.compose.foundation.shape.CircleShape
    val animatedWidth by animateDpAsState(
        targetValue = if (isSelected) 68.dp else 76.dp,
        label = "animatedWidth_${filter.name}"
    )
    
    Column(
        modifier = if (isSupportingPanel) {
            Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp)
        } else {
            Modifier.padding(horizontal = 6.dp)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = animatedWidth, height = 68.dp)
                .clip(filterShape)
                .border(
                    width = strokeSize,
                    color = MaterialTheme.colorScheme.tertiary.copy(strokeAlpha),
                    shape = filterShape
                )
                .clickable {
                    onClick(imageFilter)
                }
        ) {
            GlideImage(
                modifier = Modifier.matchParentSize(),
                model = thumbnailBitmap,
                colorFilter = remember(imageFilter) {
                    imageFilter.colorMatrix()?.let { ColorFilter.colorMatrix(it) }
                },
                contentDescription = imageFilter.name,
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FiltersSelector(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    isSupportingPanel: Boolean,
    appliedAdjustments: List<Adjustment> = emptyList(),
    activeFilterName: String? = null,
    onClick: (ImageFilter) -> Unit = {},
    filterIntensity: Float = 1f,
    onFilterIntensityChange: (Float) -> Unit = {},
) {
    // Pre-compute a small thumbnail for filter previews to avoid lag
    val thumbnailBitmap = remember(bitmap) {
        val maxSize = 200
        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height, 1f)
        if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
        } else bitmap
    }
    val noFilterActive = activeFilterName == null

    if (isSupportingPanel) {
        LazyVerticalGrid(
            modifier = modifier
                .fillMaxWidth()
                .safeSystemGesturesPadding(onlyRight = true)
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp)),
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(
                items = ImageFilterTypes.entries,
                key = { it.name }
            ) { filter ->
                FilterItem(
                    filter = filter,
                    thumbnailBitmap = thumbnailBitmap,
                    activeFilterName = activeFilterName,
                    noFilterActive = noFilterActive,
                    onClick = onClick,
                    isSupportingPanel = true
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            val hasActiveFilter = activeFilterName != null
            AnimatedVisibility(
                visible = hasActiveFilter && activeFilterName != ImageFilterTypes.Original.createImageFilter().name
            ) {
                HorizontalScrubber(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    allowNegative = false,
                    minValue = 0f,
                    maxValue = 100f,
                    defaultValue = 0f,
                    currentValue = filterIntensity * 100f,
                    displayValue = { "%" },
                    onValueChanged = { _, newValue ->
                        onFilterIntensityChange(newValue / 100f)
                    }
                )
            }
            SupportiveLazyLayout(
                modifier = Modifier
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                isSupportingPanel = false
            ) {
                items(
                    items = ImageFilterTypes.entries,
                    key = { it.name }
                ) { filter ->
                    FilterItem(
                        filter = filter,
                        thumbnailBitmap = thumbnailBitmap,
                        activeFilterName = activeFilterName,
                        noFilterActive = noFilterActive,
                        onClick = onClick,
                        isSupportingPanel = false
                    )
                }
            }
        }
    }
}
