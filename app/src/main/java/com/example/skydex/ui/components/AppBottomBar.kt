package com.example.skydex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.skydex.ui.navigation.Routes
import com.example.skydex.ui.theme.SkyDexSpacing
import com.example.skydex.ui.theme.SkyDexTheme

/** Hairline separating the bar from the content scrolling behind it. */
private val DividerThickness = 1.dp

/**
 * The icon grows on selection. Both ends come from the spacing scale, and the slot the icon sits in
 * is fixed at the larger of the two — otherwise the label underneath would bob up and down every
 * time the user switched tabs.
 */
private val SelectedIconSize = SkyDexSpacing.xxl
private val UnselectedIconSize = SkyDexSpacing.xl

private data class BarItem(val route: String, val icon: ImageVector, val label: String)

/**
 * The app's four top-level tabs.
 *
 * It has exactly these four items, which is why `SkyDexNavHost.BAR_ROUTES` must contain exactly
 * these four routes and nothing else — finding A7 was `MY_CAPTURES` and `FRIENDS` being in that set
 * with no item here to match, so the bar rendered with every tab unselected and the user lost track
 * of where they were. Those two are pushed destinations and carry a back arrow instead.
 *
 * Finding M3, all fixed here:
 * - the background was a hardcoded white literal that ignored the theme, so in dark mode the bar
 *   stayed white; it is now `colorScheme.surface`,
 * - there was no elevation and no top border, so white cards scrolling behind it merged into it;
 *   there is now a hairline divider on an opaque surface,
 * - `label` existed but was only fed to `contentDescription`, leaving a bar of bare icons; the
 *   labels are now drawn, at `labelLarge`.
 */
@Composable
fun AppBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        BarItem(Routes.FEED, Icons.Default.DynamicFeed, "Feed"),
        BarItem(Routes.HOME, Icons.Default.Home, "Início"),
        BarItem(Routes.SKYDEX, Icons.Default.CatchingPokemon, "SkyDex"),
        BarItem(Routes.PROFILE, Icons.Default.Person, "Perfil")
    )

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = DividerThickness,
                color = MaterialTheme.colorScheme.outline
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = SkyDexSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    BarTab(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * One tab. The whole column is the touch target — icon *and* label — rather than just the icon, so
 * a tap that lands on the word still switches tab. At `sm + xxl + xs + labelLarge + sm` it clears
 * the 48dp minimum even in its unselected state.
 */
@Composable
private fun BarTab(
    item: BarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tint-${item.route}"
    )
    val iconSize by animateDpAsState(
        if (selected) SelectedIconSize else UnselectedIconSize,
        label = "size-${item.route}"
    )

    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = SkyDexSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed-height slot: the icon animates inside it so the label below never moves.
        Box(
            modifier = Modifier.size(SelectedIconSize),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                imageVector = item.icon,
                // The label is right underneath and `Role.Tab` already announces selection state;
                // a contentDescription here would make the reader say the name twice.
                contentDescription = null,
                tint = tint
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(top = SkyDexSpacing.xs)
        )
    }
}

@Preview(name = "Bottom bar — light", showBackground = true)
@Composable
private fun AppBottomBarLightPreview() {
    SkyDexTheme(darkTheme = false) {
        AppBottomBar(currentRoute = Routes.HOME, onNavigate = {})
    }
}

@Preview(name = "Bottom bar — dark", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun AppBottomBarDarkPreview() {
    SkyDexTheme(darkTheme = true) {
        AppBottomBar(currentRoute = Routes.PROFILE, onNavigate = {})
    }
}
