package dev.finnley.pcswitch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark Cyber Gothic Design-Tokens (improved contrast for readability)
val Bg = Color(0xFF0A0A0F)
val Surface = Color(0xFF14141C)
val SurfaceAlt = Color(0xFF1A1A24)
val Ink = Color(0xFFF0F0F8)
val InkSoft = Color(0xFFB0B0C8)
val Line = Color(0xFF2A2A38)
val Accent = Color(0xFF7C4DFF)
val AccentSoft = Color(0xFF9B7CFF)
val Ok = Color(0xFF34D399)
val Warn = Color(0xFFEAB308)
val Err = Color(0xFFEF4444)

// Glassmorphism effect with subtle border, shadow, and refined corners
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                spotColor = Accent.copy(alpha = 0.08f),
                ambientColor = Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp)),
        content = content
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun UppercaseLabel(text: String, color: Color = InkSoft, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
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