package com.example.skydex // Mude para o pacote correto do seu projeto

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun FooterSection(
	abaAtual: String, // Nova variável para o Footer saber quem está ativo!
	aoClicarHome: () -> Unit,
	aoClicarNearEvents: () -> Unit,
	aoClicarMyRegistros: () -> Unit
){
	// Cores do seu app (usando o azul que você colocou no botão da Home)
	val corAtiva = Color(0xFF0284C7)
	val corInativa = Color.Gray

	Row (
		modifier = Modifier
			.fillMaxWidth()
			.background(Color.White)
			.navigationBarsPadding()
			.padding(16.dp),
		verticalAlignment = Alignment.CenterVertically
	){
		// --- COLUNA 1: EVENTOS ---
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = Alignment.Start
		) {
			// Animadores de Cor e Tamanho
			val isSelected = abaAtual == "eventos"
			val iconColor by animateColorAsState(if (isSelected) corAtiva else corInativa, label = "corEventos")
			val iconSize by animateDpAsState(if (isSelected) 36.dp else 28.dp, label = "tamanhoEventos")

			IconButton(onClick = aoClicarNearEvents) {
				Icon(
					modifier = Modifier.size(iconSize), // Usa o tamanho animado
					imageVector = Icons.Default.WbSunny,
					contentDescription = "Eventos Próximos",
					tint = iconColor // Usa a cor animada
				)
			}
		}

		// --- COLUNA 2: HOME ---
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			val isSelected = abaAtual == "home"
			val iconColor by animateColorAsState(if (isSelected) corAtiva else corInativa, label = "corHome")
			val iconSize by animateDpAsState(if (isSelected) 36.dp else 28.dp, label = "tamanhoHome")

			IconButton(onClick = aoClicarHome) {
				Icon(
					modifier = Modifier.size(iconSize),
					imageVector = Icons.Default.Home,
					contentDescription = "Homepage",
					tint = iconColor
				)
			}
		}

		// --- COLUNA 3: REGISTROS ---
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = Alignment.End
		) {
			val isSelected = abaAtual == "meus registros"
			val iconColor by animateColorAsState(if (isSelected) corAtiva else corInativa, label = "corRegistros")
			val iconSize by animateDpAsState(if (isSelected) 36.dp else 28.dp, label = "tamanhoRegistros")

			IconButton(onClick = aoClicarMyRegistros) {
				Icon(
					modifier = Modifier.size(iconSize),
					imageVector = Icons.Default.Dataset,
					contentDescription = "Meus Registros",
					tint = iconColor
				)
			}
		}
	}
}
@Composable
fun BuildHomeScreen(modifier: Modifier = Modifier) {
	LazyColumn(
		modifier = modifier
			.fillMaxSize() // width: 100%, height: 100%
			.background(Color(0xFFF3F4F6)) // Cor de fundo leve (cinza claro)
			.padding(16.dp),
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
	}
}

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

@Preview(showBackground = true)
@Composable
fun BuildHomeScreenPreview() {
	Scaffold(
		bottomBar = {FooterSection(
			aoClicarNearEvents = {},
			aoClicarHome = {},
			aoClicarMyRegistros = {},
			abaAtual = "home"
		)}
	) { innerPadding ->
		BuildHomeScreen(modifier = Modifier.padding(innerPadding))
	}
}
