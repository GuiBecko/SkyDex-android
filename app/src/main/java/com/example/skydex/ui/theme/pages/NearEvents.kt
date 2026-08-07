package com.example.skydex.ui.theme.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.ServiceLocator
import com.example.skydex.data.remote.dto.NearbyPhenomenonResponse

@Composable
fun NearEvents(modifier: Modifier = Modifier) {
	// 1. Estados da tela (mesmo padrão da página Registers)
	var eventos by remember { mutableStateOf<List<NearbyPhenomenonResponse>>(emptyList()) }
	var isLoading by remember { mutableStateOf(true) }
	var statusMensagem by remember { mutableStateOf("") }

	// 2. Efeito colateral para buscar dados na API ao abrir a tela
	LaunchedEffect(Unit) {
		isLoading = true
		try {

			// TODO: Substitua por coordenadas reais do GPS no futuro
			val lat = -23.55
			val lon = -46.63


			val resposta = ServiceLocator.api.nearbyPhenomena(lat, lon)
			eventos = resposta

		} catch (e: Exception) {
			statusMensagem = "Erro ao carregar eventos: ${e.message}"
		} finally {
			isLoading = false
		}
	}

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

		// 3. Controle do que mostrar (Carregando, Erro, Vazio ou Lista)
		if (isLoading) {
			item {
				Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
					CircularProgressIndicator(color = Color(0xFF0284C7))
				}
			}
		} else if (statusMensagem.isNotEmpty()) {
			item {
				Text(
					text = statusMensagem,
					color = Color.Red,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			}
		} else if (eventos.isEmpty()) {
			item {
				Text(
					text = "Nenhum evento severo detectado na sua região.",
					color = Color.Gray,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			}
		} else {
			// Desenha a lista de eventos vindos da API
			items(eventos) { evento ->
				EventoCard(evento)
			}
		}
	}
}

@Composable
fun EventoCard(evento: NearbyPhenomenonResponse) {
	// Define a cor da tag dependendo do nível de alerta
	val alertaCor = when (evento.alertLevel) {
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
				Text(text = evento.phenomenon, fontWeight = FontWeight.Bold, fontSize = 18.sp)

				val tempTexto = evento.temperatureCelsius?.let { "$it °C" } ?: "Temp. Indisponível"
				Text(text = tempTexto, color = Color.Gray, fontSize = 14.sp)

				Spacer(modifier = Modifier.height(8.dp))

				Surface(
					color = alertaCor.copy(alpha = 0.1f),
					shape = MaterialTheme.shapes.small
				) {
					Text(
						text = evento.alertLevel.uppercase(),
						color = alertaCor,
						fontSize = 10.sp,
						fontWeight = FontWeight.Bold,
						modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
					)
				}
			}

			Column(horizontalAlignment = Alignment.End) {
				Icon(
					imageVector = if (evento.alertLevel.contains("Perigo")) Icons.Default.Warning else Icons.Default.LocationOn,
					contentDescription = "Alerta",
					tint = alertaCor
				)
				val horaFormatada = evento.time.substringAfter("T")
				Text(text = horaFormatada, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun NearEventsPreview() {
	Scaffold(
		bottomBar = {
			FooterSection(
				aoClicarNearEvents = {},
				aoClicarHome = {},
				aoClicarMyRegistros = {},
				abaAtual = "eventos"
			)
		}
	) { innerPadding ->
		NearEvents(modifier = Modifier.padding(innerPadding))
	}
}