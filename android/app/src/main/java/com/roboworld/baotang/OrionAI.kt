package com.roboworld.baotang

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.agent.LLMCallback
import com.ainirobot.agent.assit.LLMResponse
import com.ainirobot.agent.base.llm.LLMConfig
import com.ainirobot.agent.base.llm.LLMMessage
import com.ainirobot.agent.base.llm.Role
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * GIAO TIẾP AI — mô hình OrionStar có sẵn trên robot (AgentCore.llm, SDK 0.4.7). Phương Uyên chốt 18/11/2026:
 * bỏ Claude API, dùng AI của OrionStar → KHÔNG cần key, KHÔNG cần nạp gì qua ADB.
 *
 * Luật (11/11, mở rộng 18/11/2026): trả lời về lịch sử, văn hoá, du lịch… của Việt Nam; KHÔNG trả lời câu tôn giáo,
 * nhạy cảm, tư tưởng sai lệch, chủ đề ngoài Việt Nam → đọc đúng câu cau_ngoai_pham_vi chị đặt.
 *
 * Cách làm (mỗi câu hỏi):
 *   1. Chặn cứng bằng regex (ai-config.json > chan_cung_regex) — không tốn lượt gọi mô hình.
 *   2. App tự TÌM trong tư liệu Bảo tàng (5 standee + 25 câu hỏi đáp) các đoạn liên quan nhất (so cụm 2 tiếng, bỏ dấu),
 *      gửi kèm tối đa so_doan_tu_lieu đoạn → prompt ngắn, mô hình trả lời nhanh.
 *   3. Mô hình phải mở đầu bằng 1 nhãn; app kiểm bằng MÃ:
 *      [TU_LIEU_BAO_TANG] mọi con số phải có trong tư liệu Bảo tàng · [CHAO_HOI] không chữ số, ≤260 ký tự ·
 *      [TRONG_PHAM_VI] hiểu biết chung của mô hình · [NGOAI_PHAM_VI]/[KHONG_CO_NGUON]/thiếu nhãn → câu từ chối.
 * ⚠ llm() KHÔNG tìm web (fileSearch máy chủ bỏ từ 26/08/2025) → câu ngoài tư liệu Bảo tàng là hiểu biết của mô hình,
 *   không còn trích nguồn báo chí như bản Claude.
 * Sửa luật / câu mẫu: CHỈ sửa web/js/ai-config.json.
 */
object OrionAI {
    private const val TAG = "BaoTang-AI"
    private const val BAO_TANG = "Bảo tàng Lịch sử Quân sự Việt Nam"
    private const val CAU_CHAO = "Nova xin kính chào Quý khách! Mời Quý khách đặt câu hỏi về lịch sử, văn hoá, du lịch Việt Nam."
    private val NHAN = Regex(
        "\\[?\\s*(TU[_ ]LIEU[_ ]BAO[_ ]TANG|TRONG[_ ]PHAM[_ ]VI|CHAO[_ ]HOI|NGOAI[_ ]PHAM[_ ]VI|KHONG[_ ]CO[_ ]NGUON)\\s*]?\\s*:?",
        RegexOption.IGNORE_CASE
    )
    private val main = Handler(Looper.getMainLooper())

    private class Doan(val tieuDe: String, val noiDung: String, val cum: Set<String>, val tu: Set<String>)

    private var cfg = JSONObject()
    private var system = ""
    private var doan: List<Doan> = emptyList()
    private var soBaoTang: Set<String> = emptySet()
    private var chan: List<Regex> = emptyList()
    private var viDu: List<Pair<String, String>> = emptyList()
    @Volatile var lastError = ""; private set
    @Volatile var lastMs = 0L; private set

    /** Tiếng hay gặp trong câu hỏi, không giúp tìm tư liệu */
    private val BO_QUA = setOf(
        "la", "gi", "nao", "cua", "co", "the", "nhu", "va", "o", "dau", "ai", "bao", "nhieu", "khong", "nhung", "duoc",
        "trong", "cho", "toi", "ban", "nova", "hay", "mot", "cac", "nay", "do", "voi", "den", "tu", "ra", "vao", "len",
        "ve", "thi", "ma", "se", "da", "dang", "con", "cung", "rat", "vay", "sao", "nhe", "a", "oi", "xin", "hoi", "biet"
    )

    fun load(ctx: Context) {
        try {
            cfg = JSONObject(asset(ctx, "web/js/ai-config.json"))
            chan = cfg.optJSONArray("chan_cung_regex")?.let { a -> (0 until a.length()).map { Regex(a.getString(it), RegexOption.IGNORE_CASE) } } ?: emptyList()
            viDu = cfg.optJSONArray("vi_du")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("hoi") to o.getString("dap") } } } ?: emptyList()
            system = cfg.getString("system_prompt")
            val tl = jsData(asset(ctx, "web/js/tulieu.js"))
            val qz = jsData(asset(ctx, "web/js/quiz.js"))
            val ds = mutableListOf<Doan>()
            val kho = StringBuilder()
            for (i in 0 until tl.length()) {
                val t = tl.getJSONObject(i)
                val paras = t.getJSONArray("paras").let { a -> (0 until a.length()).joinToString("\n") { a.getString(it) } }
                ds.add(doanMoi("Tư liệu ${t.getInt("no")}: ${t.getString("title")}", paras))
                kho.append(paras).append(' ')
            }
            for (i in 0 until qz.length()) {
                val q = qz.getJSONObject(i)
                val opts = q.getJSONArray("opts")
                val nd = "Hỏi: ${q.getString("q")} — Đáp: ${opts.getString(q.getInt("ok"))}. Thông tin: ${q.getString("why")}"
                ds.add(doanMoi("Hỏi đáp của Bảo tàng", nd))
                kho.append(q.getString("q")).append(' ')
                for (k in 0 until opts.length()) kho.append(opts.getString(k)).append(' ')
                kho.append(q.getString("why")).append(' ')
            }
            doan = ds
            soBaoTang = soTrong(kho.toString())
            Log.d(TAG, "Nạp cấu hình AI OrionStar: ${doan.size} đoạn tư liệu, ${viDu.size} câu mẫu, ${chan.size} regex chặn")
        } catch (e: Exception) {
            lastError = "config: $e"
            Log.e(TAG, "Không đọc được ai-config.json / tulieu.js / quiz.js: $e")
        }
    }

    fun ready(): Boolean = system.isNotEmpty()

    private fun asset(ctx: Context, path: String) = ctx.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    /** "window.X = [...];" → JSONArray */
    private fun jsData(js: String) = JSONArray(js.substring(js.indexOf('=') + 1, js.lastIndexOf(';')))

    /** Bỏ dấu, chữ thường, tách tiếng */
    private fun tieng(s: String): List<String> =
        Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    private fun cumHai(t: List<String>): Set<String> = (0 until maxOf(0, t.size - 1)).map { t[it] + " " + t[it + 1] }.toSet()
    private fun doanMoi(tieuDe: String, nd: String): Doan { val t = tieng("$tieuDe $nd"); return Doan(tieuDe, nd, cumHai(t), t.toSet()) }

    /** Các đoạn tư liệu liên quan nhất tới câu hỏi (điểm = 2 × cụm 2 tiếng trùng + tiếng có nghĩa trùng) */
    private fun timTuLieu(q: String): List<Doan> {
        val t = tieng(q)
        val cum = cumHai(t).filter { c -> c.split(' ').any { it !in BO_QUA } }
        val tu = t.filter { it !in BO_QUA && it.length >= 2 }.toSet()
        val toiDa = cfg.optInt("so_doan_tu_lieu", 3)
        // Ngưỡng đo 18/11 trên 8 câu thử: ≥6 điểm VÀ trùng ≥1 cụm 2 tiếng (4–5 điểm toàn là đoạn lạc đề)
        return doan.map { d -> val c = cum.count { it in d.cum }; d to (if (c == 0) 0 else 2 * c + tu.count { it in d.tu }) }
            .filter { it.second >= 6 }
            .sortedByDescending { it.second }
            .take(toiDa).map { it.first }
    }

    /** Tập con số (bỏ dấu chấm nghìn: 22.586 → 22586; 19/9/1954 → 19, 9, 1954; "05" → "5") */
    private fun soTrong(s: String): Set<String> =
        Regex("\\d+").findAll(s.replace(Regex("(?<=\\d)\\.(?=\\d{3}\\b)"), "")).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()

    private fun cau(name: String): String = cfg.optJSONObject(name)?.optString("vi") ?: ""

    /** Hỏi. history = JSON [{q, a}]. done(JSON {nhan, doc, nguon:[{host}], ms, why} | {err}) gọi đúng 1 lần, trên luồng chính. */
    fun ask(question: String, historyJson: String, done: (JSONObject) -> Unit) {
        val q = question.trim()
        chan.firstOrNull { it.containsMatchIn(q) }?.let {
            Log.d(TAG, "\"$q\" → chặn bằng regex ${it.pattern}")
            done(tuChoi("NGOAI_PHAM_VI", "regex")); return
        }
        if (!ready()) { done(JSONObject().put("err", "config")); return }

        val tl = timTuLieu(q)
        val sys = StringBuilder(system).append("\n\n").append(cfg.optString("tieu_de_tu_lieu"))
        if (tl.isEmpty()) sys.append("\n(Không có đoạn tư liệu nào của Bảo tàng liên quan tới câu hỏi này.)")
        else tl.forEach { sys.append("\n### ").append(it.tieuDe).append('\n').append(it.noiDung) }

        val msgs = mutableListOf(LLMMessage(Role.SYSTEM, sys.toString()))
        viDu.forEach { (h, d) -> msgs.add(LLMMessage(Role.USER, h)); msgs.add(LLMMessage(Role.ASSISTANT, d)) }
        val hist = try { JSONArray(historyJson) } catch (e: Exception) { JSONArray() }
        for (i in maxOf(0, hist.length() - 2) until hist.length()) {
            val h = hist.optJSONObject(i) ?: continue
            msgs.add(LLMMessage(Role.USER, h.optString("q")))
            msgs.add(LLMMessage(Role.ASSISTANT, "[TRONG_PHAM_VI]\n" + h.optString("a")))
        }
        msgs.add(LLMMessage(Role.USER, q))

        val l = cfg.optJSONObject("llm") ?: JSONObject()
        val hanMs = l.optLong("han_ms", 15_000L)
        val t0 = System.currentTimeMillis()
        var xong = false
        fun ketThuc(out: JSONObject) {
            if (xong) return
            xong = true
            lastMs = System.currentTimeMillis() - t0
            lastError = out.optString("err", "")
            out.put("ms", lastMs)
            Log.d(TAG, "\"$q\" → ${out.optString("nhan", out.optString("err"))} sau $lastMs ms (${out.optString("why")}) · ${tl.size} đoạn tư liệu")
            done(out)
        }
        // llm() gọi lúc SDK chưa bind xong thì nuốt lệnh (không callback) → đồng hồ riêng
        val dongHo = Runnable { ketThuc(JSONObject().put("err", "timeout")) }
        main.post {
            main.postDelayed(dongHo, hanMs + 1500)
            try {
                val c = LLMConfig(l.optDouble("temperature", 0.3).toFloat(), l.optInt("max_tokens", 400), (hanMs / 1000).toInt(), false, null)
                AgentCore.llm(msgs, c, hanMs, false, object : LLMCallback {
                    override fun onTaskEnd(status: Int, result: LLMResponse?) {
                        main.post {
                            main.removeCallbacks(dongHo)
                            val text = result?.message?.content ?: ""
                            Log.d(TAG, "llm status=$status out=${text.take(300)} err=${result?.error}")
                            if (status != 1 || text.isBlank()) ketThuc(JSONObject().put("err", "llm_$status").put("why", result?.error ?: ""))
                            else ketThuc(docKetQua(text))
                        }
                    }
                })
            } catch (e: Exception) {
                main.removeCallbacks(dongHo)
                Log.w(TAG, "Gọi llm() lỗi: $e")
                ketThuc(JSONObject().put("err", "llm_exception").put("why", e.toString()))
            }
        }
    }

    private fun docKetQua(raw: String): JSONObject {
        val m = NHAN.find(raw)
        val nhan = m?.groupValues?.get(1)?.uppercase()?.replace(' ', '_') ?: ""
        var doc = (if (m != null) raw.removeRange(m.range) else raw)
            .replace(NHAN, "").replace(Regex("https?://\\S+"), "").replace(Regex("[*#_`]"), "")
            .replace(Regex("^\\s*Nova\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ").trim()
        if (doc.length > 520) doc = catCau(doc, 520)

        return when (nhan) {
            "NGOAI_PHAM_VI" -> tuChoi(nhan, "mô hình: ngoài phạm vi")
            "KHONG_CO_NGUON" -> tuChoi(nhan, "mô hình: không chắc")
            "CHAO_HOI" -> {
                val ok = doc.isNotEmpty() && !doc.contains(Regex("\\d")) && doc.length <= 260
                JSONObject().put("nhan", nhan).put("doc", if (ok) doc else CAU_CHAO).put("nguon", JSONArray()).put("why", if (ok) "chào hỏi" else "chào hỏi → câu cố định")
            }
            "TU_LIEU_BAO_TANG" -> {
                val la = soTrong(doc) - soBaoTang
                if (doc.isNotEmpty() && la.isEmpty())
                    JSONObject().put("nhan", nhan).put("doc", doc).put("nguon", JSONArray().put(JSONObject().put("host", BAO_TANG))).put("why", "tư liệu Bảo tàng")
                else tuChoi("KHONG_CO_NGUON", "số ngoài tư liệu $la → app chặn")
            }
            "TRONG_PHAM_VI" ->
                if (doc.isNotEmpty()) JSONObject().put("nhan", nhan).put("doc", doc).put("nguon", JSONArray()).put("why", "hiểu biết của mô hình OrionStar")
                else tuChoi("KHONG_CO_NGUON", "nhãn trong phạm vi nhưng không có chữ")
            else -> tuChoi("KHONG_CO_NGUON", "mô hình không ghi nhãn → app chặn: ${raw.take(80)}")
        }
    }

    /** Cắt ở dấu hết câu gần nhất trước giới hạn */
    private fun catCau(s: String, max: Int): String {
        val cut = s.substring(0, max)
        val i = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
        return if (i > max / 2) cut.substring(0, i + 1) else cut.trimEnd() + "…"
    }

    private fun tuChoi(nhan: String, why: String) =
        JSONObject().put("nhan", nhan).put("doc", cau("cau_ngoai_pham_vi")).put("nguon", JSONArray()).put("why", why)
}
