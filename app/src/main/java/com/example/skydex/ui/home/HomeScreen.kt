package com.example.skydex.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.skydex.rarityColor
import com.example.skydex.util.Coordinates
import com.example.skydex.util.LOCATION_PERMISSIONS

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

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun HomeContent(
    state: UiState<HomeData>,
    onStartCapture: () -> Unit,
    onOpenMyCaptures: () -> Unit,
    onRetry: () -> Unit,
    permissionDenied: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Eventos Próximos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        item { MainActionCard(onClick = onStartCapture) }

        item {
            TextButton(onClick = onOpenMyCaptures) { Text("Meus Registros") }
        }

        when (state) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0284C7))
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
                val context = LocalContext.current
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (permissionDenied) {
                            "Permissão de localização negada. Ative em Configurações para continuar."
                        } else {
                            state.message
                        },
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (permissionDenied) {
                        TextButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Abrir Configurações")
                        }
                    }
                    TextButton(onClick = onRetry) { Text("Tentar novamente") }
                }
            }

            is UiState.Success -> if (state.data.phenomena.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum evento severo detectado na sua região.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(state.data.phenomena) { phenomenon -> PhenomenonCard(phenomenon) }
            }
        }
    }
}

/**
 * The screen's primary call to action: everything else on Home is informational, this is the one
 * thing the user came to the app to do. Kept visually distinct — filled with the brand accent
 * rather than the white cards below — so it reads as the entry point into the capture flow.
 */
@Composable
private fun MainActionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = "Registrar Novo Evento",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PhenomenonCard(phenomenon: NearbyPhenomenonResponse) {
    val alertColor = when (phenomenon.alertLevel) {
        "Perigo Extremo!" -> Color(0xFFB91C1C)
        "Perigo" -> Color(0xFFEF4444)
        "Atenção" -> Color(0xFFF59E0B)
        "Interessante" -> Color(0xFF3B82F6)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = phenomenon.phenomenonName, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                val temperature = phenomenon.temperatureCelsius?.let { "$it °C" } ?: "Temp. Indisponível"
                Text(text = temperature, color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = alertColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = phenomenon.alertLevel.uppercase(),
                            color = alertColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val rarityTint = rarityColor(phenomenon.rarity)
                    Surface(
                        color = rarityTint.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = phenomenon.rarity,
                            color = rarityTint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    imageVector = if (phenomenon.alertLevel.contains("Perigo")) {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.LocationOn
                    },
                    contentDescription = "Alerta",
                    tint = alertColor
                )
                Text(
                    text = phenomenon.time.substringAfter("T"),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    HomeContent(
        state = UiState.Success(HomeData(previewCoordinates, previewPhenomena)),
        onStartCapture = {},
        onOpenMyCaptures = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Eventos próximos - carregando")
@Composable
private fun HomeContentLoadingPreview() {
    HomeContent(state = UiState.Loading, onStartCapture = {}, onOpenMyCaptures = {}, onRetry = {})
}

@Preview(showBackground = true, name = "Eventos próximos - erro")
@Composable
private fun HomeContentErrorPreview() {
    HomeContent(
        state = UiState.Error("Não foi possível carregar os eventos próximos."),
        onStartCapture = {},
        onOpenMyCaptures = {},
        onRetry = {}
    )
}
