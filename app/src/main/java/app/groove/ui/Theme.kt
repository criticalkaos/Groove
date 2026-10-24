package app.groove.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GrooveBlack = Color(0xFF0A0A0C)
val GrooveSurface = Color(0xFF141419)
val GrooveCard = Color(0xFF1C1C23)
val GrooveAccent = Color(0xFFFF8A3D)
val TextDim = Color(0xFFA0A0AC)
val TextFaint = Color(0xFF6B6B78)

private val scheme = darkColorScheme(
    primary = GrooveAccent,
    onPrimary = Color.Black,
    secondary = GrooveAccent,
    background = GrooveBlack,
    onBackground = Color.White,
    surface = GrooveSurface,
    onSurface = Color.White,
    surfaceVariant = GrooveCard,
    onSurfaceVariant = TextDim,
    surfaceContainer = GrooveSurface,
    surfaceContainerHigh = GrooveCard,
    surfaceContainerHighest = Color(0xFF26262F),
    outline = Color(0xFF3A3A45),
)

private val type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
)

@Composable
fun GrooveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
