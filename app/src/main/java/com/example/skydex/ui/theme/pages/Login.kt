package com.example.skydex.ui.theme.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.RetrofitClient
import com.example.skydex.dto.LoginRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: (token: String, userId: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var mensagemErro by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SkyDex", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0284C7))
        Text("Seu radar meteorológico pessoal", color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (mensagemErro.isNotEmpty()) {
            Text(text = mensagemErro, color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = {
                if (email.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    mensagemErro = ""
                    coroutineScope.launch {
                        try {
                            val request = LoginRequest(email.trim(), password.trim())
                            val resposta = RetrofitClient.api.login(request)
                            // Retorna o token com "Bearer " já embutido e o ID salvo do banco
                            val formatoToken = seTokenJaTemBearer(resposta.tokenGerado)
                            onLoginSuccess(formatoToken, resposta.userId)
                        } catch (e: Exception) {
                            mensagemErro = "Credenciais inválidas ou erro no servidor."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Entrando..." else "Entrar", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToRegister) {
            Text("Não tem uma conta? Registre-se")
        }
    }
}

// Garante que o Bearer está no token
fun seTokenJaTemBearer(token: String): String {
    return if (token.startsWith("Bearer ")) token else "Bearer $token"
}