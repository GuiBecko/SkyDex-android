package com.example.skydex.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onRegistered: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.registered) {
        if (state.registered) onRegistered()
    }

    RegisterContent(
        state = state,
        onNameChanged = viewModel::onNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview` below can render it. */
@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Criar Conta", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = { Text("Nome Completo") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text("Senha") },
            supportingText = { Text("Mínimo de ${RegisterViewModel.MIN_PASSWORD_LENGTH} caracteres") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))

        state.errorMessage?.let {
            Text(it, color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !state.submitting
        ) {
            Text(if (state.submitting) "Registrando..." else "Registrar", fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Já possui conta? Faça Login")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterContentPreview() {
    RegisterContent(
        state = RegisterUiState(name = "Pilot", email = "pilot@skydex.com", password = "secret123"),
        onNameChanged = {},
        onEmailChanged = {},
        onPasswordChanged = {},
        onSubmit = {},
        onNavigateToLogin = {}
    )
}

@Preview(showBackground = true, name = "Registro com senha curta")
@Composable
private fun RegisterContentErrorPreview() {
    RegisterContent(
        state = RegisterUiState(
            name = "Pilot",
            email = "pilot@skydex.com",
            password = "curta",
            errorMessage = "A senha deve ter no mínimo 8 caracteres."
        ),
        onNameChanged = {},
        onEmailChanged = {},
        onPasswordChanged = {},
        onSubmit = {},
        onNavigateToLogin = {}
    )
}
