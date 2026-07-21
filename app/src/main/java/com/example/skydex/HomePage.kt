package com.example.skydex // Mude para o pacote correto do seu projeto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun BuildHomeScreen(modifier: Modifier = Modifier) {
	// LazyColumn é o equivalente a uma lista com "overflow-y: auto".
	// Ele só renderiza o que aparece na tela, otimizando a memória.
	LazyColumn(
		modifier = modifier
			.fillMaxSize() // width: 100%, height: 100%
			.background(Color(0xFFF3F4F6)) // Cor de fundo leve (cinza claro)
			.padding(16.dp), // padding: 16px
		verticalArrangement = Arrangement.spacedBy(24.dp) // gap: 24px entre as seções
	) {
		// Seção 1: Cabeçalho (Boas-vindas)
		item {
			CabecalhoSection()
		}

		// Seção 2: Call to Action (Botão de registrar novo evento)
		item {
			CardAcaoPrincipal()
		}

		// Seção 3: Título da lista de eventos recentes
		item {
			Text(
				text = "Eventos Recentes",
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold,
				color = Color.DarkGray
			)
		}

		// Seção 4: Lista de cards com os eventos (Mock de dados)
		val eventosMock = listOf(
			EventoMeteorologico("Tempestade Severa", "Ventos de 80km/h", "Hoje, 14:30"),
			EventoMeteorologico("Granizo", "Pedras de 3cm", "Ontem, 18:15"),
			EventoMeteorologico("Seca Extrema", "Umidade abaixo de 15%", "18/07/2026")
		)

		// Renderiza um card para cada item da lista (equivalente a um .map() no React)
		items(eventosMock) { evento ->
			EventoCard(evento)
			Spacer(modifier = Modifier.height(8.dp)) // Espaço entre os cards
		}
	}
}

// --- COMPONENTES MENORES (Isolados para manter o código limpo) ---

@Composable
fun CabecalhoSection() {
	Column {
		Text(
			text = "Olá, Meteorologista \uD83D\uDC4B",
			fontSize = 28.sp,
			fontWeight = FontWeight.ExtraBold,
			color = Color(0xFF1F2937)
		)
		Text(
			text = "Como está o clima na sua região hoje?",
			fontSize = 16.sp,
			color = Color.Gray,
			modifier = Modifier.padding(top = 4.dp)
		)
	}
}

@Composable
fun CardAcaoPrincipal() {
	// Card é uma "div" com bordas arredondadas e sombra nativa
	Card(
		modifier = Modifier.fillMaxWidth(), // width: 100%
		colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)), // Fundo Azul
		shape = RoundedCornerShape(16.dp), // border-radius: 16px
		elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // box-shadow
	) {
		Column(
			modifier = Modifier.padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally // align-items: center
		) {
			Text(
				text = "Viu algo incomum?",
				color = Color.White,
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold
			)
			Spacer(modifier = Modifier.height(16.dp))
			Button(
				onClick = { /* Lógica para abrir tela de registro no futuro */ },
				colors = ButtonDefaults.buttonColors(containerColor = Color.White)
			) {
				Icon(
					imageVector = Icons.Default.Add,
					contentDescription = "Adicionar",
					tint = Color(0xFF0284C7)
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(text = "Registrar Novo Evento", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
			}
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

// Classe de dados simples (equivalente a uma interface/type no TypeScript)
data class EventoMeteorologico(val titulo: String, val descricao: String, val data: String)

// Esta função permite que você veja a tela no painel "Design" do Android Studio sem precisar rodar no emulador!
@Preview(showBackground = true)
@Composable
fun BuildHomeScreenPreview() {
	BuildHomeScreen()
}
