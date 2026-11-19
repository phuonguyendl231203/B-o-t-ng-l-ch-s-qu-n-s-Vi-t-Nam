package com.roboworld.baotang

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.ainirobot.agent.AgentCore
import com.ainirobot.agent.AppAgent
import com.ainirobot.agent.OnTranscribeListener
import com.ainirobot.agent.action.Action
import com.ainirobot.agent.action.Actions
import com.ainirobot.agent.base.Transcription
import com.ainirobot.coreservice.client.ApiListener
import com.ainirobot.coreservice.client.RobotApi

class MainApplication : Application() {

    companion object {
        /** Trùng appId trong assets/actionRegistry.json.
         *  ⚠ TẠM MƯỢN appId của NovaPCCC — cần xin anh Trường cấp appId riêng cho com.roboworld.baotang. */
        const val APP_ID = "app_e3c75c7a4e96475bac42be646de6bed4"
    }

    override fun onCreate() {
        super.onCreate()

        // --- Phần cứng (RobotAPI): dẫn đường, bản đồ, đèn, ngôn ngữ ---
        try {
            RobotApi.getInstance().connectServer(this, object : ApiListener {
                override fun handleApiConnected() {
                    RobotApi.getInstance().disableBattery()
                    Log.d("BaoTang", "RobotApi đã kết nối")
                }
                override fun handleApiDisconnected() {}
                override fun handleApiDisabled() {}
            })
        } catch (e: Exception) { }

        // --- Tiếng nói (SkillApi): cửa ra duy nhất của lời robot ---
        Speech.connect(this)

        // --- Giao tiếp AI: mô hình OrionStar qua AgentCore.llm (cấu hình assets/web/js/ai-config.json) ---
        OrionAI.load(this)

        // --- AI/nghe (AgentOS SDK 0.4.7 offline) — cấu hình theo chuẩn Tây Hòa ---
        object : AppAgent(this) {
            override fun onCreate() {
                isEnableVoiceBar = false    // không để thanh phụ đề hệ thống đè giao diện
                // Chế độ Portal: BẬT hoạch định để AgentOS trả lời bằng kho kiến thức Robot Portal.
                // Không Portal: tắt hoạch định, app tự điều phối, mô hình chỉ chọn mục qua llm().
                isDisablePlan = !Cai.CHE_DO_PORTAL
                isEnableWakeFree = false    // chỉ nghe khi khách bấm mic
                // ⛔ KHÔNG đụng isMicrophoneMuted
                registerAction(Actions.SAY)            // giữ: cũng là công tắc đường nghe
                registerAction(Actions.KNOWLEDGE_QA)   // kho kiến thức Robot Portal (bỏ cả 2 là mất nghe)
                setPersona(
                    "Em là Nova, robot lễ tân tại Bảo tàng Lịch sử Quân sự Việt Nam, chương trình truyền thông – giáo dục di sản " +
                        "\"Mạch nguồn văn hoá quân sự Việt Nam\"."
                )
                setStyle("trang trọng, lễ phép, câu ngắn; xưng Nova, gọi khách là Quý khách")
                setObjective("KHÔNG tự trả lời. Mọi câu trả lời do ứng dụng soạn (gọi llm(), giới hạn chủ đề lịch sử, văn hoá, du lịch Việt Nam).")
                setOnTranscribeListener(object : OnTranscribeListener {
                    override fun onASRResult(transcription: Transcription): Boolean {
                        Voice.onAsr(transcription.text, transcription.final)
                        return false   // trả true là mất đường nghe vĩnh viễn
                    }
                    override fun onTTSResult(transcription: Transcription): Boolean {
                        val t = transcription.text ?: ""
                        if (t.isBlank() || Speech.isOwn(t)) return false     // chữ của chính app
                        if (Cai.CHE_DO_PORTAL) {
                            // AgentOS trả lời bằng kho Portal: chỉ cho nói TRONG LƯỢT và khi app chưa tự đáp
                            if (!Voice.agentOSNoi(t, transcription.final)) try { AgentCore.stopTTS() } catch (e: Exception) { }
                            return false
                        }
                        // Không Portal: AgentOS tự nói là cắt. Đang đọc câu của app thì không cắt
                        // (TTS đổi "1966" thành chữ -> so từ dễ trượt, tự cắt nhầm tiếng mình)
                        if (!Speech.speaking) {
                            Log.w("BaoTang", "Cắt AgentOS tự nói: $t")
                            try { AgentCore.stopTTS() } catch (e: Exception) { }
                        }
                        return false
                    }
                })
                try { AgentCore.enableWakeupMode(false) } catch (e: Exception) { }
            }

            override fun onExecuteAction(action: Action, params: Bundle?): Boolean = false
        }

        android.os.Handler(mainLooper).postDelayed({ Voice.probe() }, 4000)
    }
}
