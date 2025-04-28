package com.example.letterwars.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.letterwars.data.repository.AuthRepository

/* ---------- Ekran composable’ları ---------- */
import com.example.letterwars.ui.screen.activegames.ActiveGamesScreen
import com.example.letterwars.ui.screen.finishedgames.FinishedGamesScreen
import com.example.letterwars.ui.screen.game.GameScreen
import com.example.letterwars.ui.screen.home.HomeScreen
import com.example.letterwars.ui.screen.login.LoginScreen
import com.example.letterwars.ui.screen.newgame.NewGameScreen
import com.example.letterwars.ui.screen.profile.UserProfileScreen
import com.example.letterwars.ui.screen.queue.QueueScreen
import com.example.letterwars.ui.screen.register.RegisterScreen

/**
 * Uygulamanın tüm yönlendirme (navigation) haritası.
 */
@Composable
fun AppNavGraph(navController: NavHostController) {

    /*--------------------------------------------------*
     *   Otomatik oturum açma (remember-me) kontrolü    *
     *--------------------------------------------------*/
    val authRepository     = remember { AuthRepository() }
    val rememberedUsername = authRepository.getRememberedUsername()

    LaunchedEffect(rememberedUsername) {
        if (rememberedUsername != null) {
            navController.navigate("home") { popUpTo(0) }
        }
    }

    /*--------------------------------------------------*
     *               Navigation haritası                *
     *--------------------------------------------------*/
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {

        /* ------------------ Kimlik Doğrulama ------------------ */
        composable("login")    { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }

        /* ----------------------- Ana Menü --------------------- */
        composable("home") {
            HomeScreen(
                onStartNewGame      = { navController.navigate("new_game") },
                onViewActiveGames   = { navController.navigate("active_games") },
                onViewFinishedGames = { navController.navigate("finished_games") },
                onProfileClick      = { navController.navigate("profile") }
            )
        }

        /* --------------------- Oyun Akışı ---------------------- */
        composable("new_game") { NewGameScreen(navController) }

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

        composable(
            route = "game/{gameId}",
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getString("gameId")
            GameScreen(gameId = gameId, navController = navController)
        }

        /* -------------------- Oyun Listeleri ------------------- */
        composable("active_games") {
            ActiveGamesScreen(navController = navController)
        }

        composable("finished_games") {
            FinishedGamesScreen(navController = navController)
        }

        /* ------------------ Kullanıcı Profili ------------------ */
        composable("profile") {
            UserProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut    = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
