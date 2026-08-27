package io.github.guibecko.skydex.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.guibecko.skydex.data.remote.dto.NearbyPhenomenonResponse
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.common.UiState
import io.github.guibecko.skydex.ui.common.formatEventTime
import io.github.guibecko.skydex.ui.components.SkyDexEmptyState
import io.github.guibecko.skydex.ui.components.SkyDexNotice
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme
import io.github.guibecko.skydex.ui.theme.alertColorFor
import io.github.guibecko.skydex.ui.theme.rarityColorFor
import io.github.guibecko.skydex.util.Coordinates
import io.github.guibecko.skydex.util.LOCATION_PERMISSIONS

/**
 * Shown instead of the ViewModel's message when the permission was actively denied — the screen is
 * the only place that knows the difference between "no fix yet" and "the user said no".
 */
private val PermissionDeniedNotice = UiMessage(
    title = "O SkyDex não pode ver onde você está",
    body = "Ative a permissão de localização em Configurações para ver os eventos da sua região.",
    tone = Tone.NOTICE,
    actionLabel = "Abrir Configurações"
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartCapture: () -> Unit,
    onOpenMyCaptures: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    // Tracks whether the user has actively denied the permission (as opposed to simply never
    // having been asked yet, or having granted it and then lost the fix for some other reason —
    // e.g. GPS switched off). Android will not show the system permission dialog again after a
    // denial, so once this flips true, "ask again" is not a real option: the only way out is
    // Settings. Same flag, same reasoning as CaptureScreen's.
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionDenied = results.values.none { granted -> granted }
        viewModel.loadForCurrentPosition()
    }

    // Gated by the ViewModel, not by `LaunchedEffect(Unit)` alone: this effect re-runs on every
    // Activity recreation, and the ViewModel outlives them. See HomeViewModel.shouldLoadOnEntry.
    LaunchedEffect(Unit) {
        if (viewModel.shouldLoadOnEntry()) requestLocation.launch(LOCATION_PERMISSIONS)
    }

    HomeContent(
        state = state,
        onStartCapture = onStartCapture,
        onOpenMyCaptures = onOpenMyCaptures,
        onDismissMessage = viewModel::dismissMessage,
        // Retry goes through the permission launcher rather than calling the ViewModel straight,
        // which is what the initial load does too. An already-granted permission makes `launch`
        // return immediately with no dialog, so the granted case costs nothing. This is also the
        // ONLY way `permissionDenied` (above) ever clears: that flag is written exclusively from
        // this launcher's result map, so a user who denied, then fixed it in Settings, needs this
        // same retry — re-run through `HomeContent`'s denied branch — to re-check and turn the
        // flag back off. Nothing else in this screen re-evaluates the permission.
        onRetry = { requestLocation.launch(LOCATION_PERMISSIONS) },
        permissionDenied = permissionDenied,
        modifier = modifier
    )
}

/**
 * The screen without its ViewModel, so the `@Preview`s below can render it.
 *
 * Layout note (audit finding M6): the primary CTA is **not** in the scrolling list. It is an
 * extended FAB pinned to the bottom of this `Box`, so "Registrar Novo Evento" stays under the
 * thumb at every scroll position instead of being the second row of a list that scrolls away.
 * The list reserves [SkyDexSpacing.listBottomPadding] at the bottom so the last card clears it —
 * the FAB overlaps nothing, it only floats above the empty tail of the scroll.
 *
 * There is no in-screen title any more: the route's `TopAppBar` owns it (finding A8), and a second
 * copy inside the list would both duplicate it and scroll out of sight.
 */
@Composable
private fun HomeContent(
    state: UiState<HomeData>,
    onStartCapture: () -> Unit,
    onOpenMyCaptures: () -> Unit,
    onRetry: () -> Unit,
    permissionDenied: Boolean = false,
    onDismissMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SkyDexSpacing.screenPadding,
                end = SkyDexSpacing.screenPadding,
                top = SkyDexSpacing.lg,
                // Clears the pinned FAB below. Without it the CTA would sit on top of the last
                // phenomenon card once the list is scrolled to the end.
                bottom = SkyDexSpacing.listBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
        ) {
            // Audit finding A4. Above everything, including the secondary action, because a failure
            // the user has to hunt for is a failure they will not find — and *below* nothing, since
            // the list already holds the phenomena that a destructive `when` would have deleted.
            // Same permission-aware presentation as the empty case: see [LocationNotice].
            if (state is UiState.Success && state.staleMessage != null) {
                item {
                    LocationNotice(
                        message = state.staleMessage,
                        permissionDenied = permissionDenied,
                        onRetry = onRetry,
                        onDismiss = onDismissMessage
                    )
                }
            }

            item { MyCapturesAction(onClick = onOpenMyCaptures) }

            when (state) {
                is UiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SkyDexPalette.colors.accentDecorative)
                    }
                }

                // The message alone would be a dead end. Nothing else on this screen triggers a load —
                // `loadForCurrentPosition` has exactly one caller, the permission-launcher callback —
                // so without this button a user who opened the app offline would have no way back once
                // connectivity returned, short of killing the process. Matches CaptureScreen's copy.
                //
                // A denied permission gets an extra affordance rather than a swapped one. Two different
                // users land here with `permissionDenied` true:
                // - one has NOT fixed it in Settings yet — for them "Tentar novamente" just re-reports
                //   the same denial (Android will not show the system dialog again after a denial), so
                //   they need "Abrir Configurações" to actually do anything about it;
                // - one HAS already fixed it in Settings and is looking at a stale error — for them
                //   "Abrir Configurações" is a no-op detour, and "Tentar novamente" (`onRetry`, the same
                //   permission launcher the non-denied branch uses) is what re-checks and clears
                //   `permissionDenied`.
                // Neither button can tell which user is looking at the screen, so both are offered.
                is UiState.Error -> item {
                    LocationNotice(
                        message = state.message,
                        permissionDenied = permissionDenied,
                        onRetry = onRetry
                    )
                }

                // The last empty state that was still a bare grey sentence (audit finding A10).
                //
                // Unlike every other empty state in the app, this one is GOOD news: an empty list
                // here means no severe weather is heading for the user. So it gets the sun, not the
                // camera; "tranquilo", not "ainda não"; and deliberately **no button** — a CTA would
                // frame a calm sky as something to fix, and "Tentar novamente" would be flatly
                // wrong, since the request succeeded. The capture FAB is already pinned below for
                // anyone who wants to shoot the clear sky anyway.
                is UiState.Success -> if (state.data.phenomena.isEmpty()) {
                    item {
                        SkyDexEmptyState(
                            icon = Icons.Default.WbSunny,
                            title = "Céu tranquilo por aqui",
                            body = "Nenhum evento severo perto de você agora. " +
                                "Assim que algo aparecer na sua região, ele entra nesta lista."
                        )
                    }
                } else {
                    items(state.data.phenomena) { phenomenon -> PhenomenonCard(phenomenon) }
                }
            }
        }

        // The one thing the whole app exists for, kept permanently within thumb reach (finding M6).
        // Filled with `primary` — the accessible accent — because white sits on top of it; the
        // brighter decorative accent measures 4.10:1 under white and would fail AA.
        ExtendedFloatingActionButton(
            onClick = onStartCapture,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SkyDexSpacing.lg)
        ) {
            Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null)
            Spacer(Modifier.width(SkyDexSpacing.sm))
            Text(
                text = "Registrar Novo Evento",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * The one place this screen presents a location failure, used by both cases: the first load, where
 * there is nothing else on screen, and a refresh that failed over a list that is still there
 * (finding A4). Extracted so the permission reasoning below is stated once and cannot drift between
 * the two — a user who revokes the permission after a successful load must be told about Settings on
 * the banner for exactly the reasons it must be told on the empty screen.
 *
 * @param onDismiss non-null only in the stale-refresh case. The empty screen's notice is deliberately
 *   not dismissible: closing it would leave nothing at all, which is the dead end finding B3 named.
 */
@Composable
private fun LocationNotice(
    message: UiMessage,
    permissionDenied: Boolean,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A denied permission is an INSTRUCTION, not a failure. The audit (A3) found
        // this exact sentence painted red — the app telling a user who did nothing
        // wrong that something broke. It is a notice now, like everything else.
        SkyDexNotice(
            message = if (permissionDenied) PermissionDeniedNotice else message,
            onAction = if (permissionDenied) {
                {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            } else {
                onRetry
            },
            onDismiss = onDismiss
        )
        // The denied branch keeps BOTH affordances — the notice carries "Abrir
        // Configurações" and this keeps the retry. The reasoning above is unchanged:
        // one user has not fixed it in Settings yet and needs the intent; another
        // already has and is looking at a stale error, for whom only re-running the
        // permission launcher clears `permissionDenied`. Neither button can tell which
        // user is looking, so both are offered.
        if (permissionDenied) {
            TextButton(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}

/**
 * "Meus Registros" as a real secondary action instead of the orphan `TextButton` the audit found
 * floating between the CTA and the list (finding M7): full card width so it lines up with the
 * phenomenon cards below it, an icon that says what it opens, and a chevron that says it navigates.
 * It reads as belonging to the list it sits above rather than as leftover chrome.
 */
@Composable
private fun MyCapturesAction(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(SkyDexSpacing.xl)
        )
        Spacer(Modifier.width(SkyDexSpacing.md))
        Text(
            text = "Meus Registros",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(SkyDexSpacing.xl)
        )
    }
}

@Composable
private fun PhenomenonCard(phenomenon: NearbyPhenomenonResponse) {
    // Was a five-branch `when` of raw hexes. `alertColorFor` matches case-insensitively by prefix
    // and falls through to Calm on anything unknown, so the same five levels land on the same
    // severities — now with a dark-theme variant each.
    val alertColor = alertColorFor(phenomenon.alertLevel)
    val rarityTint = rarityColorFor(phenomenon.rarity)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Elevation, not spacing — the 8-point grid in `SkyDexSpacing` does not govern shadows.
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(SkyDexSpacing.lg)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phenomenon.phenomenonName,
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyDexPalette.colors.textPrimary
                )

                val temperature = phenomenon.temperatureCelsius?.let { "$it °C" } ?: "Temp. Indisponível"
                Text(
                    text = temperature,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(SkyDexSpacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
                    MetadataBadge(text = phenomenon.alertLevel.uppercase(), tint = alertColor)
                    MetadataBadge(text = phenomenon.rarity, tint = rarityTint)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = if (phenomenon.alertLevel.contains("Perigo")) {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.LocationOn
                    },
                    // The alert level is spelled out in the badge two lines to the left, so the
                    // icon is a repeat of information already in text (finding M9).
                    contentDescription = null,
                    tint = alertColor
                )
                Text(
                    text = formatEventTime(phenomenon.time),
                    style = MaterialTheme.typography.labelLarge,
                    color = SkyDexPalette.colors.textSecondary
                )
            }
        }
    }
}

/** The alert-level / rarity pill. Same shape for both so they read as one row of metadata. */
@Composable
private fun MetadataBadge(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.1f), shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(
                horizontal = SkyDexSpacing.sm,
                vertical = SkyDexSpacing.xs
            )
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — light and dark (audit finding B4)
// ---------------------------------------------------------------------------------------------

private val previewPhenomena = listOf(
    NearbyPhenomenonResponse(
        "THUNDERSTORM", "Tempestade com Trovões", "RARE", "2026-08-07T14:30", 21.5, "Perigo"
    ),
    NearbyPhenomenonResponse(
        "HAILSTORM", "Tempestade Severa com Granizo", "LEGENDARY", "2026-08-07T09:15", 27.0,
        "Perigo Extremo!"
    ),
    NearbyPhenomenonResponse(
        "FOG", "Nevoeiro Intenso", "UNCOMMON", "2026-08-07T06:00", null, "Interessante"
    )
)

private val previewCoordinates = Coordinates(-23.55, -46.63)

private val previewErrorMessage = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

@Composable
private fun HomePreviewHost(darkTheme: Boolean, state: UiState<HomeData>) {
    SkyDexTheme(darkTheme = darkTheme) {
        HomeContent(state = state, onStartCapture = {}, onOpenMyCaptures = {}, onRetry = {})
    }
}

@Preview(showBackground = true, name = "Home — lista, claro")
@Composable
private fun HomeContentPreview() {
    HomePreviewHost(
        darkTheme = false,
        state = UiState.Success(HomeData(previewCoordinates, previewPhenomena))
    )
}

@Preview(showBackground = true, name = "Home — lista, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun HomeContentDarkPreview() {
    HomePreviewHost(
        darkTheme = true,
        state = UiState.Success(HomeData(previewCoordinates, previewPhenomena))
    )
}

@Preview(showBackground = true, name = "Home — vazio, claro")
@Composable
private fun HomeContentEmptyPreview() {
    HomePreviewHost(
        darkTheme = false,
        state = UiState.Success(HomeData(previewCoordinates, emptyList()))
    )
}

@Preview(showBackground = true, name = "Home — carregando, claro")
@Composable
private fun HomeContentLoadingPreview() {
    HomePreviewHost(darkTheme = false, state = UiState.Loading)
}

@Preview(showBackground = true, name = "Home — carregando, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun HomeContentLoadingDarkPreview() {
    HomePreviewHost(darkTheme = true, state = UiState.Loading)
}

@Preview(showBackground = true, name = "Home — erro, claro")
@Composable
private fun HomeContentErrorPreview() {
    HomePreviewHost(darkTheme = false, state = UiState.Error(previewErrorMessage))
}

@Preview(showBackground = true, name = "Home — erro, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun HomeContentErrorDarkPreview() {
    HomePreviewHost(darkTheme = true, state = UiState.Error(previewErrorMessage))
}

/**
 * Audit finding A4, against the two previews above: the same failure, but the phenomena the user was
 * already reading are still on the list and the notice sits over them instead of taking their place.
 */
@Preview(showBackground = true, name = "Home — falha ao atualizar, claro")
@Composable
private fun HomeContentStalePreview() {
    HomePreviewHost(
        darkTheme = false,
        state = UiState.Success(
            HomeData(previewCoordinates, previewPhenomena),
            staleMessage = previewErrorMessage
        )
    )
}

@Preview(showBackground = true, name = "Home — falha ao atualizar, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun HomeContentStaleDarkPreview() {
    HomePreviewHost(
        darkTheme = true,
        state = UiState.Success(
            HomeData(previewCoordinates, previewPhenomena),
            staleMessage = previewErrorMessage
        )
    )
}
