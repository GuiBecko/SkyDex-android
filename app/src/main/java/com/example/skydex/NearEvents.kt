package com.example.skydex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.ModifierLocalReadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NearEvents(modifier: Modifier = Modifier) {
	LazyColumn(
		modifier = modifier
			.fillMaxSize() // width: 100%, height: 100%
			.background(Color(0xFFF3F4F6)) // Cor de fundo leve (cinza claro)
			.padding(16.dp), // padding: 16px
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
		val eventosMock = listOf(
			EventoMeteorologico("Tempestade Severa", "Ventos de 80km/h", "Hoje, 14:30"),
			EventoMeteorologico("Granizo", "Pedras de 3cm", "Ontem, 18:15"),
			EventoMeteorologico("Seca Extrema", "Umidade abaixo de 15%", "18/07/2026")
		)


		items(eventosMock) { evento ->
			EventoCard(evento)
			Spacer(modifier = Modifier.height(8.dp)) // Espaço entre os cards
		}
	}
}

@Composable
fun EventoCard(evento: EventoMeteorologico) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = Color.White),
		elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
	) {
		Row(
			modifier = Modifier
				.padding(16.dp)
				.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween, // justify-content: space-between
			verticalAlignment = Alignment.CenterVertically
		) {
			Column {
				Text(text = evento.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
				Text(text = evento.descricao, color = Color.Gray, fontSize = 14.sp)
			}
			Column(horizontalAlignment = Alignment.End) {
				Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Local", tint = Color.Red)
				Text(text = evento.data, fontSize = 12.sp, color = Color.Gray)
			}
		}
	}
}


data class EventoMeteorologico(val titulo: String, val descricao: String, val data: String)


@Preview(showBackground = true)
@Composable
fun NearEventsPreview() {
	Scaffold(
		bottomBar = { FooterSection(
			aoClicarNearEvents = {},
			aoClicarHome = {},
			aoClicarMyRegistros = {},
			abaAtual = "eventos"
		) }
		) { innerPadding ->
		NearEvents(modifier = Modifier.padding(innerPadding))
	}
}
