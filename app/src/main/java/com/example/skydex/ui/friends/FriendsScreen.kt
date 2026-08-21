package com.example.skydex.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.KeyboardOptions
import com.example.skydex.data.remote.dto.FriendRequestResponse
import com.example.skydex.data.remote.dto.FriendResponse
import com.example.skydex.ui.common.Tone
import com.example.skydex.ui.common.UiMessage
import com.example.skydex.ui.components.SkyDexEmptyState
import com.example.skydex.ui.components.SkyDexNotice
import com.example.skydex.ui.theme.SkyDexPalette
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme

@Composable
fun FriendsScreen(viewModel: FriendsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    FriendsContent(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onSendRequest = viewModel::sendRequest,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
        onUnfriend = viewModel::unfriend,
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
    onUnfriend: (FriendResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // The "Amigos" heading that used to be the first item is gone — the NavHost draws it in a
        // real TopAppBar now (audit finding A8). The bottom inset keeps the last row clear of the
        // bottom bar (finding M5).
        contentPadding = PaddingValues(
            start = SkyDexSpacing.screenPadding,
            end = SkyDexSpacing.screenPadding,
            top = SkyDexSpacing.lg,
            bottom = SkyDexSpacing.listBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(SkyDexSpacing.xl)
    ) {
        item {
            InviteCard(
                email = state.email,
                message = state.message,
                onEmailChanged = onEmailChanged,
                onSendRequest = onSendRequest
            )
        }

        item { SectionHeader("Convites recebidos") }

        if (state.requests.isEmpty()) {
            item {
                // No CTA here on purpose: the way forward is the invite field a few dp above, so
                // the copy points at it instead of sending the user somewhere else.
                SkyDexEmptyState(
                    icon = Icons.Default.MarkEmailRead,
                    title = "Nenhum convite por enquanto",
                    body = "Quando alguém convidar você, o convite aparece aqui."
                )
            }
        } else {
            items(state.requests, key = { it.id }) { request ->
                FriendRequestRow(request = request, onAccept = onAccept, onDecline = onDecline)
            }
        }

        item { SectionHeader("Meus amigos") }

        if (state.friends.isEmpty()) {
            item {
                SkyDexEmptyState(
                    icon = Icons.Default.Group,
                    title = "Sua lista ainda está vazia",
                    body = "Use o campo acima para convidar alguém pelo e-mail."
                )
            }
        } else {
            // Keyed, and it matters here rather than merely being tidy: [FriendRow] holds its
            // confirmation dialog in `rememberSaveable`, which is stored per slot. Unkeyed, removing
            // the first friend shifts everyone up a slot and the open dialog would reappear over
            // whoever inherited it — pointed at a different person than the one the user picked.
            items(state.friends, key = { it.friendshipId }) { friend ->
                FriendRow(friend = friend, onUnfriend = onUnfriend)
            }
        }
    }
}

@Composable
private fun InviteCard(
    email: String,
    message: UiMessage?,
    onEmailChanged: (String) -> Unit,
    onSendRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChanged,
                label = { Text("E-mail do seu amigo") },
                singleLine = true,
                // An address typed on a phone keyboard that auto-capitalises is an address that
                // fails to match. The e-mail keyboard also puts "@" on the main row.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SkyDexSpacing.md))
            Button(onClick = onSendRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Convidar", style = MaterialTheme.typography.titleMedium)
            }
            // The tone rides on the message. It used to be inferred with
            // `message == "Convite enviado!"` (audit finding A5), so an edit as small as
            // dropping the exclamation mark repainted a success as a red failure — and the
            // success itself rendered grey. Nothing here reads the copy any more.
            message?.let {
                Spacer(Modifier.height(SkyDexSpacing.sm))
                SkyDexNotice(message = it)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = SkyDexPalette.colors.textPrimary
    )
}

@Composable
private fun FriendRequestRow(
    request: FriendRequestResponse,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
            Text(
                text = request.requesterName,
                style = MaterialTheme.typography.titleMedium,
                color = SkyDexPalette.colors.textPrimary
            )
            Text(
                text = request.requesterEmail,
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textSecondary
            )
            Spacer(Modifier.height(SkyDexSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(SkyDexSpacing.sm)) {
                Button(onClick = { onAccept(request.id) }) { Text("Aceitar") }
                TextButton(onClick = { onDecline(request.id) }) { Text("Recusar") }
            }
        }
    }
}

@Composable
private fun FriendRow(friend: FriendResponse, onUnfriend: (FriendResponse) -> Unit) {
    // Per row, and `rememberSaveable` so a rotation with the dialog open does not silently answer
    // "cancelar" — the same reasoning as `ProfileScreen.confirmingLogout`. See the `key` on the
    // `items` call for why this must not be slot-positional.
    var confirming by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = SkyDexSpacing.xs),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SkyDexSpacing.lg)) {
            Text(
                text = friend.name,
                style = MaterialTheme.typography.titleMedium,
                color = SkyDexPalette.colors.textPrimary
            )
            Text(
                text = friend.email,
                style = MaterialTheme.typography.bodySmall,
                color = SkyDexPalette.colors.textSecondary
            )
            // A `TextButton`, not the `Button` that "Aceitar" gets: removing a friend is never the
            // action the screen is inviting, so it must not compete with the invite field above.
            TextButton(onClick = { confirming = true }) {
                Text("Desfazer amizade")
            }
        }
    }

    if (confirming) {
        UnfriendConfirmationDialog(
            friendName = friend.name,
            onConfirm = {
                confirming = false
                // The whole row, not one of its two ids: picking between them is the ViewModel's
                // job, where a test can see the choice. See `FriendsViewModel.unfriend`.
                onUnfriend(friend)
            },
            onDismiss = { confirming = false }
        )
    }
}

/**
 * Mirrors `ProfileScreen.LogoutConfirmationDialog` down to the button colours, and for the same
 * reason: this is the second action in the app that throws work away, and the only one that throws
 * away something the user cannot get back alone — re-friending needs the other person to accept
 * again. Red on the confirm side only; "Cancelar" stays neutral so the safe choice is not the loud
 * one.
 */
@Composable
private fun UnfriendConfirmationDialog(
    friendName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Desfazer amizade?", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                "Você e $friendName param de ver os registros um do outro. Para voltar atrás, " +
                    "alguém precisa convidar de novo e o outro aceitar.",
                style = MaterialTheme.typography.bodyLarge,
                color = SkyDexPalette.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SkyDexPalette.colors.danger
                )
            ) {
                Text("Desfazer", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = MaterialTheme.typography.titleMedium)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

private val previewRequests = listOf(
    FriendRequestResponse("r1", "u2", "Bob", "bob@skydex.com", "2026-08-02T10:00:00Z")
)

private val previewFriends = listOf(
    FriendResponse("f1", "u1", "Alice", "alice@skydex.com", "2026-08-01T10:00:00Z")
)

@Preview(showBackground = true)
@Composable
private fun FriendsContentPreview() {
    SkyDexTheme(darkTheme = false) {
        FriendsContent(
            state = FriendsUiState(requests = previewRequests, friends = previewFriends),
            onEmailChanged = {},
            onSendRequest = {},
            onAccept = {},
            onDecline = {},
            onUnfriend = {}
        )
    }
}

@Preview(showBackground = true, name = "Amigos - escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FriendsContentDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FriendsContent(
            state = FriendsUiState(requests = previewRequests, friends = previewFriends),
            onEmailChanged = {},
            onSendRequest = {},
            onAccept = {},
            onDecline = {},
            onUnfriend = {}
        )
    }
}

/** The A5 regression guard, made visible: the invite confirmation must read as a success. */
@Preview(showBackground = true, name = "Amigos - convite enviado")
@Composable
private fun FriendsContentInviteSentPreview() {
    SkyDexTheme(darkTheme = false) {
        FriendsContent(
            state = FriendsUiState(
                friends = previewFriends,
                message = UiMessage(
                    title = "Convite enviado!",
                    body = "Avisamos você assim que ele aceitar.",
                    tone = Tone.SUCCESS
                )
            ),
            onEmailChanged = {},
            onSendRequest = {},
            onAccept = {},
            onDecline = {},
            onUnfriend = {}
        )
    }
}

@Preview(showBackground = true, name = "Amigos - vazio")
@Composable
private fun FriendsContentEmptyPreview() {
    SkyDexTheme(darkTheme = false) {
        FriendsContent(
            state = FriendsUiState(),
            onEmailChanged = {},
            onSendRequest = {},
            onAccept = {},
            onDecline = {},
            onUnfriend = {}
        )
    }
}

@Preview(showBackground = true, name = "Amigos - vazio, escuro", backgroundColor = 0xFF0B1220)
@Composable
private fun FriendsContentEmptyDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        FriendsContent(
            state = FriendsUiState(),
            onEmailChanged = {},
            onSendRequest = {},
            onAccept = {},
            onDecline = {},
            onUnfriend = {}
        )
    }
}
