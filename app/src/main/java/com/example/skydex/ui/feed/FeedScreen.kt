package com.example.skydex.ui.feed

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.ui.common.UiState
import com.example.skydex.ui.skydex.rarityColor

@Composable
fun FeedScreen(viewModel: FeedViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF3F4F6)).padding(16.dp)) {
        when (val current = state) {
            is UiState.Loading -> CircularProgressIndicator(
                color = Color(0xFF0284C7),
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Error -> Text(
                current.message,
                color = Color(0xFFB91C1C),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Success -> if (current.data.isEmpty()) {
                Text(
                    "Nada por aqui ainda. Adicione amigos para ver os registros deles!",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("Feed", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    items(current.data) { capture -> FeedCard(capture) }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(capture: WeatherEventResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(capture.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0284C7))
            Spacer(Modifier.height(8.dp))

            AsyncImage(
                model = capture.photoUrl,
                contentDescription = capture.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.LightGray)
            )

            Spacer(Modifier.height(12.dp))
            Text(capture.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(capture.description, color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(capture.phenomenonName, rarityColor(capture.rarity))
                if (capture.validationStatus == "CONFIRMED") {
                    Badge("CONFIRMADO +${capture.xpAwarded} XP", Color(0xFF10B981))
                } else {
                    Badge("NÃO CONFIRMADO", Color(0xFF6B7280))
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
