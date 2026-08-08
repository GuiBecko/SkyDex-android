package com.example.skydex.ui.captures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.UiState

@Composable
fun MyCapturesScreen(viewModel: MyCapturesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    MyCapturesContent(state = state, modifier = modifier)
}

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun MyCapturesContent(
    state: UiState<List<WeatherEventResponse>>,
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
                text = "Meus Registros",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        when (state) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0284C7))
                }
            }

            is UiState.Error -> item {
                Text(
                    text = state.message,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UiState.Success -> if (state.data.isEmpty()) {
                item {
                    Text(
                        text = "Você ainda não possui eventos registrados.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(state.data) { capture -> CaptureCard(capture) }
            }
        }
    }
}

@Composable
private fun CaptureCard(capture: WeatherEventResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = capture.photoUrl,
                contentDescription = capture.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.LightGray) // Placeholder background while the photo loads.
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(text = capture.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = capture.description, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Data: ${capture.capturedAt}", color = Color(0xFF0284C7), fontSize = 12.sp)
        }
    }
}

private val previewCaptures = listOf(
    WeatherEventResponse(
        id = "1",
        title = "Cumulonimbus sobre a Paulista",
        description = "Uma torre de nuvens enorme no fim da tarde.",
        photoUrl = "",
        capturedAt = "2026-08-07T18:20:00Z",
        userId = "u1",
        authorName = "Pilot"
    ),
    WeatherEventResponse(
        id = "2",
        title = "Arco-íris duplo",
        description = "Logo depois da chuva de granizo.",
        photoUrl = "",
        capturedAt = "2026-08-06T16:05:00Z",
        userId = "u1",
        authorName = "Pilot"
    )
)

@Preview(showBackground = true)
@Composable
private fun MyCapturesContentPreview() {
    MyCapturesContent(state = UiState.Success(previewCaptures))
}

@Preview(showBackground = true, name = "Meus registros - vazio")
@Composable
private fun MyCapturesContentEmptyPreview() {
    MyCapturesContent(state = UiState.Success(emptyList()))
}

@Preview(showBackground = true, name = "Meus registros - erro")
@Composable
private fun MyCapturesContentErrorPreview() {
    MyCapturesContent(state = UiState.Error("Não foi possível carregar seus registros."))
}
