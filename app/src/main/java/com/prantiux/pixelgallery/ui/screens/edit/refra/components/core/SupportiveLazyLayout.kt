package com.prantiux.pixelgallery.ui.screens.edit.refra.components.core

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.Alignment

@Composable
fun SupportiveLazyLayout(
    modifier: Modifier = Modifier,
    isSupportingPanel: Boolean,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: LazyListScope.() -> Unit
) {
    if (!isSupportingPanel) {
        LazyRow(
            modifier = modifier
                .animateContentSize()
                .fillMaxWidth(),
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        LazyColumn(
            modifier = modifier
                .animateContentSize()
                .fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            contentPadding = contentPadding,
            content = content
        )
    }
}
