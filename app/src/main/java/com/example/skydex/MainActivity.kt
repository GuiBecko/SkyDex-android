package com.example.skydex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.example.skydex.data.session.Session
import com.example.skydex.ui.navigation.SkyDexNavHost
import com.example.skydex.ui.theme.SkyDexTheme

/**
 * Nothing but a host: it answers the single question "is there a stored session?" and hands the
 * answer to the navigation graph, which owns every screen from there on.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyDexTheme {
                val snapshot by produceState<SessionSnapshot?>(initialValue = null) {
                    ServiceLocator.sessionStore.session.collect { value = SessionSnapshot(it) }
                }

                // Draw no *screen* until DataStore has answered. `NavHost` fixes its start
                // destination the first time it is composed, so composing it against a
                // not-yet-read session would strand a logged-in user on the login screen.
                //
                // The empty Surface is not cosmetic: `produceState` is not saveable, so this
                // branch is re-entered on every Activity recreation, not just cold start. Without
                // it the frame shows the bare window background instead of the app's.
                when (val current = snapshot) {
                    null -> Surface(modifier = Modifier.fillMaxSize()) {}
                    else -> SkyDexNavHost(session = current.session)
                }
            }
        }
    }
}

/**
 * Distinguishes "DataStore has not answered yet" (no snapshot) from "there is no stored session"
 * (a snapshot holding `null`). Collecting the session flow straight into state collapses the two
 * into the same `null`, and the difference is exactly what decides the start destination.
 */
private data class SessionSnapshot(val session: Session?)
