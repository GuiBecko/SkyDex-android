package com.example.skydex.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 1.dp hairline for the swatch borders. Local to the preview so `Spacing.kt` stays a pure scale. */
private val HairlineWidth = 1.dp

/**
 * Inspectable swatch sheet for the SkyDex palette, rendered in both light and dark.
 *
 * This exists so the palette can be eyeballed in the IDE without running the app — it is not part
 * of the product UI and must never be referenced from a screen.
 */
@Composable
private fun PaletteSheet() {
    val colors = SkyDexPalette.colors
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SkyDexSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
        ) {
            Text("Paleta SkyDex", style = MaterialTheme.typography.headlineMedium)

            SwatchGroup(
                title = "Superfícies",
                swatches = listOf(
                    "background" to MaterialTheme.colorScheme.background,
                    "surface" to MaterialTheme.colorScheme.surface,
                    "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
                    "outline" to MaterialTheme.colorScheme.outline
                )
            )

            SwatchGroup(
                title = "Texto",
                swatches = listOf(
                    "textPrimary" to colors.textPrimary,
                    "textSecondary" to colors.textSecondary,
                    "textTertiary" to colors.textTertiary
                )
            )

            SwatchGroup(
                title = "Semânticas",
                swatches = listOf(
                    "accentDecorative (só decorativo)" to colors.accentDecorative,
                    "accentStrong = primary" to MaterialTheme.colorScheme.primary,
                    "success" to colors.success,
                    "notice" to colors.notice,
                    "danger" to colors.danger
                )
            )

            SwatchGroup(
                title = "Raridade",
                swatches = listOf(
                    "LEGENDARY" to colors.rarityLegendary,
                    "EPIC" to colors.rarityEpic,
                    "RARE" to colors.rarityRare,
                    "UNCOMMON" to colors.rarityUncommon,
                    "COMMON" to colors.rarityCommon
                )
            )

            SwatchGroup(
                title = "Nível de alerta",
                swatches = listOf(
                    "Perigo Extremo!" to colors.alertExtreme,
                    "Perigo" to colors.alertDanger,
                    "Atenção" to colors.alertAttention,
                    "Interessante" to colors.alertInteresting,
                    "Calmo" to colors.alertCalm
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.xs)) {
                Text("Legibilidade", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Título de tela — 28sp Bold",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary
                )
                Text(
                    "Corpo do texto em 16sp Normal, o tamanho padrão de leitura do app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary
                )
                Text(
                    "Texto secundário — substitui o antigo Color.Gray.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary
                )
                Text(
                    "Legenda em 12sp Normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun SwatchGroup(title: String, swatches: List<Pair<String, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        swatches.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md)
            ) {
                Column(
                    modifier = Modifier
                        .size(SkyDexSpacing.xxl)
                        .background(color, RoundedCornerShape(SkyDexSpacing.sm))
                        .border(
                            width = HairlineWidth,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(SkyDexSpacing.sm)
                        )
                ) {}
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Preview(name = "Palette — light", showBackground = true, heightDp = 1400)
@Composable
private fun PaletteSheetLightPreview() {
    SkyDexTheme(darkTheme = false) { PaletteSheet() }
}

@Preview(name = "Palette — dark", showBackground = true, heightDp = 1400)
@Composable
private fun PaletteSheetDarkPreview() {
    SkyDexTheme(darkTheme = true) { PaletteSheet() }
}
