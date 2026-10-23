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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design-Tokens 1:1 aus der PWA (paper/ink-Editorial-Look)
val Paper = Color(0xFFF2EFE7)
val Ink = Color(0xFF1A1815)
val InkSoft = Color(0xFF5C564C)
val Line = Color(0xFFD8D2C4)
val Accent = Color(0xFFB4552D)
val Ok = Color(0xFF4A7C59)
val Warn = Color(0xFFB08A2E)
val Err = Color(0xFFA33A2A)

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
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
        modifier = modifier
    )
}

@Composable
fun EntityCard(label: String, entityId: String, state: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, Line, RoundedCornerShape(12.dp))
            .background(Paper)
            .padding(14.dp)
    ) {
        UppercaseLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            entityId,
            fontSize = 12.sp,
            color = InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(state.uppercase(), fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium)
    }
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
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, if (active) Accent else Line, RoundedCornerShape(12.dp))
            .background(Paper)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Accent else Ink),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (active) "⏻" else "✕", color = Color.White, fontSize = 26.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Medium)
    }
}
