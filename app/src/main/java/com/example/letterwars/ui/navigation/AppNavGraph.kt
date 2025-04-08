package com.example.letterwars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.letterwars.ui.screen.home.HomeScreen
import com.example.letterwars.ui.screen.login.LoginScreen
import com.example.letterwars.ui.screen.register.RegisterScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(navController)
        }
        composable("register") {
            RegisterScreen(navController)
        }
        composable("home") {
            HomeScreen(
                onStartNewGame = { navController.navigate("new_game") },
                onViewActiveGames = { navController.navigate("active_games") },
                onViewFinishedGames = { navController.navigate("finished_games") },
                onProfileClick = { navController.navigate("profile") }
            )
        }
    }
}
