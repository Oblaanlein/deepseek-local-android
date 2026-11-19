package com.local.dsapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 公式渲染：用 JLaTeXMath（TeX 解析 + 排版）出位图。
 *
 * 之前用 Unicode 硬替换符号，上下标会挤在一起、分式根本排不了；
 * 换成真库之后 \frac、\sum 的上下限、矩阵都能正常排。
 * 结果按 (公式, 字号, 颜色, padding) 缓存，滚动不会反复重排。
 */
private val mathCache = LruCache<String, Bitmap>(64)

@Composable
fun MathBlock(latex: String, colors: DsColors) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 18.sp.toPx() }
    val colorInt = colors.text.toArgb()
    val padPx = with(density) { 2.dp.toPx() }.toInt()

    val bitmap = remember(latex, textSizePx, colorInt) {
        cachedMath(latex, textSizePx, colorInt, padPx)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = latex,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    } else {
        // 语法坏了就退回原文，至少不丢内容
        Text(
            latex,
            style = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = colors.textSecondary),
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/** 带缓存的渲染入口；解析失败返回 null */
internal fun cachedMath(latex: String, textSizePx: Float, color: Int, pad: Int): Bitmap? {
    val key = latex + "|" + textSizePx.toInt() + "|" + color + "|" + pad
    mathCache.get(key)?.let { return it }
    val bmp = runCatching { renderMath(latex, textSizePx, color, pad) }.getOrNull() ?: return null
    mathCache.put(key, bmp)
    return bmp
}

private fun renderMath(latex: String, textSizePx: Float, color: Int, pad: Int): Bitmap {
    val drawable = JLatexMathDrawable.builder(latex)
        .textSize(textSizePx)
        .color(color)
        .padding(pad)
        .align(JLatexMathDrawable.ALIGN_CENTER)
        .build()

    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}
