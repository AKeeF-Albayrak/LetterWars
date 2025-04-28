package com.example.letterwars

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.letterwars.ui.navigation.AppNavGraph
import com.example.letterwars.ui.theme.LetterWarsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LetterWarsTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

}