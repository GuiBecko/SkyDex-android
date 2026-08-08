package com.example.skydex.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

data class Session(val token: String, val userId: String)

private val Context.sessionDataStore by preferencesDataStore(name = "skydex_session")

/**
 * Takes the [DataStore] rather than the [Context] so the persistence round trip is testable on
 * the JVM: a test can hand it `PreferenceDataStoreFactory.create { tempFile }` and assert that a
 * saved session reads back. Constructing it from a `Context` — what the app does — is the
 * secondary constructor below. Session survival is the entire point of this task, and with no
 * device available it would otherwise be verified by nothing at all.
 */
class SessionStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.sessionDataStore)

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val session: Flow<Session?> = dataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]
        val userId = prefs[Keys.USER_ID]
        if (token.isNullOrBlank() || userId.isNullOrBlank()) null else Session(token, userId)
    }

    suspend fun save(token: String, userId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /**
     * Read the token from OkHttp's blocking interceptor thread. Never call this from the
     * main thread — OkHttp interceptors always run on a background dispatcher.
     *
     * Nothing may escape this method. OkHttp's `AsyncCall.execute` reports a throwing
     * interceptor to the callback and then **rethrows on its own pool thread**, where it reaches
     * the default uncaught-exception handler and kills the process. A missing header producing a
     * clean 401 is strictly better than a crash, so every failure — a corrupt DataStore file, an
     * uninitialised ServiceLocator — degrades to `null` here.
     *
     * `runCatching` is correct in this one place, unlike in the repositories:
     * [runBlocking] starts its own event loop, so there is no caller coroutine whose cancellation
     * could be swallowed. See `com.example.skydex.data.repository.resultOf` for why the
     * repositories must not use it.
     */
    fun blockingToken(): String? = runCatching { runBlocking { session.first()?.token } }.getOrNull()
}
