package com.example.skydex.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse
import com.example.skydex.ui.common.UiState
import com.example.skydex.util.Coordinates
import com.example.skydex.util.LOCATION_PERMISSIONS

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.loadForCurrentPosition() }

    // Gated by the ViewModel, not by `LaunchedEffect(Unit)` alone: this effect re-runs on every
    // Activity recreation, and the ViewModel outlives them. See HomeViewModel.shouldLoadOnEntry.
    LaunchedEffect(Unit) {
        if (viewModel.shouldLoadOnEntry()) requestLocation.launch(LOCATION_PERMISSIONS)
    }

    HomeContent(
        state = state,
        onStartCapture = onStartCapture,
        // Retry goes through the permission launcher rather than calling the ViewModel straight,
        // which is what the initial load does too. An already-granted permission makes `launch`
        // return immediately with no dialog, so the granted case costs nothing; the case it buys is
        // the user who denied the permission, fixed it, and is now looking at the error — a bare
        // `loadForCurrentPosition()` would take a fix the app is still not allowed to take and put
        // the same message back on screen. The callback calls the ViewModel either way.
        onRetry = { requestLocation.launch(LOCATION_PERMISSIONS) },
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun HomeContent(
    state: UiState<HomeData>,
    onStartCapture: () -> Unit,
    onRetry: () -> Unit,
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
            is UiState.Error -> item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                Text(text = phenomenon.phenomenon, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                val temperature = phenomenon.temperatureCelsius?.let { "$it °C" } ?: "Temp. Indisponível"
                Text(text = temperature, color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))

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
    NearbyPhenomenonResponse("Tempestade elétrica", "2026-08-07T14:30", 21.5, "Perigo"),
    NearbyPhenomenonResponse("Halo solar", "2026-08-07T09:15", 27.0, "Interessante"),
    NearbyPhenomenonResponse("Névoa", "2026-08-07T06:00", null, "Tranquilo")
)

private val previewCoordinates = Coordinates(-23.55, -46.63)

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    HomeContent(
        state = UiState.Success(HomeData(previewCoordinates, previewPhenomena)),
        onStartCapture = {},
        onRetry = {}
    )
}

@Preview(showBackground = true, name = "Eventos próximos - carregando")
@Composable
private fun HomeContentLoadingPreview() {
    HomeContent(state = UiState.Loading, onStartCapture = {}, onRetry = {})
}

@Preview(showBackground = true, name = "Eventos próximos - erro")
@Composable
private fun HomeContentErrorPreview() {
    HomeContent(
        state = UiState.Error("Não foi possível carregar os eventos próximos."),
        onStartCapture = {},
        onRetry = {}
    )
}
