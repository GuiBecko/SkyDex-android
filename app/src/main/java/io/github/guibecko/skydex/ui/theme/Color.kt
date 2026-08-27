package io.github.guibecko.skydex.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SkyDex color tokens.
 *
 * These are the ONLY color literals allowed in the app. No screen, component or ViewModel may
 * declare a `Color(0xFF...)` of its own — consume the palette through `SkyDexTheme` via
 * `MaterialTheme.colorScheme.*` (for the standard Material3 roles) or `SkyDexPalette.colors.*`
 * (for the SkyDex-specific roles Material3 has no slot for).
 *
 * Design intent (in the user's words): text must be EASY TO READ, input fields must be COMFORTABLE
 * to write in, and navigating the app must feel LIGHT ("leveza"). Hence an airy cool off-white as
 * the dominant surface, a single brand accent, and a warm — non-alarming — notice color instead of
 * red for ordinary failures.
 *
 * ## Contrast contract
 *
 * Every token below carries its **measured** WCAG contrast ratio, written as
 * `bg X.XX / surface X.XX` — i.e. against [BackgroundLight] (`#F7F9FC`) and against [SurfaceLight]
 * (`#FFFFFF`). AA for normal-size text needs **4.5:1**; AA for large text (>=18.66sp Normal or
 * >=14sp Bold — in this app's scale, 20sp Bold and up) needs **3:1**.
 *
 * These numbers are part of the contract. If you change a hex here, re-measure and update the
 * comment. Two of them ([TextTertiaryLight] and [AccentLight]) sit at or below the line and have
 * explicit usage restrictions in their KDoc — read them before using either.
 */

// ---------------------------------------------------------------------------------------------
// Light theme
// ---------------------------------------------------------------------------------------------

/** Airy cool off-white. The "60%" of the 60/30/10 rule — the app's default canvas. */
val BackgroundLight = Color(0xFFF7F9FC)

/** Cards, sheets and anything that must lift off the canvas. */
val SurfaceLight = Color(0xFFFFFFFF)

/** Recessed surface: input fields, chips, disabled tiles. */
val SurfaceVariantLight = Color(0xFFEEF2F7)

/** Hairline borders and dividers. */
val OutlineLight = Color(0xFFE2E8F0)

/** Primary text. Contrast: bg 16.93 / surface 17.85. Passes AA and AAA everywhere. */
val TextPrimaryLight = Color(0xFF0F172A)

/**
 * Secondary text. Contrast: bg 7.18 / surface 7.58 — passes AA at any size.
 * This REPLACES every use of `Color.Gray` in the app (which measured ~3.5:1 and failed AA).
 */
val TextSecondaryLight = Color(0xFF475569)

/**
 * Tertiary text (hints, timestamps). Contrast: bg **4.51** / surface 4.76.
 *
 * **This is the floor.** 4.51 clears the 4.5:1 AA threshold by 0.01 — there is no margin left.
 * Do NOT lighten this value, and do NOT introduce a fourth, lighter text token below it. If a
 * label feels too loud at this weight, reduce its size or its prominence, not its contrast.
 */
val TextTertiaryLight = Color(0xFF64748B)

/**
 * Brand sky. Contrast: bg **3.88** / surface **4.10**; white text on a fill of this color is
 * **4.10**. All three FAIL WCAG AA (4.5:1) for normal-size text — they only clear the 3:1
 * large-text/non-text threshold.
 *
 * **Decorative use ONLY:** icons, progress-indicator tracks, borders, dividers, decorative fills,
 * and large display text (>=20sp Bold, i.e. `titleLarge` and above).
 * **Never** for body or caption text, and never as a fill with white text on top.
 * For either of those, use [AccentStrongLight].
 */
val AccentLight = Color(0xFF0284C7)

/**
 * The accessible accent. Contrast: bg **5.63** / surface 5.93; white text on a fill of this color
 * is **5.93**. Passes AA comfortably.
 *
 * Use it whenever the accent carries TEXT at body or caption size, and as the fill color whenever
 * white text sits on top. This is what `colorScheme.primary` maps to, so `Button`, `FilledCard`
 * and `FloatingActionButton` land here by default.
 */
val AccentStrongLight = Color(0xFF0369A1)

/** Tinted accent background (selected chip, highlighted card). */
val AccentContainerLight = Color(0xFFE0F2FE)

/** Content drawn on top of [AccentContainerLight]. */
val OnAccentContainerLight = Color(0xFF075985)

/** Positive outcome: capture saved, friend request accepted. Contrast: bg 5.20 / surface 5.48. */
val SuccessLight = Color(0xFF047857)
val SuccessContainerLight = Color(0xFFD1FAE5)

/**
 * Warm amber. THE non-alarming error/notice color: ordinary failures, empty results, permission
 * hints. Material3's `error` role maps here on purpose — see `Theme.kt`.
 *
 * Contrast: bg 4.76 / surface 5.02, and 4.51 on [NoticeContainerLight] — passes AA in all three.
 */
val NoticeLight = Color(0xFFB45309)
val NoticeContainerLight = Color(0xFFFEF3C7)

/**
 * RESERVED. Only for genuinely destructive confirmation (e.g. the logout dialog's confirm action).
 * Never use it for a failed request, a validation message or a hint.
 *
 * Contrast: 6.13 on the light background.
 */
val DangerLight = Color(0xFFB91C1C)

// ---------------------------------------------------------------------------------------------
// Dark theme
// ---------------------------------------------------------------------------------------------

/** Deep navy — never pure black, which reads as heavy and kills the sense of lightness. */
val BackgroundDark = Color(0xFF0B1220)
val SurfaceDark = Color(0xFF151E2E)
val SurfaceVariantDark = Color(0xFF1E293B)
val OutlineDark = Color(0xFF263449)

/** Contrast on [BackgroundDark]: 15.93. */
val TextPrimaryDark = Color(0xFFE8EDF5)

/** Replaces `Color.Gray` in dark theme. Contrast on [BackgroundDark]: 8.69. */
val TextSecondaryDark = Color(0xFFA3B2C7)

/** Contrast on [BackgroundDark]: 5.66. Still the floor of the dark text ramp — do not lighten. */
val TextTertiaryDark = Color(0xFF7D8FA6)

/**
 * Contrast on [BackgroundDark]: **8.74** — unlike its light counterpart, this one passes AA for
 * text at any size, so dark mode has no decorative-only restriction on the accent.
 */
val AccentDark = Color(0xFF38BDF8)
val AccentStrongDark = Color(0xFF7DD3FC)
val AccentContainerDark = Color(0xFF0C3A55)
val OnAccentContainerDark = Color(0xFFBAE6FD)

val SuccessDark = Color(0xFF34D399)
val SuccessContainerDark = Color(0xFF064E3B)

/** Contrast on [BackgroundDark]: 11.22. */
val NoticeDark = Color(0xFFFBBF24)
val NoticeContainerDark = Color(0xFF452C06)

/** RESERVED — see [DangerLight]. */
val DangerDark = Color(0xFFFB7185)

// ---------------------------------------------------------------------------------------------
// Rarity
// ---------------------------------------------------------------------------------------------
// Mutually distinguishable AND readable as text on the light surfaces. Resolve them through
// `rarityColorFor(rarity)` rather than reading these directly, so the theme picks the right set.
// Every light rarity token passes AA as text on white (ratio noted per token).

/** Contrast on white: 5.02. */
val RarityLegendaryLight = Color(0xFFB45309)

/** Contrast on white: 5.70. */
val RarityEpicLight = Color(0xFF7C3AED)

/** Contrast on white: 6.70. */
val RarityRareLight = Color(0xFF1D4ED8)

/** Contrast on white: 5.48. */
val RarityUncommonLight = Color(0xFF047857)

/** Contrast on white: 7.58. */
val RarityCommonLight = Color(0xFF475569)

val RarityLegendaryDark = Color(0xFFFBBF24)
val RarityEpicDark = Color(0xFFC4B5FD)
val RarityRareDark = Color(0xFF93C5FD)
val RarityUncommonDark = Color(0xFF6EE7B7)
val RarityCommonDark = Color(0xFFA3B2C7)

// ---------------------------------------------------------------------------------------------
// Alert level
// ---------------------------------------------------------------------------------------------
// Home's weather severity ladder ("Perigo Extremo!" / "Perigo" / "Atenção" / "Interessante").
// Resolve them through `alertColorFor(alertLevel)`.

val AlertExtremeLight = Color(0xFF9F1239)
val AlertDangerLight = Color(0xFFB91C1C)
val AlertAttentionLight = Color(0xFFB45309)
val AlertInterestingLight = Color(0xFF1D4ED8)
val AlertCalmLight = Color(0xFF047857)

val AlertExtremeDark = Color(0xFFFDA4AF)
val AlertDangerDark = Color(0xFFFCA5A5)
val AlertAttentionDark = Color(0xFFFBBF24)
val AlertInterestingDark = Color(0xFF93C5FD)
val AlertCalmDark = Color(0xFF6EE7B7)
