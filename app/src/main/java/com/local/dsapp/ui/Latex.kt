package com.local.dsapp.ui

/**
 * 极简 LaTeX → Unicode 转换。
 *
 * Compose 里没有 KaTeX，不能真正排版数学公式，但把常见的命令换成 Unicode 符号后
 * （\Delta → Δ、\nabla → ∇、x_0 → x₀、\frac{a}{b} → a/b），读起来就和公式差不多了，
 * 总比满屏 `\mathbf{x}` 强。
 *
 * 只处理聊天里真会出现的子集：希腊字母、常用运算符、上下标、分式、根号、字体命令。
 */
object Latex {

    private val SYMBOLS = mapOf(
        // 希腊字母
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "lambda" to "λ",
        "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ", "sigma" to "σ",
        "tau" to "τ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // 运算符
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓", "cdot" to "·",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "leq" to "≤", "le" to "≤",
        "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠", "approx" to "≈",
        "equiv" to "≡", "sim" to "∼", "propto" to "∝", "infty" to "∞",
        "sum" to "∑", "prod" to "∏", "int" to "∫", "iint" to "∬", "oint" to "∮",
        "partial" to "∂", "nabla" to "∇", "sqrt" to "√", "in" to "∈", "notin" to "∉",
        "subset" to "⊂", "supset" to "⊃", "cup" to "∪", "cap" to "∩", "forall" to "∀",
        "exists" to "∃", "emptyset" to "∅", "to" to "→", "rightarrow" to "→",
        "leftarrow" to "←", "Rightarrow" to "⇒", "Leftarrow" to "⇐", "leftrightarrow" to "↔",
        "top" to "⊤", "bot" to "⊥", "cdots" to "⋯", "implies" to "⇒", "iff" to "⇔", "angle" to "∠", "degree" to "°",
        "quad" to "  ", "qquad" to "    ", "space" to " ", "," to " ", ";" to " ",
        // 函数名保持原样但去掉反斜杠
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "log" to "log", "ln" to "ln",
        "exp" to "exp", "lim" to "lim", "max" to "max", "min" to "min", "det" to "det",
        "arg" to "arg", "sup" to "sup", "inf" to "inf", "mod" to "mod",
    )

    private val SUPER = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        'n' to 'ⁿ', 'i' to 'ⁱ', '+' to '⁺', '-' to '⁻', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'k' to 'ᵏ',
        'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 't' to 'ᵗ', 'x' to 'ˣ', 'y' to 'ʸ',
    )

    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'n' to 'ₙ',
        'o' to 'ₒ', 'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    /** 把一段 LaTeX 转成可读的 Unicode 文本 */
    fun prettify(src: String): String {
        var s = src

        // 0) 先剥掉纯排版命令（必须在符号替换之前，否则反斜杠被吃掉就匹配不到了）
        s = s.replace("\\displaystyle", "").replace("\\textstyle", "")
            .replace("\\limits", "").replace("\\nolimits", "")
            .replace("\\rm", "").replace("\\bf", "")

        // 1) 先去字体/文本命令的壳：\mathbf{x} → x，\text{abc} → abc
        val wrapper = Regex("""\\(mathbf|mathrm|mathit|mathcal|mathbb|mathsf|boldsymbol|text|operatorname|hat|bar|vec|tilde)\s*\{([^{}]*)\}""")
        while (wrapper.containsMatchIn(s)) s = wrapper.replace(s) { it.groupValues[2] }

        // 2) 分式：\frac{a}{b} → (a)/(b)。必须做花括号配对，
        //    正则 \{([^{}]*)\} 遇到 \frac{f^{(k)}(x_0)}{k!} 这种嵌套会直接失效
        s = replaceFrac(s)

        // 3) 根号：\sqrt{x} → √(x)
        s = replaceSqrt(s)

        // 4) 上下标：^{...} / _{...} / ^x / _x
        s = Regex("""\^\{([^{}]*)\}""").replace(s) { superscript(it.groupValues[1]) }
        s = Regex("""_\{([^{}]*)\}""").replace(s) { subscript(it.groupValues[1]) }
        s = Regex("""\^([A-Za-z0-9])""").replace(s) { superscript(it.groupValues[1]) }
        s = Regex("""_([A-Za-z0-9])""").replace(s) { subscript(it.groupValues[1]) }

        // 5) 命令换符号：按长度从长到短替换，避免 \Deltax 被当成一个命令名
        for (key in SYMBOLS.keys.sortedByDescending { it.length }) {
            s = s.replace("\\" + key, SYMBOLS.getValue(key))
        }
        // 剩下没收录的命令，去掉反斜杠保留名字
        s = Regex("""\\([A-Za-z]+)""").replace(s) { m -> m.groupValues[1] }

        // 6) 丢掉纯排版命令（不改变语义）
        s = s.replace("\\displaystyle", "").replace("\\textstyle", "")
            .replace("\\limits", "").replace("\\nolimits", "")
            .replace("\\rm", "").replace("\\bf", "")
        // 上标符号：^⊤ -> ᵀ
        s = s.replace("^⊤", "ᵀ").replace("^T", "ᵀ")

        // 7) 去掉 \left \right 和多余的花括号、转义符
        s = s.replace("\\left", "").replace("\\right", "")
            .replace("\\{", "{").replace("\\}", "}")
            .replace("\\\\", "\n")
            .replace("{", "").replace("}", "")
            .replace("\\", "")

        // 7) 收一下空格
        return s.replace(Regex("""[ \t]{2,}"""), " ").trim()
    }

    private fun superscript(x: String): String {
        val mapped = x.map { SUPER[it] ?: it }
        return if (mapped.all { SUPER.containsValue(it) } && x.isNotEmpty()) {
            mapped.joinToString("")
        } else if (x.length == 1) {
            x
        } else {
            "^($x)"
        }
    }

    private fun subscript(x: String): String {
        val mapped = x.map { SUB[it] ?: it }
        return if (mapped.all { SUB.containsValue(it) } && x.isNotEmpty()) {
            mapped.joinToString("")
        } else if (x.length == 1) {
            x
        } else {
            "_($x)"
        }
    }

    /** 从 start（必须是 '{'）开始读一组配平的花括号，返回内容和结束位置 */
    private fun readGroup(s: String, start: Int): Pair<String, Int>? {
        if (s.getOrNull(start) != '{') return null
        var depth = 0
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start + 1, i) to i + 1
                }
            }
            i++
        }
        return null
    }

    private fun replaceFrac(input: String): String {
        var s = input
        while (true) {
            val idx = s.indexOf("\\frac")
            if (idx < 0) return s
            var i = idx + 5
            while (i < s.length && s[i] == ' ') i++
            val a = readGroup(s, i)
            if (a == null) { s = s.substring(0, idx) + "frac" + s.substring(idx + 5); continue }
            var j = a.second
            while (j < s.length && s[j] == ' ') j++
            val b = readGroup(s, j)
            if (b == null) { s = s.substring(0, idx) + "frac" + s.substring(idx + 5); continue }
            val an = a.first.trim()
            val bn = b.first.trim()
            val aFmt = if (an.length <= 1) an else "($an)"
            val bFmt = if (bn.length <= 1) bn else "($bn)"
            s = s.substring(0, idx) + "$aFmt/$bFmt" + s.substring(b.second)
        }
    }

    private fun replaceSqrt(input: String): String {
        var s = input
        while (true) {
            val idx = s.indexOf("\\sqrt")
            if (idx < 0) return s
            var i = idx + 5
            while (i < s.length && s[i] == ' ') i++
            val a = readGroup(s, i)
            if (a == null) { s = s.substring(0, idx) + "√" + s.substring(idx + 5); continue }
            val v = a.first.trim()
            val fmt = if (v.length <= 1) "√$v" else "√($v)"
            s = s.substring(0, idx) + fmt + s.substring(a.second)
        }
    }
}
