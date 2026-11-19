# Nova · Bảo tàng Lịch sử Quân sự Việt Nam

App cho robot lễ tân **Nova (OrionStar)** phục vụ chương trình truyền thông – giáo dục di sản **"Mạch nguồn văn hoá quân sự Việt Nam"** tại Bảo tàng Lịch sử Quân sự Việt Nam. Roboworld phát triển.

4 tính năng:
- **Dẫn đường**: robot dẫn khách tới 5 tư liệu hoặc tham quan lần lượt cả 5.
- **Giao tiếp AI**: khách bấm micro để hỏi. AI OrionStar của robot (`AgentCore.llm`) trả lời về lịch sử, văn hoá, du lịch Việt Nam. Câu tôn giáo, câu nhạy cảm, câu có tư tưởng sai lệch và câu ngoài chủ đề đều bị từ chối bằng một câu cố định.
- **Hỏi đáp**: 25 câu trắc nghiệm, hiện đúng/sai và thông tin bổ sung.
- **Xem tư liệu**: 5 standee hiện vật, robot thuyết minh.

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `android/` | Dự án Android (Kotlin): WebView + cầu nối `NovaNative` (nói, nghe, AI, dẫn đường, đèn). Chi tiết ở [android/README.md](android/README.md) |
| `web/` | Giao diện (HTML/CSS/JS), khổ 1280×720. Mở `web/index.html` trên Chrome để xem trước (robot giả lập) |
| `web/js/ai-config.json` | Luật AI: system prompt, câu mẫu, regex chặn, câu từ chối |
| `web/js/data.js` | Câu thoại, lời thuyết minh, cấu hình dẫn đường |
| `web/js/quiz.js`, `web/js/tulieu.js` | Tự sinh từ tài liệu Bảo tàng bằng `scripts/make_data_nova_baotang.py`, không sửa tay |

## Build

1. Đặt SDK OrionStar (không có trong repo) vào `android/app/libs/`: `sdk-0.4.7.aar`, `agent-base-0.2.10.aar`, `robot-core-1.0.20250821.jar`.
2. Tạo `android/local.properties` với `sdk.dir=<đường dẫn Android SDK>`.
3. Build bằng JDK của Android Studio và Gradle 9.7.1:
   ```
   cd android
   gradle assembleDebug --offline
   ```
   Gradle tự chép `web/` vào `app/src/main/assets/web` trước mỗi lần build.
4. Cài lên robot: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, rồi mở app từ màn hình chính robot.
