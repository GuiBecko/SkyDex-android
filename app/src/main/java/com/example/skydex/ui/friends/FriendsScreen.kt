package com.example.skydex.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse

@Composable
fun FriendsScreen(viewModel: FriendsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    FriendsContent(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onSendRequest = viewModel::sendRequest,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
        modifier = modifier
    )
}

/** The screen without its ViewModel, so the `@Preview`s below can render it. */
@Composable
private fun FriendsContent(
    state: FriendsUiState,
    onEmailChanged: (String) -> Unit,
    onSendRequest: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Amigos", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = { Text("E-mail do seu amigo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSendRequest, modifier = Modifier.fillMaxWidth()) {
                        Text("Convidar")
                    }
                    state.message?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            color = if (message == "Convite enviado!") Color.Gray else Color(0xFFB91C1C)
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Convites recebidos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        if (state.requests.isEmpty()) {
            item {
                Text("Nenhum convite pendente.", color = Color.Gray)
            }
        } else {
            items(state.requests) { request ->
                FriendRequestRow(request = request, onAccept = onAccept, onDecline = onDecline)
            }
        }

        item {
            Text(
                "Meus amigos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        if (state.friends.isEmpty()) {
            item {
                Text("Você ainda não tem amigos no SkyDex.", color = Color.Gray)
            }
        } else {
            items(state.friends) { friend -> FriendRow(friend = friend) }
        }
    }
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestResponse,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(request.requesterName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(request.requesterEmail, color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAccept(request.id) }) { Text("Aceitar") }
                TextButton(onClick = { onDecline(request.id) }) { Text("Recusar") }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: FriendResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(friend.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(friend.email, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

private val previewRequests = listOf(
    FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
)

private val previewFriends = listOf(
    FriendResponse("u1", "Alice", "alice@skydex.com", "2026-08-01T10:00:00Z")
)

@Preview(showBackground = true)
@Composable
private fun FriendsContentPreview() {
    FriendsContent(
        state = FriendsUiState(requests = previewRequests, friends = previewFriends),
        onEmailChanged = {},
        onSendRequest = {},
        onAccept = {},
        onDecline = {}
    )
}

@Preview(showBackground = true, name = "Amigos - vazio")
@Composable
private fun FriendsContentEmptyPreview() {
    FriendsContent(
        state = FriendsUiState(),
        onEmailChanged = {},
        onSendRequest = {},
        onAccept = {},
        onDecline = {}
    )
}
