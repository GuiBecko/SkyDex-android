package com.example.skydex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.ui.theme.SkyDexTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			SkyDexTheme {

				// Variável que guarda qual tela deve aparecer (começa na "home")
				var telaAtual by remember { mutableStateOf("home") }

				Scaffold(
					modifier = Modifier.fillMaxSize(),
					bottomBar = {
						FooterSection(
							abaAtual = telaAtual,
							// Quando clicar na casinha, muda a variável para "home"
							aoClicarHome = { telaAtual = "home" },
							// Quando clicar no sol, muda a variável para "eventos"
							aoClicarNearEvents = { telaAtual = "eventos" },
							// Quando clicar no dashboard, muda a variável para "meus registros"
							aoClicarMyRegistros = {telaAtual = "meus registros"}
						)
					}
				) { innerPadding ->

					// O Compose olha para a variável e decide qual função desenhar!
					when (telaAtual) {
						"home" -> BuildHomeScreen(modifier = Modifier.padding(innerPadding))
						"eventos" -> NearEvents(modifier = Modifier.padding(innerPadding))
						"meus registros" -> Registers(modifier = Modifier.padding(innerPadding))
					}
				}
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
fun AppCompletoPreview() {
	// 1. Trazemos a variável de estado para dentro do Preview
	var telaAtual by remember { mutableStateOf("home") }

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		bottomBar = {
			FooterSection(
				abaAtual = telaAtual,
				// 2. Agora os cliques mudam a variável do Preview!
				aoClicarHome = { telaAtual = "home" },
				aoClicarNearEvents = { telaAtual = "eventos" },
				aoClicarMyRegistros = { telaAtual = "meus registros"}
			)
		}
	) { innerPadding ->
		// 3. O Compose do Preview vai reagir a essa troca
		when (telaAtual) {
			"home" -> BuildHomeScreen(modifier = Modifier.padding(innerPadding))
			"eventos" -> NearEvents(modifier = Modifier.padding(innerPadding))
		}
	}
}
