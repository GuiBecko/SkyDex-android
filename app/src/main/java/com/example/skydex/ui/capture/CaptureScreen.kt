package com.example.skydex.ui.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.skydex.util.LOCATION_PERMISSIONS
import com.example.skydex.util.PhotoCaptureFiles
import java.io.File

/**
 * Hardcoded because the species catalog is a backend enum with no discovery endpoint in the MVP.
 * The names must match `Phenomenon` exactly — a drift here fails a capture with "Unknown
 * phenomenon". Exposing `GET /api/phenomena` and driving the chips from it is a small post-MVP
 * follow-up worth doing.
 */
private val SPECIES = listOf(
    "CLEAR_SKY" to "Céu Limpo",
    "CLOUDS" to "Nublado",
    "FOG" to "Nevoeiro Intenso",
    "DRIZZLE" to "Garoa",
    "RAIN" to "Chuva",
    "RAIN_SHOWER" to "Pancada de Chuva",
    "SNOW" to "Neve",
    "THUNDERSTORM" to "Tempestade com Trovões",
    "HAILSTORM" to "Tempestade Severa com Granizo"
)

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

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    // `verticalScroll`, because this content does not fit: a 220dp preview, a 110dp description
    // box, a 50dp button and the gaps between them overflow a small phone as soon as `adjustResize`
    // lifts the window for the keyboard — and "Salvar Registro" is the thing that goes off-screen.
    // HomeScreen gets this for free from its LazyColumn; this screen never got the same treatment.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Novo Registro", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        if (state.photoFile != null) {
            AsyncImage(
                model = state.photoFile,
                contentDescription = "Foto do fenômeno",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.LightGray, RoundedCornerShape(12.dp))
            )
        }

        // Disabled while a save is in flight, for the same reason "Salvar Registro" is: a retake
        // that lands mid-upload replaces the photo under a coroutine that is already carrying the
        // old one, and the user's only clue would be a preview that no longer matches what gets
        // saved. The ViewModel guards that window too — this is a hint, not the guard, since it
        // only takes effect at the next recomposition — but the hint is worth having: it says
        // "wait" instead of letting the tap open the camera and then discarding the result.
        OutlinedButton(
            onClick = {
                val file = PhotoCaptureFiles.newCaptureFile(context)
                pendingPath = file.absolutePath
                takePicture.launch(PhotoCaptureFiles.uriFor(context, file))
            },
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(
                if (state.photoFile == null) "  Tirar Foto" else "  Tirar Outra Foto",
                fontWeight = FontWeight.Bold
            )
        }

        Text("Qual fenômeno?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SPECIES.forEach { (name, label) ->
                FilterChip(
                    selected = state.phenomenon == name,
                    onClick = { viewModel.onPhenomenonSelected(name) },
                    label = { Text(label) }
                )
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChanged,
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text("Descrição") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )

        Text(
            text = when {
                state.locating -> "Obtendo sua localização..."
                state.coordinates != null ->
                    "Localização: %.4f, %.4f".format(
                        state.coordinates!!.latitude,
                        state.coordinates!!.longitude
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
            color = if (state.coordinates != null) Color.Gray else Color(0xFFB91C1C),
            fontSize = 13.sp
        )

        if (state.coordinates == null && !state.locating) {
            if (permissionDenied) {
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir Configurações")
                }
            } else {
                TextButton(onClick = viewModel::refreshLocation) {
                    Text("Tentar novamente")
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = Color(0xFFB91C1C), fontSize = 14.sp)
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (state.submitting) "Salvando..." else "Salvar Registro", fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}
