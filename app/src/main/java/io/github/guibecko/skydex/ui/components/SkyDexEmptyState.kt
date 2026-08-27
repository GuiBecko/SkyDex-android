package io.github.guibecko.skydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme

/**
 * The shape every empty list in SkyDex takes: an icon, an encouraging title, one supporting line,
 * and — where there is somewhere to go — a real button.
 *
 * Audit finding A10: all five empty states were a single grey centred sentence with no way forward.
 * The most expensive one was `MyCapturesScreen` ("Você ainda não possui eventos registrados."), the
 * screen the app lands on right after a capture is saved, offering nothing to do next.
 *
 * The copy contract mirrors [io.github.guibecko.skydex.ui.common.UiMessage]: the title says what is (not)
 * there, the body names the next step. Warm, short, and no exclamation-mark alarm — an empty
 * collection is a beginning, not a failure, which is also why this is not a [SkyDexNotice]: nothing
 * went wrong here.
 *
 * Used by `ui/home`, `ui/feed`, `ui/skydex`, `ui/friends` and `ui/captures` — a shape shared by five
 * screens, hence `ui/components`.
 *
 * @param icon a topical glyph — a camera for "register something", people for "invite someone".
 *   Decorative: the title says the same thing in words, so it carries no `contentDescription`.
 * @param title what the user is looking at. Short, no final period.
 * @param body the next step.
 * @param actionLabel wording of the CTA. The button renders only when this **and** [onAction] are
 *   present — screens whose empty state has no destination (the two in Amigos, which sit right
 *   under the invite field) pass neither.
 * @param onAction what the CTA does.
 */
@Composable
fun SkyDexEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SkyDexSpacing.xl, horizontal = SkyDexSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                // A 48dp box clipped with the 24dp `extraLarge` radius is exactly a circle, which
                // keeps the shape coming from the theme instead of an inline RoundedCornerShape.
                .size(SkyDexSpacing.xxxl)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(SkyDexSpacing.xl)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = SkyDexPalette.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = SkyDexSpacing.sm)) {
                Text(text = actionLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, name = "Estado vazio - claro")
@Composable
private fun SkyDexEmptyStateLightPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexEmptyState(
            icon = Icons.Default.PhotoCamera,
            title = "Sua coleção começa aqui",
            body = "Registre um fenômeno do céu e ele aparece nesta lista.",
            actionLabel = "Registrar evento",
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Estado vazio - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun SkyDexEmptyStateDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        SkyDexEmptyState(
            icon = Icons.Default.PhotoCamera,
            title = "Sua coleção começa aqui",
            body = "Registre um fenômeno do céu e ele aparece nesta lista.",
            actionLabel = "Registrar evento",
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Estado vazio - sem CTA")
@Composable
private fun SkyDexEmptyStateNoActionPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexEmptyState(
            icon = Icons.Default.PhotoCamera,
            title = "Nenhum convite por enquanto",
            body = "Quando alguém convidar você, o convite aparece aqui."
        )
    }
}
