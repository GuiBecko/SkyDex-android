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
				// Variáveis globais da sessão do usuário
				var telaAtual by remember { mutableStateOf("login") }
				var tokenJwtGlobal by remember { mutableStateOf("") }
				var userIdGlobal by remember { mutableStateOf("") }

				// Se não estiver logado, mostra o fluxo de autenticação sem a barra inferior
				if (telaAtual == "login" || telaAtual == "register") {
					Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
						when (telaAtual) {
							"login" -> LoginScreen(
								modifier = Modifier.padding(innerPadding),
								onNavigateToRegister = { telaAtual = "register" },
								onLoginSuccess = { token, userId ->
									tokenJwtGlobal = token
									userIdGlobal = userId
									telaAtual = "home" // A chave vira aqui e abre o app!
								}
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
						// Injeta os dados da sessão nas telas que precisam fazer chamadas à API
						when (telaAtual) {
							"home" -> BuildHomeScreen(
								modifier = Modifier.padding(innerPadding),
								token = tokenJwtGlobal,
								userId = userIdGlobal
							)
							"eventos" -> NearEvents(
								modifier = Modifier.padding(innerPadding),
								token = tokenJwtGlobal,
								userId = userIdGlobal
							)
							"meus registros" -> Registers(
								modifier = Modifier.padding(innerPadding),
								token = tokenJwtGlobal,
								userId = userIdGlobal
							)
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
			"home" -> BuildHomeScreen(modifier = Modifier.padding(innerPadding), token = "xyz", userId = "123")
			"eventos" -> NearEvents(modifier = Modifier.padding(innerPadding), token = "xyz", userId = "123")
			"meus registros" -> Registers(modifier = Modifier.padding(innerPadding), token = "xyz", userId = "123")
		}
	}
}