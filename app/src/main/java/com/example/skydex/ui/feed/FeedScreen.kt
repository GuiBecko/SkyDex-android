package com.example.skydex.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.CaptureDate
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.components.CaptureImage
import com.example.skydex.ui.components.SkyDexEmptyState
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.components.SkyDexNoticeState
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.ui.theme.rarityColorFor

/**
 * @param onOpenFriends where "Ver amigos" goes from the empty feed. **Optional on purpose**: the CTA
 *   renders only when it is wired, so a `@Preview` or a test can compose the screen without a dead
 *   button. `SkyDexNavHost` passes `Routes.FRIENDS` — an empty feed that tells the user to add
 *   friends and then does not offer the way there was audit finding A10.
 * @param onOpenCapture opens the tapped capture's detail page. Optional under the same rule: without
 *   it the cards are inert, so a `@Preview` composes a feed that does not pretend to be tappable. It
 *   receives the whole [WeatherEventResponse] rather than an id because the detail screen has no
 *   endpoint to look one up with — the object travels with the tap. See
 *   `com.example.skydex.ui.detail.CaptureRegistry`.
 */
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier,
    onOpenFriends: (() -> Unit)? = null,
    onOpenCapture: ((WeatherEventResponse) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    // Two separate signals on purpose — see `FeedViewModel.isRefreshing`. `state` drives what the
    // screen *is*; `isRefreshing` drives only the pull indicator, so opening the Feed shows one
    // spinner (the centred `Loading` one) rather than two.
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    FeedContent(
        state = state,
        onRetry = viewModel::refresh,
        isRefreshing = isRefreshing,
        onPullRefresh = viewModel::refreshFromPull,
        onDismissMessage = viewModel::dismissMessage,
        onOpenFriends = onOpenFriends,
        onOpenCapture = onOpenCapture,
        modifier = modifier
    )
}

/**
 * The screen without its ViewModel, so the `@Preview`s below can render it.
 *
 * @param isRefreshing whether the pull gesture is loading. Only the indicator reads it — never the
 *   `when` below, which would defeat the point of keeping it out of [UiState].
 * @param onPullRefresh fired once, when the drag crosses the threshold and is released.
 */
@Composable
private fun FeedContent(
    state: UiState<List<WeatherEventResponse>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onPullRefresh: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onOpenFriends: (() -> Unit)? = null,
    onOpenCapture: ((WeatherEventResponse) -> Unit)? = null
) {
    // Hoisted out of `PullToRefreshBox`'s default so the custom `indicator` below can read the same
    // instance — the box and its indicator must share one state or the arrow never follows the drag.
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onPullRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        indicator = {
            // The stock indicator is `surfaceContainerHigh` + `onSurfaceVariant` — a grey puck on a
            // grey disc. Themed instead: the app's own blue on the same white the cards use, so the
            // thing that appears when you pull looks like it belongs to this app.
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        when (state) {
            is UiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )

            // Full-area, because nothing is loaded — and with a retry. This was a centred red
            // sentence and nothing else (audit finding B3): the feed never reloads on its own, so
            // one failed request left it permanently empty until the app was killed.
            is UiState.Error -> SkyDexNoticeState(
                message = state.message,
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                // The other half of the same story, and audit finding A4. Once a feed exists, a
                // *later* failure is not allowed to take it away: the state kept the posts and the
                // failure arrives here, inline and above them. Dismissible, because the user may
                // simply want to keep reading what is already loaded.
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
                // the feed takes the rest, whatever that turns out to be at the reader's font scale.
                if (state.data.isEmpty()) {
                    // A `LazyColumn` holding one full-height item, not a plain `Box`. It centres the
                    // empty state exactly the same way, but `PullToRefreshBox` listens for nested
                    // scroll and a `Box` never emits any — so on a `Box` the gesture would be dead
                    // on the one screen where the user most wants it, waiting for a friend's first
                    // post. A lazy list that cannot scroll still forwards the whole drag upwards.
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                SkyDexEmptyState(
                                    icon = Icons.Default.Group,
                                    title = "Seu feed começa com os amigos",
                                    body = "Convide alguém e os registros que essa pessoa fizer " +
                                        "aparecem aqui.",
                                    actionLabel = "Ver amigos".takeIf { onOpenFriends != null },
                                    onAction = onOpenFriends
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        // The "Feed" title that used to be the first item is gone — the NavHost
                        // draws it in a real TopAppBar now (audit finding A8). The bottom inset
                        // stops the last card from ending up under the bottom bar (finding M5).
                        contentPadding = PaddingValues(
                            start = SkyDexSpacing.screenPadding,
                            end = SkyDexSpacing.screenPadding,
                            top = SkyDexSpacing.lg,
                            bottom = SkyDexSpacing.listBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
                    ) {
                        items(state.data) { capture ->
                            FeedCard(
                                capture = capture,
                                onClick = onOpenCapture?.let { open -> { open(capture) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * @param onClick opens this capture's detail page. When it is `null` the card is composed as a plain
 *   `Card` with no ripple and no click semantics — a card that looks pressable and is not is worse
 *   than one that never suggested it. The clickable variant is Material3's own `Card(onClick)`
 *   rather than a `Modifier.clickable` on the content, which is what makes the tap target and the
 *   ripple cover the whole card including its padding, instead of only the column inside it.
 */
@Composable
private fun FeedCard(capture: WeatherEventResponse, onClick: (() -> Unit)? = null) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs)

    if (onClick == null) {
        Card(colors = colors, elevation = elevation, modifier = Modifier.fillMaxWidth()) {
            FeedCardBody(capture)
        }
    } else {
        Card(
            onClick = onClick,
            colors = colors,
            elevation = elevation,
            modifier = Modifier.fillMaxWidth()
        ) {
            FeedCardBody(capture)
        }
    }
}

@Composable
private fun FeedCardBody(capture: WeatherEventResponse) {
    Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = capture.authorName,
                style = MaterialTheme.typography.labelLarge,
                // `colorScheme.primary` (0xFF0369A1), not `accentDecorative` (0xFF0284C7):
                // this line is 12sp text and the brighter hue measures 3.88:1, which fails AA.
                color = MaterialTheme.colorScheme.primary
            )
            // The Feed showed no date at all while Meus Registros showed a raw ISO timestamp
            // (audit finding A11). Both now read the same formatter.
            CaptureDate.format(capture.capturedAt)?.let { moment ->
                Text(
                    text = moment,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textTertiary
                )
            }
        }

        Spacer(Modifier.height(SkyDexSpacing.sm))

        CaptureImage(
            url = capture.photoUrl,
            contentDescription = capture.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(FeedImageHeight)
        )

        Spacer(Modifier.height(SkyDexSpacing.md))
        Text(
            text = capture.title,
            style = MaterialTheme.typography.titleMedium,
            color = SkyDexPalette.colors.textPrimary
        )
        Spacer(Modifier.height(SkyDexSpacing.xs))
        Text(
            text = capture.description,
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary
        )
        Spacer(Modifier.height(SkyDexSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
            Badge(capture.phenomenonName, rarityColorFor(capture.rarity))
            if (capture.validationStatus == "CONFIRMED") {
                Badge(
                    text = "Confirmado +${capture.xpAwarded} XP",
                    color = SkyDexPalette.colors.success
                )
            } else {
                Badge(
                    text = "Não confirmado",
                    color = SkyDexPalette.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = BadgeTintAlpha), shape = MaterialTheme.shapes.small) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(
                horizontal = SkyDexSpacing.sm,
                vertical = SkyDexSpacing.xs
            )
        )
    }
}

/** Just enough tint for the badge to read as a chip without competing with its own label. */
private const val BadgeTintAlpha = 0.12f

/** Photo slot height. Taller than the Meus Registros card: the Feed is the browsing surface. */
private val FeedImageHeight = SkyDexSpacing.xxxl * 4

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewFeed = listOf(
    WeatherEventResponse(
        id = "1",
        title = "Relâmpago sobre a represa",
        description = "Peguei a descarga bem no meio do enquadramento.",
        photoUrl = "",
        capturedAt = "2026-08-13T21:40:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u2",
        authorName = "Alice",
        phenomenon = "THUNDERSTORM",
        phenomenonName = "Tempestade com Trovões",
        rarity = "LEGENDARY",
        validationStatus = "CONFIRMED",
        xpAwarded = 120
    ),
    WeatherEventResponse(
        id = "2",
        title = "Névoa no vale",
        description = "Cinco da manhã, tudo branco lá embaixo.",
        photoUrl = "",
        capturedAt = "2026-08-10T08:05:00Z",
        latitude = -23.55,
        longitude = -46.63,
        userId = "u3",
        authorName = "Bob",
        phenomenon = "FOG",
        phenomenonName = "Névoa",
        rarity = "COMMON",
        validationStatus = "PENDING",
        xpAwarded = 20
    )
)

@Preview(showBackground = true, name = "Feed")
@Composable
private fun FeedContentPreview() {
    SkyDexTheme(darkTheme = false) {
        // `onOpenCapture` wired so the preview renders the *clickable* Card variant, which is what
        // the app composes. Without it the preview would silently show a different component.
        FeedContent(state = UiState.Success(previewFeed), onRetry = {}, onOpenCapture = {})
    }
}

@Preview(showBackground = true, name = "Feed - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FeedContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FeedContent(state = UiState.Success(previewFeed), onRetry = {}, onOpenCapture = {})
    }
}

/**
 * The pull indicator mid-refresh, parked at its threshold. Worth a preview of its own because it is
 * the one piece of this screen that never appears in a static render otherwise — and because it is
 * where a stock grey puck would be most obvious next to the app's blue.
 */
@Preview(showBackground = true, name = "Feed - atualizando")
@Composable
private fun FeedContentRefreshingPreview() {
    SkyDexTheme(darkTheme = false) {
        FeedContent(
            state = UiState.Success(previewFeed),
            onRetry = {},
            isRefreshing = true,
            onOpenCapture = {}
        )
    }
}

@Preview(showBackground = true, name = "Feed - atualizando, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FeedContentRefreshingDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FeedContent(
            state = UiState.Success(previewFeed),
            onRetry = {},
            isRefreshing = true,
            onOpenCapture = {}
        )
    }
}

@Preview(showBackground = true, name = "Feed - vazio")
@Composable
private fun FeedContentEmptyPreview() {
    SkyDexTheme(darkTheme = false) {
        FeedContent(state = UiState.Success(emptyList()), onRetry = {}, onOpenFriends = {})
    }
}

@Preview(showBackground = true, name = "Feed - vazio, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FeedContentEmptyDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FeedContent(state = UiState.Success(emptyList()), onRetry = {}, onOpenFriends = {})
    }
}

private val previewFailure = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

@Preview(showBackground = true, name = "Feed - erro")
@Composable
private fun FeedContentErrorPreview() {
    SkyDexTheme(darkTheme = false) {
        FeedContent(state = UiState.Error(previewFailure), onRetry = {})
    }
}

/**
 * Audit finding A4, side by side with the preview above it.
 *
 * Same failure, same copy — but the posts are still there and the notice sits over them instead of
 * where they used to be. If this preview ever renders without the two cards under the banner,
 * someone has put the destructive `when` back.
 */
@Preview(showBackground = true, name = "Feed - falha ao atualizar")
@Composable
private fun FeedContentStalePreview() {
    SkyDexTheme(darkTheme = false) {
        FeedContent(
            state = UiState.Success(previewFeed, staleMessage = previewFailure),
            onRetry = {},
            onDismissMessage = {}
        )
    }
}

@Preview(showBackground = true, name = "Feed - falha ao atualizar, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FeedContentStaleDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FeedContent(
            state = UiState.Success(previewFeed, staleMessage = previewFailure),
            onRetry = {},
            onDismissMessage = {}
        )
    }
}
