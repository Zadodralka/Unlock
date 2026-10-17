package app.unlock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TITLE = "Unlockless"

/**
 * Экран загрузки: свечение и вращающаяся орбита, щит из логотипа
 * прорисовывается переливающейся линией, ромб «выпрыгивает», название
 * появляется по буквам, внизу бегут точки. Через ~2 с вызывает [onFinished].
 */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val finished by rememberUpdatedState(onFinished)

    val glow = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    val diamond = remember { Animatable(0f) }
    val letters = remember { List(TITLE.length) { Animatable(0f) } }
    val subtitle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { glow.animateTo(1f, tween(600)) }
            launch {
                delay(120)
                draw.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
            }
            launch {
                delay(900)
                diamond.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f))
            }
            letters.forEachIndexed { i, letter ->
                launch {
                    delay(650L + i * 45L)
                    letter.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                }
            }
            launch {
                delay(1250)
                subtitle.animateTo(1f, tween(450))
            }
        }
        delay(450)
        finished()
    }

    val infinite = rememberInfiniteTransition(label = "intro")
    val orbit by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "orbit",
    )
    val pulse by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse",
    )

    Box(
        // Начинаем с того же сплошного цвета, что у системной заставки, и плавно
        // проявляем градиент — без скачка цвета при переходе
        Modifier
            .fillMaxSize()
            .background(UnlockColors.Background)
            .drawBehind { drawRect(UnlockColors.ScreenBrush, alpha = glow.value) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(220.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2

                // Мягкое свечение
                drawCircle(
                    Brush.radialGradient(
                        listOf(UnlockColors.Primary.copy(alpha = 0.35f * glow.value), Color.Transparent),
                        center, radius,
                    ),
                    radius = radius,
                )

                // Расходящиеся кольца
                for (i in 0 until 2) {
                    val p = (pulse + i / 2f) % 1f
                    drawCircle(
                        UnlockColors.Primary.copy(alpha = (1f - p) * 0.35f * glow.value),
                        radius = radius * (0.42f + 0.55f * p),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }

                // Вращающаяся орбита с «кометой»
                val orbitRadius = radius * 0.62f
                rotate(orbit, center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, UnlockColors.Accent.copy(alpha = 0.9f * glow.value)),
                            center,
                        ),
                        startAngle = 0f, sweepAngle = 300f, useCenter = false,
                        topLeft = Offset(center.x - orbitRadius, center.y - orbitRadius),
                        size = Size(orbitRadius * 2, orbitRadius * 2),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // Щит и ромб — в координатах иконки (108×108); щит ~32 единицы в высоту,
                // масштаб подобран так, чтобы он помещался внутри орбиты
                val unit = radius / 40f
                translate(center.x - 54f * unit, center.y - 54f * unit) {
                    scale(unit, unit, pivot = Offset.Zero) {
                        val shield = shieldPath()
                        val measure = PathMeasure().apply { setPath(shield, false) }
                        val visible = Path()
                        measure.getSegment(0f, measure.length * draw.value, visible, true)
                        drawPath(
                            visible,
                            brush = Brush.linearGradient(
                                listOf(Color.White, UnlockColors.Primary, UnlockColors.Accent),
                                start = Offset(39f, 38f), end = Offset(69f, 70f),
                            ),
                            style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        val d = diamond.value
                        if (d > 0f) {
                            scale(d, d, pivot = Offset(54f, 58f)) {
                                drawPath(
                                    diamondPath(),
                                    color = Color.White,
                                    style = Stroke(width = 2.4f, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Название по буквам: каждая выезжает снизу и проявляется
            Row {
                TITLE.forEachIndexed { i, ch ->
                    val p = letters[i].value
                    Text(
                        ch.toString(),
                        color = UnlockColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            translationY = (1f - p) * 14.dp.toPx()
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Защищённое соединение",
                color = UnlockColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.graphicsLayer { alpha = subtitle.value },
            )
            Spacer(Modifier.height(28.dp))
            LoadingDots(Modifier.graphicsLayer { alpha = subtitle.value })
        }
    }
}

/** Три точки, которые по очереди подпрыгивают и светлеют. */
@Composable
private fun LoadingDots(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "dots")
    val phase by infinite.animateFloat(
        0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 3) {
            // Треугольный «горб» вокруг своей позиции в цикле
            val distance = minOf(kotlin.math.abs(phase - i), 3f - kotlin.math.abs(phase - i))
            val p = (1f - distance).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = -p * 6.dp.toPx() }
                    .background(lerpColor(UnlockColors.SurfaceHigh, UnlockColors.Accent, p), CircleShape),
            )
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

private fun shieldPath() = Path().apply {
    moveTo(54f, 38.2f)
    lineTo(68.6f, 43.2f)
    lineTo(68.6f, 54f)
    cubicTo(68.6f, 62.8f, 62.1f, 67.5f, 54f, 69.9f)
    cubicTo(45.9f, 67.5f, 39.4f, 62.8f, 39.4f, 54f)
    lineTo(39.4f, 43.2f)
    close()
}

private fun diamondPath() = Path().apply {
    moveTo(54f, 53.3f)
    lineTo(58.7f, 58f)
    lineTo(54f, 62.7f)
    lineTo(49.3f, 58f)
    close()
}
