package com.example.letterwars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.letterwars.ui.screen.home.HomeScreen
import com.example.letterwars.ui.screen.login.LoginScreen
import com.example.letterwars.ui.screen.newgame.NewGameScreen
import com.example.letterwars.ui.screen.profile.UserProfileScreen
import com.example.letterwars.ui.screen.queue.QueueScreen
import com.example.letterwars.ui.screen.register.RegisterScreen
import com.example.letterwars.ui.screen.game.GameScreen

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

        composable("new_game") {
            NewGameScreen(navController = navController)
        }

        composable(
            route = "queue?duration={duration}",
            arguments = listOf(
                navArgument("duration") {
                    type = NavType.StringType
                    defaultValue = "5"
                    nullable = true
                }
            )
        ) {
            QueueScreen(
                navController = navController,
                onMatchFound = { gameId ->
                    navController.navigate("game/$gameId") {
                        popUpTo("queue") { inclusive = true }
                    }
                }
            )
        }

        composable("active_games") {
            // ActiveGamesScreen will be implemented later
        }

        composable("finished_games") {
            // FinishedGamesScreen will be implemented later
        }

        composable("profile") {
            UserProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    // Çıkış yapıldığında login ekranına yönlendir ve backstack temizle
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable(
            "game/{gameId}",
            arguments = listOf(
                navArgument("gameId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            GameScreen(gameId = gameId, navController = navController)
        }
    }
}