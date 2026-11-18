package com.local.dsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 极简 Markdown 渲染。官方网页版用的是 mdast 全家桶（约 10 个依赖），
 * 手机上没必要，这里只覆盖聊天里真正常见的语法：
 * 段落、标题、粗体/斜体/行内代码、围栏代码块、无序/有序列表、引用、分隔线、链接。
 */
private sealed class Md {
    data class Para(val text: String) : Md()
    data class Heading(val level: Int, val text: String) : Md()
    data class Code(val lang: String, val code: String) : Md()
    data class Bullet(val items: List<String>) : Md()
    data class Ordered(val items: List<String>) : Md()
    data class Quote(val text: String) : Md()
    data object Divider : Md()
}

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
        val line = lines[i]
        val t = line.trim()
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

            t.isEmpty() -> flushPara()

            t.matches(Regex("^#{1,6}\\s+.*")) -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.length
                out.add(Md.Heading(level, t.drop(level).trim()))
            }

            t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> {
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
                out.add(Md.Bullet(items))
            }

            t.matches(Regex("^\\d+[.)]\\s+.*")) -> {
                flushPara()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.matches(Regex("^\\d+[.)]\\s+.*"))) {
                        items.add(l.replaceFirst(Regex("^\\d+[.)]\\s+"), ""))
                        i++
                    } else break
                }
                i--
                out.add(Md.Ordered(items))
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

/** 行内格式：**粗**、*斜*、`码`、[文字](链接) */
private fun inline(text: String, colors: DsColors): AnnotatedString = buildAnnotatedString {
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

            rest.startsWith("*") || rest.startsWith("_") -> {
                val mark = rest[0]
                val end = text.indexOf(mark, i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
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
                        SpanStyle(
                            color = colors.accent,
                            textDecoration = TextDecoration.Underline
                        )
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

@Composable
fun Markdown(text: String, colors: DsColors = LocalDsColors.current, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parse(text) }
    Column(modifier) {
        blocks.forEach { b ->
            when (b) {
                is Md.Para -> Text(
                    inline(b.text, colors),
                    style = DsType.body.copy(color = colors.text),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                is Md.Heading -> Text(
                    inline(b.text, colors),
                    style = DsType.body.copy(
                        color = colors.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = when (b.level) {
                            1 -> 21.sp
                            2 -> 19.sp
                            3 -> 17.5.sp
                            else -> 16.5.sp
                        }
                    ),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )

                is Md.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(colors.codeBg, RoundedCornerShape(10.dp))
                ) {
                    if (b.lang.isNotBlank()) {
                        Text(
                            b.lang,
                            style = DsType.label.copy(color = colors.textSecondary),
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }
                    Text(
                        b.code,
                        style = DsType.code.copy(
                            color = colors.text,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                is Md.Bullet -> b.items.forEach { item ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text("•", style = DsType.body.copy(color = colors.textSecondary))
                        Text(
                            inline(item, colors),
                            style = DsType.body.copy(color = colors.text),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                is Md.Ordered -> b.items.forEachIndexed { idx, item ->
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Text(
                            "${idx + 1}.",
                            style = DsType.body.copy(color = colors.textSecondary)
                        )
                        Text(
                            inline(item, colors),
                            style = DsType.body.copy(color = colors.text),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                is Md.Quote -> Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(colors.divider, RoundedCornerShape(1.dp))
                    )
                    Text(
                        inline(b.text, colors),
                        style = DsType.body.copy(color = colors.textSecondary),
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }

                Md.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(colors.divider)
                )
            }
        }
    }
}
