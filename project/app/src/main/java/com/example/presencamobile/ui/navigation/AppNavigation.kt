package com.example.presencamobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.presencamobile.ui.screens.login.LoginScreen
import com.example.presencamobile.ui.screens.login.LoginViewModel
import com.example.presencamobile.ui.screens.register.RegisterScreen
import com.example.presencamobile.ui.screens.register.RegisterViewModel
import com.example.presencamobile.ui.auth.SessionViewModel
import com.example.presencamobile.ui.screens.home.HomeScreen

private const val LOGIN_ROUTE = "login"
private const val REGISTER_ROUTE = "register"
private const val LOADING_ROUTE = "loading"
private const val HOME_ROUTE = "home"

@Composable

fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel()
    val session = sessionViewModel.uiState

    LaunchedEffect(session.isInitializing, session.isAuthenticated, session.userId) {
        if (!session.isInitializing) {
            val destination = if (session.isAuthenticated) HOME_ROUTE else LOGIN_ROUTE
            val currentRoute = navController.currentDestination?.route
            val alreadyInAuthFlow = !session.isAuthenticated &&
                (currentRoute == LOGIN_ROUTE || currentRoute == REGISTER_ROUTE)
            if (currentRoute == destination || alreadyInAuthFlow) return@LaunchedEffect
            // Remove o histórico anterior ao entrar ou sair da conta.
            navController.navigate(destination) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = LOADING_ROUTE,
        modifier = modifier
    ) {
        composable(route = LOADING_ROUTE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        composable(route = HOME_ROUTE) {
            if (session.isAuthenticated) {
                HomeScreen(
                    uiState = session,
                    onSignOutClick = sessionViewModel::onSignOutClick,
                    onRetryProfile = sessionViewModel::loadProfile
                )
            }
        }

        composable(route = LOGIN_ROUTE) {
            val loginViewModel: LoginViewModel = viewModel()

            LoginScreen(
                uiState = loginViewModel.uiState,
                sessionMessage = session.errorMessage,
                onEmailChange = loginViewModel::onEmailChange,
                onPasswordChange = loginViewModel::onPasswordChange,
                onLoginClick = loginViewModel::onLoginClick,
                onNavigateToRegister = {
                    navController.navigate(REGISTER_ROUTE) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(route = REGISTER_ROUTE) {
            val registerViewModel: RegisterViewModel = viewModel()

            RegisterScreen(
                uiState = registerViewModel.uiState,
                onNameChange = registerViewModel::onNameChange,
                onEmailChange = registerViewModel::onEmailChange,
                onPasswordChange = registerViewModel::onPasswordChange,
                onRoleChange = registerViewModel::onRoleChange,
                onRegisterClick = registerViewModel::onRegisterClick,
                onNavigateToLogin = {
                    navController.popBackStack(
                        route = LOGIN_ROUTE,
                        inclusive = false
                    )
                }
            )
        }
    }
}
