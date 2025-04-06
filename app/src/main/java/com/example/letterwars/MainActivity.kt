package com.example.letterwars

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.rememberNavController
import com.example.letterwars.ui.navigation.AppNavGraph
import com.example.letterwars.ui.screen.login.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            MaterialTheme {
                AppNavGraph(navController = navController)
            }
        }
    }
}

