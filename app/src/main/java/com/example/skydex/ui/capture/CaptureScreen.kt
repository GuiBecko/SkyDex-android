package com.example.skydex.ui.capture

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
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

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Held outside the ViewModel because TakePicture only reports success/failure, not the URI.
    var pendingFile by remember { mutableStateOf<File?>(null) }

    // Tracks whether the user has actively denied the permission (as opposed to simply never
    // having been asked yet, or having granted it and then lost the fix for some other reason —
    // e.g. GPS switched off). Android will not show the system permission dialog again after a
    // denial, so once this flips true, "ask again" is not a real option: the only way out is
    // Settings, and the copy below has to say so.
    var permissionDenied by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        if (ok && file != null) viewModel.onPhotoTaken(file)
        pendingFile = null
    }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionDenied = results.values.none { granted -> granted }
        viewModel.refreshLocation()
    }

    LaunchedEffect(Unit) { requestLocation.launch(LOCATION_PERMISSIONS) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
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

        OutlinedButton(
            onClick = {
                val file = PhotoCaptureFiles.newCaptureFile(context)
                pendingFile = file
                takePicture.launch(PhotoCaptureFiles.uriFor(context, file))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(
                if (state.photoFile == null) "  Tirar Foto" else "  Tirar Outra Foto",
                fontWeight = FontWeight.Bold
            )
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
