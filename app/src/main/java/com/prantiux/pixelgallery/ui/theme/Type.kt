package com.prantiux.pixelgallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.prantiux.pixelgallery.R

@OptIn(ExperimentalTextApi::class)
fun getDisplaySettings(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.width(85f),
    FontVariation.Setting("opsz", 30f),
    FontVariation.Setting("ROND", 100f)
)

@OptIn(ExperimentalTextApi::class)
fun getZenithTimeSettings(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.width(100f),
    FontVariation.Setting("opsz", 40f),
    FontVariation.Setting("ROND", 100f) // Fully rounded
)

@OptIn(ExperimentalTextApi::class)
fun getZenithHeadingSettings(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.width(105f),
    FontVariation.Setting("opsz", 24f),
    FontVariation.Setting("ROND", 100f) // Soft, round corners
)

@OptIn(ExperimentalTextApi::class)
fun getHeadlineSettings(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.width(115f),
    FontVariation.Setting("opsz", 32f),
    FontVariation.Setting("ROND", 100f)
)

@OptIn(ExperimentalTextApi::class)
fun getBodySettings(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.width(100f),
    FontVariation.Setting("opsz", 16f),
    FontVariation.grade(20),
    FontVariation.Setting("ROND", 100f)
)

@OptIn(ExperimentalTextApi::class)
val displayFont = FontFamily(
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Normal, variationSettings = getDisplaySettings(400))
)

@OptIn(ExperimentalTextApi::class)
val zenithTimeFont = FontFamily(
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Light, variationSettings = getZenithTimeSettings(300)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Normal, variationSettings = getZenithTimeSettings(400)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.ExtraBold, variationSettings = getZenithTimeSettings(800)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Black, variationSettings = getZenithTimeSettings(900))
)

@OptIn(ExperimentalTextApi::class)
val zenithHeadingFont = FontFamily(
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Medium, variationSettings = getZenithHeadingSettings(500)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.SemiBold, variationSettings = getZenithHeadingSettings(600)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Bold, variationSettings = getZenithHeadingSettings(700)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.ExtraBold, variationSettings = getZenithHeadingSettings(800)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Black, variationSettings = getZenithHeadingSettings(900))
)

@OptIn(ExperimentalTextApi::class)
val headlineFont = FontFamily(
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Normal, variationSettings = getHeadlineSettings(400)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Medium, variationSettings = getHeadlineSettings(500)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.SemiBold, variationSettings = getHeadlineSettings(600)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Bold, variationSettings = getHeadlineSettings(700))
)

@OptIn(ExperimentalTextApi::class)
val bodyFont = FontFamily(
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Normal, variationSettings = getBodySettings(400)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Medium, variationSettings = getBodySettings(500)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.SemiBold, variationSettings = getBodySettings(600)),
    Font(resId = R.font.google_sans_flex_variable, weight = FontWeight.Bold, variationSettings = getBodySettings(700))
)

// Material 3 Typography with Expressive styling matching Zenith
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = headlineFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
