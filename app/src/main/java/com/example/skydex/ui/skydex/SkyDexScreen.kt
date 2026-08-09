package com.example.skydex.ui.skydex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.data.remote.dto.SkyDexEntryResponse
import com.example.skydex.data.remote.dto.SkyDexResponse
import com.example.skydex.ui.common.UiState

@Composable
fun SkyDexScreen(viewModel: SkyDexViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFFF3F4F6)).padding(16.dp)
    ) {
        when (val current = state) {
            is UiState.Loading -> CircularProgressIndicator(
                color = Color(0xFF0284C7),
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Error -> Text(
                text = current.message,
                color = Color(0xFFB91C1C),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Success -> CollectionGrid(current.data)
        }
    }
}

@Composable
private fun CollectionGrid(data: SkyDexResponse) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Column {
                Text("Meu SkyDex", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Nível ${data.level} · ${data.totalXp} XP · " +
                        "${data.capturedSpecies}/${data.totalSpecies} espécies",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        val span = data.totalXp + data.xpToNextLevel
                        if (span <= 0) 0f else data.totalXp.toFloat() / span
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFF0284C7)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Faltam ${data.xpToNextLevel} XP para o nível ${data.level + 1}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        items(data.entries) { entry -> SpeciesCard(entry) }
    }
}

@Composable
private fun SpeciesCard(entry: SkyDexEntryResponse) {
    val accent = rarityColor(entry.rarity)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.captured) Color.White else Color(0xFFE5E7EB)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (entry.captured) 4.dp else 0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(130.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.rarity,
                color = if (entry.captured) accent else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (entry.captured) entry.displayName else "???",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (entry.captured) Color(0xFF1F2937) else Color.Gray
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (entry.captured) {
                    "${entry.captureCount} registro${if (entry.captureCount == 1) "" else "s"}"
                } else {
                    "${entry.xpPerCapture} XP ao capturar"
                },
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

internal fun rarityColor(rarity: String): Color = when (rarity) {
    "LEGENDARY" -> Color(0xFFF59E0B)
    "EPIC" -> Color(0xFF8B5CF6)
    "RARE" -> Color(0xFF3B82F6)
    "UNCOMMON" -> Color(0xFF10B981)
    else -> Color(0xFF6B7280)
}
