package com.local.dsapp.data

import java.util.Calendar
import java.util.TimeZone

/**
 * 花费估算。价目表和峰谷规则照搬网页版（deepseek-chat-demo 的 config.local.json + overlay.js）：
 *  - 单价单位：美元 / 百万 token
 *  - hit  = cache_read_input_tokens（缓存命中，便宜）
 *  - miss = input_tokens + cache_creation_input_tokens
 *  - 低谷时段单价减半：UTC 01:00–04:00 / 06:00–10:00 之外，且周末全天低谷
 *  - 人民币 = 美元 × 7.1（固定汇率，只是量级参考）
 */
object Pricing {

    const val USD_TO_CNY = 7.1
    const val SYMBOL = "¥"

    private data class Unit(val hit: Double, val miss: Double, val out: Double)

    private val MODELS = mapOf(
        MODEL_FLASH to Unit(hit = 0.006, miss = 0.3, out = 1.2),
        MODEL_PRO to Unit(hit = 0.044, miss = 1.32, out = 3.96)
    )

    /** 返回人民币金额；没有用量数据时返回 null */
    fun cny(msg: Msg): Double? = detail(msg)?.cny

    /** 明细（点用量行时弹出来的那些信息，字段和网页版悬停提示一一对应） */
    data class Detail(
        val hit: Int,
        val miss: Int,
        val out: Int,
        val total: Int,
        val usd: Double,
        val cny: Double,
        val peak: Boolean,
        val model: String,
        val hitPrice: Double,
        val missPrice: Double,
        val outPrice: Double
    )

    fun detail(msg: Msg): Detail? {
        val u = msg.usage ?: return null
        val hit = u.cacheHit
        val miss = u.input + u.cacheMiss
        val out = u.output
        if (hit == 0 && miss == 0 && out == 0) return null
        val model = msg.model ?: MODEL_FLASH
        val unit = MODELS[model] ?: MODELS.getValue(MODEL_FLASH)
        val peak = isPeak(msg.createdAt)
        val factor = if (peak) 1.0 else 0.5
        val usd = (hit * unit.hit + miss * unit.miss + out * unit.out) / 1e6 * factor
        return Detail(
            hit = hit,
            miss = miss,
            out = out,
            total = hit + miss + out,
            usd = usd,
            cny = usd * USD_TO_CNY,
            peak = peak,
            model = model,
            hitPrice = unit.hit,
            missPrice = unit.miss,
            outPrice = unit.out
        )
    }

    /** 金额太小的时候多留几位小数，别显示成 ¥0.00 */
    fun format(cny: Double): String = when {
        cny >= 1 -> String.format("%s%.2f", SYMBOL, cny)
        cny >= 0.01 -> String.format("%s%.3f", SYMBOL, cny)
        else -> String.format("%s%.4f", SYMBOL, cny)
    }

    private fun isPeak(ts: Long): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ts }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) return false
        val h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
        return (h >= 1 && h < 4) || (h >= 6 && h < 10)
    }
}
