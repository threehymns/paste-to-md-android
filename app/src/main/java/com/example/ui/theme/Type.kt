package com.example.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Expressive display/editorial typeface paired with a legible geometric body font and monospace code font
val BrandFontFamily: FontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal)
)

val PlainFontFamily: FontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val MonoFontFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

private val BaseDisplayLarge = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.25).sp
)
private val BaseDisplayMedium = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 45.sp,
    lineHeight = 52.sp,
    letterSpacing = 0.sp
)
private val BaseDisplaySmall = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = 0.sp
)
private val BaseHeadlineLarge = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp
)
private val BaseHeadlineMedium = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.sp
)
private val BaseHeadlineSmall = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)
private val BaseTitleLarge = TextStyle(
    fontFamily = BrandFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)
private val BaseTitleMedium = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)
private val BaseTitleSmall = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)
private val BaseBodyLarge = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.3.sp
)
private val BaseBodyMedium = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.2.sp
)
private val BaseBodySmall = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.35.sp
)
private val BaseLabelLarge = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)
private val BaseLabelMedium = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)
private val BaseLabelSmall = TextStyle(
    fontFamily = PlainFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)

// Unified Material 3 Expressive typography scale (baseline + emphasized tokens)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    displayLarge = BaseDisplayLarge,
    displayMedium = BaseDisplayMedium,
    displaySmall = BaseDisplaySmall,
    headlineLarge = BaseHeadlineLarge,
    headlineMedium = BaseHeadlineMedium,
    headlineSmall = BaseHeadlineSmall,
    titleLarge = BaseTitleLarge,
    titleMedium = BaseTitleMedium,
    titleSmall = BaseTitleSmall,
    bodyLarge = BaseBodyLarge,
    bodyMedium = BaseBodyMedium,
    bodySmall = BaseBodySmall,
    labelLarge = BaseLabelLarge,
    labelMedium = BaseLabelMedium,
    labelSmall = BaseLabelSmall,
    displayLargeEmphasized = BaseDisplayLarge.copy(fontWeight = FontWeight.Bold),
    displayMediumEmphasized = BaseDisplayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmallEmphasized = BaseDisplaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLargeEmphasized = BaseHeadlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMediumEmphasized = BaseHeadlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmallEmphasized = BaseHeadlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLargeEmphasized = BaseTitleLarge.copy(fontWeight = FontWeight.Bold),
    titleMediumEmphasized = BaseTitleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmallEmphasized = BaseTitleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLargeEmphasized = BaseBodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMediumEmphasized = BaseBodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmallEmphasized = BaseBodySmall.copy(fontWeight = FontWeight.Medium),
    labelLargeEmphasized = BaseLabelLarge.copy(fontWeight = FontWeight.Bold),
    labelMediumEmphasized = BaseLabelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmallEmphasized = BaseLabelSmall.copy(fontWeight = FontWeight.Bold)
)
