package com.example.skydex.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.skydex.ui.common.LogWarning
import com.example.skydex.ui.common.androidLogWarning
import com.example.skydex.ui.components.CaptureImage
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.util.formatCoordinates
import com.example.skydex.util.geoUri
import com.example.skydex.util.mapTileFor
import com.example.skydex.util.osmTileUrl

/**
 * # Where the capture happened
 *
 * ## Why this is a picture of a map and not a map
 *
 * The project has no maps dependency and no Maps API key. Adding Google Maps Compose would require
 * someone to provision a key in Google Cloud and accept its billing terms — a product decision, not
 * a coding one. So the card shows a **single static OpenStreetMap raster tile**, which needs neither,
 * and delegates the interactive part to the map app the phone already has.
 *
 * The split of responsibilities the user actually feels:
 *
 * | | shows | needs |
 * |---|---|---|
 * | this card | *roughly where*, at a glance | one cached image |
 * | the `geo:` intent | pan, zoom, route, satellite | the user's own map app |
 *
 * ## Three layers, so the location is never invisible
 *
 * 1. The **tile**, when it loads.
 * 2. The **coordinates in words**, always — [formatCoordinates], never a raw `-23.55, -46.63`.
 *    This is the fallback that makes a failed tile a cosmetic loss rather than an information loss.
 * 3. The **"Abrir no mapa" action**, always, because it does not depend on the tile at all.
 *
 * A tile that fails to load therefore degrades to [CaptureImage]'s own error slot — a labelled
 * "Imagem indisponível" panel on the theme's recessed surface — sitting above coordinates and an
 * action that both still work. That is the "clean placeholder showing the coordinates" this card
 * owes the user; what it can never become is the unexplained grey rectangle the audit found (M4),
 * because [CaptureImage] is the component that fixed exactly that and this card reuses it rather
 * than opening a second network-image code path.
 */
@Composable
fun CaptureLocationCard(
    latitude: Double,
    longitude: Double,
    label: String,
    modifier: Modifier = Modifier,
    logWarning: LogWarning = androidLogWarning
) {
    val context = LocalContext.current

    // Pure math, no allocation on recomposition. Also the seam the unit tests cover: an off-by-one
    // in the projection produces a plausible-looking tile of the wrong neighbourhood.
    val tileUrl = remember(latitude, longitude) {
        mapTileFor(latitude, longitude).osmTileUrl()
    }
    val readableCoordinates = remember(latitude, longitude) {
        formatCoordinates(latitude, longitude)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
            Text(
                text = "Onde foi registrado",
                style = MaterialTheme.typography.titleMedium,
                color = SkyDexPalette.colors.textPrimary
            )

            CaptureImage(
                url = tileUrl,
                // Announced, unlike the decorative icons elsewhere: for a screen reader this image
                // is the only thing standing between the user and "there is a picture here". The
                // coordinates below say the same thing precisely, so the description stays short
                // and non-numeric.
                contentDescription = "Mapa aproximado do local do registro",
                modifier = Modifier
                    .padding(top = SkyDexSpacing.md)
                    .fillMaxWidth()
                    .height(MapPreviewHeight)
            )

            Row(
                modifier = Modifier
                    .padding(top = SkyDexSpacing.md)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    // The coordinates next to it carry the meaning.
                    contentDescription = null,
                    tint = SkyDexPalette.colors.accentDecorative,
                    modifier = Modifier.size(SkyDexSpacing.lg)
                )
                Text(
                    text = readableCoordinates,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SkyDexPalette.colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            TextButton(
                onClick = { openInMapApp(context, latitude, longitude, label, logWarning) },
                modifier = Modifier
                    .padding(top = SkyDexSpacing.xs)
                    .heightIn(min = ActionMinHeight)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(SkyDexSpacing.lg)
                )
                Text(
                    text = "Abrir no mapa",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = SkyDexSpacing.sm)
                )
            }

            // OpenStreetMap's tile usage policy requires visible attribution wherever its tiles are
            // shown. It is a licence condition, not a nicety — the map above is not ours to display
            // silently.
            Text(
                text = "Mapa © colaboradores do OpenStreetMap",
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textTertiary,
                modifier = Modifier.padding(top = SkyDexSpacing.xs)
            )
        }
    }
}

/**
 * Hands the point to whatever app handles `geo:` on this device.
 *
 * Guarded with `try`/`catch` rather than `resolveActivity`: since API 30, package visibility filters
 * what `resolveActivity` can see, so it returns `null` on a phone that has a perfectly good map app
 * unless the manifest declares a matching `<queries>` entry — a check that fails open in exactly the
 * wrong direction. Catching [ActivityNotFoundException] asks the real question ("did anything
 * handle this?") and is unaffected by visibility rules.
 *
 * A device with no map app at all is the one case that reaches the `catch`. Nothing is shown for it:
 * the coordinates are already on screen above the button, which is the whole content the map app
 * would have opened with. The cause goes to logcat, and the app does not crash — which is the entire
 * requirement.
 */
private fun openInMapApp(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String,
    logWarning: LogWarning
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(latitude, longitude, label)))
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        logWarning(TAG, "no application on this device handles geo: intents", error)
    }
}

private const val TAG = "CaptureLocationCard"

/**
 * Tall enough for the tile to read as a place rather than as a texture, short enough that the photo
 * above it stays the subject of the screen.
 */
private val MapPreviewHeight = SkyDexSpacing.xxxl * 3

/** Touch-target floor, matching the other primary affordances. Accessibility, not spacing. */
private val ActionMinHeight = SkyDexSpacing.xxxl

// ---------------------------------------------------------------------------------------------
// Previews — the tile never loads in the IDE, so these show the degraded state, on purpose
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, name = "Local — claro")
@Composable
private fun CaptureLocationCardLightPreview() {
    SkyDexTheme(darkTheme = false) {
        CaptureLocationCard(
            latitude = -23.5505,
            longitude = -46.6333,
            label = "Cumulonimbus sobre a Paulista"
        )
    }
}

@Preview(showBackground = true, name = "Local — escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun CaptureLocationCardDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        CaptureLocationCard(
            latitude = -23.5505,
            longitude = -46.6333,
            label = "Cumulonimbus sobre a Paulista"
        )
    }
}
