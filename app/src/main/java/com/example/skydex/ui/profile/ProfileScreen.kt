package com.example.skydex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.data.remote.dto.BadgeResponse
import com.example.skydex.data.remote.dto.ProfileResponse
import com.example.skydex.ui.common.UiState
import java.time.Duration
import java.time.Instant

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    // Navigation waits for the ViewModel to confirm the session write actually finished —
    // firing it straight from the button's click handler would race the pending disk write
    // against the ViewModelStore teardown that popping the back stack triggers. See
    // ProfileViewModel.loggedOut for the full reasoning.
    LaunchedEffect(loggedOut) {
        if (loggedOut) onLoggedOut()
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF3F4F6)).padding(16.dp)) {
        when (val current = state) {
            is UiState.Loading -> CircularProgressIndicator(
                color = Color(0xFF0284C7),
                modifier = Modifier.align(Alignment.Center)
            )

            is UiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.message, color = Color(0xFFB91C1C), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::refresh) { Text("Tentar de novo") }
            }

            is UiState.Success -> ProfileBody(
                profile = current.data,
                onOpenMyCaptures = onOpenMyCaptures,
                onOpenFriends = onOpenFriends
            )
        }

        // Outside the `when`, and that placement is the whole point.
        //
        // This is the only logout affordance in the app. While it lived inside `ProfileBody` it
        // rendered on `UiState.Success` alone — so when the token expired, `profile()` answered
        // 401, Profile landed in `Error`, and the only way out of a signed-in-but-unauthorised app
        // sat behind a load that could never succeed. The app was soft-bricked until the user
        // cleared its data. Logging out needs no profile data, so it must not depend on having any.
        //
        // (The real fix for the 401 itself is an interceptor that clears the session and returns
        // the user to login; that is deliberately still on the backlog. This makes the dead end
        // escapable in the meantime.)
        TextButton(
            onClick = viewModel::logout,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Text("Sair da conta", color = Color(0xFFB91C1C))
        }
    }
}

@Composable
private fun ProfileBody(
    profile: ProfileResponse,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Room for the logout button, which the parent draws over this list's bottom edge so it
        // is reachable in every UiState. Without this the last badge scrolls under it.
        contentPadding = PaddingValues(bottom = 56.dp)
    ) {
        item { IdentityCard(profile) }
        item { StatsRow(profile, onOpenMyCaptures, onOpenFriends) }

        item {
            Text(
                "Conquistas  ${profile.unlockedBadges}/${profile.totalBadges}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )
        }

        // Unlocked badges first so the shelf leads with what the user actually earned.
        items(profile.badges.sortedByDescending { it.unlocked }) { badge -> BadgeRow(badge) }
    }
}

@Composable
private fun IdentityCard(profile: ProfileResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(profile.user.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(profile.user.email, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)

            Spacer(Modifier.height(16.dp))

            Text(
                "Nível ${profile.level} · ${profile.totalXp} XP",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    val span = profile.totalXp + profile.xpToNextLevel
                    if (span <= 0) 0f else profile.totalXp.toFloat() / span
                },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Faltam ${profile.xpToNextLevel} XP para o nível ${profile.level + 1}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatsRow(
    profile: ProfileResponse,
    onOpenMyCaptures: () -> Unit,
    onOpenFriends: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            value = "${profile.confirmedCaptures}",
            label = "confirmados",
            hint = "de ${profile.totalCaptures}",
            modifier = Modifier.weight(1f),
            onClick = onOpenMyCaptures
        )
        StatTile(
            value = "${profile.capturedSpecies}/${profile.totalSpecies}",
            label = "espécies",
            hint = "no SkyDex",
            modifier = Modifier.weight(1f),
            onClick = null
        )
        StatTile(
            value = "${profile.friends}",
            label = "amigos",
            hint = "ver todos",
            modifier = Modifier.weight(1f),
            onClick = onOpenFriends
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1F2937))
            Text(label, fontSize = 12.sp, color = Color.Gray)
            if (onClick != null) {
                TextButton(onClick = onClick) {
                    Text(hint, fontSize = 11.sp, color = Color(0xFF0284C7))
                }
            } else {
                Text(hint, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: BadgeResponse) {
    val accent = if (badge.unlocked) Color(0xFFF59E0B) else Color(0xFF9CA3AF)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (badge.unlocked) Color.White else Color(0xFFE5E7EB)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (badge.unlocked) 3.dp else 0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.15f), shape = CircleShape) {
                Icon(
                    imageVector = if (badge.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(8.dp).size(24.dp)
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = badge.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (badge.unlocked) Color(0xFF1F2937) else Color.Gray
                    )
                    if (isRecent(badge.unlockedAt)) {
                        Spacer(Modifier.size(6.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "NOVO",
                                color = Color(0xFF10B981),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = badge.description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/** A badge unlocked in the last day gets a NOVO marker — the payoff for the capture. */
private fun isRecent(unlockedAt: String?): Boolean {
    if (unlockedAt == null) return false
    return try {
        Duration.between(Instant.parse(unlockedAt), Instant.now()).toHours() < 24
    } catch (e: Exception) {
        false
    }
}
