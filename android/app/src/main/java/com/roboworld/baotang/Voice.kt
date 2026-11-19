package com.roboworld.baotang

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.agent.LLMCallback
import com.ainirobot.agent.assit.LLMResponse
import com.ainirobot.agent.base.llm.LLMConfig
import com.ainirobot.agent.base.llm.LLMMessage
import com.ainirobot.agent.base.llm.Role
import org.json.JSONObject

/**
 * Micro BẤM-MỚI-NGHE + gọi mô hình (theo chuẩn app Tây Hòa, GHI-CHU-KY-THUAT-APP-TAY-HOA.md mục 2):
 *  - KHÔNG đụng AgentCore.isMicrophoneMuted (bẫy #1: robot điếc vĩnh viễn). Bật/tắt nghe bằng isEnableWakeFree + cờ micOn.
 *  - Mở mic trễ 250 ms (1,4 s nếu robot đang nói). Gom các vế `final`, im 1,8 s mới gửi cả câu. Trần 15 s.
 *  - 1 lần bấm = đúng 1 câu. Xoá vế đã gom ở start(), KHÔNG ở lúc tắt mic.
 */
object Voice {
    private const val TAG = "BaoTang-Voice"
    private val main = Handler(Looper.getMainLooper())

    @Volatile var micOn = false; private set
    private var sent = false
    private val buf = StringBuilder()
    private var partial = ""
    @Volatile var aiReady = false; private set
    @Volatile var lastError = ""; private set
    @Volatile var lastHeardAt = 0L; private set
    private var probes = 0

    /** Gửi sự kiện lên trang web (MainActivity gán) */
    @Volatile var emit: ((String, JSONObject) -> Unit)? = null
    private fun send(type: String, d: JSONObject = JSONObject()) { emit?.invoke(type, d) }

    private val openMic = Runnable {
        micOn = true
        try { AgentCore.isEnableWakeFree = true } catch (e: Exception) { }
        send("mic", JSONObject().put("on", true))
        main.postDelayed(cap, 15_000)
        Log.d(TAG, "Mic MỞ")
    }
    private val endOfSpeech = Runnable { finish(false) }
    private val cap = Runnable { finish(false) }

    fun start() {
        main.removeCallbacks(openMic); main.removeCallbacks(endOfSpeech); main.removeCallbacks(cap)
        buf.setLength(0); partial = ""; sent = false   // quên vế cũ Ở ĐÂY
        Speech.stop()
        moLuot()                                        // đầu lượt mới — mở cửa sổ cho AgentOS (chế độ Portal)
        val delay = if (Speech.speaking) 1400L else 250L
        main.postDelayed(openMic, delay)
    }

    /** Khách bấm tắt mic: gửi phần đã nghe (nếu có) */
    fun stopByUser() { main.post { finish(true) } }

    /** Rời màn / app xuống nền: tắt hẳn, không gửi gì */
    fun abort() {
        main.removeCallbacks(openMic); main.removeCallbacks(endOfSpeech); main.removeCallbacks(cap)
        sent = true
        closeMic()
    }

    private fun closeMic() {
        val was = micOn
        micOn = false
        try { AgentCore.isEnableWakeFree = false } catch (e: Exception) { }
        if (was) send("mic", JSONObject().put("on", false))
    }

    private fun finish(manual: Boolean) {
        main.removeCallbacks(openMic); main.removeCallbacks(endOfSpeech); main.removeCallbacks(cap)
        if (sent) { closeMic(); return }
        val text = (if (buf.isEmpty()) partial else buf.toString()).trim()
        sent = true
        closeMic()
        if (text.isEmpty() && !manual) Log.w(TAG, "Hết giờ mà chưa nghe được chữ nào")
        Log.d(TAG, "Gửi câu: \"$text\" (manual=$manual)")
        send("heard", JSONObject().put("text", text).put("manual", manual))
    }

    /** Gọi từ onASRResult (luồng phụ). Luôn để listener trả false. */
    fun onAsr(text: String?, final: Boolean) {
        val t = text?.trim() ?: return
        if (t.isEmpty()) return
        lastHeardAt = System.currentTimeMillis()
        if (!aiReady) { aiReady = true; send("ai_ready") }
        main.post {
            if (!micOn || sent) return@post
            if (final) {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(t); partial = ""
                main.removeCallbacks(endOfSpeech)
                main.postDelayed(endOfSpeech, 1800)
            } else {
                partial = t
                main.removeCallbacks(endOfSpeech)
                main.postDelayed(endOfSpeech, 3500)   // có chữ tạm mà không có final: chờ lâu hơn rồi gửi
            }
            send("asr", JSONObject().put("text", (buf.toString() + " " + partial).trim()))
        }
    }

    // ---------------- Chế độ PORTAL: lượt hỏi của AgentOS (theo Tây Hòa) ----------------
    @Volatile private var lucMoLuot = 0L
    /** Lượt này app đã tự đáp (dữ liệu trường / người dùng thao tác) → AgentOS nói gì cũng cắt */
    @Volatile var appDaDap = false; private set
    /** AgentOS đã nói ít nhất 1 mẩu trong lượt này → đường lui khỏi chen vào */
    @Volatile private var agentDaNoi = false
    @Volatile private var soLuot = 0

    fun moLuot() { lucMoLuot = System.currentTimeMillis(); appDaDap = false; agentDaNoi = false; soLuot++ }
    fun trongLuot() = System.currentTimeMillis() - lucMoLuot < Cai.CUA_SO_LUOT_MS

    /** App tự trả lời lượt này (hoặc người dùng chạm màn hình) → cắt AgentOS cả lượt */
    fun appTuDap() { appDaDap = true; try { AgentCore.stopTTS() } catch (e: Exception) { } }

    /**
     * Để AgentOS trả lời câu này bằng kho Portal. typed=true (khách chạm câu gợi ý, AgentOS chưa nghe)
     * thì gửi chữ cho AgentOS bằng AgentCore.query. Quá CHO_AGENTOS_MS mà AgentOS chưa nói gì →
     * báo trang web tự trả lời bằng dữ liệu của trường.
     */
    fun deAgentOSTraLoi(id: String, text: String, typed: Boolean) {
        moLuot()
        if (typed) try { AgentCore.query(text) } catch (e: Exception) { Log.w(TAG, "query lỗi: $e") }
        val luot = soLuot
        Log.d(TAG, "Để AgentOS (Portal) trả lời: \"$text\" typed=$typed")
        main.postDelayed({
            if (luot != soLuot || appDaDap || agentDaNoi) return@postDelayed
            Log.w(TAG, "AgentOS IM ${Cai.CHO_AGENTOS_MS} ms — app tự trả lời: \"$text\"")
            appTuDap()
            send("agent_timeout", JSONObject().put("id", id))
        }, Cai.CHO_AGENTOS_MS)
    }

    /** Gọi từ onTTSResult khi chữ KHÔNG phải của app. Trả true = cho AgentOS nói (đã đẩy lên màn hình). */
    fun agentOSNoi(text: String, final: Boolean): Boolean {
        if (!trongLuot()) { Log.w(TAG, "AgentOS tự nói NGOÀI LƯỢT — cắt: $text"); return false }
        if (appDaDap) { Log.w(TAG, "AgentOS nói chen lượt app đã đáp — cắt: $text"); return false }
        agentDaNoi = true
        if (final) Log.d(TAG, "AgentOS nói: $text")
        send("agent_tts", JSONObject().put("text", text).put("final", final))
        return true
    }

    // ---------------- Mô hình ngôn ngữ ----------------
    /** Mô hình chỉ CHỌN MỤC; app kiểm đầu ra. businessInfo PHẢI null (chuỗi rỗng -> status=2 câm). */
    fun askLLM(id: String, system: String, user: String) {
        val msgs = listOf(LLMMessage(Role.SYSTEM, system), LLMMessage(Role.USER, user))
        var done = false
        val timeout = Runnable {
            if (!done) { done = true; lastError = "timeout"; send("llm", JSONObject().put("id", id).put("ok", false).put("err", "timeout")) }
        }
        main.postDelayed(timeout, 9000)
        try {
            AgentCore.llm(msgs, LLMConfig(0.1f, 160, 8, false, null), 8000L, false, object : LLMCallback {
                override fun onTaskEnd(status: Int, result: LLMResponse?) {
                    main.post {
                        if (done) return@post
                        done = true; main.removeCallbacks(timeout)
                        val text = result?.message?.content ?: ""
                        val ok = status == 1 && text.isNotBlank()
                        if (ok) { if (!aiReady) { aiReady = true; send("ai_ready") } } else lastError = "status=$status ${result?.error ?: ""}"
                        Log.d(TAG, "llm status=$status out=${text.take(200)} err=${result?.error}")
                        send("llm", JSONObject().put("id", id).put("ok", ok).put("text", text).put("err", if (ok) "" else lastError))
                    }
                }
            })
        } catch (e: Exception) {
            main.post { if (!done) { done = true; main.removeCallbacks(timeout); lastError = e.toString(); send("llm", JSONObject().put("id", id).put("ok", false).put("err", e.toString())) } }
        }
    }

    /** Thăm dò đường lên mô hình lúc mở app (SDK cần vài giây để bind; mô hình có thể mất ~75 s mới trả lời) */
    fun probe() {
        if (aiReady) return
        probes++
        var done = false
        val timer = Runnable { if (!done) { done = true; main.postDelayed({ probe() }, 20_000) } }
        main.postDelayed(timer, 25_000)
        try {
            AgentCore.llm(listOf(LLMMessage(Role.USER, "Xin chào")), LLMConfig(0.1f, 20, 20, false, null), 20_000L, false, object : LLMCallback {
                override fun onTaskEnd(status: Int, result: LLMResponse?) {
                    main.post {
                        if (done) return@post
                        done = true; main.removeCallbacks(timer)
                        Log.d(TAG, "Thăm dò AI lần $probes: status=$status err=${result?.error}")
                        if (status == 1) { aiReady = true; send("ai_ready") }
                        else { lastError = "probe status=$status ${result?.error ?: ""}"; main.postDelayed({ probe() }, 20_000) }
                    }
                }
            })
        } catch (e: Exception) { lastError = e.toString() }
    }

    fun info(): String = JSONObject()
        .put("ready", aiReady).put("portal", Cai.CHE_DO_PORTAL).put("focus", DoiDien.dangHoatDong()).put("mic", micOn).put("lastError", lastError).put("probes", probes)
        .put("heardAgoS", if (lastHeardAt == 0L) -1 else (System.currentTimeMillis() - lastHeardAt) / 1000)
        .toString()
}
