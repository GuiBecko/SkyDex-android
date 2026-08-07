package com.example.skydex.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

data class Session(val token: String, val userId: String)

private val Context.sessionDataStore by preferencesDataStore(name = "skydex_session")

class SessionStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]
        val userId = prefs[Keys.USER_ID]
        if (token.isNullOrBlank() || userId.isNullOrBlank()) null else Session(token, userId)
    }

    suspend fun save(token: String, userId: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    /**
     * Read the token from OkHttp's blocking interceptor thread. Never call this from the
     * main thread — OkHttp interceptors always run on a background dispatcher.
     */
    fun blockingToken(): String? = runBlocking { session.first()?.token }
}
