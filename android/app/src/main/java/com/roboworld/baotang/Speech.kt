package com.roboworld.baotang

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.coreservice.client.ApiListener
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.TextListener
import com.ainirobot.coreservice.client.robotsetting.RobotSettingApi
import com.ainirobot.coreservice.client.speech.SkillApi
import com.ainirobot.coreservice.client.speech.entity.TTSEntity
import org.json.JSONArray
import java.text.Normalizer

/**
 * Cửa ra DUY NHẤT của tiếng nói: SkillApi.playText(TTSEntity) (chuẩn Tây Hòa — không dùng bản playText(String)).
 * Ghi nhớ 3 câu gần nhất app đọc để phân biệt với câu AgentOS TỰ NÓI (bị cắt ở onTTSResult).
 * Đổi ngôn ngữ robot (nghe + nói) bằng RobotApi.setLanguage.
 */
object Speech {
    private const val TAG = "BaoTang-Speech"
    private val main = Handler(Looper.getMainLooper())
    private val skill = SkillApi()
    @Volatile var ready = false; private set
    @Volatile var speaking = false; private set
    private var token = 0
    private val recent = ArrayDeque<Set<String>>()

    fun connect(ctx: Context) {
        try {
            skill.connectApi(ctx, object : ApiListener {
                override fun handleApiConnected() { ready = true; Log.d(TAG, "SkillApi đã kết nối") }
                override fun handleApiDisconnected() { ready = false }
                override fun handleApiDisabled() { ready = false }
            })
        } catch (e: Exception) { Log.w(TAG, "connectApi lỗi: $e") }
    }

    /** Đọc text; onDone gọi đúng 1 lần (đọc xong / bị ngắt / lỗi). */
    fun speak(text: String, onDone: () -> Unit) {
        remember(text)
        val my = synchronized(this) { ++token }
        speaking = true
        var done = false
        val finish = {
            main.post {
                if (!done) {
                    done = true
                    if (my == token) speaking = false
                    onDone()
                }
            }
        }
        if (!ready) {
            // Dự phòng: SkillApi chưa kết nối -> nói qua AgentCore
            try {
                AgentCore.tts(text, 120_000L, object : com.ainirobot.agent.TTSCallback {
                    override fun onTaskEnd(status: Int, result: String?) { finish() }
                })
            } catch (e: Exception) { Log.w(TAG, "AgentCore.tts lỗi: $e"); finish() }
            return
        }
        try {
            skill.playText(TTSEntity(text), object : TextListener() {
                override fun onComplete() { finish() }
                override fun onStop() { finish() }
                override fun onError() { finish() }
            })
        } catch (e: Exception) { Log.w(TAG, "playText lỗi: $e"); finish() }
    }

    /** Cắt CẢ HAI đường tiếng: SkillApi và AgentOS */
    fun stop() {
        synchronized(this) { token++ }
        speaking = false
        try { skill.stopTTS() } catch (e: Exception) { }
        try { AgentCore.stopTTS() } catch (e: Exception) { }
    }

    // ---------------- Phân biệt chữ TTS của app với AgentOS tự nói ----------------
    private fun words(s: String): Set<String> {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
        return n.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    }

    @Synchronized private fun remember(text: String) {
        recent.addLast(words(text))
        while (recent.size > 3) recent.removeFirst()
    }

    /** true = mẩu chữ này là của app (hoặc chỉ là dấu câu) -> KHÔNG cắt. So theo tỉ lệ từ trùng >= 60%. */
    @Synchronized fun isOwn(fragment: String): Boolean {
        val w = words(fragment)
        if (w.isEmpty()) return true
        for (r in recent) {
            val hit = w.count { it in r }
            if (hit.toDouble() / w.size >= 0.6) return true
        }
        return false
    }

    // ---------------- Ngôn ngữ ----------------
    /** Trả mã ngôn ngữ robot hỗ trợ khớp với "vi"/"en" (đọc danh sách thật của robot), mặc định vi_VN / en_US. */
    private fun codeFor(lang: String): String {
        val want = if (lang == "en") "en" else "vi"
        try {
            val raw = RobotApi.getInstance().localAndServerSupportLanguageList
            Log.d(TAG, "Ngôn ngữ robot hỗ trợ: $raw")
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                var best: String? = null
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i)?.optString("langCode") ?: continue
                    if (c.lowercase().startsWith(want)) {
                        if (best == null || c == "en_US" || c == "vi_VN") best = c
                    }
                }
                if (best != null) return best
            }
        } catch (e: Exception) { Log.w(TAG, "Đọc danh sách ngôn ngữ lỗi: $e") }
        return if (lang == "en") "en_US" else "vi_VN"
    }

    /** Ngôn ngữ nghe/nói app đã đặt gần nhất (null = chưa đặt) */
    @Volatile var appLang: String? = null; private set
    /** Ngôn ngữ hệ điều hành robot (chỉ để hiển thị chẩn đoán) */
    fun currentLanguage(): String? = try { RobotSettingApi.getInstance().getRobotString("robot_language") } catch (e: Exception) { null }

    /** Đổi ngôn ngữ nghe + nói của robot. Trả (ok, thông báo) */
    fun setLanguage(lang: String): Pair<Boolean, String> {
        // KHÔNG so với khoá robot_language: đó là ngôn ngữ HỆ ĐIỀU HÀNH (luôn vi_VN), còn setLanguage đổi ngôn ngữ
        // nghe/nói của AgentOS cho app. So sánh sẽ làm lệnh chuyển về tiếng Việt bị bỏ qua -> luôn gọi (lệnh nhẹ).
        val code = codeFor(lang)
        val before = appLang
        return try {
            RobotApi.getInstance().setLanguage(code)
            appLang = code
            Log.w(TAG, "Đổi ngôn ngữ robot: $before -> $code")
            true to code
        } catch (e: Exception) {
            Log.w(TAG, "setLanguage lỗi: $e"); false to (e.message ?: "lỗi")
        }
    }
}
