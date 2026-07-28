package com.example.skydex

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DateRange
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
import com.example.skydex.dto.EventoRequest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FooterSection(
	abaAtual: String,
	aoClicarHome: () -> Unit,
	aoClicarNearEvents: () -> Unit,
	aoClicarMyRegistros: () -> Unit
) {
	val corAtiva = Color(0xFF0284C7)
	val corInativa = Color.Gray

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(Color.White)
			.navigationBarsPadding()
			.padding(16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = Alignment.Start
		) {
			val isSelected = abaAtual == "eventos"
			val iconColor by animateColorAsState(if (isSelected) corAtiva else corInativa, label = "corEventos")
			val iconSize by animateDpAsState(if (isSelected) 36.dp else 28.dp, label = "tamanhoEventos")

			IconButton(onClick = aoClicarNearEvents) {
				Icon(
					modifier = Modifier.size(iconSize),
					imageVector = Icons.Default.WbSunny,
					contentDescription = "Eventos Próximos",
					tint = iconColor
				)
			}
		}

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
			.fillMaxSize()
			.background(Color(0xFFF3F4F6))
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(24.dp)
	) {
		item {
			CabecalhoSection()
		}
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
	val coroutineScope = rememberCoroutineScope()
	var statusMensagem by remember { mutableStateOf("") }
	var isLoading by remember { mutableStateOf(false) }

	var mostrarDialog by remember { mutableStateOf(false) }

	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
		shape = RoundedCornerShape(16.dp),
		elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
	) {
		Column(
			modifier = Modifier.padding(24.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = "Viu algo incomum?",
				color = Color.White,
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold
			)

			Spacer(modifier = Modifier.height(16.dp))

			Button(
				onClick = { mostrarDialog = true },
				colors = ButtonDefaults.buttonColors(containerColor = Color.White),
				enabled = !isLoading
			) {
				Icon(
					imageVector = Icons.Default.Add,
					contentDescription = "Adicionar",
					tint = Color(0xFF0284C7)
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = if (isLoading) "Enviando..." else "Registrar Novo Evento",
					color = Color(0xFF0284C7),
					fontWeight = FontWeight.Bold
				)
			}

			if (statusMensagem.isNotEmpty()) {
				Spacer(modifier = Modifier.height(12.dp))
				Text(
					text = statusMensagem,
					color = Color.White,
					fontSize = 14.sp,
					textAlign = TextAlign.Center
				)
			}
		}
	}

	if (mostrarDialog) {
		FormularioEventoDialog(
			onDismiss = { mostrarDialog = false },
			onConfirm = { titulo, descricao, urlFoto ->
				mostrarDialog = false

				coroutineScope.launch {
					isLoading = true
					statusMensagem = "Enviando para a API..."

					try {
						val meuTokenJwt = "Bearer <token>"

						val novoEvento = EventoRequest(
							titulo = titulo,
							descricao = descricao,
							urlFoto = urlFoto
						)

						val resposta = RetrofitClient.api.criarRegistro(novoEvento, meuTokenJwt)

						statusMensagem = "Sucesso! Evento salvo. ID: ${resposta.id}"
					} catch (e: Exception) {
						statusMensagem = "Erro: ${e.message}"
					} finally {
						isLoading = false
					}
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormularioEventoDialog(
	onDismiss: () -> Unit,
	onConfirm: (String, String, String) -> Unit
) {
	// Configurações e estados da Data
	val hoje = remember { LocalDate.now() }
	val dataDeHojeFormatada = remember { hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }

	// Estado do DatePicker apontando para o dia de hoje (em milissegundos)
	val datePickerState = rememberDatePickerState(
		initialSelectedDateMillis = System.currentTimeMillis()
	)
	var mostrarCalendario by remember { mutableStateOf(false) }

	// Estados do Formulário
	var titulo by remember { mutableStateOf("") }
	var descricao by remember { mutableStateOf("") }
	var urlFoto by remember { mutableStateOf("") }
	var dataRegistro by remember { mutableStateOf(dataDeHojeFormatada) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(text = "Novo Registro Climático", fontWeight = FontWeight.Bold)
		},
		text = {
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = titulo,
					onValueChange = { titulo = it },
					label = { Text("Título") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = descricao,
					onValueChange = { descricao = it },
					label = { Text("Descrição") },
					modifier = Modifier.fillMaxWidth().height(100.dp),
					maxLines = 4
				)

				OutlinedTextField(
					value = urlFoto,
					onValueChange = { urlFoto = it },
					label = { Text("URL da Foto") },
					placeholder = { Text("Link, Galeria ou Câmera") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
					trailingIcon = {
						Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Câmera")
					}
				)

				// Campo de Data (apenas leitura, clicar nele abre o DatePicker)
				OutlinedTextField(
					value = dataRegistro,
					onValueChange = { },
					label = { Text("Data do Registro") },
					readOnly = true, // Impede o teclado padrão de subir
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
					trailingIcon = {
						IconButton(onClick = { mostrarCalendario = true }) {
							Icon(imageVector = Icons.Default.DateRange, contentDescription = "Selecionar Data")
						}
					}
				)
			}
		},
		confirmButton = {
			Button(
				onClick = { onConfirm(titulo, descricao, urlFoto) },
				enabled = titulo.isNotBlank() && descricao.isNotBlank()
			) {
				Text("Salvar Evento")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancelar")
			}
		}
	)

	// O Pop-up do Calendário propriamente dito
	if (mostrarCalendario) {
		DatePickerDialog(
			onDismissRequest = { mostrarCalendario = false },
			confirmButton = {
				TextButton(onClick = {
					mostrarCalendario = false
					// Pega a data selecionada em milissegundos e converte para nossa string "dd/MM/yyyy"
					datePickerState.selectedDateMillis?.let { millis ->
						val dataSelecionada = Instant.ofEpochMilli(millis)
							.atZone(ZoneId.of("UTC"))
							.toLocalDate()
						dataRegistro = dataSelecionada.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
					}
				}) {
					Text("OK")
				}
			},
			dismissButton = {
				TextButton(onClick = { mostrarCalendario = false }) {
					Text("Cancelar")
				}
			}
		) {
			// O componente visual do Calendário (que já vem com botão de trocar para digitação manual)
			DatePicker(state = datePickerState)
		}
	}
}

@Preview(showBackground = true)
@Composable
fun BuildHomeScreenPreview() {
	Scaffold(
		bottomBar = {
			FooterSection(
				aoClicarNearEvents = {},
				aoClicarHome = {},
				aoClicarMyRegistros = {},
				abaAtual = "home"
			)
		}
	) { innerPadding ->
		BuildHomeScreen(modifier = Modifier.padding(innerPadding))
	}
}