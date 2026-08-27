package io.github.guibecko.skydex.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * # SkyDex theme contract
 *
 * `SkyDexTheme` is the single source of truth for color, type, shape and spacing. The rules:
 *
 * 1. **No color literal outside `Color.kt`.** Screens read `MaterialTheme.colorScheme.*` for the
 *    standard Material roles and [SkyDexPalette]`.colors.*` for the SkyDex-specific roles Material3
 *    has no slot for (success, notice, danger, tertiary text, strong accent, rarity, alert level).
 * 2. **No `fontSize` / `fontWeight` outside `Type.kt`.** 4 sizes, 2 weights — see `Type.kt`.
 * 3. **No `dp` literal for spacing outside `Spacing.kt`.** See [SkyDexSpacing].
 * 4. **No `RoundedCornerShape` outside `Shape.kt`.** See [SkyDexShapes].
 * 5. **`dynamicColor` defaults to `false`.** The audit (finding B5) found the brand color was being
 *    derived from the user's wallpaper, producing two different "brand" blues in the same fold. In
 *    a collection/gamification app the chromatic identity *is* the product. The parameter is kept
 *    for opt-in experimentation only; nothing in the app should pass `true`.
 *
 * ## Semantic mapping decisions a later agent must know about
 *
 * - `colorScheme.error` maps to **Notice** (warm amber), **not** Danger (red). Product decision:
 *   ordinary failures orient, they do not alarm. Any `Text(color = MaterialTheme.colorScheme.error)`
 *   therefore renders amber. Red is [SkyDexColors.danger] and is RESERVED for genuinely destructive
 *   confirmation (the logout dialog, and nothing else).
 * - `colorScheme.onSurfaceVariant` is **TextSecondary**. This is the app-wide replacement for the
 *   ~30 occurrences of `Color.Gray`, which failed WCAG AA at ~3.5:1 (finding A9).
 * - `colorScheme.secondary` is the same accent family as `primary` (there is a single brand hue);
 *   `tertiary` carries Success so positive moments have a themed slot.
 * - **`colorScheme.primary` is AccentStrong (`0xFF0369A1`), not the brighter `0xFF0284C7`.**
 *   Measured: white on `0xFF0284C7` = 4.10:1 (fails AA), white on `0xFF0369A1` = 5.93:1 (passes).
 *   Since `Button`, `FilledCard` and `FloatingActionButton` all paint `onPrimary` on a `primary`
 *   fill, `primary` has to be the accessible one. The brighter hue is still reachable as
 *   [SkyDexColors.accentDecorative] and is restricted to icons, tracks, borders and >=20sp Bold
 *   display text. Dark theme has no such split — `0xFF38BDF8` measures 8.74:1.
 *
 * ## Name-clash note
 *
 * The composable is `SkyDexTheme` and the token accessor object is **[SkyDexPalette]** — the object
 * could not also be called `SkyDexTheme` because the composable already owns that name in this
 * package. Callers write `SkyDexPalette.colors.success`.
 */

// ---------------------------------------------------------------------------------------------
// Extended tokens (no Material3 slot exists for these)
// ---------------------------------------------------------------------------------------------

/**
 * SkyDex-specific color roles carried alongside the Material3 `ColorScheme`.
 *
 * Read it through [SkyDexPalette.colors], never by constructing it yourself.
 */
@Immutable
data class SkyDexColors(
    /** Positive outcome: capture saved, level up, friend request accepted. */
    val success: Color,
    /** Container behind [success] content. */
    val successContainer: Color,
    /** Warm amber. The non-alarming failure/notice color. Mirrors `colorScheme.error`. */
    val notice: Color,
    /** Container behind [notice] content. Mirrors `colorScheme.errorContainer`. */
    val noticeContainer: Color,
    /** RESERVED for destructive confirmation only. Never for an ordinary failure. */
    val danger: Color,
    /** Highest-emphasis text. Mirrors `colorScheme.onBackground`. */
    val textPrimary: Color,
    /** Secondary text. Mirrors `colorScheme.onSurfaceVariant`. Replaces `Color.Gray`. */
    val textSecondary: Color,
    /** Hints, timestamps, de-emphasised metadata. Still WCAG AA. */
    val textTertiary: Color,
    /**
     * The bright brand sky (`0xFF0284C7` in light). **Decorative only** in light theme — it
     * measures 3.88:1 on the background and fails AA for normal text. Icons, tracks, borders and
     * >=20sp Bold display text only. `colorScheme.primary` deliberately does NOT map here.
     */
    val accentDecorative: Color,
    /**
     * The accessible accent (`0xFF0369A1` in light, 5.63:1). Use for accent-colored TEXT at body or
     * caption size, and as a fill under white text. Same value as `colorScheme.primary`.
     */
    val accentStrong: Color,
    /** Tinted accent background. Mirrors `colorScheme.primaryContainer`. */
    val accentContainer: Color,
    /** Content on top of [accentContainer]. */
    val onAccentContainer: Color,
    val rarityLegendary: Color,
    val rarityEpic: Color,
    val rarityRare: Color,
    val rarityUncommon: Color,
    val rarityCommon: Color,
    val alertExtreme: Color,
    val alertDanger: Color,
    val alertAttention: Color,
    val alertInteresting: Color,
    val alertCalm: Color
)

private val LightSkyDexColors = SkyDexColors(
    success = SuccessLight,
    successContainer = SuccessContainerLight,
    notice = NoticeLight,
    noticeContainer = NoticeContainerLight,
    danger = DangerLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    accentDecorative = AccentLight,
    accentStrong = AccentStrongLight,
    accentContainer = AccentContainerLight,
    onAccentContainer = OnAccentContainerLight,
    rarityLegendary = RarityLegendaryLight,
    rarityEpic = RarityEpicLight,
    rarityRare = RarityRareLight,
    rarityUncommon = RarityUncommonLight,
    rarityCommon = RarityCommonLight,
    alertExtreme = AlertExtremeLight,
    alertDanger = AlertDangerLight,
    alertAttention = AlertAttentionLight,
    alertInteresting = AlertInterestingLight,
    alertCalm = AlertCalmLight
)

private val DarkSkyDexColors = SkyDexColors(
    success = SuccessDark,
    successContainer = SuccessContainerDark,
    notice = NoticeDark,
    noticeContainer = NoticeContainerDark,
    danger = DangerDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    // In dark theme AccentDark measures 8.74:1, so there is no decorative-only restriction and the
    // two accent tokens can serve interchangeably.
    accentDecorative = AccentDark,
    accentStrong = AccentStrongDark,
    accentContainer = AccentContainerDark,
    onAccentContainer = OnAccentContainerDark,
    rarityLegendary = RarityLegendaryDark,
    rarityEpic = RarityEpicDark,
    rarityRare = RarityRareDark,
    rarityUncommon = RarityUncommonDark,
    rarityCommon = RarityCommonDark,
    alertExtreme = AlertExtremeDark,
    alertDanger = AlertDangerDark,
    alertAttention = AlertAttentionDark,
    alertInteresting = AlertInterestingDark,
    alertCalm = AlertCalmDark
)

/**
 * Extended SkyDex tokens for the current theme. Defaults to the light set so a composable rendered
 * outside `SkyDexTheme` still gets sane values instead of crashing.
 */
val LocalSkyDexColors = staticCompositionLocalOf { LightSkyDexColors }

/**
 * Accessor for the SkyDex tokens that Material3's `ColorScheme` has no slot for.
 *
 * Named `SkyDexPalette` (and not `SkyDexTheme`) because the composable below already owns the
 * `SkyDexTheme` name in this package.
 *
 * ```
 * Text("+60 XP", color = SkyDexPalette.colors.success)
 * ```
 */
object SkyDexPalette {
    val colors: SkyDexColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSkyDexColors.current
}

// ---------------------------------------------------------------------------------------------
// Material3 color schemes
// ---------------------------------------------------------------------------------------------

private val LightColorScheme = lightColorScheme(
    // AccentStrong, NOT Accent: white-on-Accent measures 4.10:1 and fails AA, while
    // white-on-AccentStrong measures 5.93:1. Button / FilledCard / FAB all paint onPrimary on a
    // primary fill, so primary must be the accessible one. AccentLight remains available as its
    // own token for decorative use — see its KDoc in Color.kt.
    primary = AccentStrongLight,
    onPrimary = Color.White,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,

    secondary = AccentStrongLight,
    onSecondary = Color.White,
    secondaryContainer = AccentContainerLight,
    onSecondaryContainer = OnAccentContainerLight,

    tertiary = SuccessLight,
    onTertiary = Color.White,
    tertiaryContainer = SuccessContainerLight,
    onTertiaryContainer = SuccessLight,

    background = BackgroundLight,
    onBackground = TextPrimaryLight,

    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    // The app-wide replacement for Color.Gray.
    onSurfaceVariant = TextSecondaryLight,

    outline = OutlineLight,
    outlineVariant = OutlineLight,

    // Deliberately Notice (amber), not Danger (red) — see the file KDoc.
    error = NoticeLight,
    onError = Color.White,
    errorContainer = NoticeContainerLight,
    onErrorContainer = NoticeLight,

    scrim = TextPrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = BackgroundDark,
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = OnAccentContainerDark,

    secondary = AccentStrongDark,
    onSecondary = BackgroundDark,
    secondaryContainer = AccentContainerDark,
    onSecondaryContainer = OnAccentContainerDark,

    tertiary = SuccessDark,
    onTertiary = BackgroundDark,
    tertiaryContainer = SuccessContainerDark,
    onTertiaryContainer = SuccessDark,

    background = BackgroundDark,
    onBackground = TextPrimaryDark,

    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,

    outline = OutlineDark,
    outlineVariant = OutlineDark,

    error = NoticeDark,
    onError = BackgroundDark,
    errorContainer = NoticeContainerDark,
    onErrorContainer = NoticeDark,

    scrim = Color.Black
)

// ---------------------------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------------------------

/**
 * Wraps [content] in the SkyDex design system: color scheme, typography and shapes, plus the
 * extended token set exposed through [LocalSkyDexColors] / [SkyDexPalette].
 *
 * @param darkTheme follows the system setting by default.
 * @param dynamicColor **defaults to `false` on purpose** — wallpaper-derived color destroys the
 *   brand identity (audit finding B5). Kept only so the behaviour can be opted into explicitly.
 *   When enabled, the extended [SkyDexColors] are still the SkyDex ones; only the Material roles
 *   change.
 */
@Composable
fun SkyDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val skyDexColors = if (darkTheme) DarkSkyDexColors else LightSkyDexColors

    CompositionLocalProvider(LocalSkyDexColors provides skyDexColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = SkyDexShapes,
            content = content
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Semantic color resolvers
// ---------------------------------------------------------------------------------------------

/**
 * Resolves a backend rarity code to its themed color.
 *
 * Accepted values: `LEGENDARY`, `EPIC`, `RARE`, `UNCOMMON`; anything else falls back to Common.
 *
 * This is the only rarity-to-colour mapping left in the app. The legacy
 * `SkyDexScreen.rarityColor(rarity)` — five hardcoded light-theme hexes that rendered identically in
 * dark mode — is gone; SkyDex, Home and Feed all read this function now.
 */
@Composable
@ReadOnlyComposable
fun rarityColorFor(rarity: String): Color {
    val colors = LocalSkyDexColors.current
    return when (rarity.uppercase()) {
        "LEGENDARY" -> colors.rarityLegendary
        "EPIC" -> colors.rarityEpic
        "RARE" -> colors.rarityRare
        "UNCOMMON" -> colors.rarityUncommon
        else -> colors.rarityCommon
    }
}

/**
 * The rarity code in the user's language: `LENDÁRIO` / `ÉPICO` / `RARO` / `INCOMUM` / `COMUM`.
 *
 * Same accepted values and the same fallback as [rarityColorFor], and it sits next to it so the two
 * cannot drift into disagreeing about what an unrecognised code is — a rarity painted Common but
 * labelled with a raw `SUPER_RARE` would be worse than either alone.
 *
 * Not `@Composable`: it reads no theme. It is here because rarity's presentation lives in one file.
 */
fun rarityLabelFor(rarity: String): String = when (rarity.uppercase()) {
    "LEGENDARY" -> "LENDÁRIO"
    "EPIC" -> "ÉPICO"
    "RARE" -> "RARO"
    "UNCOMMON" -> "INCOMUM"
    else -> "COMUM"
}

/**
 * Resolves a weather alert level to its themed color.
 *
 * The backend sends these already localised in pt-BR: `"Perigo Extremo!"`, `"Perigo"`, `"Atenção"`,
 * `"Interessante"`. Anything else (including an empty string) is treated as calm.
 */
@Composable
@ReadOnlyComposable
fun alertColorFor(alertLevel: String): Color {
    val colors = LocalSkyDexColors.current
    val normalized = alertLevel.trim().lowercase()
    return when {
        normalized.startsWith("perigo extremo") -> colors.alertExtreme
        normalized.startsWith("perigo") -> colors.alertDanger
        normalized.startsWith("atenção") || normalized.startsWith("atencao") -> colors.alertAttention
        normalized.startsWith("interessante") -> colors.alertInteresting
        else -> colors.alertCalm
    }
}
