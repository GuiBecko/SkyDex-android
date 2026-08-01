package com.example.skydex.ui.theme.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.skydex.RetrofitClient
import coil.compose.AsyncImage
import com.example.skydex.dto.EventoResponse

@Composable
fun Registers(modifier: Modifier = Modifier) {

	var registros by remember { mutableStateOf<List<EventoResponse>>(emptyList()) }
	var isLoading by remember { mutableStateOf(true) }
	var statusMensagem by remember { mutableStateOf("") }


	LaunchedEffect(Unit) {
		isLoading = true
		try {

			val meuTokenJwt = "Bearer <token>"
			val myUserId = "b7ad8bb3-d1e6-4e19-964e-3cd3d60f4988"

			val resposta = RetrofitClient.api.listarUserEvents(myUserId, meuTokenJwt)
			registros = resposta

		} catch (e: Exception) {
			statusMensagem = "Erro ao carregar registros: ${e.message}"
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
				text = "Meus Registros",
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
		} else if (registros.isEmpty()) {
			item {
				Text(
					text = "Você ainda não possui eventos registrados.",
					color = Color.Gray,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			}
		} else {
			// Desenha a lista de eventos vindos da API
			items(registros) { registro ->
				RegistroCard(registro)
			}
		}
	}
}

@Composable
fun RegistroCard(registro: EventoResponse) {
	Card(
		colors = CardDefaults.cardColors(containerColor = Color.White),
		elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
		modifier = Modifier.fillMaxWidth()
	) {
		Column(
			modifier = Modifier.padding(16.dp)
		) {
			// Usando AsyncImage do Coil para puxar a foto da web (URL)
			AsyncImage(
				model = registro.urlFoto,
				contentDescription = registro.titulo,
				modifier = Modifier
					.fillMaxWidth()
					.height(150.dp)
					.background(Color.LightGray), // Fundo enquanto carrega
			)

			Spacer(modifier = Modifier.height(12.dp))

			Text(text = registro.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
			Spacer(modifier = Modifier.height(4.dp))
			Text(text = registro.descricao, color = Color.Gray, fontSize = 14.sp)
			Spacer(modifier = Modifier.height(8.dp))
			Text(text = "Data: ${registro.dataHoraRegistro}", color = Color(0xFF0284C7), fontSize = 12.sp)
		}
	}
}

// Data class atualizada para bater exatamente com os dados retornados pelo seu Spring Boot


@Preview(showBackground = true)
@Composable
fun RegistersPreview() {
	Scaffold(
		bottomBar = {
			FooterSection(
				aoClicarNearEvents = {},
				aoClicarHome = {},
				aoClicarMyRegistros = {},
				abaAtual = "meus registros"
			)
		}
	) { innerPadding ->
		Registers(modifier = Modifier.padding(innerPadding))
	}
}