package com.local.dsapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 配色照官方 App 截图取的：
 *  - 背景纯白，用户气泡是很淡的蓝紫，助手回答无气泡直接铺满宽度
 *  - 品牌蓝 #4D6BFE，次要文字偏冷的灰
 */
data class DsColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val userBubble: Color,
    val divider: Color,
    val pillBorder: Color,
    val pillOnBg: Color,
    val pillOnBorder: Color,
    val codeBg: Color,
    val thinkingBg: Color,
    val danger: Color
)

val LightColors = DsColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF5F6F8),
    text = Color(0xFF1A1B1E),
    textSecondary = Color(0xFF8A8F99),
    textTertiary = Color(0xFFB0B4BC),
    accent = Color(0xFF4D6BFE),
    userBubble = Color(0xFFE9EEFC),
    divider = Color(0xFFEFF0F2),
    pillBorder = Color(0xFFE3E5E9),
    pillOnBg = Color(0xFFEAF0FF),
    pillOnBorder = Color(0xFFC9D8FF),
    codeBg = Color(0xFFF6F7F9),
    thinkingBg = Color(0xFFF7F8FA),
    danger = Color(0xFFE5484D)
)

val DarkColors = DsColors(
    bg = Color(0xFF191A1C),
    surface = Color(0xFF232426),
    surfaceAlt = Color(0xFF2A2B2E),
    text = Color(0xFFE9EAEC),
    textSecondary = Color(0xFF8A8F99),
    textTertiary = Color(0xFF6B7079),
    accent = Color(0xFF7B90FF),
    userBubble = Color(0xFF2B3550),
    divider = Color(0xFF2E3033),
    pillBorder = Color(0xFF3A3D42),
    pillOnBg = Color(0xFF25304D),
    pillOnBorder = Color(0xFF3B4C7A),
    codeBg = Color(0xFF1F2022),
    thinkingBg = Color(0xFF212224),
    danger = Color(0xFFFF6B6E)
)

val LocalDsColors = staticCompositionLocalOf { LightColors }

object DsType {
    /** 正文：16sp / 行高 27sp，接近官方那种宽松的行距 */
    val body = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, color = Color.Unspecified)
    val bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
    val thinking = TextStyle(fontSize = 14.5.sp, lineHeight = 24.sp)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontSize = 13.sp)
    val pill = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val code = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp)
}

@Composable
fun DsTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalDsColors provides colors, content = content)
}
