package com.example.skydex.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.skydex.ServiceLocator
import com.example.skydex.data.remote.dto.WeatherEventResponse
import com.example.skydex.data.session.Session
import com.example.skydex.ui.auth.LoginScreen
import com.example.skydex.ui.auth.LoginViewModel
import com.example.skydex.ui.auth.RegisterScreen
import com.example.skydex.ui.auth.RegisterViewModel
import com.example.skydex.ui.capture.CaptureScreen
import com.example.skydex.ui.capture.CaptureViewModel
import com.example.skydex.ui.captures.MyCapturesScreen
import com.example.skydex.ui.captures.MyCapturesViewModel
import com.example.skydex.ui.components.AppBottomBar
import com.example.skydex.ui.detail.CaptureDetailScreen
import com.example.skydex.ui.detail.CaptureDetailViewModel
import com.example.skydex.ui.detail.CaptureOrigin
import com.example.skydex.ui.feed.FeedScreen
import com.example.skydex.ui.feed.FeedViewModel
import com.example.skydex.ui.friends.FriendsScreen
import com.example.skydex.ui.friends.FriendsViewModel
import com.example.skydex.ui.home.HomeScreen
import com.example.skydex.ui.home.HomeViewModel
import com.example.skydex.ui.profile.ProfileScreen
import com.example.skydex.ui.profile.ProfileViewModel
import com.example.skydex.ui.skydex.SkyDexScreen
import com.example.skydex.ui.skydex.SkyDexViewModel

/**
 * Routes that show the bottom bar.
 *
 * **Exactly the four routes `AppBottomBar` has an item for, and no others.** Finding A7 was
 * `MY_CAPTURES` and `FRIENDS` sitting in this set with nothing in the bar to match them: opening
 * either drew all four tabs unselected, so the bar stopped telling the user where they were. They
 * are pushed destinations — reached from Home and from Profile, not from the bar — and they now
 * carry a back arrow instead (see [TOP_BARS]).
 *
 * If you add an item to `AppBottomBar`, add its route here too. If you add a route here without an
 * item there, you have reintroduced A7.
 */
private val BAR_ROUTES = setOf(
    Routes.FEED,
    Routes.HOME,
    Routes.SKYDEX,
    Routes.PROFILE
)

/** Title and back affordance for one destination's top bar. */
private data class TopBarSpec(val title: String, val showBack: Boolean)

/**
 * The app's title bar, in one place.
 *
 * Finding A8: no screen had a top bar, and every screen's title was an `item {}` inside its own
 * `LazyColumn` — so two scrolls in, the title was gone and nothing said where the user was. Titles
 * are centralised here so they cannot scroll away and cannot drift apart between screens.
 *
 * A route absent from this map gets **no top bar at all**. `LOGIN` and `REGISTER` are absent on
 * purpose: they are full-bleed, pre-authentication screens that own their own layout, and a bar
 * there would only add chrome.
 *
 * `showBack` marks the pushed destinations. It is the exact inverse of [BAR_ROUTES] over the
 * destinations listed here: a route with the bottom bar is a tab and never shows a back arrow; a
 * route without it was pushed and always does. Keep it that way — a screen offering both is a
 * screen the user cannot form a mental model of.
 */
private val TOP_BARS = mapOf(
    Routes.HOME to TopBarSpec("Eventos Próximos", showBack = false),
    Routes.FEED to TopBarSpec("Feed", showBack = false),
    Routes.SKYDEX to TopBarSpec("Meu SkyDex", showBack = false),
    Routes.PROFILE to TopBarSpec("Perfil", showBack = false),
    Routes.CAPTURE to TopBarSpec("Novo Registro", showBack = true),
    Routes.MY_CAPTURES to TopBarSpec("Meus Registros", showBack = true),
    Routes.FRIENDS to TopBarSpec("Amigos", showBack = true),
    // Keyed by the *pattern* (`capture_detail/{captureId}/{origin}`), not by a filled-in address:
    // `NavDestination.route` reports the pattern, so a key built with `Routes.captureDetail(...)`
    // would never match and the screen would lose its bar — and with it its back arrow.
    //
    // The title is the generic "Registro" rather than the capture's own: a bar title is a location
    // in the app, and the capture's title is already the 28sp heading one line below it. Two
    // copies of the same sentence, one of them truncated to fit, says less than one.
    Routes.CAPTURE_DETAIL to TopBarSpec("Registro", showBack = true)
)

/**
 * Flat title bar: painted on the theme background rather than a raised surface, with no scroll
 * behaviour and no shadow. The product brief for this app is "leveza" — a chrome-heavy elevated
 * bar on every screen is the opposite of that, so the bar reads as part of the canvas and lets the
 * content carry the weight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkyDexTopBar(spec: TopBarSpec, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            if (spec.showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

/**
 * The one place in the UI that is allowed to know about [ServiceLocator]: it builds each
 * ViewModel over the container and hands it to a screen. Screens and ViewModels themselves take
 * what they need as parameters, which is what keeps the ViewModels unit-testable and the
 * `@Preview`s renderable — the preview renderer never runs `SkyDexApplication.onCreate`, so
 * anything reaching for the container from composition would blow up in the IDE.
 *
 * [session] is read **once**, to decide where to start, and every later value is ignored by
 * design. `NavHost` pins its graph on first composition, so a start destination that changed
 * underneath it would tear the back stack down and rebuild it — which is exactly what would happen
 * on login, mid-login, if the `remember` were dropped.
 *
 * The consequence to know before you rely on it: **passing `null` here later does not return the
 * user to the login screen.** They stay wherever they are, now without a token. Logout must be
 * explicit navigation from whichever screen owns the button:
 *
 * ```
 * navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
 * ```
 *
 * `popUpTo(0)` clears the entire back stack, so "back" from the login screen cannot walk into a
 * signed-out Home. The Profile screen owns the logout affordance and navigates through exactly this
 * contract on [Routes.PROFILE].
 */
@Composable
fun SkyDexNavHost(session: Session?, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.LOGIN
    val startDestination = remember { if (session == null) Routes.LOGIN else Routes.HOME }

    /**
     * Opens one capture's detail page.
     *
     * The two statements have to stay together and in this order, which is the whole reason this is
     * a function rather than two lambdas copy-pasted into Meus Registros and the Feed: the capture
     * is stashed **before** the navigation, because the destination resolves it during its very
     * first composition. Navigate first and the detail screen renders its "não foi possível abrir
     * este registro" state for a capture that is sitting right there.
     *
     * This is also the only place in the app that writes to the registry. The list screens hand over
     * the object they already have and know nothing about where it goes — see [CaptureRegistry].
     */
    fun openCaptureDetail(capture: WeatherEventResponse, origin: CaptureOrigin) {
        ServiceLocator.captureRegistry.remember(capture)
        navController.navigate(Routes.captureDetail(capture.id, origin))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Absent from TOP_BARS => no bar composed at all, so LOGIN and REGISTER keep their
        // full-height layout instead of paying for an empty one.
        topBar = {
            TOP_BARS[currentRoute]?.let { spec ->
                SkyDexTopBar(spec = spec, onBack = { navController.popBackStack() })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in BAR_ROUTES) {
                AppBottomBar(currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel { LoginViewModel(ServiceLocator.authRepository) }
                LoginScreen(
                    viewModel = vm,
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
                )
            }

            composable(Routes.REGISTER) {
                val vm: RegisterViewModel = viewModel { RegisterViewModel(ServiceLocator.authRepository) }
                RegisterScreen(
                    viewModel = vm,
                    onRegistered = { navController.popBackStack() },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel {
                    HomeViewModel(
                        ServiceLocator.captureRepository,
                        ServiceLocator.deviceLocation::current
                    )
                }
                HomeScreen(
                    viewModel = vm,
                    onStartCapture = { navController.navigate(Routes.CAPTURE) },
                    onOpenMyCaptures = { navController.navigate(Routes.MY_CAPTURES) }
                )
            }

            composable(Routes.CAPTURE) {
                val vm: CaptureViewModel = viewModel {
                    CaptureViewModel(
                        captures = ServiceLocator.captureRepository,
                        // The new dependency, and it exists for exactly two lines of the reward
                        // overlay: "Você chegou ao nível N" and "Nova conquista". `POST
                        // /api/captures` returns neither a level nor a badge list, so both can
                        // only come from a before/after diff of the profile — see
                        // `CaptureViewModel.profile` and `loadBonus`.
                        //
                        // Passed as a method reference rather than as the `ProfileGateway`
                        // interface on purpose: that interface belongs to the profile screen, and
                        // the capture flow has no business depending on it for one optional read.
                        // Everything about this argument is optional — remove it and the whole
                        // flow still works, minus those two lines of the celebration.
                        profile = ServiceLocator.profileRepository::profile,
                        locationProvider = ServiceLocator.deviceLocation::current
                    )
                }
                CaptureScreen(
                    viewModel = vm,
                    // Unchanged in what it does, changed in when it runs. The screen no longer
                    // navigates the instant the capture lands (audit finding B6); it shows the
                    // reward first, and this fires when the user taps "Ver meus registros" — or
                    // presses back over the overlay, which `CaptureScreen` routes here too.
                    onSaved = {
                        navController.navigate(Routes.MY_CAPTURES) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                            // Meus Registros can now reach Capture itself (its empty state's CTA),
                            // so "Meus Registros -> Capture -> save" would otherwise land a second
                            // copy of Meus Registros on top of the first, and the first back press
                            // would look like it did nothing. The pop above runs before this check,
                            // so by now the top of the stack is whatever the user came from.
                            launchSingleTop = true
                        }
                    }
                )
            }

            // The three empty-state CTAs below are optional parameters on their screens: the button
            // renders only when a callback is supplied, so a screen composed without one shows no
            // dead button. Supplying them here is what makes those buttons exist at all — leave one
            // out and its empty state silently loses its only way forward (audit finding A10).
            composable(Routes.MY_CAPTURES) {
                val vm: MyCapturesViewModel = viewModel { MyCapturesViewModel(ServiceLocator.captureRepository) }
                MyCapturesScreen(
                    viewModel = vm,
                    onStartCapture = { navController.navigate(Routes.CAPTURE) },
                    onOpenCapture = { capture -> openCaptureDetail(capture, CaptureOrigin.MINE) }
                )
            }

            composable(Routes.SKYDEX) {
                val vm: SkyDexViewModel = viewModel { SkyDexViewModel(ServiceLocator.skyDexRepository) }
                SkyDexScreen(
                    viewModel = vm,
                    onStartCapture = { navController.navigate(Routes.CAPTURE) }
                )
            }

            composable(Routes.FEED) {
                val vm: FeedViewModel = viewModel { FeedViewModel(ServiceLocator.socialRepository) }
                FeedScreen(
                    viewModel = vm,
                    onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    onOpenCapture = { capture -> openCaptureDetail(capture, CaptureOrigin.FEED) }
                )
            }

            composable(
                route = Routes.CAPTURE_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_CAPTURE_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_ORIGIN) { type = NavType.StringType }
                )
            ) { entry ->
                val captureId = entry.arguments?.getString(Routes.ARG_CAPTURE_ID).orEmpty()
                val origin = CaptureOrigin.parse(entry.arguments?.getString(Routes.ARG_ORIGIN))

                val vm: CaptureDetailViewModel = viewModel {
                    // Resolved from memory, not fetched: there is no `GET api/events/{id}`. On a
                    // miss — process death restored the route but not the registry — the ViewModel
                    // publishes `UiState.Error` and the screen offers "Voltar". See CaptureRegistry.
                    CaptureDetailViewModel(captureId, ServiceLocator.captureRegistry)
                }

                CaptureDetailScreen(
                    viewModel = vm,
                    origin = origin,
                    onBack = { navController.popBackStack() },
                    // Only from the Feed. Wiring it for MINE would put a "ver amigos" button next to
                    // the user's own photo, and the author block it lives in is not even drawn
                    // there. `null` means the affordance does not render at all — the same optional-
                    // callback contract the empty-state CTAs use.
                    onOpenFriends = if (origin == CaptureOrigin.FEED) {
                        { navController.navigate(Routes.FRIENDS) }
                    } else {
                        null
                    }
                )
            }

            composable(Routes.FRIENDS) {
                val vm: FriendsViewModel = viewModel { FriendsViewModel(ServiceLocator.socialRepository) }
                FriendsScreen(viewModel = vm)
            }

            composable(Routes.PROFILE) {
                val vm: ProfileViewModel = viewModel {
                    ProfileViewModel(
                        gateway = ServiceLocator.profileRepository,
                        onLogout = { ServiceLocator.authRepository.logout() }
                    )
                }
                ProfileScreen(
                    viewModel = vm,
                    onOpenMyCaptures = { navController.navigate(Routes.MY_CAPTURES) },
                    onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
