package io.github.guibecko.skydex.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.guibecko.skydex.ui.theme.SkyDexPalette
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme
import coil.compose.SubcomposeAsyncImage

/**
 * A capture photo with the two states a network image actually has.
 *
 * Audit finding M4: `FeedScreen` and `MyCapturesScreen` drew an `AsyncImage` over a flat
 * `Color.LightGray` background and nothing else, so a slow URL and a dead URL looked identical —
 * a silent grey rectangle that never resolved. Now a pending load pulses (so the user can tell the
 * app is still working) and a failed one says so in words.
 *
 * The caller supplies the size through [modifier]; this composable never picks its own dimensions.
 *
 * Nothing about it is capture-specific — it is the app's network-image slot, shared by `ui/feed` and
 * `ui/captures`, which is why it lives in `ui/components` rather than next to either caller.
 */
@Composable
fun CaptureImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small),
        loading = { ImageLoadingPlaceholder(Modifier.fillMaxSize()) },
        error = { ImageUnavailable(Modifier.fillMaxSize()) }
    )
}

/**
 * A slow pulse on the recessed surface. Deliberately not a spinner: a spinner over a photo slot
 * reads as "something is wrong", while a breathing placeholder reads as "the photo is on its way",
 * and it also occupies exactly the space the photo will take.
 */
@Composable
private fun ImageLoadingPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "capture-image-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MILLIS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "capture-image-shimmer-alpha"
    )

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        )
    )
}

/** The visible failure. Says what happened instead of leaving an unexplained grey block. */
@Composable
private fun ImageUnavailable(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(SkyDexSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.xs, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            // The caption below already carries the meaning; announcing both repeats it.
            contentDescription = null,
            tint = SkyDexPalette.colors.textTertiary,
            modifier = Modifier.size(SkyDexSpacing.xl)
        )
        Text(
            text = "Imagem indisponível",
            style = MaterialTheme.typography.bodySmall,
            color = SkyDexPalette.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private const val SHIMMER_PERIOD_MILLIS = 900

// ---------------------------------------------------------------------------------------------
// Previews — an empty URL resolves to the error slot, which is what these show.
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, name = "Foto indisponível - claro")
@Composable
private fun CaptureImageErrorLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureImage(
            url = "",
            contentDescription = null,
            modifier = Modifier.size(SkyDexSpacing.xxxl * 4)
        )
    }
}

@Preview(showBackground = true, name = "Foto indisponível - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun CaptureImageErrorDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureImage(
            url = "",
            contentDescription = null,
            modifier = Modifier.size(SkyDexSpacing.xxxl * 4)
        )
    }
}
