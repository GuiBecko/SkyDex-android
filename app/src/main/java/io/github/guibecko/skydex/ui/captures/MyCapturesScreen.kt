package io.github.guibecko.skydex.ui.captures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse
import io.github.guibecko.skydex.ui.common.CaptureDate
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.common.UiState
import io.github.guibecko.skydex.ui.components.CaptureImage
import io.github.guibecko.skydex.ui.components.SkyDexEmptyState
import io.github.guibecko.skydex.ui.components.SkyDexNotice
import io.github.guibecko.skydex.ui.components.SkyDexNoticeState
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme

/**
 * @param onStartCapture where "Registrar evento" goes. **Optional on purpose**: the CTA renders only
 *   when it is wired, so a `@Preview` or a test can compose the screen without a dead button.
 *   `SkyDexNavHost` passes `Routes.CAPTURE` — this is the screen the app lands on right after a
 *   capture is saved, and an empty one with nothing to do next was audit finding A10's worst case.
 * @param onOpenCapture opens the tapped capture's detail page. Optional under the same rule: without
 *   it the cards are inert, so a `@Preview` composes a list that does not pretend to be tappable.
 *   It receives the whole [WeatherEventResponse] rather than an id because the detail screen has no
 *   endpoint to look one up with — the object travels with the tap. See
 *   `io.github.guibecko.skydex.ui.detail.CaptureRegistry`.
 */
@Composable
fun MyCapturesScreen(
    viewModel: MyCapturesViewModel,
    modifier: Modifier = Modifier,
    onStartCapture: (() -> Unit)? = null,
    onOpenCapture: ((WeatherEventResponse) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    MyCapturesContent(
        state = state,
        onRetry = viewModel::refresh,
        onDismissMessage = viewModel::dismissMessage,
        onStartCapture = onStartCapture,
        onOpenCapture = onOpenCapture,
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun MyCapturesContent(
    state: UiState<List<WeatherEventResponse>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissMessage: () -> Unit = {},
    onStartCapture: (() -> Unit)? = null,
    onOpenCapture: ((WeatherEventResponse) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Audit finding A4, and the reason the banner is pinned here rather than being the list's
        // first `item {}`: a failure the user has to scroll back up to find is a failure they will
        // not find. The registers themselves stay untouched below it, and the notice is dismissible
        // so a stale list is still readable.
        if (state is UiState.Success) {
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
        }

        LazyColumn(
            // `weight(1f)` rather than `fillMaxSize()`: the banner takes the height it needs and
            // the list takes the rest, whatever that is at the reader's font scale.
            modifier = Modifier.weight(1f),
            // The title Text that used to be the first item is gone: the NavHost now draws a real
            // TopAppBar with "Meus Registros" (audit finding A8), and two identical headings stacked
            // on top of each other is worse than none. The bottom inset keeps the last card clear of
            // the bottom bar instead of sliding under it (finding M5).
            contentPadding = PaddingValues(
                start = SkyDexSpacing.screenPadding,
                end = SkyDexSpacing.screenPadding,
                top = SkyDexSpacing.lg,
                bottom = SkyDexSpacing.listBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
        ) {
            when (state) {
                is UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Nothing is loaded here, so the full-area variant is the right one — and it comes
                // with a real button. Before this the screen rendered a red sentence and nothing
                // else (audit finding B3): one moment offline broke Meus Registros for the whole
                // session, because `refresh()` had no caller other than `init`.
                is UiState.Error -> item {
                    SkyDexNoticeState(message = state.message, onAction = onRetry)
                }

                is UiState.Success -> if (state.data.isEmpty()) {
                    item {
                        SkyDexEmptyState(
                            icon = Icons.Default.AddAPhoto,
                            title = "Sua coleção começa aqui",
                            body = "Fotografe um fenômeno do céu e ele aparece nesta lista, " +
                                "com XP e raridade.",
                            actionLabel = "Registrar evento".takeIf { onStartCapture != null },
                            onAction = onStartCapture
                        )
                    }
                } else {
                    items(state.data) { capture ->
                        CaptureCard(
                            capture = capture,
                            onClick = onOpenCapture?.let { open -> { open(capture) } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * @param onClick opens this capture's detail page. When it is `null` the card is composed as a plain
 *   `Card` with no ripple and no click semantics at all — a card that looks pressable and is not is
 *   worse than one that never suggested it. The clickable variant is Material3's own `Card(onClick)`
 *   rather than a `Modifier.clickable` on the content, which is what makes the tap target and the
 *   ripple cover the whole card including its padding, instead of only the column inside it.
 */
@Composable
private fun CaptureCard(capture: WeatherEventResponse, onClick: (() -> Unit)? = null) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs)

    if (onClick == null) {
        Card(colors = colors, elevation = elevation, modifier = Modifier.fillMaxWidth()) {
            CaptureCardBody(capture)
        }
    } else {
        Card(
            onClick = onClick,
            colors = colors,
            elevation = elevation,
            modifier = Modifier.fillMaxWidth()
        ) {
            CaptureCardBody(capture)
        }
    }
}

@Composable
private fun CaptureCardBody(capture: WeatherEventResponse) {
    Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
        CaptureImage(
            url = capture.photoUrl,
            contentDescription = capture.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(CaptureImageHeight)
        )

        Spacer(modifier = Modifier.height(SkyDexSpacing.md))

        Text(
            text = capture.title,
            style = MaterialTheme.typography.titleMedium,
            color = SkyDexPalette.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(SkyDexSpacing.xs))
        Text(
            text = capture.description,
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary
        )

        // Unconfirmed captures are kept now instead of being deleted behind the user's back, so
        // the list has to say which ones they are. Without this the user sees a capture worth no
        // XP sitting among ones that are, with nothing to explain it.
        if (!capture.validationStatus.equals("CONFIRMED", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(SkyDexSpacing.xs))
            Text(
                text = "Não confirmada",
                style = MaterialTheme.typography.labelSmall,
                color = SkyDexPalette.colors.textSecondary
            )
        }

        // `"Data: ${capture.capturedAt}"` used to render `Data: 2026-08-07T18:20:00Z` here
        // (audit finding A11). Null means the timestamp did not parse — the line is dropped
        // rather than falling back to the raw string, which would put the ISO text back.
        CaptureDate.format(capture.capturedAt)?.let { moment ->
            Spacer(modifier = Modifier.height(SkyDexSpacing.sm))
            Text(
                text = moment,
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textTertiary
            )
        }
    }
}

/** Photo slot height. Shared with the Feed card so both lists scan at the same rhythm. */
private val CaptureImageHeight = SkyDexSpacing.xxxl * 3

private val previewCaptures = listOf(
    WeatherEventResponse(
        id = "1",
        title = "Cumulonimbus sobre a Paulista",
        description = "Uma torre de nuvens enorme no fim da tarde.",
        photoUrl = "",
        capturedAt = "2026-08-07T18:20:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u1",
        authorName = "Pilot",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "RARE",
        validationStatus = "CONFIRMED",
        xpAwarded = 60
    ),
    WeatherEventResponse(
        id = "2",
        title = "Arco-íris duplo",
        description = "Logo depois da chuva de granizo.",
        photoUrl = "",
        capturedAt = "2026-08-06T16:05:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u1",
        authorName = "Pilot",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "RARE",
        validationStatus = "CONFIRMED",
        xpAwarded = 60
    ),
    // Kept rather than deleted (Task 10), so the badge below has something to render in a preview.
    WeatherEventResponse(
        id = "3",
        title = "Nuvem estranha sobre o bairro",
        description = "Achei que era uma tempestade chegando.",
        photoUrl = "",
        capturedAt = "2026-08-05T14:10:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u1",
        authorName = "Pilot",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "RARE",
        validationStatus = "UNCONFIRMED",
        unconfirmedReason = "PHOTO_CONTRADICTS_WEATHER",
        xpAwarded = 0
    )
)

@Preview(showBackground = true)
@Composable
private fun MyCapturesContentPreview() {
    SkyDexTheme(darkTheme = false) {
        // `onOpenCapture` wired so the preview renders the *clickable* Card variant, which is what
        // the app composes. Without it the preview would silently show a different component.
        MyCapturesContent(
            state = UiState.Success(previewCaptures),
            onRetry = {},
            onOpenCapture = {}
        )
    }
}

@Preview(showBackground = true, name = "Meus registros - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun MyCapturesContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        MyCapturesContent(
            state = UiState.Success(previewCaptures),
            onRetry = {},
            onOpenCapture = {}
        )
    }
}

@Preview(showBackground = true, name = "Meus registros - vazio")
@Composable
private fun MyCapturesContentEmptyPreview() {
    SkyDexTheme(darkTheme = false) {
        MyCapturesContent(
            state = UiState.Success(emptyList()),
            onRetry = {},
            onStartCapture = {}
        )
    }
}

@Preview(showBackground = true, name = "Meus registros - vazio, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun MyCapturesContentEmptyDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        MyCapturesContent(
            state = UiState.Success(emptyList()),
            onRetry = {},
            onStartCapture = {}
        )
    }
}

private val previewFailure = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

@Preview(showBackground = true, name = "Meus registros - erro")
@Composable
private fun MyCapturesContentErrorPreview() {
    SkyDexTheme(darkTheme = false) {
        MyCapturesContent(state = UiState.Error(previewFailure), onRetry = {})
    }
}

/**
 * Audit finding A4, against the preview above it: same failure, but the registers are still there
 * and the notice sits over them instead of replacing them.
 */
@Preview(showBackground = true, name = "Meus registros - falha ao atualizar")
@Composable
private fun MyCapturesContentStalePreview() {
    SkyDexTheme(darkTheme = false) {
        MyCapturesContent(
            state = UiState.Success(previewCaptures, staleMessage = previewFailure),
            onRetry = {},
            onDismissMessage = {}
        )
    }
}
