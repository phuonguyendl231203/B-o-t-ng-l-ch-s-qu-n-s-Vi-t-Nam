package com.roboworld.baotang

/** Mọi công tắc / con số chỉnh được gom 1 chỗ (theo lối Cai.kt của app Tây Hòa). */
object Cai {

    // ══════════════ KHO KIẾN THỨC ROBOT PORTAL — TẮT ══════════════
    // App Bảo tàng LSQS: câu trả lời do OrionAI.kt soạn bằng AgentCore.llm() (giới hạn chủ đề, app kiểm nhãn bằng mã).
    // AgentOS chỉ dùng để NGHE; nó tự nói gì cũng bị cắt ở onTTSResult. KHÔNG bật Portal: kho Portal dùng chung
    // toàn tài khoản RBW (có tài liệu app khác), không kiểm soát được nội dung.
    const val CHE_DO_PORTAL = false

    /** Chỉ trong cửa sổ này (tính từ lúc gửi câu) AgentOS mới được nói — chặn nó tự đáp tiếng nói chuyện trong phòng. */
    const val CUA_SO_LUOT_MS = 30_000L

    /** ĐƯỜNG LUI: AgentOS im quá mốc này thì app tự trả lời bằng dữ liệu của trường (robot cũ trả lời sau 5–12 s). */
    const val CHO_AGENTOS_MS = 15_000L

    // ══════════════ XOAY THEO NGƯỜI (focus) — bật khi vào màn Giao tiếp AI ══════════════
    const val BAT_XOAY_THEO_NGUOI = true
    /** Chỉ theo người đứng trong bán kính này (mét). */
    const val XOAY_NGUONG_MET = 2.5   // Tây Hòa 2.0; gian trưng bày khách đứng xa hơn (đo 06/10: khách đứng 2,2 m)
    /** Người đang theo biến mất quá lâu thì nhả, tìm người khác. */
    const val XOAY_VANG_MS = 6_000L
    /** lostTimer truyền cho hãng (chưa rõ đơn vị — app tự đếm vắng người nên an toàn). */
    const val XOAY_MAT_DAU_MS = 8_000L
    /** Tốc độ quay về hướng cũ, độ/giây (hãng cho 0–50). */
    const val XOAY_TOC_DO = 25f
    /** Lệch dưới mức này (độ) thì khỏi quay về. */
    const val XOAY_BO_QUA_DO = 8.0
}
