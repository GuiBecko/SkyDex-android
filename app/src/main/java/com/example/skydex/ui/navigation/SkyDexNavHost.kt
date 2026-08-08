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
import com.example.skydex.ui.captures.MyCapturesScreen
import com.example.skydex.ui.captures.MyCapturesViewModel
import com.example.skydex.ui.components.AppBottomBar
import com.example.skydex.ui.home.HomeScreen
import com.example.skydex.ui.home.HomeViewModel

private val BAR_ROUTES = setOf(Routes.HOME, Routes.NEARBY, Routes.MY_CAPTURES)

/**
 * The one place in the UI that is allowed to know about [ServiceLocator]: it builds each
 * ViewModel over the container and hands it to a screen. Screens and ViewModels themselves take
 * what they need as parameters, which is what keeps the ViewModels unit-testable and the
 * `@Preview`s renderable — the preview renderer never runs `SkyDexApplication.onCreate`, so
 * anything reaching for the container from composition would blow up in the IDE.
 *
 * [session] is read once, to decide where to start. Everything after that is explicit navigation:
 * `NavHost` pins its graph on first composition, so a start destination that changed underneath it
 * would tear the back stack down and rebuild it mid-session.
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

            // HOME and NEARBY render the same list for now; Task 10 gives HOME its own dashboard
            // with the capture button and leaves NEARBY as the phenomena list.
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel { HomeViewModel(ServiceLocator.captureRepository) }
                HomeScreen(viewModel = vm)
            }

            composable(Routes.NEARBY) {
                val vm: HomeViewModel = viewModel { HomeViewModel(ServiceLocator.captureRepository) }
                HomeScreen(viewModel = vm)
            }

            composable(Routes.MY_CAPTURES) {
                val vm: MyCapturesViewModel = viewModel { MyCapturesViewModel(ServiceLocator.captureRepository) }
                MyCapturesScreen(viewModel = vm)
            }
        }
    }
}
