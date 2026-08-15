package com.example.skydex.ui.skydex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skydex.data.remote.dto.SkyDexEntryResponse
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.components.SkyDexEmptyState
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.components.SkyDexNoticeState
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.ui.theme.rarityColorFor

/**
 * @param onStartCapture where "Registrar evento" goes when the collection is still empty.
 *   **Optional on purpose**: the CTA renders only when it is wired, so a `@Preview` or a test can
 *   compose the screen without a dead button. `SkyDexNavHost` passes `Routes.CAPTURE`
 *   (audit finding A10).
 */
@Composable
fun SkyDexScreen(
    viewModel: SkyDexViewModel,
    modifier: Modifier = Modifier,
    onStartCapture: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    SkyDexContent(
        state = state,
        onRetry = viewModel::refresh,
        onDismissMessage = viewModel::dismissMessage,
        onStartCapture = onStartCapture,
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun SkyDexContent(
    state: UiState<SkyDexResponse>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
    onStartCapture: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            is UiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )

            // Nothing is loaded, so this is the full-area variant — with a retry. It used to be a
            // centred red sentence and no way out (audit finding B3): `refresh()` had no caller
            // besides `init`, so a momentary network failure broke the collection for the whole
            // process lifetime.
            is UiState.Error -> SkyDexNoticeState(
                message = state.message,
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                // Audit finding A4. A collection the user has already opened is not thrown away
                // because a later reload failed — the species stay, and the failure is a bar above
                // them carrying the same retry. Dismissible: the grid is worth browsing on its own.
                state.staleMessage?.let { message ->
                    SkyDexNotice(
                        message = message,
                        onAction = onRetry,
                        onDismiss = onDismissMessage,
                        modifier = Modifier.padding(
                            start = SkyDexSpacing.screenPadding,
                            end = SkyDexSpacing.screenPadding,
                            top = SkyDexSpacing.lg
                        )
                    )
                }

                // `weight(1f)` rather than `fillMaxSize()`: the banner takes the height it needs and
                // the grid takes the rest, whatever that is at the reader's font scale.
                CollectionGrid(
                    data = state.data,
                    onStartCapture = onStartCapture,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The collection, two cards to a row.
 *
 * Rows are laid out explicitly (`chunked` + [SpeciesRow]) rather than by `LazyVerticalGrid`, which
 * is what closes audit finding M8. The grid places the items of a row top-aligned at their own
 * measured heights and never stretches them, so the only way to get two cards of equal height out of
 * it is to pin an exact `height(...)` on the card — and an exact height is precisely what clips the
 * text at a large system font scale. A `Row` measured with `IntrinsicSize.Min` sizes itself to its
 * tallest child instead, and the cards fill it, so the pair matches at every font scale.
 */
@Composable
private fun CollectionGrid(
    data: SkyDexResponse,
    onStartCapture: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        // The "Meu SkyDex" heading that used to open this grid is gone — the NavHost draws it in a
        // real TopAppBar now (audit finding A8). The bottom inset keeps the last row off the bottom
        // bar (finding M5).
        contentPadding = PaddingValues(
            start = SkyDexSpacing.screenPadding,
            end = SkyDexSpacing.screenPadding,
            top = SkyDexSpacing.lg,
            bottom = SkyDexSpacing.listBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.md)
    ) {
        item { ProgressHeader(data) }

        if (data.entries.isEmpty()) {
            item {
                SkyDexEmptyState(
                    icon = Icons.Default.AddAPhoto,
                    title = "Seu SkyDex está esperando",
                    body = "Cada fenômeno que você registrar vira uma espécie desta coleção.",
                    actionLabel = "Registrar evento".takeIf { onStartCapture != null },
                    onAction = onStartCapture
                )
            }
        } else {
            items(
                items = data.entries.chunked(COLUMNS),
                key = { row -> row.first().phenomenon }
            ) { row -> SpeciesRow(row) }
        }
    }
}

/**
 * One row of the collection.
 *
 * `height(IntrinsicSize.Min)` asks the row how tall its tallest child needs to be at its final
 * width, then fixes the row to that; `fillMaxHeight()` makes both cards take it. `heightIn` sits
 * *before* `fillMaxHeight` in each chain on purpose — it has to be the modifier the intrinsic query
 * passes through, so the floor participates in the row's height, and `fillMaxHeight` has to be the
 * last word during measurement so the shorter card still stretches.
 *
 * The trailing `Spacer` is for a collection with an odd number of species: without it the lone card
 * on the last row would take the full width and stop lining up with the column above it.
 */
@Composable
private fun SpeciesRow(entries: List<SkyDexEntryResponse>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md)
    ) {
        entries.forEach { entry ->
            SpeciesCard(
                entry = entry,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SpeciesCardMinHeight)
                    .fillMaxHeight()
            )
        }
        repeat(COLUMNS - entries.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Level, XP and species count — the state of the collection, not a screen title. */
@Composable
private fun ProgressHeader(data: SkyDexResponse) {
    Column {
        Text(
            text = "Nível ${data.level} · ${data.totalXp} XP · " +
                "${data.capturedSpecies}/${data.totalSpecies} espécies",
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary
        )
        Spacer(Modifier.height(SkyDexSpacing.sm))
        LinearProgressIndicator(
            progress = {
                val span = data.totalXp + data.xpToNextLevel
                if (span <= 0) 0f else data.totalXp.toFloat() / span
            },
            modifier = Modifier.fillMaxWidth().height(SkyDexSpacing.sm),
            // A progress track carries no text, so the bright decorative accent is in scope here.
            color = SkyDexPalette.colors.accentDecorative
        )
        Spacer(Modifier.height(SkyDexSpacing.xs))
        Text(
            text = "Faltam ${data.xpToNextLevel} XP para o nível ${data.level + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = SkyDexPalette.colors.textTertiary
        )
        Spacer(Modifier.height(SkyDexSpacing.sm))
    }
}

@Composable
private fun SpeciesCard(entry: SkyDexEntryResponse, modifier: Modifier = Modifier) {
    val accent = rarityColorFor(entry.rarity)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.captured) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (entry.captured) SkyDexSpacing.xs else 0.dp
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SkyDexSpacing.md)) {
            Text(
                text = entry.rarity,
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.captured) accent else SkyDexPalette.colors.textTertiary
            )
            Spacer(Modifier.height(SkyDexSpacing.sm))
            Text(
                text = if (entry.captured) entry.displayName else "???",
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.captured) {
                    SkyDexPalette.colors.textPrimary
                } else {
                    SkyDexPalette.colors.textTertiary
                }
            )
            Spacer(Modifier.height(SkyDexSpacing.sm))
            Text(
                text = if (entry.captured) {
                    "${entry.captureCount} registro${if (entry.captureCount == 1) "" else "s"}"
                } else {
                    "${entry.xpPerCapture} XP ao capturar"
                },
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textSecondary
            )
        }
    }
}

private const val COLUMNS = 2

/**
 * Floor, not a ceiling — the point of audit finding M8.
 *
 * This used to be `Modifier.height(...)`, an exact 144.dp, so that both cards in a row shared a
 * baseline. At `fontScale = 1.5` the species name already wraps to two lines and the XP caption
 * starts losing its descenders; at `2.0` the caption is gone entirely. A minimum keeps the tidy
 * uniform grid for the common case (short names, default font scale) and lets the card grow for
 * everyone else. See [SpeciesRow] for how the two cards stay the same height once one of them grows.
 */
private val SpeciesCardMinHeight = SkyDexSpacing.xxxl * 3

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewSkyDex = SkyDexResponse(
    level = 4,
    totalXp = 640,
    xpToNextLevel = 160,
    capturedSpecies = 2,
    totalSpecies = 4,
    entries = listOf(
        SkyDexEntryResponse(
            phenomenon = "THUNDERSTORM",
            displayName = "Tempestade com Trovões",
            rarity = "RARE",
            xpPerCapture = 60,
            captured = true,
            captureCount = 3,
            firstCapturedAt = "2026-08-07T18:20:00Z"
        ),
        SkyDexEntryResponse(
            phenomenon = "RAINBOW",
            displayName = "Arco-íris",
            rarity = "UNCOMMON",
            xpPerCapture = 40,
            captured = true,
            captureCount = 1,
            firstCapturedAt = "2026-08-06T16:05:00Z"
        ),
        SkyDexEntryResponse(
            phenomenon = "AURORA",
            displayName = "Aurora",
            rarity = "LEGENDARY",
            xpPerCapture = 200,
            captured = false,
            captureCount = 0,
            firstCapturedAt = null
        ),
        SkyDexEntryResponse(
            phenomenon = "FOG",
            displayName = "Névoa",
            rarity = "COMMON",
            xpPerCapture = 20,
            captured = false,
            captureCount = 0,
            firstCapturedAt = null
        )
    )
)

@Preview(showBackground = true, name = "SkyDex")
@Composable
private fun SkyDexContentPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(state = UiState.Success(previewSkyDex), onRetry = {})
    }
}

@Preview(showBackground = true, name = "SkyDex - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun SkyDexContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        SkyDexContent(state = UiState.Success(previewSkyDex), onRetry = {})
    }
}

@Preview(showBackground = true, name = "SkyDex - vazio")
@Composable
private fun SkyDexContentEmptyPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(
            state = UiState.Success(previewSkyDex.copy(entries = emptyList())),
            onRetry = {},
            onStartCapture = {}
        )
    }
}

/**
 * Audit finding M8, made visible in the IDE.
 *
 * At `fontScale = 1.5` "Tempestade com Trovões" wraps to two or three lines in a half-width card.
 * Against the old `Modifier.height(SkyDexSpacing.xxxl * 3)` this preview showed the "3 registros"
 * caption cut off mid-glyph; against [SpeciesCardMinHeight] the row grows and both cards grow with
 * it. If this preview ever shows a clipped line again, someone has put an exact height back.
 */
@Preview(showBackground = true, name = "SkyDex - fonte 150%", fontScale = 1.5f)
@Composable
private fun SkyDexContentLargeFontPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(state = UiState.Success(previewSkyDex), onRetry = {})
    }
}

/**
 * The upper bound Android offers. Every card here is taller than [SpeciesCardMinHeight], so this is
 * the case where the minimum does nothing at all and the row's intrinsic height carries the layout.
 * The entry list is deliberately odd-length, which also exercises [SpeciesRow]'s trailing spacer —
 * without it the lone last card would stretch to the full width.
 */
@Preview(showBackground = true, name = "SkyDex - fonte 200%", fontScale = 2.0f, heightDp = 900)
@Composable
private fun SkyDexContentHugeFontPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(
            state = UiState.Success(previewSkyDex.copy(entries = previewSkyDex.entries.take(3))),
            onRetry = {}
        )
    }
}

private val previewFailure = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

@Preview(showBackground = true, name = "SkyDex - erro")
@Composable
private fun SkyDexContentErrorPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(state = UiState.Error(previewFailure), onRetry = {})
    }
}

/**
 * Audit finding A4, next to the preview above it: the same failure, but the collection is still on
 * screen and the notice sits above it rather than in its place.
 */
@Preview(showBackground = true, name = "SkyDex - falha ao atualizar")
@Composable
private fun SkyDexContentStalePreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexContent(
            state = UiState.Success(previewSkyDex, staleMessage = previewFailure),
            onRetry = {},
            onDismissMessage = {}
        )
    }
}
