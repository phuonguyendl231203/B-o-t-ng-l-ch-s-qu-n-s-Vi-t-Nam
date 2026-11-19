package com.roboworld.baotang

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ainirobot.coreservice.client.Definition
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.ainirobot.coreservice.client.listener.CommandListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * App Nova · Bảo tàng Lịch sử Quân sự Việt Nam — "Mạch nguồn văn hoá quân sự Việt Nam".
 * Toàn bộ giao diện là trang web assets/web (chép từ Roboworld-Design-Kit/website-nova-baotang-app khi build).
 * Activity chỉ: mở WebView + cầu nối `NovaNative` (nói, nghe, AI OrionStar, dẫn đường, đèn) và đẩy sự kiện về NovaEvents.dispatch.
 */
class MainActivity : AppCompatActivity() {

    companion object { const val TRANG_APP = "file:///android_asset/web/index.html" }

    private lateinit var web: WebView
    private val reqId = AtomicInteger(2000)
    @Volatile private var navReqId = -1
    @Volatile private var screenNow = "home"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        WebView.setWebContentsDebuggingEnabled(true) // soi từ máy tính qua chrome://inspect
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            textZoom = 100
            minimumFontSize = 1
            useWideViewPort = true      // tôn trọng <meta viewport width=1280> (màn Nova density 560)
            loadWithOverviewMode = true
        }
        // CHỈ được hiện trang của app (assets). 06/10/2026: một công cụ trên máy tính điều khiển Chrome qua cổng
        // gỡ lỗi đã đẩy nhầm WebView robot sang trang web khác -> chặn mọi địa chỉ ngoài assets, tự quay về.
        web.webViewClient = object : WebViewClient() {
            private fun laTrangApp(u: String?) = u != null && u.startsWith("file:///android_asset/")
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val u = request.url?.toString()
                if (laTrangApp(u)) return false
                Log.w("BaoTang", "Chặn mở trang lạ: $u")
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!laTrangApp(url)) {
                    Log.w("BaoTang", "WebView bị chuyển sang trang lạ ($url) — quay về giao diện app")
                    view.stopLoading()
                    view.post { view.loadUrl(TRANG_APP) }
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d("BaoTang-Web", "${m.message()} @${m.sourceId()?.substringAfterLast('/')}:${m.lineNumber()}")
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "NovaNative")
        web.loadUrl(TRANG_APP)

        Voice.emit = { type, d -> emit(type, d) }
        handleAdminIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { emit("back", JSONObject()) }
        })

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAdminIntent(intent)
    }

    /**
     * Lệnh quản trị qua ADB (không có nút trên màn hình):
     *   Đổi app mặc định: adb shell am start -n com.roboworld.baotang/.MainActivity --es boot_app <package>
     *   (RobotOS chỉ trao quyền nói/nghe/di chuyển cho app mặc định; đổi xong phải reboot)
     */
    private fun handleAdminIntent(intent: android.content.Intent?) {
        val pkg = intent?.getStringExtra("boot_app") ?: return
        intent.removeExtra("boot_app")
        web.postDelayed({
            try {
                val api = com.ainirobot.coreservice.client.robotsetting.RobotSettingApi.getInstance()
                val old = api.getRobotString("boot_app_package_name")
                api.setRobotString("boot_app_package_name", pkg)
                Log.w("BaoTang", "Đổi app mặc định: $old -> ${api.getRobotString("boot_app_package_name")}")
            } catch (e: Exception) { Log.w("BaoTang", "Đổi app mặc định lỗi: $e") }
        }, 3000)
    }

    private fun emit(type: String, data: JSONObject) {
        val js = "window.NovaEvents && NovaEvents.dispatch(${JSONObject.quote(type)}, ${JSONObject.quote(data.toString())})"
        runOnUiThread { web.evaluateJavascript(js, null) }
    }

    // ======================= Cầu nối trang web -> robot =======================
    inner class Bridge {

        @JavascriptInterface
        fun speak(id: String, text: String) {
            Speech.speak(text) { emit("tts_end", JSONObject().put("id", id)) }
        }

        @JavascriptInterface
        fun stopSpeak() = Speech.stop()

        /** Người dùng thao tác: bỏ lượt AgentOS đang trả lời + cắt mọi tiếng nói (SkillApi + AgentOS) */
        @JavascriptInterface
        fun userAction() { Voice.appTuDap(); Speech.stop() }

        /** Hỏi AI OrionStar (AgentCore.llm, giới hạn chủ đề). Kết quả về sự kiện "ai" {id, nhan, doc, nguon[], ms} | {id, err} */
        @JavascriptInterface
        fun askAI(id: String, question: String, historyJson: String) {
            Voice.appTuDap()   // AgentOS đã nghe câu này — không cho bộ hoạch định tự trả lời
            OrionAI.ask(question, historyJson) { r -> emit("ai", r.put("id", id)) }
        }

        @JavascriptInterface
        fun micStart() { runOnUiThread { Voice.start() } }

        @JavascriptInterface
        fun micStop() = Voice.stopByUser()


        @JavascriptInterface
        fun aiInfo(): String = JSONObject(Voice.info())
            .put("micSystem", try { android.provider.Settings.Global.getInt(contentResolver, "microphone", -1) } catch (e: Exception) { -1 })
            .put("lang", Speech.appLang ?: "").put("osLang", Speech.currentLanguage() ?: "")
            .put("appId", MainApplication.APP_ID)
            .put("config", OrionAI.ready()).put("aiMs", OrionAI.lastMs).put("aiErr", OrionAI.lastError)
            .toString()

        /** Đổi ngôn ngữ nghe + nói của robot: "vi" | "en" */
        @JavascriptInterface
        fun setLang(id: String, lang: String) {
            Thread {
                Voice.abort()
                Speech.stop()
                val (ok, msg) = Speech.setLanguage(lang)
                emit("lang", JSONObject().put("id", id).put("ok", ok).put("msg", msg))
            }.start()
        }

        @Suppress("DEPRECATION")
        @JavascriptInterface
        fun light(color: String) {
            try {
                val hex = when (color) { "green" -> "00E676"; "red" -> "FF3B30"; else -> "2F80ED" }
                val p = JSONObject()
                    .put(Definition.JSON_LAMB_TYPE, 0).put(Definition.JSON_LAMB_TARGET, 0)
                    .put(Definition.JSON_LAMB_RGB_START, hex).put(Definition.JSON_LAMB_RGB_END, hex)
                    .put(Definition.JSON_LAMB_START_TIME, 100).put(Definition.JSON_LAMB_END_TIME, 100)
                    .put(Definition.JSON_LAMB_REPEAT, 3).put(Definition.JSON_LAMB_ON_TIME, 300)
                    .put(Definition.JSON_LAMB_RGB_FREEZE, hex)
                RobotApi.getInstance().setLight(reqId.incrementAndGet(), p.toString(), null)
            } catch (e: Exception) { }
        }

        /** Danh sách tên điểm trên bản đồ hiện tại */
        @JavascriptInterface
        fun getPlaces(id: String) {
            val names = mutableListOf<String>()
            try { RobotApi.getInstance().placeList?.forEach { p -> p?.name?.let { if (it.isNotBlank()) names.add(it) } } } catch (e: Exception) { }
            if (names.isNotEmpty()) { emit("places", JSONObject().put("id", id).put("list", JSONArray(names))); return }
            try {
                val once = AtomicBoolean(false)
                val done = { message: String? ->
                    if (once.compareAndSet(false, true)) emit("places", JSONObject().put("id", id).put("list", JSONArray(parsePlaceNames(message))))
                }
                RobotApi.getInstance().getPlaceList(reqId.incrementAndGet(), object : CommandListener() {
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onResult(result: Int, message: String?) = done(message)
                    override fun onResult(result: Int, message: String?, extraData: String?) = done(message)
                })
            } catch (e: Exception) {
                emit("places", JSONObject().put("id", id).put("list", JSONObject.NULL))
            }
        }

        @JavascriptInterface
        fun navigate(id: String, name: String) {
            try {
                DoiDien.tat(veHuong = false)   // focus follow CHIẾM GẦM MÁY — tắt trước khi dẫn đường
                if (navReqId > 0) RobotApi.getInstance().stopNavigation(navReqId)
                val rid = reqId.incrementAndGet()
                navReqId = rid
                val r = RobotApi.getInstance().startNavigation(rid, name, 0.2, 30_000L, NavListener(id, rid))
                Log.d("BaoTang", "startNavigation('$name') -> $r")
                if (r < 0) {
                    navReqId = -1
                    emit("nav", JSONObject().put("id", id).put("kind", "fail").put("code", r).put("msg", reason(r, "")))
                }
            } catch (e: Exception) {
                emit("nav", JSONObject().put("id", id).put("kind", "fail").put("code", -1).put("msg", "Không kết nối được hệ thống điều hướng"))
            }
        }

        @JavascriptInterface
        fun stopNav() {
            try { if (navReqId > 0) RobotApi.getInstance().stopNavigation(navReqId) } catch (e: Exception) { }
            navReqId = -1
        }

        @JavascriptInterface
        fun setScreen(name: String, info: String) {
            screenNow = name
            if (name == "ai") {
                // Vào Giao tiếp AI: xoá hội thoại cũ (khách trước không dính sang khách sau) + bật FOCUS xoay theo người
                try { com.ainirobot.agent.AgentCore.clearContext() } catch (e: Exception) { }
                Thread { DoiDien.bat() }.start()
            } else {
                Voice.abort()
                Thread { DoiDien.tat(veHuong = true) }.start()
            }
        }

        @JavascriptInterface
        fun exitApp() { runOnUiThread { finishAffinity() } }
    }

    /** Dịch mã lỗi dẫn đường sang tiếng người */
    private fun reason(code: Int, msg: String?): String = when (code) {
        -116 -> "Robot chưa định vị trên bản đồ (đưa robot về cọc sạc hoặc định vị lại)"
        -108, -113 -> "Không tìm thấy điểm này trên bản đồ"
        -1 -> "Robot đang bận hoặc chưa sẵn sàng"
        else -> if (msg.isNullOrBlank()) "mã lỗi $code" else "$msg (mã $code)"
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private inner class NavListener(private val id: String, private val rid: Int) : ActionListener() {
        private val finished = AtomicBoolean(false)
        @Volatile private var lastStatus = 0

        private fun finish(data: JSONObject) {
            if (!finished.compareAndSet(false, true)) return
            if (navReqId == rid) navReqId = -1
            emit("nav", data.put("id", id))
        }
        private fun result(status: Int, response: String?) {
            Log.d("BaoTang", "nav result status=$status resp=$response")
            if (status == Definition.RESULT_OK && (response == null || response == "true")) finish(JSONObject().put("kind", "arrive"))
            else {
                try { RobotApi.getInstance().stopNavigation(rid) } catch (e: Exception) { }
                finish(JSONObject().put("kind", "fail").put("code", status).put("msg", reason(status, response)))
            }
        }
        private fun error(code: Int, msg: String?) {
            Log.d("BaoTang", "nav error code=$code msg=$msg")
            try { RobotApi.getInstance().stopNavigation(rid) } catch (e: Exception) { }
            finish(JSONObject().put("kind", "fail").put("code", code).put("msg", reason(code, msg)))
        }
        private fun status(code: Int) {
            if (finished.get() || code == lastStatus) return
            val msg = when (code) {
                Definition.STATUS_START_NAVIGATION -> "Đang di chuyển"
                Definition.STATUS_NAVI_AVOID -> "Phía trước có người/vật cản, robot đang tránh…"
                Definition.STATUS_NAVI_AVOID_END -> "Đang tiếp tục di chuyển"
                Definition.STATUS_NAVI_GLOBAL_PATH_FAILED -> "Đang tìm đường khác…"
                else -> return
            }
            lastStatus = code
            emit("nav", JSONObject().put("id", id).put("kind", "status").put("code", code).put("msg", msg))
        }
        override fun onResult(status: Int, response: String?) = result(status, response)
        override fun onResult(status: Int, response: String?, extraData: String?) = result(status, response)
        override fun onError(errorCode: Int, errorString: String?) = error(errorCode, errorString)
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) = error(errorCode, errorString)
        override fun onStatusUpdate(status: Int, data: String?) = status(status)
        override fun onStatusUpdate(status: Int, data: String?, extraData: String?) = status(status)
    }

    private fun parsePlaceNames(message: String?): List<String> {
        val out = mutableListOf<String>()
        if (message.isNullOrBlank()) return out
        try {
            val arr = if (message.trim().startsWith("[")) JSONArray(message) else JSONObject(message).let { o ->
                o.keys().asSequence().map { o.opt(it) }.filterIsInstance<JSONArray>().firstOrNull() ?: JSONArray()
            }
            for (i in 0 until arr.length()) when (val v = arr.opt(i)) {
                is JSONObject -> v.optString("name").takeIf { it.isNotBlank() }?.let { out.add(it) }
                is String -> if (v.isNotBlank()) out.add(v)
            }
        } catch (e: Exception) { }
        return out
    }

    override fun onResume() {
        super.onResume()
        if (screenNow == "ai") Thread { DoiDien.bat() }.start()   // quay lại app khi đang ở màn AI
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onPause() {
        Voice.abort()
        DoiDien.tat(veHuong = false)
        super.onPause()
    }

    override fun onDestroy() {
        try { if (navReqId > 0) RobotApi.getInstance().stopNavigation(navReqId) } catch (e: Exception) { }
        Speech.stop()
        // Trả robot về tiếng Việt cho các app khác
        Thread { Speech.setLanguage("vi") }.start()
        web.destroy()
        super.onDestroy()
    }
}
