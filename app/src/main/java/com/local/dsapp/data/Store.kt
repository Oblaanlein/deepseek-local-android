package com.local.dsapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储。官方网页版用 IndexedDB，这里用 SharedPreferences 存 JSON：
 * 一条索引（id / 标题 / 更新时间）+ 每条会话一个 key。
 * 结构保持和官方一致，将来要换成 Room 或加分支树都不用改上层。
 */
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("dsapp-sessions", Context.MODE_PRIVATE)

    fun sessions(): List<Session> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val ids = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
        return ids.mapNotNull { load(it) }.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Session? {
        val raw = prefs.getString(key(id), null) ?: return null
        return runCatching { sessionFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun save(session: Session) {
        prefs.edit().putString(key(session.id), session.toJson().toString()).apply()
        val ids = sessions().map { it.id }.toMutableList()
        if (!ids.contains(session.id)) {
            ids.add(0, session.id)
            prefs.edit().putString(KEY_INDEX, JSONArray(ids).toString()).apply()
        }
    }

    fun delete(id: String) {
        val ids = sessions().map { it.id }.filter { it != id }
        prefs.edit()
            .remove(key(id))
            .putString(KEY_INDEX, JSONArray(ids).toString())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(id: String) = "s_$id"

    private companion object {
        const val KEY_INDEX = "index"
    }
}

/** 应用设置（API Key 单独放一个 prefs，方便以后换 EncryptedSharedPreferences） */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("dsapp-settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v).apply()

    /** 模型：deepseek-flash / deepseek-v4-pro */
    var model: String
        get() = prefs.getString("model", MODEL_FLASH) ?: MODEL_FLASH
        set(v) = prefs.edit().putString("model", v).apply()

    /** 朗读音色：stella（海星，女声）/ tide（暗潮，男声） */
    var voice: String
        get() = prefs.getString("voice", "stella") ?: "stella"
        set(v) = prefs.edit().putString("voice", v).apply()

    /** 思考结束后自动收起思考过程 */
    var autoCollapseThinking: Boolean
        get() = prefs.getBoolean("autoCollapse", true)
        set(v) = prefs.edit().putBoolean("autoCollapse", v).apply()

    /** 是否显示每条回答的 token 用量 */
    var showUsage: Boolean
        get() = prefs.getBoolean("showUsage", true)
        set(v) = prefs.edit().putBoolean("showUsage", v).apply()

    /** 0 = 跟随系统，1 = 亮，2 = 暗 */
    var themeMode: Int
        get() = prefs.getInt("themeMode", 0)
        set(v) = prefs.edit().putInt("themeMode", v).apply()
}
