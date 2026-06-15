package com.paylisher.test

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun PaylisherTestApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLogin = { userId ->
                    navController.navigate("main/$userId") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("main/{userId}") { backStack ->
            val userId = backStack.arguments?.getString("userId") ?: ""
            MainTabScreen(
                userId = userId,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("main/$userId") { inclusive = true }
                    }
                }
            )
        }
    }
}
