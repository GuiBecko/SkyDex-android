package io.github.guibecko.skydex.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.data.remote.dto.WeatherEventResponse
import io.github.guibecko.skydex.ui.common.CaptureDate
import io.github.guibecko.skydex.ui.common.CaptureUnavailable
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.common.UiState
import io.github.guibecko.skydex.ui.components.CaptureImage
import io.github.guibecko.skydex.ui.components.SkyDexNotice
import io.github.guibecko.skydex.ui.components.SkyDexNoticeState
import io.github.guibecko.skydex.ui.components.reasonCopyFor
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme
import io.github.guibecko.skydex.ui.theme.rarityColorFor
import io.github.guibecko.skydex.ui.theme.rarityLabelFor

/**
 * # One capture, in full
 *
 * The card in Meus Registros and the card in the Feed are both summaries — a cropped photo, a title,
 * two lines. This is the page they open into: the photo large, everything the backend knows about
 * the capture, the place it was taken, and (in the Feed) who took it.
 *
 * ## What is deliberately absent
 *
 * **There is no delete action.** `CaptureRepository.delete` and `DELETE api/events/{id}` both exist,
 * and a detail page is the obvious place to hang them — which is exactly why this note is here. The
 * user asked for a page that *shows* a capture; nobody asked to be able to destroy one from it, and
 * a destructive action nobody requested, one tap from a list, is not a feature. If it is ever added
 * it needs a confirmation dialog and `SkyDexPalette.colors.danger`, in that order.
 *
 * ## Origin
 *
 * [CaptureOrigin] decides whether the author block renders. From Meus Registros it is the user's own
 * capture and their own name is noise; from the Feed it is someone else's and the first question the
 * screen has to answer is whose.
 *
 * @param viewModel resolves the capture from memory — see [CaptureDetailViewModel] for why there is
 *   no network call and what happens after process death.
 * @param origin where the user came from.
 * @param onBack pops the back stack. Wired to the error state's only action, because a capture that
 *   cannot be resolved has nothing to retry.
 * @param onOpenFriends the one social affordance the backend actually supports — see
 *   [CaptureAuthorCard]. Optional, and passed only for [CaptureOrigin.FEED].
 */
@Composable
fun CaptureDetailScreen(
    viewModel: CaptureDetailViewModel,
    origin: CaptureOrigin,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFriends: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    CaptureDetailContent(
        state = state,
        origin = origin,
        onBack = onBack,
        onOpenFriends = onOpenFriends,
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview`s below can render every branch. */
@Composable
private fun CaptureDetailContent(
    state: UiState<WeatherEventResponse>,
    origin: CaptureOrigin,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFriends: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state) {
            // Unreachable in practice — the lookup is synchronous, so the ViewModel is never in
            // this state. Handled anyway rather than with a `TODO()`: an exhaustive `when` over
            // UiState is what stops a later refactor (a real GET api/events/{id}, say) from
            // silently rendering nothing while it waits.
            is UiState.Loading -> Unit

            // Full-area, and its action is "Voltar" rather than a retry: after process death there
            // is nothing left in memory to resolve against and no endpoint to ask, so offering
            // "tentar de novo" would offer a button that cannot work. See CaptureRegistry's KDoc.
            is UiState.Error -> SkyDexNoticeState(
                message = state.message,
                onAction = onBack,
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Success -> CaptureDetailBody(
                capture = state.data,
                origin = origin,
                onOpenFriends = onOpenFriends
            )
        }
    }
}

@Composable
private fun CaptureDetailBody(
    capture: WeatherEventResponse,
    origin: CaptureOrigin,
    onOpenFriends: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // `verticalScroll` rather than a `LazyColumn`: this is a fixed, known set of blocks, not
            // a list. Lazy composition would buy nothing and would cost the scroll position on every
            // recomposition of a section.
            .verticalScroll(rememberScrollState())
            .padding(
                start = SkyDexSpacing.screenPadding,
                end = SkyDexSpacing.screenPadding,
                top = SkyDexSpacing.lg,
                // `xxl`, not `listBottomPadding`: that 96dp exists to clear the bottom bar, and a
                // pushed destination does not have one. `Scaffold` already pads for the navigation
                // bar itself, so this is breathing room and nothing more.
                bottom = SkyDexSpacing.xxl
            ),
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
    ) {
        // The reason the page exists. Far taller than either card's thumbnail — on the list the
        // photo is an identifier, here it is the subject.
        CaptureImage(
            url = capture.photoUrl,
            contentDescription = capture.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroImageHeight)
        )

        Column(verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
            Text(
                text = capture.title,
                // The one 28sp slot on the screen. A detail page has exactly one subject and this
                // is it.
                style = MaterialTheme.typography.headlineMedium,
                color = SkyDexPalette.colors.textPrimary
            )

            // Dropped rather than printed raw when the timestamp does not parse (audit finding
            // A11). The same rule the two list cards follow.
            CaptureDate.format(capture.capturedAt)?.let { moment ->
                Text(
                    text = moment,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textTertiary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
            // Species and rarity are two different facts and get two different pills. Rarity is the
            // axis the whole game is scored on, so it is also said in words — colour alone is not
            // an accessible channel, the same rule CaptureRewardOverlay follows.
            DetailPill(
                text = capture.phenomenonName,
                color = MaterialTheme.colorScheme.primary
            )
            DetailPill(
                text = rarityLabelFor(capture.rarity),
                color = rarityColorFor(capture.rarity)
            )
        }

        if (capture.description.isNotBlank()) {
            Text(
                text = capture.description,
                style = MaterialTheme.typography.bodyLarge,
                color = SkyDexPalette.colors.textSecondary
            )
        }

        ValidationSection(capture)

        // Only from the Feed — from Meus Registros this is the user's own capture and the block
        // would be telling them their own name. See CaptureOrigin.
        if (origin == CaptureOrigin.FEED) {
            CaptureAuthorCard(authorName = capture.authorName, onOpenFriends = onOpenFriends)
        }

        CaptureLocationCard(
            latitude = capture.latitude,
            longitude = capture.longitude,
            label = capture.title
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Validation and XP
// ---------------------------------------------------------------------------------------------

/**
 * What the capture was worth, in the same voice `CaptureRewardOverlay` uses at the moment it was
 * saved.
 *
 * The two have to agree, and the reason is concrete: a user sees the reward overlay once, right
 * after the shot, and this page any number of times afterwards. If the overlay says "não deu para
 * confirmar, seu registro está guardado" and this page later says something colder about the same
 * capture, the app has changed its mind about the user's photo behind their back.
 *
 * So the branches mirror it exactly:
 * - **CONFIRMED** — the XP is stated, in green, with the trophy. `xpAwarded` gates the number
 *   independently of the status, so a confirmed capture worth nothing never draws a meaningless
 *   "+0 XP".
 * - **anything else** — no XP, no trophy, no rarity celebration and no red. A `SkyDexNotice` in the
 *   app's settled amber register that says what happened, says it was not the user's fault, and
 *   says the record is safe. Shorter than the overlay's copy because by now the user is looking at
 *   the record that survived, not being told about it.
 *
 * The body reuses [reasonCopyFor] rather than one fixed sentence: a weather mismatch is only one of
 * three reasons a capture can end up unconfirmed, and this page is the *permanent* record of the
 * verdict — read far more times than the once-off reward overlay — so it is the more important
 * place to name the actual problem instead of defaulting every capture to "the weather did not
 * match", which is simply false for `MOCK_LOCATION` and `IMPLAUSIBLE_TRAVEL`.
 */
@Composable
private fun ValidationSection(capture: WeatherEventResponse) {
    val confirmed = capture.validationStatus == CONFIRMED_STATUS

    if (!confirmed) {
        SkyDexNotice(
            message = UiMessage(
                title = "Não deu para confirmar o fenômeno",
                // No promise of a re-check: the backend never re-validates a stored capture, and
                // the overlay is careful not to imply one either. `reasonCopyFor` also owns the
                // "kept, no XP" framing already, and degrades safely on a null or unknown reason.
                body = reasonCopyFor(capture.unconfirmedReason),
                tone = Tone.NOTICE
            )
        )
        return
    }

    Surface(
        color = SkyDexPalette.colors.successContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(SkyDexSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                // The heading beside it says "Registro confirmado" in words.
                contentDescription = null,
                tint = SkyDexPalette.colors.success,
                modifier = Modifier.size(SkyDexSpacing.xl)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Registro confirmado",
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyDexPalette.colors.textPrimary
                )
                Text(
                    text = "Os dados meteorológicos da região bateram com a sua foto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textSecondary
                )
            }
            if (capture.xpAwarded > 0) {
                Text(
                    text = "+${capture.xpAwarded} XP",
                    style = MaterialTheme.typography.titleLarge,
                    color = SkyDexPalette.colors.success
                )
            }
        }
    }
}

/** The backend's one status that means "this counted". Everything else is the kind branch. */
private const val CONFIRMED_STATUS = "CONFIRMED"

// ---------------------------------------------------------------------------------------------
// Author and social
// ---------------------------------------------------------------------------------------------

/**
 * Who took this, on a Feed capture.
 *
 * ## The social affordances that exist, and the ones that were refused
 *
 * `SkyDexApi` has no likes, no comments, no reactions, no follows and no per-user profile endpoint.
 * Building any of those buttons here would mean shipping a control that either does nothing or lies
 * about having sent something, which is worse than not having it. They are not implemented.
 *
 * What the API *does* support is friendship: `POST api/friends/requests` (by e-mail),
 * `GET api/friends/requests`, accept, decline and `GET api/friends`. None of that is useful pointed
 * at this author — **`GET api/feed` only ever returns captures by the user's friends**, so the
 * author of anything on this screen is already a friend, and the one action available for a stranger
 * (send an invite) needs their e-mail address, which the capture payload does not carry.
 *
 * So the affordances here are the two that are honest:
 *
 * 1. The **author, named prominently**, with the initial-avatar the rest of the app uses for people.
 * 2. **"Ver amigos"** — real navigation to the Amigos screen, where friendships are actually
 *    managed. It is offered only when the caller wires it up, so a preview or a test composes this
 *    card without a dead button, the same contract the empty-state CTAs use.
 *
 * If the backend later exposes reactions or a public profile, this card is where they go.
 */
@Composable
private fun CaptureAuthorCard(authorName: String, onOpenFriends: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(SkyDexSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuthorAvatar(authorName)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Registrado por",
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textTertiary
                )
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleLarge,
                    color = SkyDexPalette.colors.textPrimary
                )
            }

            if (onOpenFriends != null) {
                TextButton(onClick = onOpenFriends) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(SkyDexSpacing.lg)
                    )
                    Text(
                        text = "Amigos",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = SkyDexSpacing.sm)
                    )
                }
            }
        }
    }
}

/**
 * The author's initial in a tinted circle.
 *
 * There are no avatar images anywhere in the API, so the alternative is a generic person glyph on
 * every card — identical for everyone, which is the opposite of what an avatar is for. A letter at
 * least distinguishes Alice from Bob at a glance.
 *
 * A 48dp box clipped with the 24dp `extraLarge` radius is exactly a circle, which keeps the shape
 * coming from the theme rather than an inline `RoundedCornerShape` — the same trick
 * `SkyDexEmptyState` uses.
 */
@Composable
private fun AuthorAvatar(authorName: String) {
    Box(
        modifier = Modifier
            .size(SkyDexSpacing.xxxl)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = authorName.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

/**
 * A tinted chip. Same construction as the Feed card's badge — a 12% wash of the accent behind the
 * accent itself, which reads as a chip without the label having to fight its own background.
 */
@Composable
private fun DetailPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = PillTintAlpha), shape = MaterialTheme.shapes.small) {
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

private const val PillTintAlpha = 0.12f

/** The photo is the subject here, not a thumbnail: taller than either list card's slot. */
private val HeroImageHeight = SkyDexSpacing.xxxl * 5

// ---------------------------------------------------------------------------------------------
// Previews — both origins, both validation branches, the cold-start miss, light and dark
// ---------------------------------------------------------------------------------------------

private val previewConfirmed = WeatherEventResponse(
    id = "1",
    title = "Relâmpago sobre a represa",
    description = "Peguei a descarga bem no meio do enquadramento, depois de duas horas esperando " +
        "debaixo da marquise.",
    photoUrl = "",
    capturedAt = "2026-08-13T21:40:00Z",
    latitude = -23.5505,
    longitude = -46.6333,
    userId = "u2",
    authorName = "Alice",
    phenomenon = "THUNDERSTORM",
    phenomenonName = "Tempestade com Trovões",
    rarity = "LEGENDARY",
    validationStatus = "CONFIRMED",
    xpAwarded = 400
)

private val previewUnconfirmed = previewConfirmed.copy(
    id = "2",
    title = "Névoa no vale",
    description = "Cinco da manhã, tudo branco lá embaixo.",
    rarity = "COMMON",
    phenomenonName = "Névoa",
    validationStatus = "PENDING",
    // Deliberately not the weather-mismatch reason: this is the branch that used to be wrong on
    // this screen (a hardcoded "não bateram com o clima" sentence for every reason), so the
    // preview exercises the case that would still look broken if `reasonCopyFor` were reverted.
    unconfirmedReason = "MOCK_LOCATION",
    // Zero, because that is what the backend returns on every unconfirmed path.
    xpAwarded = 0
)

@Preview(showBackground = true, name = "Registro — meus, claro", heightDp = 900)
@Composable
private fun CaptureDetailMineLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureDetailContent(
            state = UiState.Success(previewConfirmed),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Registro — meus, escuro",
    backgroundColor = 0xFF0B1220,
    heightDp = 900
)
@Composable
private fun CaptureDetailMineDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureDetailContent(
            state = UiState.Success(previewConfirmed),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro — feed, claro", heightDp = 1000)
@Composable
private fun CaptureDetailFeedLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureDetailContent(
            state = UiState.Success(previewConfirmed),
            origin = CaptureOrigin.FEED,
            onBack = {},
            onOpenFriends = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Registro — feed, escuro",
    backgroundColor = 0xFF0B1220,
    heightDp = 1000
)
@Composable
private fun CaptureDetailFeedDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureDetailContent(
            state = UiState.Success(previewConfirmed),
            origin = CaptureOrigin.FEED,
            onBack = {},
            onOpenFriends = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro — não confirmado, claro", heightDp = 900)
@Composable
private fun CaptureDetailUnconfirmedLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureDetailContent(
            state = UiState.Success(previewUnconfirmed),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Registro — não confirmado, escuro",
    backgroundColor = 0xFF0B1220,
    heightDp = 900
)
@Composable
private fun CaptureDetailUnconfirmedDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureDetailContent(
            state = UiState.Success(previewUnconfirmed),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}

/** The cold-start miss: the registry is empty because the process was killed. */
@Preview(showBackground = true, name = "Registro — indisponível, claro")
@Composable
private fun CaptureDetailUnavailableLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureDetailContent(
            state = UiState.Error(CaptureUnavailable),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro — indisponível, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun CaptureDetailUnavailableDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureDetailContent(
            state = UiState.Error(CaptureUnavailable),
            origin = CaptureOrigin.MINE,
            onBack = {}
        )
    }
}
