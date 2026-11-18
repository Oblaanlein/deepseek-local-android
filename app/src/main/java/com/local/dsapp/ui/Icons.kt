package com.local.dsapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标全部手绘（24×24 坐标系、圆头描边），不用 Material 图标集：
 * 官方 App 的图标是细线风格，Material 的实心图标放进去会显得很重。
 */
enum class IconKind {
    Menu, Plus, PlusCircle, SpeakerOff, Speaker, Atom, Globe, Mic, Send,
    Copy, ThumbUp, ThumbDown, Share, Refresh, ChevronDown, ChevronRight,
    ChevronLeft, More, Search, Close, Check, Sliders, Pin, Stop, ArrowUp,
    User, Trash, Pencil, SelectText, Turns, Gear, Camera, Image, File, Play
}

@Composable
fun DsIcon(
    kind: IconKind,
    size: Dp = 24.dp,
    tint: Color = LocalDsColors.current.text,
    stroke: Float = 1.7f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val sw = stroke * s
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun pt(x: Float, y: Float) = Offset(x * s, y * s)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, pt(x1, y1), pt(x2, y2), sw, StrokeCap.Round)

        fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
            drawCircle(tint, r * s, pt(cx, cy), style = if (filled) Fill else st)

        fun oval(x: Float, y: Float, w: Float, h: Float) =
            drawOval(tint, pt(x, y), Size(w * s, h * s), style = st)

        fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(tint, pt(x, y), Size(w * s, h * s), CornerRadius(r * s), style = st)

        fun path(block: Path.() -> Unit) =
            drawPath(Path().apply(block), tint, style = st)

        fun arc(x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float) =
            drawArc(tint, start, sweep, false, pt(x, y), Size(w * s, h * s), style = st)

        when (kind) {
            IconKind.Menu -> {
                ln(3.5f, 7f, 20.5f, 7f)
                ln(3.5f, 12f, 20.5f, 12f)
                ln(3.5f, 17f, 20.5f, 17f)
            }

            IconKind.Plus -> {
                ln(12f, 5f, 12f, 19f)
                ln(5f, 12f, 19f, 12f)
            }

            IconKind.PlusCircle -> {
                circle(12f, 12f, 8.6f)
                ln(12f, 8.2f, 12f, 15.8f)
                ln(8.2f, 12f, 15.8f, 12f)
            }

            IconKind.SpeakerOff, IconKind.Speaker -> {
                path {
                    moveTo(4f * s, 9.5f * s)
                    lineTo(7.5f * s, 9.5f * s)
                    lineTo(11.5f * s, 5.5f * s)
                    lineTo(11.5f * s, 18.5f * s)
                    lineTo(7.5f * s, 14.5f * s)
                    lineTo(4f * s, 14.5f * s)
                    close()
                }
                if (kind == IconKind.SpeakerOff) {
                    ln(15f, 9.5f, 20.5f, 15f)
                    ln(20.5f, 9.5f, 15f, 15f)
                } else {
                    arc(11f, 8f, 7f, 8f, -50f, 100f)
                    arc(11f, 5f, 11f, 14f, -50f, 100f)
                }
            }

            // 深度思考：原子形状（中心点 + 两条交叉轨道）
            IconKind.Atom -> {
                circle(12f, 12f, 1.5f, filled = true)
                rotate(45f, pt(12f, 12f)) { oval(4f, 7.4f, 16f, 9.2f) }
                rotate(-45f, pt(12f, 12f)) { oval(4f, 7.4f, 16f, 9.2f) }
            }

            IconKind.Globe -> {
                circle(12f, 12f, 8.6f)
                oval(8.4f, 3.4f, 7.2f, 17.2f)
                ln(3.6f, 12f, 20.4f, 12f)
            }

            IconKind.Mic -> {
                roundRect(9.3f, 3f, 5.4f, 10.4f, 2.7f)
                arc(5.8f, 6.6f, 12.4f, 12.4f, 0f, 180f)
                ln(12f, 19f, 12f, 21.2f)
            }

            IconKind.Send, IconKind.ArrowUp -> {
                if (kind == IconKind.Send) circle(12f, 12f, 9f)
                ln(12f, 16.5f, 12f, 7.5f)
                ln(8f, 11.5f, 12f, 7.5f)
                ln(16f, 11.5f, 12f, 7.5f)
            }

            IconKind.Stop -> roundRect(8.5f, 8.5f, 7f, 7f, 1.6f)

            IconKind.Copy -> {
                roundRect(8.6f, 8.6f, 11.4f, 11.4f, 2.4f)
                path {
                    moveTo(15.4f * s, 5.6f * s)
                    lineTo(5.6f * s, 5.6f * s)
                    lineTo(5.6f * s, 15.4f * s)
                }
            }

            IconKind.ThumbUp -> thumb(tint, s, st)
            IconKind.ThumbDown -> scale(1f, -1f, pt(12f, 12f)) { thumb(tint, s, st) }

            IconKind.Share -> {
                ln(4.5f, 12f, 17.5f, 12f)
                ln(13f, 7.5f, 17.5f, 12f)
                ln(13f, 16.5f, 17.5f, 12f)
            }

            IconKind.Refresh -> {
                arc(3.6f, 3.6f, 16.8f, 16.8f, 60f, 280f)
                ln(17.6f, 4.4f, 17.6f, 8.4f)
                ln(17.6f, 8.4f, 13.6f, 8.4f)
            }

            IconKind.ChevronDown -> {
                ln(7f, 10f, 12f, 15f)
                ln(12f, 15f, 17f, 10f)
            }

            IconKind.ChevronRight -> {
                ln(10f, 6.5f, 15.5f, 12f)
                ln(15.5f, 12f, 10f, 17.5f)
            }

            IconKind.ChevronLeft -> {
                ln(14f, 6.5f, 8.5f, 12f)
                ln(8.5f, 12f, 14f, 17.5f)
            }

            IconKind.More -> {
                circle(5.5f, 12f, 1.4f, filled = true)
                circle(12f, 12f, 1.4f, filled = true)
                circle(18.5f, 12f, 1.4f, filled = true)
            }

            IconKind.Search -> {
                circle(11f, 11f, 6.6f)
                ln(15.8f, 15.8f, 20f, 20f)
            }

            IconKind.Close -> {
                ln(6.5f, 6.5f, 17.5f, 17.5f)
                ln(17.5f, 6.5f, 6.5f, 17.5f)
            }

            IconKind.Check -> {
                ln(5f, 12.5f, 10f, 17.5f)
                ln(10f, 17.5f, 19f, 7f)
            }

            // 置顶区右侧那个调节图标
            IconKind.Sliders -> {
                ln(3.5f, 8f, 20.5f, 8f)
                ln(3.5f, 16f, 20.5f, 16f)
                circle(9f, 8f, 2.2f)
                circle(15.5f, 16f, 2.2f)
            }

            IconKind.Pin -> {
                path {
                    moveTo(9.4f * s, 3.4f * s)
                    lineTo(14.6f * s, 3.4f * s)
                    lineTo(13.6f * s, 9f * s)
                    lineTo(16.4f * s, 11.8f * s)
                    lineTo(7.6f * s, 11.8f * s)
                    lineTo(10.4f * s, 9f * s)
                    close()
                }
                ln(12f, 11.8f, 12f, 20f)
            }

            // 头像占位：头 + 肩
            IconKind.User -> {
                circle(12f, 8.6f, 3.6f)
                arc(5f, 13f, 14f, 14f, 180f, 180f)
            }

            IconKind.Trash -> {
                ln(4.5f, 6.5f, 19.5f, 6.5f)
                path {
                    moveTo(7f * s, 6.5f * s)
                    lineTo(8.2f * s, 20f * s)
                    lineTo(15.8f * s, 20f * s)
                    lineTo(17f * s, 6.5f * s)
                }
                path {
                    moveTo(9.6f * s, 6.5f * s)
                    lineTo(9.6f * s, 4f * s)
                    lineTo(14.4f * s, 4f * s)
                    lineTo(14.4f * s, 6.5f * s)
                }
            }

            IconKind.Pencil -> {
                path {
                    moveTo(4.5f * s, 19.5f * s)
                    lineTo(5.4f * s, 15.4f * s)
                    lineTo(15.6f * s, 5.2f * s)
                    lineTo(18.8f * s, 8.4f * s)
                    lineTo(8.6f * s, 18.6f * s)
                    close()
                }
                ln(14.2f, 6.6f, 17.4f, 9.8f)
            }

            // 「选择文本」：虚线框 + 工字光标，和官方菜单里那个一致
            IconKind.SelectText -> {
                val dash = Stroke(
                    width = sw,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.2f * s, 2.6f * s))
                )
                drawRoundRect(
                    tint,
                    pt(3.5f, 4f),
                    Size(17f * s, 16f * s),
                    CornerRadius(2.2f * s),
                    style = dash
                )
                ln(12f, 8.2f, 12f, 15.8f)
            }
            // 「对话轮次」：三行带小圆点的条目，像目录
            IconKind.Turns -> {
                ln(9f, 7f, 20f, 7f)
                ln(9f, 12f, 20f, 12f)
                ln(9f, 17f, 20f, 17f)
                circle(5.2f, 7f, 1.25f, filled = true)
                circle(5.2f, 12f, 1.25f, filled = true)
                circle(5.2f, 17f, 1.25f, filled = true)
            }

            // 齿轮：八颗齿 + 内外两圈
            IconKind.Gear -> {
                for (i in 0 until 8) {
                    rotate(i * 45f, pt(12f, 12f)) {
                        ln(12f, 2.6f, 12f, 5.2f)
                    }
                }
                circle(12f, 12f, 6.4f)
                circle(12f, 12f, 2.4f)
            }

            // 拍照：相机机身 + 镜头
            IconKind.Camera -> {
                roundRect(3f, 7f, 18f, 12.5f, 2.6f)
                path {
                    moveTo(8.4f * s, 7f * s)
                    lineTo(9.6f * s, 4.6f * s)
                    lineTo(14.4f * s, 4.6f * s)
                    lineTo(15.6f * s, 7f * s)
                }
                circle(12f, 13.2f, 3.6f)
            }

            // 相册：方框 + 山 + 太阳
            IconKind.Image -> {
                roundRect(3f, 4.5f, 18f, 15f, 2.6f)
                circle(8.4f, 9.4f, 1.5f)
                path {
                    moveTo(4.4f * s, 17.2f * s)
                    lineTo(9.6f * s, 12.2f * s)
                    lineTo(13.2f * s, 15.6f * s)
                    lineTo(15.6f * s, 13.4f * s)
                    lineTo(19.6f * s, 17.2f * s)
                }
            }

            // 播放：实心三角（朗读入口，和官方一致）
            IconKind.Play -> {
                val tri = Path().apply {
                    moveTo(5.5f * s, 3.8f * s)
                    lineTo(19f * s, 12f * s)
                    lineTo(5.5f * s, 20.2f * s)
                    close()
                }
                drawPath(tri, tint)
            }

            // 文件：纸张 + 折角
            IconKind.File -> {
                path {
                    moveTo(5.5f * s, 3.4f * s)
                    lineTo(14f * s, 3.4f * s)
                    lineTo(18.5f * s, 7.9f * s)
                    lineTo(18.5f * s, 20.6f * s)
                    lineTo(5.5f * s, 20.6f * s)
                    close()
                }
                path {
                    moveTo(14f * s, 3.4f * s)
                    lineTo(14f * s, 7.9f * s)
                    lineTo(18.5f * s, 7.9f * s)
                }
            }
        }
    }
}

/** 点赞/点踩共用一套形状，点踩靠 scale(1,-1) 翻转 */
private fun DrawScope.thumb(tint: Color, s: Float, st: Stroke) {
    drawRoundRect(
        tint,
        Offset(3.2f * s, 10.2f * s),
        Size(4.2f * s, 10f * s),
        CornerRadius(1.2f * s),
        style = st
    )
    val p = Path().apply {
        moveTo(9.2f * s, 10.4f * s)
        lineTo(11.2f * s, 4.6f * s)
        quadraticBezierTo(11.7f * s, 3.2f * s, 13f * s, 3.4f * s)
        quadraticBezierTo(14.6f * s, 3.7f * s, 14.4f * s, 5.4f * s)
        lineTo(13.6f * s, 9.2f * s)
        lineTo(19.2f * s, 9.2f * s)
        quadraticBezierTo(20.8f * s, 9.2f * s, 20.5f * s, 10.8f * s)
        lineTo(19.2f * s, 18.6f * s)
        quadraticBezierTo(19f * s, 20.2f * s, 17.4f * s, 20.2f * s)
        lineTo(9.2f * s, 20.2f * s)
        close()
    }
    drawPath(p, tint, style = st)
}
