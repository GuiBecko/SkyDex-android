package com.example.skydex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SkyDex type scale.
 *
 * CONTRACT — **exactly 4 sizes and exactly 2 weights. No exceptions.**
 *
 * | Size | Weight | Role |
 * |------|--------|------|
 * | 28sp | Bold   | screen titles |
 * | 20sp | Bold   | section headers |
 * | 16sp | Bold   | card titles / emphasis |
 * | 16sp | Normal | body — the default reading size |
 * | 12sp | Bold   | buttons, chips, badges |
 * | 12sp | Normal | captions, hints |
 *
 * Sizes: `28 / 20 / 16 / 12`. Weights: `Normal(400) / Bold(700)`.
 * Medium(500) and ExtraBold(800) are deliberately absent — the audit found 12 sizes and 4 weights
 * scattered across the screens (finding A1), which is why no screen may write `fontSize = X.sp` or
 * `fontWeight = ...` inline. Every text style comes from `MaterialTheme.typography.*`.
 *
 * If a future edit "just needs" a 5th size, that is the signal that the layout — not the scale —
 * is wrong. Every Material3 slot below is filled with one of the four sizes so no slot silently
 * falls back to a Material default outside the scale.
 */

private val SkyDexFontFamily = FontFamily.Default

/** 28sp Bold — screen titles. */
private val Display = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.sp
)

/** 20sp Bold — section headers. */
private val Section = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

/** 16sp Bold — card titles and inline emphasis. */
private val Emphasis = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/** 16sp Normal — body copy. The default reading size of the app. */
private val Body = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)

/** 12sp Bold — buttons, chips, badges. */
private val Label = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

/** 12sp Normal — captions and hints. */
private val Caption = TextStyle(
    fontFamily = SkyDexFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)

val Typography = Typography(
    // Display slots are unused by SkyDex screens; they are pinned to the largest size so an
    // accidental use still lands inside the scale.
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,

    headlineLarge = Display,
    headlineMedium = Display,
    headlineSmall = Section,

    titleLarge = Section,
    titleMedium = Emphasis,
    titleSmall = Emphasis,

    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Caption,

    labelLarge = Label,
    labelMedium = Label,
    labelSmall = Label
)
