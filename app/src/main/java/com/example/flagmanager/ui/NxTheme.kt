@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.flagmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Whole-window palettes. The accent tints the background, cards and borders,
 * the same way modern bootstrapper shells theme the entire window instead of
 * only the controls.
 */
data class NxSpec(
    val id: String,
    val name: String,
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
    val light: Boolean = false
)

val NX_THEMES = listOf(
    NxSpec(
        "void", "Void Purple", Color(0xFF0C0A12), Color(0xFF06050A),
        Color(0xFF151221), Color(0xFF1D1830), Color(0xFF2C2542),
        Color(0xFFF4F1FF), Color(0xFFA99FC4), Color(0xFF6D6486),
        Color(0xFF8B6CFF), Color(0xFF5E52C2), Color(0xFFFFFFFF),
        Color(0xFF5CE0A0), Color(0xFFFFC46B), Color(0xFFFF6B8A)
    ),
    NxSpec(
        "mint", "Signal Mint", Color(0xFF05090A), Color(0xFF020405),
        Color(0xFF0C1516), Color(0xFF122022), Color(0xFF1C3535),
        Color(0xFFE6FBF6), Color(0xFF93B7B0), Color(0xFF59736F),
        Color(0xFF00D4AA), Color(0xFF7C5CFC), Color(0xFF001410),
        Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFF87171)
    ),
    NxSpec(
        "berry", "Berry", Color(0xFF1B0917), Color(0xFF0E040C),
        Color(0xFF2A1024), Color(0xFF38132F), Color(0xFF5A2050),
        Color(0xFFFFEAF8), Color(0xFFD3A9C8), Color(0xFF8E6384),
        Color(0xFFE56BD4), Color(0xFF9B5BD2), Color(0xFF1A0514),
        Color(0xFF6BE0A8), Color(0xFFFFC06B), Color(0xFFFF6B8A)
    ),
    NxSpec(
        "cyan", "Deep Cyan", Color(0xFF051316), Color(0xFF02090B),
        Color(0xFF0A1D22), Color(0xFF0E2B32), Color(0xFF16454F),
        Color(0xFFE4FAFF), Color(0xFF95BEC8), Color(0xFF5A818C),
        Color(0xFF2FD6D0), Color(0xFF4E8CFF), Color(0xFF00191A),
        Color(0xFF5CE0A0), Color(0xFFFFC46B), Color(0xFFFF7387)
    ),
    NxSpec(
        "ember", "Ember", Color(0xFF140B08), Color(0xFF0A0503),
        Color(0xFF24140E), Color(0xFF331B12), Color(0xFF52301E),
        Color(0xFFFFF1E8), Color(0xFFD6B19B), Color(0xFF96705C),
        Color(0xFFFF8A4C), Color(0xFFFFC857), Color(0xFF1C0900),
        Color(0xFF7DDC8E), Color(0xFFFFC46B), Color(0xFFFF6B5E)
    ),
    NxSpec(
        "graphite", "Graphite", Color(0xFF0B0B0D), Color(0xFF050506),
        Color(0xFF151518), Color(0xFF1D1D22), Color(0xFF303038),
        Color(0xFFF5F5F8), Color(0xFFA6A6B2), Color(0xFF6C6C7A),
        Color(0xFFB993FF), Color(0xFF7C5CFC), Color(0xFF10061F),
        Color(0xFF22C55E), Color(0xFFF1C40F), Color(0xFFE84855)
    ),
    NxSpec(
        "paper", "Paper Light", Color(0xFFF4F2F8), Color(0xFFE8E5EE),
        Color(0xFFFFFFFF), Color(0xFFF0EDF6), Color(0xFFDCD7E8),
        Color(0xFF211A30), Color(0xFF6E6580), Color(0xFF9A92A8),
        Color(0xFF625690), Color(0xFF8B6CFF), Color(0xFFFFFFFF),
        Color(0xFF1E9E63), Color(0xFFB67B08), Color(0xFFD43B52),
        light = true
    )
)

val LocalNx = staticCompositionLocalOf { NX_THEMES[0] }
val LocalNxMotion = staticCompositionLocalOf { true }
val LocalNxDense = staticCompositionLocalOf { false }

object NxType {
    val display @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, letterSpacing = (-0.6).sp, color = LocalNx.current.text
    )
    val title @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, letterSpacing = (-0.2).sp, color = LocalNx.current.text
    )
    val body @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, color = LocalNx.current.text
    )
    val caption @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, color = LocalNx.current.dim
    )
    val label @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, letterSpacing = 1.3.sp, color = LocalNx.current.dim
    )
    val mono @Composable get() = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, color = LocalNx.current.text
    )
    val monoSmall @Composable get() = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, color = LocalNx.current.dim
    )
    val stat @Composable get() = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp, color = LocalNx.current.text
    )
}

@Composable
fun NxTheme(
    spec: NxSpec,
    motion: Boolean = true,
    dense: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (spec.light) {
        lightColorScheme(
            primary = spec.accent, onPrimary = spec.onAccent,
            secondary = spec.accent2, background = spec.bg, onBackground = spec.text,
            surface = spec.surface, onSurface = spec.text,
            surfaceVariant = spec.surfaceAlt, onSurfaceVariant = spec.dim,
            outline = spec.border, error = spec.danger, onError = Color.White
        )
    } else {
        darkColorScheme(
            primary = spec.accent, onPrimary = spec.onAccent,
            secondary = spec.accent2, background = spec.bg, onBackground = spec.text,
            surface = spec.surface, onSurface = spec.text,
            surfaceVariant = spec.surfaceAlt, onSurfaceVariant = spec.dim,
            outline = spec.border, error = spec.danger, onError = Color.White
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalNx provides spec,
        LocalNxMotion provides motion,
        LocalNxDense provides dense
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = NxType.body,
                bodyMedium = NxType.body,
                titleLarge = NxType.title,
                labelLarge = NxType.label
            ),
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}
