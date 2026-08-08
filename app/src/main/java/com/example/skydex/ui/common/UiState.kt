package com.example.skydex.ui.common

/**
 * The three states every screen that loads something can be in. Screens render a `when` over this
 * instead of juggling a `loading` boolean, a nullable payload and an error string that can all
 * disagree with one another.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
