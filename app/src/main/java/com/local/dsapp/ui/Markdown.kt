package com.local.dsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown 渲染。排版对齐官方：正文 16/26、h1 24/34、h2 22/32、h3 20/30、h4~h6 16/26、
 * 表格 15/25（表头 500）、代码块 13/22。
 *
 * 块间距按 CSS 的 margin 折叠来算：h1~h3 上 32 下 16，其余块上下 16，
 * 相邻两块取 max(上一块的下边距, 下一块的上边距)，所以段与段之间是 16 而不是 32。
 *
 * 公式交给 JLaTeXMath 真排版（见 MathRender.kt）：块级用 MathBlock，
 * 行内用 InlineTextContent 把位图嵌进文字流。支持 $$…$$、\[…\]、\(…\)、$…$。
 */

private sealed class Md {
    data class Para(val text: String) : Md()
    data class Heading(val level: Int, val text: String) : Md()
    data class Code(val lang: String, val code: String) : Md()
    data class Bullet(val items: List<String>, val ordered: Boolean) : Md()
    data class Quote(val text: String) : Md()
    data class Table(val header: List<String>, val rows: List<List<String>>) : Md()
    data class Math(val tex: String) : Md()
    data object Divider : Md()
}

/* ------------------------------------------------------------------ 解析 */

private val RE_HEADING = Regex("^#{1,6}\\s+.*")
private val RE_HR = Regex("^(-{3,}|\\*{3,}|_{3,})$")
private val RE_OL = Regex("^\\d+[.)]\\s+.*")
private val RE_TABLE_SEP = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?$")

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private val parseCache = android.util.LruCache<String, List<Md>>(24)

/** 解析结果缓存：切换分支来回看同一段文字时不用重新解析 */
private fun parseCached(text: String): List<Md> =
    parseCache.get(text) ?: parse(text).also { parseCache.put(text, it) }

private fun parse(src: String): List<Md> {
    val out = mutableListOf<Md>()
    val lines = src.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) out.add(Md.Para(para.toString().trim()))
        para.setLength(0)
    }

    while (i < lines.size) {
        val t = lines[i].trim()
        when {
            t.startsWith("```") -> {
                flushPara()
                val lang = t.removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body.appendLine(lines[i])
                    i++
                }
                out.add(Md.Code(lang, body.toString().trimEnd()))
            }

            // 公式块：$$ … $$ 或 \[ … \]
            t.startsWith("$$") || t.startsWith("\\[") -> {
                flushPara()
                val open = if (t.startsWith("$$")) "$$" else "\\["
                val close = if (open == "$$") "$$" else "\\]"
                val body = StringBuilder()
                val rest = t.removePrefix(open)
                if (rest.trimEnd().endsWith(close)) {
                    out.add(Md.Math(rest.trimEnd().removeSuffix(close).trim()))
                    i++
                    continue
                }
                if (rest.isNotBlank()) body.appendLine(rest)
                i++
                while (i < lines.size && !lines[i].trim().endsWith(close)) {
                    body.appendLine(lines[i])
                    i++
                }
                if (i < lines.size) body.appendLine(lines[i].trim().removeSuffix(close))
                out.add(Md.Math(body.toString().trim()))
            }

            t.isEmpty() -> flushPara()

            t.contains('|') && i + 1 < lines.size && RE_TABLE_SEP.matches(lines[i + 1].trim()) -> {
                flushPara()
                val header = splitRow(t)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows.add(splitRow(lines[i]))
                    i++
                }
                i--
                out.add(Md.Table(header, rows))
            }

            RE_HEADING.matches(t) -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.length
                out.add(Md.Heading(level, t.drop(level).trim()))
            }

            RE_HR.matches(t) -> {
                flushPara()
                out.add(Md.Divider)
            }

            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("- ") || l.startsWith("* ") || l.startsWith("+ ")) {
                        items.add(l.drop(2))
                        i++
                    } else break
                }
                i--
                out.add(Md.Bullet(items, ordered = false))
            }

            RE_OL.matches(t) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (RE_OL.matches(l)) {
                        items.add(l.replaceFirst(Regex("^\\d+[.)]\\s+"), ""))
                        i++
                    } else break
                }
                i--
                out.add(Md.Bullet(items, ordered = true))
            }

            t.startsWith("> ") -> {
                flushPara()
                val q = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    q.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                i--
                out.add(Md.Quote(q.joinToString("\n")))
            }

            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(t)
            }
        }
        i++
    }
    flushPara()
    return out
}

/* ------------------------------------------------------------------ 行内 */

/**
 * 行内格式：**粗**、*斜*、`码`、[文字](链接)、\(…\) 与 $…$ 行内公式。
 * 公式用 JLaTeXMath 渲染成位图后作为 InlineTextContent 嵌入，与文字基线对齐。
 */
private fun inline(text: String, colors: DsColors): AnnotatedString {
    return buildAnnotatedString {
        fun appendMath(expr: String) {
            append(Latex.prettify(expr))
        }

        var i = 0
        while (i < text.length) {
            val rest = text.substring(i)
            when {
                rest.startsWith("**") -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        append(text.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append(rest[0]); i++
                    }
                }

                rest.startsWith("`") -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = colors.codeBg,
                                fontSize = 14.sp
                            )
                        )
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(rest[0]); i++
                    }
                }

                // 行内公式 \( … \)
                rest.startsWith("\\(") -> {
                    val end = text.indexOf("\\)", i + 2)
                    if (end > 0) {
                        appendMath(text.substring(i + 2, end))
                        i = end + 2
                    } else {
                        append(rest[0]); i++
                    }
                }

                // 行内公式 $ … $
                rest.startsWith("$") && i + 1 < text.length && text[i + 1] != '$' -> {
                    val end = text.indexOf('$', i + 1)
                    if (end > i + 1) {
                        appendMath(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        append(rest[0]); i++
                    }
                }

                rest.startsWith("*") || rest.startsWith("_") -> {
                    val mark = rest[0]
                    val end = text.indexOf(mark, i + 1)
                    if (end > i + 1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(rest[0]); i++
                    }
                }

                rest.startsWith("[") -> {
                    val close = text.indexOf(']', i)
                    val open = if (close > 0) text.indexOf('(', close) else -1
                    val end = if (open == close + 1) text.indexOf(')', open) else -1
                    if (close > 0 && end > 0) {
                        pushStyle(
                            SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
                        )
                        append(text.substring(i + 1, close))
                        pop()
                        i = end + 1
                    } else {
                        append(rest[0]); i++
                    }
                }

                else -> {
                    append(rest[0]); i++
                }
            }
        }
    }
}

@Composable
private fun RichTextView(
    rt: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(rt, style = style, modifier = modifier)
}

/* ------------------------------------------------------------------ 渲染 */

private fun topMargin(b: Md): Int = when (b) {
    is Md.Heading -> if (b.level <= 3) 32 else 16
    else -> 16
}

@Composable
fun Markdown(text: String, colors: DsColors = LocalDsColors.current, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseCached(text) }
    val bodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp)

    Column(modifier) {
        blocks.forEachIndexed { index, b ->
            if (index > 0) {
                val gap = maxOf(16, topMargin(b))
                Spacer(Modifier.height(gap.dp))
            }
            when (b) {
                is Md.Para -> RichTextView(
                    inline(b.text, colors),
                    bodyStyle.copy(color = colors.text)
                )

                is Md.Heading -> {
                    val (size, line) = when (b.level) {
                        1 -> 24 to 34
                        2 -> 22 to 32
                        3 -> 20 to 30
                        else -> 16 to 26
                    }
                    RichTextView(
                        inline(b.text, colors),
                        TextStyle(
                            fontSize = size.sp,
                            lineHeight = line.sp,
                            fontWeight = if (b.level <= 3) FontWeight.Bold else FontWeight.SemiBold,
                            color = colors.text
                        )
                    )
                }

                is Md.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.codeBg, RoundedCornerShape(12.dp))
                ) {
                    if (b.lang.isNotBlank()) {
                        Text(
                            b.lang,
                            style = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary
                            ),
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp)
                        )
                    }
                    Text(
                        b.code,
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.text
                        ),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }

                is Md.Bullet -> Column {
                    b.items.forEachIndexed { idx, item ->
                        if (idx > 0) Spacer(Modifier.height(13.dp))
                        Row {
                            // 用 4dp 的小圆点，而不是「•」字符：字符自带宽度，会把点和文字撑开
                            if (b.ordered) {
                                Text(
                                    "${idx + 1}.",
                                    style = bodyStyle.copy(color = colors.textSecondary),
                                    modifier = Modifier.width(18.dp)
                                )
                            } else {
                                Box(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .size(4.dp)
                                        .background(colors.textSecondary, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            RichTextView(
                                inline(item, colors),
                                bodyStyle.copy(color = colors.text),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is Md.Quote -> Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .padding(start = 2.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(colors.divider, RoundedCornerShape(1.5.dp))
                    )
                    RichTextView(
                        inline(b.text, colors),
                        bodyStyle.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                is Md.Table -> TableBlock(b, colors)

                is Md.Math -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceAlt, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    MathBlock(b.tex, colors)
                }

                Md.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                )
            }
        }
    }
}

/**
 * 表格：表头 500 15/25、单元格 15/25、padding 10/16、只有横向分隔线、
 * 首列不缩进、末列不右缩进；列宽按内容测量（min 100dp、max 320dp）。
 *
 * 注意：horizontalScroll 里宽度无界，分隔线必须给**显式宽度**，
 * 否则 fillMaxWidth 会算成 0，表格就变成没有线的纯文字。
 */
@Composable
private fun TableBlock(t: Md.Table, colors: DsColors) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
    val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)

    val widths = remember(t) {
        (0 until cols).map { c ->
            val natural = (listOf(t.header) + t.rows).maxOfOrNull { row ->
                val cell = row.getOrNull(c).orEmpty()
                measurer.measure(
                    text = AnnotatedString(cell),
                    style = cellStyle,
                    softWrap = false
                ).size.width
            } ?: 0
            val dp = with(density) { natural.toDp().value }
            (dp + 32f).coerceIn(100f, 320f).dp
        }
    }
    val totalWidth = widths.fold(0.dp) { acc, d -> acc + d }

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(totalWidth)) {
            Row {
                t.header.forEachIndexed { c, cell ->
                    RichTextView(
                        inline(cell, colors),
                        cellStyle.copy(fontWeight = FontWeight.Medium, color = colors.text),
                        modifier = Modifier
                            .width(widths.getOrElse(c) { 100.dp })
                            .padding(
                                start = if (c == 0) 0.dp else 16.dp,
                                end = if (c == cols - 1) 0.dp else 16.dp,
                                top = 10.dp,
                                bottom = 10.dp
                            )
                    )
                }
            }
            Box(
                Modifier
                    .width(totalWidth)
                    .height(1.dp)
                    .background(colors.divider)
            )
            t.rows.forEach { row ->
                Row {
                    (0 until cols).forEach { c ->
                        RichTextView(
                            inline(row.getOrNull(c).orEmpty(), colors),
                            cellStyle.copy(color = colors.text),
                            modifier = Modifier
                                .width(widths.getOrElse(c) { 100.dp })
                                .padding(
                                    start = if (c == 0) 0.dp else 16.dp,
                                    end = if (c == cols - 1) 0.dp else 16.dp,
                                    top = 10.dp,
                                    bottom = 10.dp
                                )
                        )
                    }
                }
                Box(
                    Modifier
                        .width(totalWidth)
                        .height(1.dp)
                        .background(colors.divider.copy(alpha = 0.6f))
                )
            }
        }
    }
}
