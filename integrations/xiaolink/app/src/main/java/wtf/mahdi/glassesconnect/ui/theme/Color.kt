package wtf.mahdi.glassesconnect.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Claude-inspired warm editorial palette:
 * - Light: warm parchment (#F5F2EB), paper cards (#FCFBF7), deep charcoal ink (#1F1E1B), terracotta accent (#C96442).
 * - Dark: warm espresso charcoal (#1B1A17), elevated slate (#24231F), ivory ink (#F4F1EA), warm coral terracotta (#D97757).
 */
@Immutable
data class XiaoColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val rec: Color,
    val recSoft: Color,
    val ok: Color,
    val warn: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val nav: Color,
    val isDark: Boolean,
)

val DarkXiao = XiaoColors(
    bg = Color(0xFF1B1A17), surface = Color(0xFF24231F), surface2 = Color(0xFF2E2C27), line = Color(0xFF3C3932),
    text = Color(0xFFF4F1EA), muted = Color(0xFFA6A095),
    accent = Color(0xFFD97757), onAccent = Color(0xFF1F120D), accentSoft = Color(0x2BD97757),
    rec = Color(0xFFE85D5A), recSoft = Color(0x29E85D5A), ok = Color(0xFF5CC290), warn = Color(0xFFE6AE52),
    heroTop = Color(0xFF2C2923), heroBottom = Color(0xFF1C1B18), nav = Color(0xFF1F1E1A), isDark = true,
)

val LightXiao = XiaoColors(
    bg = Color(0xFFF5F2EB), surface = Color(0xFFFCFBF7), surface2 = Color(0xFFECE7DD), line = Color(0xFFDFD8CB),
    text = Color(0xFF1F1E1B), muted = Color(0xFF6E6A61),
    accent = Color(0xFFC96442), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0x1FC96442),
    rec = Color(0xFFC73E3A), recSoft = Color(0x1AC73E3A), ok = Color(0xFF2D7D5A), warn = Color(0xFFB87822),
    heroTop = Color(0xFFFCFBF7), heroBottom = Color(0xFFEFEAE0), nav = Color(0xFFFAF8F3), isDark = false,
)

val LocalXiao = staticCompositionLocalOf { DarkXiao }
