package com.example.skydex.ui.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.components.CaptureRewardOverlay
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme
import com.example.skydex.util.Coordinates
import com.example.skydex.util.LOCATION_PERMISSIONS
import com.example.skydex.util.PhotoCaptureFiles
import java.io.File

/** Fixed because it frames a photo, not text — it does not grow with the system font scale. */
private val PhotoPreviewHeight = 220.dp

/**
 * Minimum, not fixed (audit finding M8). The old `height(110.dp)` clipped the description as soon
 * as the user raised the system font scale: four lines of 16sp with the label above them do not fit
 * in 110dp at 1.3x. `heightIn` gives the field the same resting size and lets it grow instead.
 */
private val DescriptionMinHeight = 120.dp

/** 8-point-grid touch target for the screen's commit action. */
private val PrimaryButtonMinHeight = 56.dp

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Held outside the ViewModel because TakePicture only reports success/failure, not the URI.
    //
    // `rememberSaveable`, not `remember`: the system camera is a separate Activity and low-memory
    // devices routinely kill the host process behind it. With a plain `remember` the path comes
    // back null, the `file != null` check below fails, and a JPEG that exists on disk is dropped
    // with no message at all — the user watches the photo they just took disappear. The absolute
    // path is stored as a String because `File` is not `Parcelable`.
    //
    // What this deliberately does *not* fix: `CaptureViewModel` is a plain `ViewModel` with no
    // `SavedStateHandle`, so its own state still dies with the process. After a kill behind the
    // camera the user gets their photo back (via the line below) and the location is re-fetched by
    // the `LaunchedEffect`, but a title and description typed *before* opening the camera come back
    // empty; likewise a photo confirmed earlier and then lost to a kill while the app sat in the
    // background, where there is no pending path to restore from. Both are visible losses — empty
    // fields, an empty preview — not the silent one this fixes, so full restoration through
    // `createSavedStateHandle()` is left to a later round rather than bolted on here.
    //
    // One loss in that set *is* invisible: `uploadedPhotoUrl` dies with the process too, so the
    // orphan-compounding that cache prevents is not durable across a kill. A photo uploaded before
    // the kill is already on the server, referenced by nothing, and the retry after restart cannot
    // know that — it uploads again. Nothing on screen shows it, because the waste is server-side;
    // it is bounded by backlog item #13 (the server-side sweep), not by anything the client does.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Tracks whether the user has actively denied the permission (as opposed to simply never
    // having been asked yet, or having granted it and then lost the fix for some other reason —
    // e.g. GPS switched off). Android will not show the system permission dialog again after a
    // denial, so once this flips true, "ask again" is not a real option: the only way out is
    // Settings, and the copy below has to say so.
    //
    // `rememberSaveable` for the same reason as `pendingPath`: a process kill behind the camera
    // would otherwise bring this back false and briefly offer "ative o GPS" to a user whose real
    // problem is a denial. It self-heals as soon as the re-launched permission request answers, so
    // this is a flicker rather than the dead end above — but a Boolean costs nothing to carry.
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let(::File)
        if (ok && file != null) viewModel.onPhotoTaken(file)
        pendingPath = null
    }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionDenied = results.values.none { granted -> granted }
        viewModel.refreshLocation()
    }

    // Same latch as HomeScreen's, for the same reason: this effect re-runs on Activity recreation.
    LaunchedEffect(Unit) {
        if (viewModel.shouldRequestInitialLocation()) requestLocation.launch(LOCATION_PERMISSIONS)
    }

    // This used to be `LaunchedEffect(state.saved) { if (state.saved) onSaved() }` — the line the
    // audit named as finding B6. The capture landed and the screen was simply swapped for Meus
    // Registros: no XP, no animation, no badge, no haptic, and the reward surfaced later only as a
    // number in a list. `onSaved` still does exactly what it always did; what changed is *when*.
    // It now fires from the reward overlay's primary action, so the peak moment happens before the
    // navigation instead of being erased by it.
    //
    // While the overlay is up, the system back gesture goes to that same destination rather than
    // to `popBackStack`. The capture is already on the server, so backing out would drop the user
    // on the form they just committed — a screen whose only remaining action is one they must not
    // take — and the celebration would vanish with nothing shown in its place.
    BackHandler(enabled = state.reward != null, onBack = onSaved)

    Box(modifier = modifier.fillMaxSize()) {
        CaptureContent(
            state = state,
            permissionDenied = permissionDenied,
            onTakePhoto = {
                val file = PhotoCaptureFiles.newCaptureFile(context)
                pendingPath = file.absolutePath
                takePicture.launch(PhotoCaptureFiles.uriFor(context, file))
            },
            onTitleChanged = viewModel::onTitleChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onRetryLocation = viewModel::refreshLocation,
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            },
            onSubmit = viewModel::submit
        )

        // Composed only while there is a reward to show. A rotation rebuilds this composition from
        // the surviving ViewModel state, so the overlay comes back rather than stranding the user
        // on a form they already committed — and `CaptureRewardOverlay` remembers across that
        // recreation that it has already fired its haptic, so the buzz belongs to the capture and
        // not to the composition.
        state.reward?.let { reward ->
            CaptureRewardOverlay(
                reward = reward,
                onSeeCaptures = onSaved,
                // Stays on this screen with a blank form and the position already held — see
                // `CaptureViewModel.startNewCapture` for why the location survives and nothing
                // else does.
                onCaptureAnother = viewModel::startNewCapture
            )
        }
    }
}

/**
 * The screen without its ViewModel, its launchers or its `Context`, so the `@Preview`s below can
 * render it in both themes.
 *
 * There is no in-screen title: the route's `TopAppBar` carries "Novo Registro" together with the
 * back arrow (finding A8).
 */
@Composable
private fun CaptureContent(
    state: CaptureUiState,
    permissionDenied: Boolean,
    onTakePhoto: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onRetryLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // `verticalScroll`, because this content does not fit: the photo preview, the description box,
    // the commit button and the gaps between them overflow a small phone as soon as `adjustResize`
    // lifts the window for the keyboard — and "Salvar Registro" is the thing that goes off-screen.
    // HomeScreen gets this for free from its LazyColumn; this screen never got the same treatment.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(SkyDexSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.lg)
    ) {
        if (state.photoFile != null) {
            AsyncImage(
                model = state.photoFile,
                contentDescription = "Foto do fenômeno",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PhotoPreviewHeight)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.medium
                    )
            )
        }

        // Disabled while a save is in flight, for the same reason "Salvar Registro" is: a retake
        // that lands mid-upload replaces the photo under a coroutine that is already carrying the
        // old one, and the user's only clue would be a preview that no longer matches what gets
        // saved. The ViewModel guards that window too — this is a hint, not the guard, since it
        // only takes effect at the next recomposition — but the hint is worth having: it says
        // "wait" instead of letting the tap open the camera and then discarding the result.
        OutlinedButton(
            onClick = onTakePhoto,
            enabled = !state.submitting,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PrimaryButtonMinHeight)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            // Was `Spacer(Modifier.height(0.dp))` plus two leading spaces inside the label — a
            // no-op and a typographic hack standing in for the gap this now actually draws.
            Spacer(Modifier.width(SkyDexSpacing.sm))
            Text(
                text = if (state.photoFile == null) "Tirar Foto" else "Tirar Outra Foto",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = "O SkyDex identifica o fenômeno sozinho, comparando sua foto com o clima real do lugar.",
            style = MaterialTheme.typography.bodyMedium,
            color = SkyDexPalette.colors.textSecondary
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChanged,
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChanged,
            label = { Text("Descrição") },
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DescriptionMinHeight)
        )

        Text(
            text = when {
                state.locating -> "Obtendo sua localização..."
                state.coordinates != null ->
                    "Localização: %.4f, %.4f".format(
                        state.coordinates.latitude,
                        state.coordinates.longitude
                    )
                // Denial and a failed fix look identical in `state.coordinates`, but they are not
                // the same dead end: Android will not show the system dialog again after a denial,
                // so telling this user to "ativar o GPS" would send them looking for a switch that
                // was never the problem. `permissionDenied` — set from the RequestMultiplePermissions
                // result map above — is what tells the two apart.
                permissionDenied ->
                    "Permissão de localização negada. Ative em Configurações para continuar."
                else -> "Localização indisponível — ative o GPS e tente de novo."
            },
            // Amber, not red. None of the three branches above is a crash — two are progress and
            // one is an instruction — and the audit (A3) flagged the red here for exactly that.
            color = if (state.coordinates != null) {
                SkyDexPalette.colors.textSecondary
            } else {
                SkyDexPalette.colors.notice
            },
            style = MaterialTheme.typography.bodySmall
        )

        if (state.coordinates == null && !state.locating) {
            if (permissionDenied) {
                TextButton(onClick = onOpenSettings) { Text("Abrir Configurações") }
            } else {
                TextButton(onClick = onRetryLocation) { Text("Tentar novamente") }
            }
        }

        // Inline and non-destructive: the photo and everything typed stay on screen. Most of the
        // capture 400s are only recoverable *because* the form is still there.
        //
        // `photoMessage` takes priority: it names the more specific, more actionable problem (the
        // photo itself), and `CaptureViewModel` keeps it alive through typing for exactly this
        // moment — see `CaptureUiState.photoMessage`.
        (state.photoMessage ?: state.errorMessage)?.let { message ->
            SkyDexNotice(message = message)
        }

        Button(
            onClick = onSubmit,
            enabled = !state.submitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PrimaryButtonMinHeight)
        ) {
            Text(
                text = if (state.submitting) "Salvando..." else "Salvar Registro",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(SkyDexSpacing.sm))
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — light and dark (audit finding B4)
// ---------------------------------------------------------------------------------------------

private val previewState = CaptureUiState(
    title = "Tempestade sobre a Paulista",
    description = "Raios a cada poucos segundos, vento forte vindo do sul.",
    coordinates = Coordinates(-23.55, -46.63)
)

@Composable
private fun CapturePreviewHost(darkTheme: Boolean, state: CaptureUiState, denied: Boolean = false) {
    SkyDexTheme(darkTheme = darkTheme) {
        CaptureContent(
            state = state,
            permissionDenied = denied,
            onTakePhoto = {},
            onTitleChanged = {},
            onDescriptionChanged = {},
            onRetryLocation = {},
            onOpenSettings = {},
            onSubmit = {}
        )
    }
}

@Preview(showBackground = true, name = "Captura — formulário, claro", heightDp = 900)
@Composable
private fun CaptureContentPreview() {
    CapturePreviewHost(darkTheme = false, state = previewState)
}

@Preview(
    showBackground = true,
    name = "Captura — formulário, escuro",
    backgroundColor = 0xFF0B1220,
    heightDp = 900
)
@Composable
private fun CaptureContentDarkPreview() {
    CapturePreviewHost(darkTheme = true, state = previewState)
}

@Preview(showBackground = true, name = "Captura — sem localização, claro", heightDp = 900)
@Composable
private fun CaptureContentDeniedPreview() {
    CapturePreviewHost(
        darkTheme = false,
        state = previewState.copy(coordinates = null),
        denied = true
    )
}

@Preview(
    showBackground = true,
    name = "Captura — erro, escuro",
    backgroundColor = 0xFF0B1220,
    heightDp = 900
)
@Composable
private fun CaptureContentErrorDarkPreview() {
    CapturePreviewHost(
        darkTheme = true,
        state = previewState.copy(
            errorMessage = UiMessage(
                title = "Falta a foto",
                body = "Tire uma foto do fenômeno antes de salvar.",
                tone = Tone.NOTICE
            )
        )
    )
}
