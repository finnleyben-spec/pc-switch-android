package dev.finnley.pcswitch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark design with warm paper-like text (inspired by high-end editorial/gothic design)
val Bg = Color(0xFF0A0A0A)
val Surface = Color(0xFF141210)
val SurfaceAlt = Color(0xFF1A1612)
val Ink = Color(0xFFFDF8EF)
val InkSoft = Color(0xFFE8E0D0)
val Line = Color(0xFF3A2E20)
val Accent = Color(0xFFC4A96C)
val AccentSoft = Color(0xFFD4BC8A)
val Ok = Color(0xFF34D399)
val Warn = Color(0xFFEAB308)
val Err = Color(0xFFEF4444)

// Simple container without box decoration
@Composable
fun GothicCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier,
        content = content
    )
}

// Pixel-art gothic eye icon (open state)
@Composable
fun GothicEyeOpen(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        // Draw using nested boxes for pixel-art effect
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(12) { x ->
                        val isEyePixel = isOpenEyePixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isEyePixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

// Pixel-art gothic eye icon (closed/crossed state)
@Composable
fun GothicEyeClosed(modifier: Modifier = Modifier, color: Color = Accent) {
    Box(modifier = modifier.size(48.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(12) { y ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(12) { x ->
                        val isCrossedPixel = isClosedEyePixel(x, y)
                        Box(
                            modifier = Modifier
                                .size(pixelSize)
                                .then(if (isCrossedPixel) Modifier.background(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

val pixelSize = 3.dp

fun isOpenEyePixel(x: Int, y: Int): Boolean {
    // Open eye: oval shape with pupil
    // Upper arc: y=2, x=3..8; y=3, x=2..9
    // Middle: y=4..7, x=1..10 with eye opening
    // Lower arc: y=8, x=2..9; y=9, x=3..8
    return when (y) {
        2 -> x in 3..8
        3 -> x in 2..9
        4 -> x == 1 || x == 10 || (x in 5..6)
        5 -> x == 1 || x == 10 || (x in 4..7)
        6 -> x == 1 || x == 10 || (x in 4..7)
        7 -> x == 1 || x == 10 || (x in 5..6)
        8 -> x in 2..9
        9 -> x in 3..8
        else -> false
    }
}

fun isClosedEyePixel(x: Int, y: Int): Boolean {
    // Closed eye: same outer shape but with X through the middle
    val outer = isOpenEyePixel(x, y)
    if (!outer) return false
    
    // Replace pupil area with diagonal lines (X)
    if (y in 4..7) {
        if (y == 4) return x == 3 || x == 8
        if (y == 5) return x == 4 || x == 7
        if (y == 6) return x == 4 || x == 7
        if (y == 7) return x == 3 || x == 8
    }
    return false
}

// OutlinedTextField styling with warm paper-like text
@Composable
fun GothicOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    placeholder: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    visible: Boolean = false,
    onVisibleChange: ((Boolean) -> Unit)? = null
) {
    val showText = !password || visible

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = modifier,
        singleLine = singleLine,
        visualTransformation = if (!showText) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (password) {
                IconButton(onClick = { onVisibleChange?.invoke(!visible) }) {
                    Crossfade(
                        targetState = showText,
                        label = "eye_toggle"
                    ) { isShown ->
                        if (isShown) {
                            GothicEyeClosed(color = Accent)
                        } else {
                            GothicEyeOpen(color = Accent)
                        }
                    }
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            disabledTextColor = Ink.copy(alpha = 0.5f),
            errorTextColor = Err,
            focusedContainerColor = Bg,
            unfocusedContainerColor = Bg,
            disabledContainerColor = Bg,
            errorContainerColor = Bg,
            cursorColor = Accent,
            errorCursorColor = Err,
            focusedBorderColor = Accent,
            unfocusedBorderColor = InkSoft.copy(alpha = 0.3f),
            disabledBorderColor = InkSoft.copy(alpha = 0.2f),
            errorBorderColor = Err,
            focusedLeadingIconColor = Accent,
            unfocusedLeadingIconColor = InkSoft,
            disabledLeadingIconColor = InkSoft.copy(alpha = 0.5f),
            errorLeadingIconColor = Err,
            focusedTrailingIconColor = Accent,
            unfocusedTrailingIconColor = InkSoft,
            disabledTrailingIconColor = InkSoft.copy(alpha = 0.5f),
            errorTrailingIconColor = Err,
            focusedLabelColor = Accent,
            unfocusedLabelColor = InkSoft,
            disabledLabelColor = InkSoft.copy(alpha = 0.5f),
            errorLabelColor = Err,
            focusedPlaceholderColor = InkSoft,
            unfocusedPlaceholderColor = InkSoft.copy(alpha = 0.5f),
            disabledPlaceholderColor = InkSoft.copy(alpha = 0.3f),
            errorPlaceholderColor = Err
        )
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun GothicLabel(text: String, color: Color = InkSoft, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.0.sp,
        modifier = modifier
    )
}

@Composable
fun PowerButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Accent else Surface)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else InkSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (active) "⏻" else "✕", color = if (active) Accent else Ink, fontSize = 26.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 24.sp, color = if (active) Color.White else Ink, fontWeight = FontWeight.SemiBold)
    }
}