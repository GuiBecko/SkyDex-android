package com.example.skydex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skydex.ui.navigation.Routes

private data class BarItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun AppBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        BarItem(Routes.NEARBY, Icons.Default.WbSunny, "Eventos Próximos"),
        BarItem(Routes.HOME, Icons.Default.Home, "Início"),
        BarItem(Routes.MY_CAPTURES, Icons.Default.Dataset, "Meus Registros")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val tint by animateColorAsState(
                if (selected) Color(0xFF0284C7) else Color.Gray,
                label = "tint-${item.route}"
            )
            val size by animateDpAsState(
                if (selected) 36.dp else 28.dp,
                label = "size-${item.route}"
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { onNavigate(item.route) }) {
                    Icon(
                        modifier = Modifier.size(size),
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppBottomBarPreview() {
    AppBottomBar(currentRoute = Routes.HOME, onNavigate = {})
}
