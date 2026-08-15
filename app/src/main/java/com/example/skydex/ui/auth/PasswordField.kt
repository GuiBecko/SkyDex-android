package com.example.skydex.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.ui.theme.SkyDexTheme

/**
 * A password field the user can actually read back.
 *
 * Both auth screens masked the password with no way to reveal it, which on a phone keyboard means
 * typing a credential blind and finding out it was wrong only after a failed round trip. The
 * user's own framing for this pass was "comfort when writing"; this is the cheapest instance of it.
 *
 * Visibility is local, `rememberSaveable` state: it survives rotation but is **never** hoisted into
 * the ViewModel or its `UiState` — whether the characters are on screen is a property of this
 * widget, not of the login attempt.
 *
 * Shared by [LoginScreen] and [RegisterScreen], which is why it is its own file in this package.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        supportingText = if (hint == null) null else {
            { Text(hint) }
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    // The label has to state the ACTION, not the state: a screen reader user
                    // needs to know what the button will do, not what the field currently is.
                    contentDescription = if (visible) "Ocultar senha" else "Mostrar senha"
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true, name = "Senha oculta")
@Composable
private fun PasswordFieldPreview() {
    SkyDexTheme(darkTheme = false) {
        PasswordField(value = "secret123", onValueChange = {}, label = "Senha")
    }
}

@Preview(showBackground = true, name = "Senha - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun PasswordFieldDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        PasswordField(
            value = "secret123",
            onValueChange = {},
            label = "Senha",
            hint = "Mínimo de 8 caracteres"
        )
    }
}
