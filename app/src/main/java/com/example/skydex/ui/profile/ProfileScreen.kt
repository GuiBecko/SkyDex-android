package com.example.skydex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skydex.data.remote.dto.BadgeResponse
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.data.remote.dto.UserSummary
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.components.SkyDexNoticeState
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import java.time.Duration
import java.time.Instant

// ---------------------------------------------------------------------------------------------
// Local dimensions
// ---------------------------------------------------------------------------------------------
// Elevation and touch-target minimums are not spacing, so they do not belong in `SkyDexSpacing`
// (whose KDoc pins it to an 8-point *spacing* grid). They are declared here, next to their only
// use, mirroring `ThemePreview.HairlineWidth`.

/** The identity card is the screen's hero and lifts furthest off the canvas. */
private val HeroElevation = 8.dp

/** Stat tiles sit just above the canvas. */
private val TileElevation = 2.dp

/** An earned badge lifts; a locked one lies flat, which is half of how "locked" reads. */
private val BadgeElevation = 3.dp
private val LockedBadgeElevation = 0.dp

/** Hairline above the pinned logout bar. */
private val DividerThickness = 1.dp

/**
 * Minimum height of the pinned logout bar — see [ProfileScreen]. Comfortably over the 48dp minimum
 * touch target, and a `min` rather than a fixed height so the strip still grows with the system font
 * scale instead of clipping its own label.
 *
 * It is no longer *also* the size of a hole punched in the content above it. That coupling — one
 * constant used twice, once as `padding(bottom = …)` on the content and once as the bar's own
 * height — is what the layout used to rest on, and it is what broke: the padding was honoured, the
 * height was not, and the two silently disagreed by the height of the whole screen. The screen is
 * now a `Column` whose content carries `weight(1f)`, so the space the bar occupies IS the space
 * removed from the content, measured once by the layout rather than asserted twice by a constant.
 * Nothing can drift, whatever this value or the font scale turns out to be.
 */
private val LogoutBarHeight = 64.dp

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    // `rememberSaveable`, not `remember`: an open confirmation must survive a rotation, otherwise
    // turning the phone silently answers "cancel" for the user. It is pure view state — nothing
    // outside this composable can observe it — so it deliberately does NOT live in the ViewModel.
    var confirmingLogout by rememberSaveable { mutableStateOf(false) }

    // Navigation waits for the ViewModel to confirm the session write actually finished —
    // firing it straight from the button's click handler would race the pending disk write
    // against the ViewModelStore teardown that popping the back stack triggers. See
    // ProfileViewModel.loggedOut for the full reasoning.
    LaunchedEffect(loggedOut) {
        if (loggedOut) onLoggedOut()
    }

    // A `Column`, not a `Box` with the bar overlaid on it.
    //
    // The bar used to be a sibling of the content inside a `fillMaxSize()` `Box`, aligned
    // `BottomCenter`, with the content inset by `padding(bottom = LogoutBarHeight)` to leave a hole
    // for it. That arrangement asserts the bar's height twice — once as the content's inset, once
    // as the bar's own layout — and a `Box` hands its children LOOSE constraints, so the bar's
    // `maxHeight` was the entire content region rather than [LogoutBarHeight]. A `fillMaxSize()`
    // inside it (see [LogoutBar]) took that offer: the "bar" measured the full height of the screen,
    // `align(BottomCenter)` had nothing left to offset, and its opaque surface covered the profile
    // completely.
    //
    // Stacking them removes the whole class of fault. The content takes `weight(1f)` — everything
    // the bar did not take — so the space reserved and the space occupied are the same measurement,
    // not two numbers that have to agree. The bar cannot overlap the content because it is not
    // drawn over it, and nothing can scroll under it because it is outside the scrolling area. That
    // is still the fix for finding B2, arrived at without a magic inset.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val current = state) {
                is UiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )

                is UiState.Error -> SkyDexNoticeState(
                    message = current.message,
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )

                is UiState.Success -> Column(modifier = Modifier.fillMaxSize()) {
                    // Audit finding A4. The identity card, the stats and the badge shelf are not
                    // taken away because a later refresh failed — they stay, and the failure is a
                    // bar above them. It sits INSIDE the weighted region, so it eats into the
                    // profile's own height and never into the logout strip below.
                    current.staleMessage?.let { message ->
                        SkyDexNotice(
                            message = message,
                            onAction = viewModel::refresh,
                            onDismiss = viewModel::dismissMessage,
                            modifier = Modifier.padding(
                                start = SkyDexSpacing.screenPadding,
                                end = SkyDexSpacing.screenPadding,
                                top = SkyDexSpacing.screenPadding
                            )
                        )
                    }

                    // `weight(1f)` rather than `fillMaxSize()`: the banner takes the height it
                    // needs and the profile takes the rest, so the badge shelf still ends exactly
                    // where the logout bar begins.
                    ProfileBody(
                        profile = current.data,
                        onOpenMyCaptures = onOpenMyCaptures,
                        onOpenFriends = onOpenFriends,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Outside the `when`, and that placement is still the whole point.
        //
        // This is the only logout affordance in the app. While it lived inside `ProfileBody` it
        // rendered on `UiState.Success` alone — so when the token expired, `profile()` answered
        // 401, Profile landed in `Error`, and the only way out of a signed-in-but-unauthorised app
        // sat behind a load that could never succeed. The app was soft-bricked until the user
        // cleared its data. Logging out needs no profile data, so it must not depend on having any:
        // the bar renders identically in Loading, Error and Success.
        //
        // (The real fix for the 401 itself is an interceptor that clears the session and returns
        // the user to login; that is deliberately still on the backlog. This makes the dead end
        // escapable in the meantime.)
        //
        // WHY PINNED RATHER THAN A LAST `item {}` OF THE LIST — the two options the audit offered:
        // a list item only exists while there *is* a list, i.e. on `UiState.Success`. Moving the
        // button into the `LazyColumn` would reintroduce the exact soft-brick described above. So
        // it stays pinned, and the overlap is solved the other way: it is a sibling BELOW the
        // scrolling region rather than a surface drawn over it.
        LogoutBar(onLogoutClick = { confirmingLogout = true })
    }

    if (confirmingLogout) {
        LogoutConfirmationDialog(
            onConfirm = {
                confirmingLogout = false
                viewModel.logout()
            },
            onDismiss = { confirmingLogout = false }
        )
    }
}

/**
 * The bottom strip. Opaque `surface` plus a hairline divider, so the scrolling content ends against
 * a real edge rather than fading into the label the way it did when this was a transparent
 * `TextButton` drawn straight onto the `Box`.
 *
 * ## This composable MUST measure to its content's height, never to what is offered
 *
 * That is what went wrong, and it is worth stating as a rule because the shape that broke it looked
 * completely ordinary. The strip was `Box(heightIn(min = …)) { TextButton(Modifier.fillMaxSize()) }`.
 * `heightIn(min = …)` sets a floor and nothing else: the ceiling is whatever the parent offers, and
 * the parent — a `Box` with `fillMaxSize()`, which hands its children LOOSE constraints — offered
 * the entire content region. So `fillMaxSize()` resolved to the full height of the screen, the
 * `Box`, the `Column` and this `Surface` grew with it, `align(BottomCenter)` had a child exactly as
 * tall as its parent and therefore nothing to push down, and the result was a full-screen opaque
 * panel with "Sair da conta" centred on it, painted on top of the profile. The profile loaded fine
 * the whole time; it was simply underneath.
 *
 * The button therefore sizes itself: `fillMaxWidth()` for the strip's width, `heightIn(min = …)` for
 * the touch target, and **nothing that fills the height**. The height it reports is the height it
 * needs — which is what lets [ProfileScreen]'s `Column` give the rest to the content, and what lets
 * the strip grow instead of clipping when the system font scale does.
 *
 * Making the button the strip (rather than centring a smaller button inside a sized `Box`) also
 * keeps the touch target the entire 64dp bar instead of the 40dp a default `TextButton` draws.
 * `RectangleShape` so the ripple fills the strip instead of drawing a 32dp-radius pill across it.
 *
 * Not painted in `danger`. Red here is a permanent alarm on a routine affordance (finding B2) and,
 * under the Phase 1 contract, `danger` belongs to the destructive *confirmation* — which is now
 * where it actually appears, in [LogoutConfirmationDialog].
 */
@Composable
private fun LogoutBar(onLogoutClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(
                thickness = DividerThickness,
                color = MaterialTheme.colorScheme.outline
            )
            TextButton(
                onClick = onLogoutClick,
                shape = RectangleShape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LogoutBarHeight - DividerThickness)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    // The label right next to it already says it; announcing both would make a
                    // screen reader read "sair" twice.
                    contentDescription = null,
                    modifier = Modifier.size(SkyDexSpacing.lg)
                )
                Spacer(Modifier.size(SkyDexSpacing.sm))
                Text("Sair da conta", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * The app's ONE sanctioned use of `SkyDexPalette.colors.danger`. Everything else that can fail is
 * amber (`notice`) — red is spent only here, on the single action that throws work away, and only
 * on the confirm side. "Cancelar" stays neutral so the safe choice is not the loud one.
 */
@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sair da conta?", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                "Você vai precisar entrar de novo com seu e-mail e senha para voltar ao SkyDex. " +
                    "Seus registros e conquistas continuam salvos.",
                style = MaterialTheme.typography.bodyLarge,
                color = SkyDexPalette.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SkyDexPalette.colors.danger
                )
            ) {
                Text("Sair", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = MaterialTheme.typography.titleMedium)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun ProfileBody(
    profile: ProfileResponse,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg),
        // No extra bottom inset: the parent already excludes the logout bar's strip from this
        // composable's layout bounds, so the last badge stops exactly where the bar begins.
        contentPadding = PaddingValues(SkyDexSpacing.screenPadding)
    ) {
        item { IdentityCard(profile) }
        item { StatsRow(profile, onOpenMyCaptures, onOpenFriends) }

        item {
            Text(
                "Conquistas  ${profile.unlockedBadges}/${profile.totalBadges}",
                style = MaterialTheme.typography.titleLarge,
                color = SkyDexPalette.colors.textPrimary
            )
        }

        // Unlocked badges first so the shelf leads with what the user actually earned.
        items(profile.badges.sortedByDescending { it.unlocked }) { badge -> BadgeRow(badge) }
    }
}

@Composable
private fun IdentityCard(profile: ProfileResponse) {
    // `colorScheme.primary` (#0369A1), NOT `accentDecorative` (#0284C7): everything on this card is
    // white text on the fill, and white-on-#0284C7 measures 4.10:1 — it fails WCAG AA. On primary
    // it measures 5.93:1. See the contrast contract in Color.kt.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = HeroElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        val onPrimary = MaterialTheme.colorScheme.onPrimary

        Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
            Text(
                profile.user.name,
                style = MaterialTheme.typography.headlineMedium,
                color = onPrimary
            )
            Text(
                profile.user.email,
                style = MaterialTheme.typography.bodySmall,
                color = onPrimary.copy(alpha = SecondaryOnFillAlpha)
            )

            Spacer(Modifier.height(SkyDexSpacing.lg))

            Text(
                "Nível ${profile.level} · ${profile.totalXp} XP",
                style = MaterialTheme.typography.titleMedium,
                color = onPrimary
            )
            Spacer(Modifier.height(SkyDexSpacing.sm))
            LinearProgressIndicator(
                progress = {
                    val span = profile.totalXp + profile.xpToNextLevel
                    if (span <= 0) 0f else profile.totalXp.toFloat() / span
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SkyDexSpacing.xs),
                color = onPrimary,
                trackColor = onPrimary.copy(alpha = TrackOnFillAlpha)
            )
            Spacer(Modifier.height(SkyDexSpacing.xs))
            Text(
                "Faltam ${profile.xpToNextLevel} XP para o nível ${profile.level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = onPrimary.copy(alpha = SecondaryOnFillAlpha)
            )
        }
    }
}

/** De-emphasised text on a brand fill. Still >=4.5:1 against `primary` in both themes. */
private const val SecondaryOnFillAlpha = 0.8f

/** The unfilled part of the XP bar — non-text, so the 3:1 non-text threshold applies. */
private const val TrackOnFillAlpha = 0.3f

@Composable
private fun StatsRow(
    profile: ProfileResponse,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit
) {
    // `IntrinsicSize.Min` + `fillMaxHeight` on each tile: the three cards are the same height even
    // if one label wraps to a second line at a narrow width. Structural identity (see [StatTile])
    // fixes the M1 mismatch; this keeps it fixed under text scaling and translation.
    Row(
        horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        StatTile(
            value = "${profile.confirmedCaptures}",
            label = "confirmados",
            hint = "de ${profile.totalCaptures}",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onOpenMyCaptures
        )
        StatTile(
            value = "${profile.capturedSpecies}/${profile.totalSpecies}",
            label = "espécies",
            hint = "no SkyDex",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = null
        )
        StatTile(
            value = "${profile.friends}",
            label = "amigos",
            hint = "ver todos",
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = onOpenFriends
        )
    }
}

/**
 * One statistic. Finding M1: the tile used to swap its hint between a `TextButton` (~48dp tall) and
 * a bare `Text` depending on `onClick`, so the three tiles in [StatsRow] came out at three different
 * heights with three different baselines.
 *
 * Now the subtree is *structurally identical* whatever `onClick` is — always three `Text`s, same
 * styles, same paddings. Clickability moved onto the whole card, which also grows the touch target
 * from the hint line to the entire tile (well past 48dp) and only changes the hint's colour.
 */
@Composable
private fun StatTile(
    value: String,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = TileElevation),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(SkyDexSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = SkyDexPalette.colors.textPrimary
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textSecondary
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelLarge,
                // The only difference a clickable tile makes: the hint reads as the affordance.
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    SkyDexPalette.colors.textTertiary
                }
            )
        }
    }
}

@Composable
private fun BadgeRow(badge: BadgeResponse) {
    // `rarityLegendary` is the palette's gold, and a trophy is exactly what it is for. `notice` is
    // the same amber family but means "something needs your attention" — wrong sentence for an
    // achievement the user earned.
    val accent = if (badge.unlocked) {
        SkyDexPalette.colors.rarityLegendary
    } else {
        SkyDexPalette.colors.textTertiary
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (badge.unlocked) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (badge.unlocked) BadgeElevation else LockedBadgeElevation
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SkyDexSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = BadgeHaloAlpha), shape = CircleShape) {
                Icon(
                    imageVector = if (badge.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    // Locked/unlocked is already carried by the "NOVO" chip, the elevation and the
                    // description; the shelf reads fine without the icon repeating it.
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(SkyDexSpacing.sm)
                        .size(SkyDexSpacing.xl)
                )
            }

            Spacer(Modifier.size(SkyDexSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = badge.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (badge.unlocked) {
                            SkyDexPalette.colors.textPrimary
                        } else {
                            SkyDexPalette.colors.textSecondary
                        }
                    )
                    if (isRecent(badge.unlockedAt)) {
                        Spacer(Modifier.size(SkyDexSpacing.sm))
                        Surface(
                            color = SkyDexPalette.colors.successContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "NOVO",
                                style = MaterialTheme.typography.labelLarge,
                                color = SkyDexPalette.colors.success,
                                modifier = Modifier.padding(
                                    horizontal = SkyDexSpacing.sm,
                                    vertical = SkyDexSpacing.xs
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.size(SkyDexSpacing.xs))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textSecondary
                )
            }
        }
    }
}

/** The soft disc behind the badge icon — a tint of the accent, never a second hue. */
private const val BadgeHaloAlpha = 0.15f

/** A badge unlocked in the last day gets a NOVO marker — the payoff for the capture. */
private fun isRecent(unlockedAt: String?): Boolean {
    if (unlockedAt == null) return false
    return try {
        Duration.between(Instant.parse(unlockedAt), Instant.now()).toHours() < 24
    } catch (e: Exception) {
        false
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — light and dark, which is the point (finding B4)
// ---------------------------------------------------------------------------------------------

private val PreviewProfile = ProfileResponse(
    user = UserSummary(
        id = "u1",
        name = "Guilherme Becker",
        email = "pilot@skydex.com",
        joinedAt = "2026-01-04T10:00:00Z"
    ),
    level = 7,
    totalXp = 1420,
    xpToNextLevel = 380,
    confirmedCaptures = 23,
    totalCaptures = 31,
    capturedSpecies = 9,
    totalSpecies = 18,
    friends = 5,
    unlockedBadges = 3,
    totalBadges = 6,
    badges = listOf(
        BadgeResponse(
            achievement = "FIRST_CAPTURE",
            displayName = "Primeiro Registro",
            description = "Registre seu primeiro fenômeno.",
            unlocked = true,
            unlockedAt = Instant.now().toString()
        ),
        BadgeResponse(
            achievement = "THREE_CAPTURES",
            displayName = "Observador",
            description = "Registre três fenômenos diferentes.",
            unlocked = true,
            unlockedAt = "2026-01-20T09:00:00Z"
        ),
        BadgeResponse(
            achievement = "STORM_CHASER",
            displayName = "Caçador de Tempestades",
            description = "Registre uma tempestade com raios.",
            unlocked = false,
            unlockedAt = null
        )
    )
)

@Composable
private fun ProfilePreviewScaffold(staleMessage: UiMessage? = null) {
    // Deliberately the SAME structure as [ProfileScreen] — a `Column`, the content weighted, the bar
    // below it. A preview scaffold that arranges the screen its own way stops being able to show the
    // screen being broken, which is exactly what happened here: the old scaffold reproduced the
    // `Box` + `align(BottomCenter)` shape faithfully enough to look right in the IDE (where the
    // preview is only 820dp tall and the overgrown bar was less obviously wrong) while the real
    // screen was unusable on a device.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            staleMessage?.let { message ->
                SkyDexNotice(
                    message = message,
                    onAction = {},
                    onDismiss = {},
                    modifier = Modifier.padding(
                        start = SkyDexSpacing.screenPadding,
                        end = SkyDexSpacing.screenPadding,
                        top = SkyDexSpacing.screenPadding
                    )
                )
            }
            ProfileBody(
                PreviewProfile,
                onOpenMyCaptures = {},
                onOpenFriends = {},
                modifier = Modifier.weight(1f)
            )
        }
        LogoutBar(onLogoutClick = {})
    }
}

@Preview(name = "Profile — light", showBackground = true, heightDp = 820)
@Composable
private fun ProfileBodyLightPreview() {
    SkyDexTheme(darkTheme = false) { ProfilePreviewScaffold() }
}

@Preview(name = "Profile — dark", showBackground = true, heightDp = 820, backgroundColor = 0xFF0B1220)
@Composable
private fun ProfileBodyDarkPreview() {
    SkyDexTheme(darkTheme = true) { ProfilePreviewScaffold() }
}

/**
 * Audit finding A4 on the one screen where it collides with the B2 fix. The profile stays, the
 * failure is a bar above it — and the logout strip is still [LogoutBarHeight] tall with nothing
 * scrolling into it, because the notice went inside the weighted region rather than beside it.
 */
@Preview(name = "Profile — falha ao atualizar", showBackground = true, heightDp = 820)
@Composable
private fun ProfileBodyStalePreview() {
    SkyDexTheme(darkTheme = false) {
        ProfilePreviewScaffold(
            staleMessage = UiMessage(
                title = "Sem conexão",
                body = "Verifique sua internet e tente de novo.",
                tone = Tone.NOTICE,
                actionLabel = "Tentar de novo"
            )
        )
    }
}

@Preview(name = "Logout dialog — light", showBackground = true)
@Composable
private fun LogoutDialogLightPreview() {
    SkyDexTheme(darkTheme = false) { LogoutConfirmationDialog(onConfirm = {}, onDismiss = {}) }
}

@Preview(name = "Logout dialog — dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun LogoutDialogDarkPreview() {
    SkyDexTheme(darkTheme = true) { LogoutConfirmationDialog(onConfirm = {}, onDismiss = {}) }
}
