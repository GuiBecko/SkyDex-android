package com.example.skydex.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Session survival across process death is the entire point of Task 4, and with no device
 * available it would otherwise be verified by nothing. [SessionStore] takes a
 * `DataStore<Preferences>` precisely so this round trip can run on the JVM: the store here writes
 * to a real file in a temp folder, and a second [SessionStore] built over the *same file* stands in
 * for a fresh process reading back what the previous one wrote.
 */
class SessionStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun createStore() {
        // PreferenceDataStoreFactory requires the `.preferences_pb` extension and refuses to
        // create over an existing file, so name it without touching the disk first.
        val file = temporaryFolder.newFolder("datastore").resolve("skydex_session.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
    }

    @Test
    fun `a saved session reads back`() = runBlocking {
        val store = SessionStore(dataStore)

        store.save(token = "jwt-abc", userId = "user-1")

        assertEquals(Session("jwt-abc", "user-1"), store.session.first())
        assertEquals("jwt-abc", store.blockingToken())
    }

    @Test
    fun `a session written by one instance survives into another over the same file`() = runBlocking {
        SessionStore(dataStore).save(token = "jwt-abc", userId = "user-1")

        // A second SessionStore over the same DataStore — the closest a JVM test gets to the
        // "force-stop and reopen" check, minus the process boundary.
        assertEquals(Session("jwt-abc", "user-1"), SessionStore(dataStore).session.first())
    }

    @Test
    fun `no session is reported before anything is saved`() = runBlocking {
        assertNull(SessionStore(dataStore).session.first())
        assertNull(SessionStore(dataStore).blockingToken())
    }

    @Test
    fun `clear removes the session`() = runBlocking {
        val store = SessionStore(dataStore)
        store.save(token = "jwt-abc", userId = "user-1")

        store.clear()

        assertNull(store.session.first())
        assertNull(store.blockingToken())
    }

    @Test
    fun `a half-written session with no user id is not a session`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("token")] = "jwt-abc" }

        assertNull(SessionStore(dataStore).session.first())
    }

    @Test
    fun `a half-written session with no token is not a session`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("user_id")] = "user-1" }

        assertNull(SessionStore(dataStore).session.first())
    }

    @Test
    fun `a blank token or user id is treated as no session`() = runBlocking {
        SessionStore(dataStore).save(token = "   ", userId = "user-1")
        assertNull(SessionStore(dataStore).session.first())

        SessionStore(dataStore).save(token = "jwt-abc", userId = "")
        assertNull(SessionStore(dataStore).session.first())
    }

    /**
     * DataStore reports an unreadable file as an [IOException] emitted *into* the flow. The whole
     * UI is gated on `session` now, so an uncaught one crashes the composition with no login
     * screen left to fall back on. It must degrade to "no session" instead.
     */
    @Test
    fun `an unreadable preferences file reads as no session instead of throwing`() = runBlocking {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt .preferences_pb") }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = throw UnsupportedOperationException()
        }

        assertNull(SessionStore(broken).session.first())
        assertNull(SessionStore(broken).blockingToken())
    }

    /** Anything that is not an IO failure is a bug, and hiding it would only delay the diagnosis. */
    @Test
    fun `a non-IO failure is not swallowed`() {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("bug") }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = throw UnsupportedOperationException()
        }

        try {
            runBlocking { SessionStore(broken).session.first() }
            fail("the non-IO failure should have propagated")
        } catch (expected: IllegalStateException) {
            assertEquals("bug", expected.message)
        }
    }

    /**
     * Pins the on-disk key names. Renaming these silently logs every installed user out on
     * upgrade, which no other assertion here would notice.
     */
    @Test
    fun `the persisted keys are token and user_id`() = runBlocking {
        SessionStore(dataStore).save(token = "jwt-abc", userId = "user-1")

        val prefs = dataStore.data.first()
        assertEquals("jwt-abc", prefs[stringPreferencesKey("token")])
        assertEquals("user-1", prefs[stringPreferencesKey("user_id")])
    }
}
