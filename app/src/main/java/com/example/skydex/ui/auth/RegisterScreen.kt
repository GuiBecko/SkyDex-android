package com.example.skydex.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme

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
            // Declared explicitly: with no background of its own this screen inherited the dark
            // Surface in dark mode while its content assumed light (audit finding B4).
            .background(MaterialTheme.colorScheme.background)
            .padding(SkyDexSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Criar Conta",
            style = MaterialTheme.typography.headlineMedium,
            // `colorScheme.primary`, not the brighter decorative accent — this is text.
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(SkyDexSpacing.xxl))

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = { Text("Nome Completo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(SkyDexSpacing.lg))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(SkyDexSpacing.lg))

        PasswordField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = "Senha",
            hint = "Mínimo de ${RegisterViewModel.MIN_PASSWORD_LENGTH} caracteres"
        )
        Spacer(Modifier.height(SkyDexSpacing.xl))

        state.errorMessage?.let { message ->
            SkyDexNotice(message = message, modifier = Modifier.padding(bottom = SkyDexSpacing.lg))
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(SkyDexSpacing.xxxl),
            enabled = !state.submitting
        ) {
            Text(
                text = if (state.submitting) "Registrando..." else "Registrar",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(SkyDexSpacing.lg))

        TextButton(onClick = onNavigateToLogin) {
            Text("Já possui conta? Faça Login", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterContentPreview() {
    SkyDexTheme(darkTheme = false) {
        RegisterContent(
            state = RegisterUiState(
                name = "Pilot",
                email = "pilot@skydex.com",
                password = "secret123"
            ),
            onNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun RegisterContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        RegisterContent(
            state = RegisterUiState(
                name = "Pilot",
                email = "pilot@skydex.com",
                password = "secret123"
            ),
            onNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(showBackground = true, name = "Registro com senha curta")
@Composable
private fun RegisterContentErrorPreview() {
    SkyDexTheme(darkTheme = false) {
        RegisterContent(
            state = RegisterUiState(
                name = "Pilot",
                email = "pilot@skydex.com",
                password = "curta",
                errorMessage = UiMessage(
                    title = "A senha está curta",
                    body = "Use no mínimo ${RegisterViewModel.MIN_PASSWORD_LENGTH} caracteres.",
                    tone = Tone.NOTICE
                )
            ),
            onNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Registro com senha curta - escuro",
    backgroundColor = 0xFF0B1220
)
@Composable
private fun RegisterContentErrorDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        RegisterContent(
            state = RegisterUiState(
                name = "Pilot",
                email = "pilot@skydex.com",
                password = "curta",
                errorMessage = UiMessage(
                    title = "A senha está curta",
                    body = "Use no mínimo ${RegisterViewModel.MIN_PASSWORD_LENGTH} caracteres.",
                    tone = Tone.NOTICE
                )
            ),
            onNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToLogin = {}
        )
    }
}
