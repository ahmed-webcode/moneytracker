package com.yourname.etbledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF0F766E)
val EmeraldContainer = Color(0xFFCCFBF1)
val OnEmeraldContainer = Color(0xFF042F2E)

val IncomeGreen = Color(0xFF15803D)
val IncomeGreenBg = Color(0xFFDCFCE7)
val OutgoingCoral = Color(0xFFDC2626)
val OutgoingCoralBg = Color(0xFFFEE2E2)
val TransferAmber = Color(0xFFD97706)
val TransferAmberBg = Color(0xFFFEF3C7)

val ChartPalette = listOf(
    Color(0xFF0F766E),
    Color(0xFF2563EB),
    Color(0xFFD97706),
    Color(0xFF9333EA),
    Color(0xFFDC2626),
    Color(0xFF059669),
    Color(0xFF4F46E5),
    Color(0xFF64748B)
)

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

@Composable
fun EtbLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
