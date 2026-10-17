package app.unlock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Фирменная палитра. Интерфейс всегда тёмный — так выглядят современные VPN-клиенты. */
object UnlockColors {
    val BackgroundTop = Color(0xFF171C38)
    val Background = Color(0xFF0A0E1A)
    val Surface = Color(0xFF141A2E)
    val SurfaceHigh = Color(0xFF1C233B)
    val Outline = Color(0x1AFFFFFF)

    /** Бренд и состояние «отключено». */
    val Primary = Color(0xFF7C6CFF)
    /** Состояние «подключено». */
    val Accent = Color(0xFF2BE4A7)
    /** Подключение / отключение. */
    val Warning = Color(0xFFFFB547)
    val Danger = Color(0xFFFF5C7A)

    val TextPrimary = Color(0xFFF2F4FF)
    val TextSecondary = Color(0xFF8C93B0)

    val ScreenBrush = Brush.verticalGradient(listOf(BackgroundTop, Background, Background))
}

private val colorScheme = darkColorScheme(
    primary = UnlockColors.Primary,
    onPrimary = Color.White,
    secondary = UnlockColors.Accent,
    onSecondary = Color(0xFF00281A),
    background = UnlockColors.Background,
    onBackground = UnlockColors.TextPrimary,
    surface = UnlockColors.Surface,
    onSurface = UnlockColors.TextPrimary,
    surfaceVariant = UnlockColors.SurfaceHigh,
    onSurfaceVariant = UnlockColors.TextSecondary,
    surfaceContainer = UnlockColors.Surface,
    surfaceContainerHigh = UnlockColors.SurfaceHigh,
    surfaceContainerLow = UnlockColors.Surface,
    error = UnlockColors.Danger,
    outline = UnlockColors.Outline,
    outlineVariant = UnlockColors.Outline,
)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Мелкая подпись капсом над секциями. */
val SectionLabel = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp,
    color = UnlockColors.TextSecondary,
)

@Composable
fun UnlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
