package io.github.guibecko.skydex.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SkyDex spacing scale — an 8-point grid.
 *
 * CONTRACT: every padding, margin, gap and spacer in the app must come from here. No `12.dp`,
 * `6.dp` or `14.dp` literals in screens or components. If a value you need is missing, the answer
 * is to pick the nearest step, not to add a new one.
 *
 * Usage:
 * ```
 * Column(modifier = Modifier.padding(SkyDexSpacing.screenPadding)) { ... }
 * Spacer(Modifier.height(SkyDexSpacing.md))
 * ```
 */
object SkyDexSpacing {

    /** 4.dp — hairline gaps, icon-to-label inside a badge. */
    val xs: Dp = 4.dp

    /** 8.dp — the grid unit. Gap between tightly related elements. */
    val sm: Dp = 8.dp

    /** 12.dp — inner padding of compact cards and chips. */
    val md: Dp = 12.dp

    /** 16.dp — default card padding and gap between siblings in a list. */
    val lg: Dp = 16.dp

    /** 24.dp — gap between sections. */
    val xl: Dp = 24.dp

    /** 32.dp — gap between major blocks. */
    val xxl: Dp = 32.dp

    /** 48.dp — top/bottom breathing room on sparse screens (auth, empty states). */
    val xxxl: Dp = 48.dp

    /** Horizontal inset of any full-screen content. Same on every screen, no exceptions. */
    val screenPadding: Dp = 16.dp

    /**
     * Bottom `contentPadding` for any scrollable list rendered behind the bottom bar, so the last
     * item clears it instead of hiding under it (audit finding M5).
     */
    val listBottomPadding: Dp = 96.dp
}
