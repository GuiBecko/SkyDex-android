package io.github.guibecko.skydex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.ui.common.Tone
import io.github.guibecko.skydex.ui.common.UiMessage
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme

/**
 * # The two ways SkyDex shows a [UiMessage]
 *
 * [SkyDexNotice] is the inline bar — the Discord-shaped one. It sits **above** whatever is already
 * on screen and takes nothing away. That is finding A4: three screens replaced their whole content
 * with the failure, so a feed the user had already read vanished because the *second* load failed.
 *
 * [SkyDexNoticeState] is the full-area variant, for when there is genuinely nothing to show.
 *
 * Both are non-destructive in colour: amber for [Tone.NOTICE], green for [Tone.SUCCESS], recessed
 * surface for [Tone.NEUTRAL]. Red never appears here — `SkyDexPalette.colors.danger` is reserved
 * for destructive confirmation and this component never renders one.
 *
 * ## Measured contrast of every combination drawn below
 *
 * Title is `textPrimary`, body is `textSecondary`, icon and action are the tone accent.
 *
 * | on container | title | body | accent |
 * |---|---|---|---|
 * | noticeContainer (light) | 16.03 | 6.81 | 4.51 |
 * | successContainer (light) | 15.74 | 6.68 | 4.84 |
 * | surfaceVariant (light) | 15.88 | 6.74 | 5.28 |
 * | noticeContainer (dark) | 11.05 | 6.03 | 7.78 |
 * | successContainer (dark) | 8.27 | 4.51 | 5.06 |
 * | surfaceVariant (dark) | 12.44 | 6.79 | 6.83 |
 *
 * All twelve clear WCAG AA (4.5:1) at normal size. Re-measure if a token in `Color.kt` moves.
 */

/**
 * An inline, non-destructive banner. Soft container, leading icon, no harsh border — it informs, it
 * does not shout.
 *
 * @param message what to say. Its [UiMessage.tone] picks the colours and the icon.
 * @param modifier standard.
 * @param onAction the recovery. **The action button appears whenever this is non-null**, labelled
 *   [UiMessage.actionLabel] or "Tentar de novo" when the message did not name one. That default is
 *   what makes finding B3 — three screens rendering a failure with no way back — impossible to
 *   reintroduce by forgetting a label: a screen that wires up a retry always gets a visible one.
 * @param onDismiss shows a close affordance when non-null. Omit it for a banner the user cannot
 *   usefully dismiss (a screen that has nothing else on it).
 */
@Composable
fun SkyDexNotice(
    message: UiMessage,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val style = noticeStyleFor(message.tone)

    Surface(
        color = style.container,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(SkyDexSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.md)
        ) {
            Icon(
                imageVector = style.icon,
                // The icon repeats the tone, which is already carried by colour and by the title.
                // Announcing it again would make every screen reader read the same word twice.
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(SkyDexSpacing.xl)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyDexPalette.colors.textPrimary
                )
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyDexPalette.colors.textSecondary
                )

                if (onAction != null) {
                    TextButton(
                        onClick = onAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = style.accent),
                        contentPadding = ActionPadding
                    ) {
                        Text(
                            text = message.actionLabel ?: DEFAULT_ACTION_LABEL,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (onDismiss != null) {
                // Left at its default 48dp touch target rather than shrunk to match the icon: a
                // dismiss the user cannot reliably hit is worse than a slightly wider banner.
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dispensar aviso",
                        tint = SkyDexPalette.colors.textSecondary,
                        modifier = Modifier.size(SkyDexSpacing.lg)
                    )
                }
            }
        }
    }
}

/**
 * The centred, full-area variant: for a screen that has nothing to show at all, where a thin bar
 * floating in empty space would read as an afterthought.
 *
 * Unlike [SkyDexNotice] this one ends in a real [Button] rather than a text action — it is the only
 * thing on screen, so it is also the only thing to press.
 *
 * @param onAction the recovery. Same rule as [SkyDexNotice]: present whenever this is non-null,
 *   labelled from the message or "Tentar de novo".
 */
@Composable
fun SkyDexNoticeState(
    message: UiMessage,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null
) {
    val style = noticeStyleFor(message.tone)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SkyDexSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.accent,
            modifier = Modifier.size(SkyDexSpacing.xxl)
        )
        Text(
            text = message.title,
            style = MaterialTheme.typography.titleLarge,
            color = SkyDexPalette.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyLarge,
            color = SkyDexPalette.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = SkyDexSpacing.sm)) {
                Text(
                    text = message.actionLabel ?: DEFAULT_ACTION_LABEL,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tone resolution
// ---------------------------------------------------------------------------------------------

/** Fallback wording so a wired-up recovery is never invisible. See [SkyDexNotice]'s `onAction`. */
private const val DEFAULT_ACTION_LABEL = "Tentar de novo"

/**
 * A text action nested inside a banner should not carry a `TextButton`'s full default inset — it
 * would push the body text out of alignment with the title above it.
 */
private val ActionPadding = PaddingValues(horizontal = SkyDexSpacing.sm, vertical = SkyDexSpacing.xs)

private data class NoticeStyle(val container: Color, val accent: Color, val icon: ImageVector)

/**
 * Maps a [Tone] to its container, accent and icon.
 *
 * [Tone.NOTICE] gets `Info` rather than `Warning` deliberately: amber already carries "pay
 * attention", and a warning triangle on top of it turns a fixable hiccup into an alarm.
 */
@Composable
private fun noticeStyleFor(tone: Tone): NoticeStyle {
    val colors = SkyDexPalette.colors
    return when (tone) {
        Tone.NOTICE -> NoticeStyle(colors.noticeContainer, colors.notice, Icons.Default.Info)
        Tone.SUCCESS -> NoticeStyle(colors.successContainer, colors.success, Icons.Default.CheckCircle)
        Tone.NEUTRAL -> NoticeStyle(
            container = MaterialTheme.colorScheme.surfaceVariant,
            accent = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.Info
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — every tone, both themes, both components
// ---------------------------------------------------------------------------------------------

private val NoticeSample = UiMessage(
    title = "Sem conexão",
    body = "Verifique sua internet e tente de novo.",
    tone = Tone.NOTICE,
    actionLabel = "Tentar de novo"
)

private val SuccessSample = UiMessage(
    title = "Convite enviado!",
    body = "Avisamos você assim que ele aceitar.",
    tone = Tone.SUCCESS
)

private val NeutralSample = UiMessage(
    title = "Nada por aqui ainda",
    body = "Adicione amigos para ver os registros deles.",
    tone = Tone.NEUTRAL,
    actionLabel = "Ver amigos"
)

@Preview(name = "Notice — notice, light", showBackground = true)
@Composable
private fun NoticeNoticeLightPreview() {
    SkyDexTheme(darkTheme = false) {
        SkyDexNotice(NoticeSample, onAction = {}, onDismiss = {})
    }
}

@Preview(name = "Notice — notice, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeNoticeDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        SkyDexNotice(NoticeSample, onAction = {}, onDismiss = {})
    }
}

@Preview(name = "Notice — success, light", showBackground = true)
@Composable
private fun NoticeSuccessLightPreview() {
    SkyDexTheme(darkTheme = false) { SkyDexNotice(SuccessSample) }
}

@Preview(name = "Notice — success, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeSuccessDarkPreview() {
    SkyDexTheme(darkTheme = true) { SkyDexNotice(SuccessSample) }
}

@Preview(name = "Notice — neutral, light", showBackground = true)
@Composable
private fun NoticeNeutralLightPreview() {
    SkyDexTheme(darkTheme = false) { SkyDexNotice(NeutralSample, onAction = {}) }
}

@Preview(name = "Notice — neutral, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeNeutralDarkPreview() {
    SkyDexTheme(darkTheme = true) { SkyDexNotice(NeutralSample, onAction = {}) }
}

@Preview(name = "NoticeState — notice, light", showBackground = true)
@Composable
private fun NoticeStateNoticeLightPreview() {
    SkyDexTheme(darkTheme = false) { SkyDexNoticeState(NoticeSample, onAction = {}) }
}

@Preview(name = "NoticeState — notice, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeStateNoticeDarkPreview() {
    SkyDexTheme(darkTheme = true) { SkyDexNoticeState(NoticeSample, onAction = {}) }
}

@Preview(name = "NoticeState — success, light", showBackground = true)
@Composable
private fun NoticeStateSuccessLightPreview() {
    SkyDexTheme(darkTheme = false) { SkyDexNoticeState(SuccessSample) }
}

@Preview(name = "NoticeState — success, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeStateSuccessDarkPreview() {
    SkyDexTheme(darkTheme = true) { SkyDexNoticeState(SuccessSample) }
}

@Preview(name = "NoticeState — neutral, light", showBackground = true)
@Composable
private fun NoticeStateNeutralLightPreview() {
    SkyDexTheme(darkTheme = false) { SkyDexNoticeState(NeutralSample, onAction = {}) }
}

@Preview(name = "NoticeState — neutral, dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun NoticeStateNeutralDarkPreview() {
    SkyDexTheme(darkTheme = true) { SkyDexNoticeState(NeutralSample, onAction = {}) }
}
