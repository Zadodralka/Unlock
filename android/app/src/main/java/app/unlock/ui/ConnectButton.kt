package app.unlock.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.unlock.vpn.VpnState

/**
 * Большая круглая кнопка. Цвет — состояние (фиолетовый / жёлтый / мятный),
 * вокруг расходятся кольца: быстро при подключении, спокойно когда подключено.
 */
@Composable
fun ConnectButton(
    status: VpnState.Status,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Диаметр всей области с кольцами; сама кнопка — ~60% от него. */
    size: Dp = 260.dp,
) {
    val buttonSize = size * 0.6f
    val busy = status == VpnState.Status.CONNECTING || status == VpnState.Status.DISCONNECTING
    val target = when {
        busy -> UnlockColors.Warning
        status == VpnState.Status.CONNECTED -> UnlockColors.Accent
        else -> UnlockColors.Primary
    }
    val color by animateColorAsState(target, tween(600), label = "buttonColor")

    val transition = rememberInfiniteTransition(label = "rings")
    val fastPhase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "fast",
    )
    val slowPhase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "slow",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press",
    )
    val haptic = LocalHapticFeedback.current

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2
            val core = buttonSize.toPx() / 2

            // Мягкое свечение под кнопкой
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), center, outer),
                radius = outer,
            )

            val phase = when {
                busy -> fastPhase
                status == VpnState.Status.CONNECTED -> slowPhase
                else -> null
            }
            if (phase != null) {
                for (i in 0 until 3) {
                    val p = (phase + i / 3f) % 1f
                    drawCircle(
                        color.copy(alpha = (1f - p) * 0.45f),
                        radius = core + (outer - core) * p,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            } else {
                drawCircle(color.copy(alpha = 0.10f), radius = core * 1.3f)
                drawCircle(color.copy(alpha = 0.06f), radius = core * 1.6f)
            }
        }

        Box(
            Modifier
                .size(buttonSize)
                .scale(scale)
                .shadow(28.dp, CircleShape, ambientColor = color, spotColor = color)
                .background(
                    Brush.linearGradient(listOf(lerp(color, Color.White, 0.25f), lerp(color, Color.Black, 0.25f))),
                    CircleShape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(interactionSource = interaction, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(buttonSize * 0.35f))
            } else {
                PowerIcon(Modifier.size(buttonSize * 0.37f))
            }
        }
    }
}
