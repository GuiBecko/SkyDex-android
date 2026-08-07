package com.example.skydex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.ui.theme.pages.*

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			SkyDexTheme {
				// A sessão vive no SessionStore; a tela só guarda a aba atual.
				var telaAtual by remember { mutableStateOf("login") }

				// Se não estiver logado, mostra o fluxo de autenticação sem a barra inferior
				if (telaAtual == "login" || telaAtual == "register") {
					Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
						when (telaAtual) {
							"login" -> LoginScreen(
								modifier = Modifier.padding(innerPadding),
								onNavigateToRegister = { telaAtual = "register" },
								onLoginSuccess = { telaAtual = "home" } // A chave vira aqui e abre o app!
							)
							"register" -> RegisterScreen(
								modifier = Modifier.padding(innerPadding),
								onNavigateToLogin = { telaAtual = "login" },
								onRegisterSuccess = { telaAtual = "login" }
							)
						}
					}
				} else {
					// Fluxo normal do app com o BottomBar navegável
					Scaffold(
						modifier = Modifier.fillMaxSize(),
						bottomBar = {
							FooterSection(
								abaAtual = telaAtual,
								aoClicarHome = { telaAtual = "home" },
								aoClicarNearEvents = { telaAtual = "eventos" },
								aoClicarMyRegistros = { telaAtual = "meus registros" }
							)
						}
					) { innerPadding ->
						// O token é anexado automaticamente pelo AuthInterceptor.
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
}

@Preview(showBackground = true)
@Composable
fun AppCompletoPreview() {
	var telaAtual by remember { mutableStateOf("home") }

	Scaffold(
		modifier = Modifier.fillMaxSize(),
		bottomBar = {
			FooterSection(
				abaAtual = telaAtual,
				aoClicarHome = { telaAtual = "home" },
				aoClicarNearEvents = { telaAtual = "eventos" },
				aoClicarMyRegistros = { telaAtual = "meus registros" }
			)
		}
	) { innerPadding ->

		when (telaAtual) {
			"home" -> BuildHomeScreen(modifier = Modifier.padding(innerPadding))
			"eventos" -> NearEvents(modifier = Modifier.padding(innerPadding))
			"meus registros" -> Registers(modifier = Modifier.padding(innerPadding))
		}
	}
}