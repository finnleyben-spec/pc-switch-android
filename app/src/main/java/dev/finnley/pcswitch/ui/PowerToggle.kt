package dev.finnley.pcswitch.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PowerToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var isOn by remember { mutableStateOf(false) }

    // Bounce animation
    val slide by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "power_toggle"
    )

    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable(enabled = enabled) {
                isOn = !isOn
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggle()
            }
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isOn) Accent else SurfaceAlt)
        )

        // Slider
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (slide * 240).dp)
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            Text(
                text = if (isOn) "AN" else "AUS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOn) Accent else InkSoft,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}