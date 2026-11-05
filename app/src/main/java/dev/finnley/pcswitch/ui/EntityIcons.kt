package dev.finnley.pcswitch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.finnley.pcswitch.ui.Accent
import dev.finnley.pcswitch.ui.pixelSize

// Pixel-art gothic power icon
@Composable
fun PixelPowerIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isPowerPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic light icon
@Composable
fun PixelLightIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isLightPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic switch icon
@Composable
fun PixelSwitchIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isSwitchPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic window icon
@Composable
fun PixelWindowIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isWindowPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic lock icon
@Composable
fun PixelLockIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isLockPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic fan icon
@Composable
fun PixelFanIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isFanPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic settings icon
@Composable
fun PixelSettingsIcon(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxSize()) {
                    repeat(12) { x ->
                        val isPixel = isSettingsPixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

/** Get entity type pixel icon based on domain prefix. */
@Composable
fun EntityPixelIcon(entityId: String, modifier: Modifier = Modifier, color: Color = Accent) {
    when {
        entityId.startsWith("button.") || entityId.startsWith("input_button.") -> PixelPowerIcon(modifier, color)
        entityId.startsWith("light.") -> PixelLightIcon(modifier, color)
        entityId.startsWith("switch.") -> PixelSwitchIcon(modifier, color)
        entityId.startsWith("cover.") -> PixelWindowIcon(modifier, color)
        entityId.startsWith("lock.") -> PixelLockIcon(modifier, color)
        entityId.startsWith("fan.") -> PixelFanIcon(modifier, color)
        else -> PixelSettingsIcon(modifier, color)
    }
}

// Pixel patterns
fun isPowerPixel(x: Int, y: Int): Boolean {
    return when (y) {
        3 -> x == 5
        4 -> x == 5
        5 -> x == 5 || x == 4 || x == 6
        6 -> x == 4 || x == 7
        7 -> x == 4 || x == 7
        8 -> x == 4 || x == 7
        9 -> x == 4 || x == 7
        10 -> x == 4 || x == 7
        else -> false
    }
}

fun isLightPixel(x: Int, y: Int): Boolean {
    return when (y) {
        2 -> x == 5
        3 -> x == 5
        4 -> x == 5
        5 -> x == 4 || x == 5 || x == 6
        6 -> x == 3 || x == 8
        7 -> x == 3 || x == 8
        8 -> x == 3 || x == 4 || x == 7 || x == 8
        9 -> x == 4 || x == 5 || x == 6
        10 -> x == 4 || x == 5 || x == 6
        else -> false
    }
}

fun isSwitchPixel(x: Int, y: Int): Boolean {
    return when (y) {
        4 -> x == 3 || x == 8
        5 -> x == 3 || x == 8
        6 -> x == 2 || x == 9
        7 -> x == 2 || x == 9
        8 -> x == 3 || x == 8
        9 -> x == 3 || x == 8
        else -> false
    }
}

fun isWindowPixel(x: Int, y: Int): Boolean {
    return when (y) {
        2 -> x in 2..9
        3 -> x == 2 || x == 9
        4 -> x == 2 || x == 9
        5 -> x == 2 || x == 9
        6 -> x == 2 || x == 5 || x == 9
        7 -> x == 2 || x == 5 || x == 9
        8 -> x == 2 || x == 9
        9 -> x == 2 || x == 9
        10 -> x in 2..9
        else -> false
    }
}

fun isLockPixel(x: Int, y: Int): Boolean {
    return when (y) {
        2 -> x == 4 || x == 7
        3 -> x == 4 || x == 7
        4 -> x == 4 || x == 7
        5 -> x in 2..9
        6 -> x == 2 || x == 9
        7 -> x == 2 || x == 9
        8 -> x == 2 || x == 9
        9 -> x in 2..9
        else -> false
    }
}

fun isFanPixel(x: Int, y: Int): Boolean {
    return when (y) {
        2 -> x == 5
        3 -> x == 3 || x == 7
        4 -> x == 3 || x == 7
        5 -> x == 4 || x == 5 || x == 6
        6 -> x == 2 || x == 8
        7 -> x == 4 || x == 5 || x == 6
        8 -> x == 3 || x == 7
        9 -> x == 3 || x == 7
        10 -> x == 5
        else -> false
    }
}

fun isSettingsPixel(x: Int, y: Int): Boolean {
    return when (y) {
        3 -> x == 5
        4 -> x == 4 || x == 5 || x == 6
        5 -> x == 2 || x == 3 || x == 8 || x == 9
        6 -> x == 2 || x == 9
        7 -> x == 2 || x == 9
        8 -> x == 2 || x == 3 || x == 8 || x == 9
        9 -> x == 4 || x == 5 || x == 6
        10 -> x == 5
        else -> false
    }
}