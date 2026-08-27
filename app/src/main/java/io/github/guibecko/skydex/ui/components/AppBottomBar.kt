package io.github.guibecko.skydex.ui.components

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.guibecko.skydex.ui.navigation.Routes
import io.github.guibecko.skydex.ui.theme.SkyDexSpacing
import io.github.guibecko.skydex.ui.theme.SkyDexTheme

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
 * Above this the badge shows `9+` instead of the number. Not a design flourish: the dot is drawn
 * over a fixed-size icon slot, and a third digit pushes it wide enough to collide with the
 * neighbouring tab.
 */
private const val MaxBadgeCount = 9

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
/**
 * @param pendingInvites friend invites waiting to be answered. Badges the **Perfil** tab, because
 *   that is where the route to Amigos lives — `FRIENDS` is a pushed destination and has no tab of
 *   its own to badge. Zero draws nothing.
 */
@Composable
fun AppBottomBar(currentRoute: String, pendingInvites: Int = 0, onNavigate: (String) -> Unit) {
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
                        badgeCount = if (item.route == Routes.PROFILE) pendingInvites else 0,
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
    badgeCount: Int,
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
        // Fixed-height slot: the icon animates inside it so the label below never moves. The badge
        // is drawn inside the same slot, so a tab that gains one does not grow taller than its
        // neighbours and shove its own label down.
        Box(
            modifier = Modifier.size(SelectedIconSize),
            contentAlignment = Alignment.Center
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge {
                            Text(
                                // The count is the whole point — a bare dot says "something
                                // happened" and makes the user open the screen to find out how much.
                                text = if (badgeCount > MaxBadgeCount) "$MaxBadgeCount+" else "$badgeCount",
                                // Announced here rather than on the icon: this is the part of the
                                // tab a reader has no other way to learn about.
                                modifier = Modifier.semantics {
                                    contentDescription = if (badgeCount == 1) {
                                        "1 convite de amizade"
                                    } else {
                                        "$badgeCount convites de amizade"
                                    }
                                }
                            )
                        }
                    }
                }
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = item.icon,
                    // The label is right underneath and `Role.Tab` already announces selection
                    // state; a contentDescription here would make the reader say the name twice.
                    contentDescription = null,
                    tint = tint
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(top = SkyDexSpacing.xs)
        )
    }
}

@Preview(name = "Bottom bar — invites pending", showBackground = true)
@Composable
private fun AppBottomBarBadgePreview() {
    SkyDexTheme {
        AppBottomBar(currentRoute = Routes.FEED, pendingInvites = 3, onNavigate = {})
    }
}

@Preview(name = "Bottom bar — invites overflowing", showBackground = true)
@Composable
private fun AppBottomBarBadgeOverflowPreview() {
    SkyDexTheme {
        AppBottomBar(currentRoute = Routes.FEED, pendingInvites = 42, onNavigate = {})
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
