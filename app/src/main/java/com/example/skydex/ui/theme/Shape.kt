package com.example.skydex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SkyDex corner radii, wired into `MaterialTheme.shapes`.
 *
 * CONTRACT: no screen or component declares `RoundedCornerShape(n.dp)` inline. Use
 * `MaterialTheme.shapes.*`:
 *
 * - [Shapes.extraSmall] 4.dp — badges, tiny tags
 * - [Shapes.small] 8.dp — chips, text fields, small buttons
 * - [Shapes.medium] 12.dp — cards (the app's default)
 * - [Shapes.large] 16.dp — hero cards, bottom sheets
 * - [Shapes.extraLarge] 24.dp — dialogs, full-bleed containers
 */
val SkyDexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
