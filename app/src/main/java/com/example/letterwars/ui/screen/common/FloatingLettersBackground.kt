package com.example.letterwars.ui.screen.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun FloatingLettersBackground() {
    val letters = remember {
        listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z")
    }

    // Increased number of letters from 20 to 40
    val letterData = remember {
        List(40) {
            LetterData(
                letter = letters[Random.nextInt(letters.size)],
                initialX = Random.nextFloat(),
                initialY = Random.nextFloat(),
                size = Random.nextFloat() * 30f + 20f
            )
        }
    }

    // Animations for each letter
    val letterPositions = letterData.map {
        val xAnimation = remember { Animatable(it.initialX) }
        val yAnimation = remember { Animatable(it.initialY) }

        // Animate each letter
        LaunchedEffect(key1 = it) {
            while (true) {
                // Animate to a new random position
                launch {
                    xAnimation.animateTo(
                        targetValue = Random.nextFloat(),
                        animationSpec = tween(
                            durationMillis = (5000 * (Random.nextFloat() * 0.5f + 0.5f)).toInt(),
                            easing = LinearEasing
                        )
                    )
                }
                launch {
                    yAnimation.animateTo(
                        targetValue = Random.nextFloat(),
                        animationSpec = tween(
                            durationMillis = (7000 * (Random.nextFloat() * 0.5f + 0.5f)).toInt(),
                            easing = LinearEasing
                        )
                    )
                }
                delay(8000)
            }
        }

        AnimatedLetterData(
            letter = it.letter,
            x = xAnimation.value,
            y = yAnimation.value,
            size = it.size
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        letterPositions.forEach { letterData ->
            drawContext.canvas.nativeCanvas.drawText(
                letterData.letter,
                letterData.x * this.size.width,
                letterData.y * this.size.height,
                android.graphics.Paint().apply {
                    // Increased opacity from 40 to 80 (more visible)
                    color = android.graphics.Color.argb(80, 0, 0, 255)
                    textSize = letterData.size
                    isAntiAlias = true
                }
            )
        }
    }
}

// Simple data class to hold letter properties
data class LetterData(
    val letter: String,
    val initialX: Float,
    val initialY: Float,
    val size: Float
)

// Data class for animated letter data
data class AnimatedLetterData(
    val letter: String,
    val x: Float,
    val y: Float,
    val size: Float
)
