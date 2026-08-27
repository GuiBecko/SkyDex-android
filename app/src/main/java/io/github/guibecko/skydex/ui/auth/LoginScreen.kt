package io.github.guibecko.skydex.ui.auth

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.components.SkyDexNotice
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    LoginContent(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier
    )
}

/**
 * The screen without its ViewModel. Keeping the drawing separate from the wiring is what makes
 * the `@Preview` below renderable — a preview cannot build a ViewModel over a live repository.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // This screen used to declare no background at all, so in dark mode it inherited the
            // dark Surface while every colour in it assumed light (audit finding B4).
            .background(MaterialTheme.colorScheme.background)
            .padding(SkyDexSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SkyDex",
            // Was 36sp ExtraBold — off both ends of the scale, which tops out at 28sp Bold.
            style = MaterialTheme.typography.headlineMedium,
            // `colorScheme.primary`, not `accentDecorative`: this is coloured text, and the
            // brighter hue measures 3.88:1 against the background.
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Seu radar meteorológico pessoal",
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = SkyDexSpacing.xxl)
        )

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
            label = "Senha"
        )
        Spacer(Modifier.height(SkyDexSpacing.xl))

        // Non-destructive: the form stays exactly as the user left it, with the notice above the
        // button rather than a red sentence replacing anything.
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
                text = if (state.submitting) "Entrando..." else "Entrar",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(SkyDexSpacing.lg))

        TextButton(onClick = onNavigateToRegister) {
            Text("Não tem uma conta? Registre-se", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    SkyDexTheme(darkTheme = false) {
        LoginContent(
            state = LoginUiState(email = "pilot@skydex.com", password = "secret123"),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToRegister = {}
        )
    }
}

@Preview(showBackground = true, name = "Login - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun LoginContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        LoginContent(
            state = LoginUiState(email = "pilot@skydex.com", password = "secret123"),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToRegister = {}
        )
    }
}

@Preview(showBackground = true, name = "Login com erro")
@Composable
private fun LoginContentErrorPreview() {
    SkyDexTheme(darkTheme = false) {
        LoginContent(
            state = LoginUiState(
                email = "pilot@skydex.com",
                errorMessage = UiMessage(
                    title = "E-mail ou senha não conferem",
                    body = "Confira os dados e tente de novo.",
                    tone = Tone.NOTICE
                )
            ),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToRegister = {}
        )
    }
}

@Preview(showBackground = true, name = "Login com erro - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun LoginContentErrorDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        LoginContent(
            state = LoginUiState(
                email = "pilot@skydex.com",
                errorMessage = UiMessage(
                    title = "E-mail ou senha não conferem",
                    body = "Confira os dados e tente de novo.",
                    tone = Tone.NOTICE
                )
            ),
            onEmailChanged = {},
            onPasswordChanged = {},
            onSubmit = {},
            onNavigateToRegister = {}
        )
    }
}
