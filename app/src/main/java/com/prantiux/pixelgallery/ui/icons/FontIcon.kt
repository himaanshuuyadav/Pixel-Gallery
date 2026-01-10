package com.prantiux.pixelgallery.ui.icons

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Reusable Font Icon Composable
 * 
 * Renders a Google Material Symbols Rounded icon using the font file.
 * 
 * @param unicode The unicode character for the icon (e.g., "\ue5d2" for "close")
 * @param contentDescription Accessibility description for screen readers
 * @param modifier Modifier to apply to the icon
 * @param size Size of the icon in sp (default: 24.sp)
 * @param tint Color to tint the icon (default: LocalContentColor)
 * @param filled Whether to use filled variant (default: false)
 */
@Composable
fun FontIcon(
    unicode: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: TextUnit = 24.sp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false
) {
    Text(
        text = unicode,
        fontFamily = MaterialSymbolsRounded,
        fontSize = size,
        color = tint,
        style = LocalTextStyle.current.merge(
            TextStyle(
                lineHeight = size,
                letterSpacing = 0.sp,
                fontFeatureSettings = if (filled) "'FILL' 1" else "'FILL' 0"
            )
        ),
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics {
                    this.contentDescription = contentDescription
                }
            } else {
                Modifier
            }
        )
    )
}
