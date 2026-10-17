package app.unlock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Имя сервера без флага-эмодзи в начале и сам флаг, если он есть («🇳🇱 Netherlands»). */
data class ServerTitle(val flag: String?, val name: String)

fun splitFlag(raw: String): ServerTitle {
    val text = raw.trim()
    val points = text.codePoints().toArray()
    val isRegional = { cp: Int -> cp in 0x1F1E6..0x1F1FF }
    if (points.size >= 2 && isRegional(points[0]) && isRegional(points[1])) {
        val flag = String(points, 0, 2)
        val rest = String(points, 2, points.size - 2).trim().trimStart('|', '-', '·', '—').trim()
        return ServerTitle(flag, rest.ifEmpty { text })
    }
    return ServerTitle(null, text)
}

/** Круглый значок страны; без флага — первая буква названия. */
@Composable
fun FlagBadge(title: ServerTitle, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(UnlockColors.SurfaceHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (title.flag != null) {
            Text(title.flag, fontSize = (size.value * 0.5f).sp)
        } else {
            Text(
                title.name.take(1).uppercase(),
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = UnlockColors.Primary,
            )
        }
    }
}

fun pingColor(ping: Int): Color = when {
    ping < 0 -> UnlockColors.Danger
    ping < 300 -> UnlockColors.Accent
    ping < 800 -> UnlockColors.Warning
    else -> UnlockColors.Danger
}

/** Таблетка с задержкой: зелёная / жёлтая / красная. */
@Composable
fun PingBadge(ping: Int, modifier: Modifier = Modifier) {
    val color = pingColor(ping)
    Text(
        text = if (ping < 0) "нет ответа" else "$ping мс",
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Значок питания: дуга и вертикальная черта. */
@Composable
fun PowerIcon(modifier: Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.1f
        val inset = stroke / 2 + size.minDimension * 0.08f
        drawArc(
            color = color, startAngle = -55f, sweepAngle = 290f, useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color, Offset(size.width / 2, size.height * 0.04f), Offset(size.width / 2, size.height * 0.44f),
            strokeWidth = stroke, cap = StrokeCap.Round,
        )
    }
}

/** Значок QR-кода: три угловых квадрата и точки. */
@Composable
fun QrGlyph(modifier: Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val u = size.minDimension / 7f
        val stroke = Stroke(width = u * 0.9f)
        for ((x, y) in listOf(0f to 0f, 4f to 0f, 0f to 4f)) {
            drawRect(color, Offset(x * u + u * 0.45f, y * u + u * 0.45f), Size(u * 2.1f, u * 2.1f), style = stroke)
        }
        drawRect(color, Offset(4.2f * u, 4.2f * u), Size(u, u))
        drawRect(color, Offset(5.8f * u, 5.8f * u), Size(u * 1.2f, u * 1.2f))
        drawRect(color, Offset(5.8f * u, 4.2f * u), Size(u * 1.2f, u))
    }
}

/** Значок «картинка»: рамка, гора и солнце. */
@Composable
fun GalleryGlyph(modifier: Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.14f),
            size = Size(w * 0.84f, h * 0.72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
            style = Stroke(width = stroke),
        )
        drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.34f))
        val mountain = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, h * 0.78f)
            lineTo(w * 0.40f, h * 0.48f)
            lineTo(w * 0.58f, h * 0.68f)
            lineTo(w * 0.68f, h * 0.58f)
            lineTo(w * 0.84f, h * 0.78f)
            close()
        }
        drawPath(mountain, color)
    }
}

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Unlock", text))
}

fun pasteFromClipboard(context: Context): String =
    context.getSystemService(ClipboardManager::class.java)
        ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

/**
 * Появление при запуске: элемент выезжает снизу и проявляется. [index] — порядок
 * в каскаде (каждый следующий чуть позже), [play] — старт после ухода заставки.
 */
@Composable
fun Modifier.entrance(index: Int, play: Boolean, scaleFrom: Float = 1f): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        delay(index * 70L)
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
        val scale = scaleFrom + (1f - scaleFrom) * p
        scaleX = scale
        scaleY = scale
    }
}
