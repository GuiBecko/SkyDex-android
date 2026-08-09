package com.example.skydex.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.skydex.ServiceLocator
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
import com.example.skydex.ui.home.HomeScreen
import com.example.skydex.ui.home.HomeViewModel
import com.example.skydex.ui.skydex.SkyDexScreen
import com.example.skydex.ui.skydex.SkyDexViewModel

private val BAR_ROUTES = setOf(Routes.HOME, Routes.NEARBY, Routes.MY_CAPTURES, Routes.SKYDEX)

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
 * signed-out Home. There is no logout affordance in the app yet; this is the contract for the task
 * that adds one.
 */
@Composable
fun SkyDexNavHost(session: Session?, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.LOGIN
    val startDestination = remember { if (session == null) Routes.LOGIN else Routes.HOME }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                    onStartCapture = { navController.navigate(Routes.CAPTURE) }
                )
            }

            // Registered but unreachable: AppBottomBar still ships no NEARBY tab. See the note on
            // Routes.NEARBY usage in AppBottomBar.kt — restoring the tab is left to whichever task
            // actually gives NEARBY a screen distinct from HOME's dashboard (see the report on this
            // task for why Task 10's own plan leaves that split undone).
            composable(Routes.NEARBY) {
                val vm: HomeViewModel = viewModel {
                    HomeViewModel(
                        ServiceLocator.captureRepository,
                        ServiceLocator.deviceLocation::current
                    )
                }
                HomeScreen(
                    viewModel = vm,
                    onStartCapture = { navController.navigate(Routes.CAPTURE) }
                )
            }

            composable(Routes.CAPTURE) {
                val vm: CaptureViewModel = viewModel {
                    CaptureViewModel(
                        ServiceLocator.captureRepository,
                        ServiceLocator.deviceLocation::current
                    )
                }
                CaptureScreen(
                    viewModel = vm,
                    onSaved = {
                        navController.navigate(Routes.MY_CAPTURES) {
                            popUpTo(Routes.CAPTURE) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.MY_CAPTURES) {
                val vm: MyCapturesViewModel = viewModel { MyCapturesViewModel(ServiceLocator.captureRepository) }
                MyCapturesScreen(viewModel = vm)
            }

            composable(Routes.SKYDEX) {
                val vm: SkyDexViewModel = viewModel { SkyDexViewModel(ServiceLocator.skyDexRepository) }
                SkyDexScreen(viewModel = vm)
            }
        }
    }
}
