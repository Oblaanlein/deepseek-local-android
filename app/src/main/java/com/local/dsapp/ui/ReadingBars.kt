package com.local.dsapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * 朗读中的四根跳动竖条，参数照官方 ReadingBars：
 * 16 视口内 4 根、条宽 2、间距 2；播放时高度在 4–14 之间按正弦变化，
 * 相邻条相位差 0.25 周期（波从左往右传），周期 1.6s。
 * 加载态（已请求、还没出声）是四根矮条 + 呼吸。
 */
@Composable
fun ReadingBars(
    playing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bars")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(750, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier.size(16.dp)) {
        val barW = 2.dp.toPx()
        val gap = 2.dp.toPx()
        val total = barW * 4 + gap * 3
        var x = (size.width - total) / 2f

        for (i in 0 until 4) {
            val h = if (playing) {
                val phase = ((t - i * 0.25f) % 1f + 1f) % 1f
                val s = (0.5f - 0.5f * cos(2 * PI * phase)).toFloat()
                (4f + 10f * s).dp.toPx()
            } else {
                4.dp.toPx()
            }
            drawRoundRect(
                color = if (playing) tint else tint.copy(alpha = pulse),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
            x += barW + gap
        }
    }
}
