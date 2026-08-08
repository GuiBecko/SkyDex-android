package com.example.skydex.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skydex.data.remote.dto.CreateWeatherEventRequest
import com.example.skydex.util.Coordinates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CaptureUiState(
    val title: String = "",
    val description: String = "",
    val photoFile: File? = null,
    val coordinates: Coordinates? = null,
    val locating: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null
)

class CaptureViewModel(
    private val captures: CaptureGateway,
    private val locationProvider: suspend () -> Coordinates?
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    fun onTitleChanged(value: String) = _state.update { it.copy(title = value, errorMessage = null) }

    fun onDescriptionChanged(value: String) =
        _state.update { it.copy(description = value, errorMessage = null) }

    fun onPhotoTaken(file: File) = _state.update { it.copy(photoFile = file, errorMessage = null) }

    fun refreshLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val coordinates = locationProvider()
            _state.update { it.copy(locating = false, coordinates = coordinates) }
        }
    }

    fun submit() {
        val current = _state.value

        val error = when {
            current.title.isBlank() || current.description.isBlank() ->
                "Preencha o título e a descrição."
            current.photoFile == null ->
                "Tire uma foto do fenômeno antes de salvar."
            current.coordinates == null ->
                "Não foi possível obter sua localização. Ative o GPS e tente de novo."
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(errorMessage = error) }
            return
        }

        val photoFile = current.photoFile!!
        val coordinates = current.coordinates!!

        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val uploaded = captures.uploadPhoto(photoFile)
            if (uploaded.isFailure) {
                _state.update {
                    it.copy(submitting = false, errorMessage = "Falha ao enviar a foto. Tente de novo.")
                }
                return@launch
            }

            val request = CreateWeatherEventRequest(
                title = current.title,
                description = current.description,
                photoUrl = uploaded.getOrThrow(),
                latitude = coordinates.latitude,
                longitude = coordinates.longitude
            )

            captures.create(request)
                .onSuccess { _state.update { s -> s.copy(submitting = false, saved = true) } }
                .onFailure {
                    _state.update { s ->
                        s.copy(submitting = false, errorMessage = "Falha ao salvar o registro. Tente de novo.")
                    }
                }
        }
    }
}
