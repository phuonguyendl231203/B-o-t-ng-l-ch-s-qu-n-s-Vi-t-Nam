# NovaBaoTang: robot lễ tân Nova · Bảo tàng Lịch sử Quân sự Việt Nam

Chương trình truyền thông – giáo dục di sản **"Mạch nguồn văn hoá quân sự Việt Nam"** (chào mừng Ngày Di sản Văn hoá Việt Nam 23/11 và Ngày Văn hoá Việt Nam 24/11/2026). Thuê **02 Nova** cho sự kiện.
Dựng 11/11/2026 · package `com.roboworld.baotang` · tên trên màn hình robot: **Nova Bảo tàng LSQS** · APK: `Nova-AgentOS-Dev/NovaBaoTang-v1.1.apk` (18/11/2026, AI OrionStar).

Nguồn dữ liệu (khách gửi 11/11/2026, đã lưu vào workspace):
- 25 câu hỏi: `02_BaoGia_HopDong/BaoTang-LSQS-VN/04.11.2026 Nội dung câu hỏi tích hợp robot. sửa.docx` (đáp án đúng = phương án khách **in đậm**)
- 5 standee tư liệu: `03_Media/Hinh-anh/BaoTang-LSQS/Standee điện DS quân sự.pdf`
- Key visual: `03_Media/Hinh-anh/BaoTang-LSQS/Standee điện - key visual.jpg`

## 1. App có gì

| Màn | Chức năng |
|---|---|
| Trang chủ | Key visual + 4 nút lớn. Không ai chạm 15 s → robot đọc câu mời (4 câu luân phiên, cách nhau 25 s). Chạm là im. |
| **Dẫn đường** | 5 thẻ tư liệu (Nỏ Liên Châu · Địa đạo Củ Chi · Cọc gỗ Bạch Đằng · La bàn Võ Bẩm · Xe đạp thồ) + **Tham quan lần lượt** cả 5. Tới nơi robot đọc lời thuyết minh ngắn; dẫn tới 1 tư liệu xong thì **tự mở trang tư liệu đó**, 25 s không ai chạm thì tự về `Vị trí chờ`. Lúc di chuyển: màn phủ có ảnh standee, **chạm bất kỳ đâu để dừng**. |
| **Giao tiếp AI** | Mic bấm-mới-nghe + **3 câu gợi ý lấy ngẫu nhiên trong 25 câu của file khách** (đổi bộ mỗi lần vào màn; chỉ câu ≤102 ký tự để nằm gọn 1 dòng — hiện là câu 9, 19, 22, 24, 25; Phương Uyên chốt 18/11/2026). Chạm câu gợi ý → robot đọc nguyên văn đáp án + Thông tin bổ sung trong file, không cần AI/mạng. Câu ngoài file khách hỏi bằng giọng nói → **AI OrionStar của robot** trả lời, chỉ về lịch sử, văn hoá, du lịch Việt Nam (mục 3). Câu lấy từ tư liệu hiện nhãn **Nguồn: Bảo tàng LSQS Việt Nam**; có nút **Xem tư liệu** nếu nhắc tới 1 trong 5 hiện vật. Nói "dẫn tôi tới xe đạp thồ" → hỏi xác nhận rồi mới đi. |
| **Hỏi đáp** | 25 câu trắc nghiệm. Robot đọc câu hỏi → khách chạm đáp án → ô đúng tô xanh / sai tô đỏ + đèn LED xanh/đỏ, robot đọc đáp án đúng + **Thông tin bổ sung**. Thanh số 1–25 ở trên để nhảy câu (xanh = đúng, trắng = sai). Hết câu → điểm tổng kết. |
| **Xem tư liệu** | 5 thẻ standee → trang chi tiết: ảnh standee (chạm để phóng to) + toàn văn, nút **Nova thuyết minh** (đọc từng đoạn, đoạn đang đọc sáng lên), **Dẫn tôi tới đây**. |

Không ai chạm 90 s ở màn khác → về trang chủ, bài Hỏi đáp làm lại từ đầu, khung chat AI xoá (khách sau không thấy của khách trước).

## 2. ⚠️ Việc PHẢI làm tại Bảo tàng

**a) Quét bản đồ + đặt tên điểm** (không phân biệt hoa thường / có dấu):

| Vị trí | Tên điểm |
|---|---|
| Standee Nỏ Liên Châu … Xe đạp thồ | `Điểm 1` … `Điểm 5` (hoặc `Tư liệu 1`…, hoặc đúng tên hiện vật, VD `Xe đạp thồ`) |
| Chỗ robot đứng đón khách | `Vị trí chờ` (không có thì robot đứng lại ở tư liệu cuối) |

Màn Dẫn đường hiện danh sách điểm thật đọc từ robot (vàng = đã nhận làm tư liệu, xanh = vị trí chờ) và báo thiếu điểm nào. Robot phải được **định vị** thì mới đi được.

**b) Wi-Fi / 4G ổn định** cho phần AI (AI OrionStar chạy trên máy chủ của hãng). Mất mạng → khách nghe câu "chưa được học", 3 tính năng còn lại vẫn chạy. **Không cần nạp key** gì nữa.

## 3. Giao tiếp AI — AI OrionStar, giới hạn chủ đề

> **18/11/2026 Phương Uyên chốt: bỏ Claude API, dùng AI của OrionStar** (`OrionAI.kt` gọi `AgentCore.llm()` của AgentOS SDK 0.4.7). Phạm vi giữ nguyên: **mọi câu về lịch sử, văn hoá, du lịch… của Việt Nam**. **Chặn:** tôn giáo/tín ngưỡng, câu nhạy cảm (chính trị thời sự, lãnh đạo đương nhiệm, tranh chấp ngoại giao), tư tưởng sai lệch, chủ đề không phải Việt Nam. Mọi câu không trả lời được (bị chặn / mô hình không chắc / mất mạng / AI lỗi) khách đều nghe **đúng 1 câu**: *"Phần thông tin này tôi chưa được học, để tôi học và sẽ trả lời bạn sau nhé. Xin cảm ơn Quý khách."* — lý do thật hiện ở dòng thông báo cho kỹ thuật.

Mỗi câu hỏi:
1. **Regex chặn cứng** (`ai-config.json > chan_cung_regex`): chế tạo bom/mìn/súng, từ khoá tôn giáo/bói toán, "lãnh đạo … hiện nay", đa đảng/biểu tình/nhân quyền → câu từ chối ngay, không gọi mô hình.
2. **App tự tìm tư liệu Bảo tàng** (5 standee + 25 câu kèm Thông tin bổ sung): so cụm 2 tiếng không dấu, lấy tối đa 3 đoạn liên quan nhất (≥6 điểm, phải trùng ≥1 cụm 2 tiếng) gửi kèm prompt → prompt ngắn, trả lời nhanh.
3. Gửi `llm()`: system prompt + đoạn tư liệu + 4 câu mẫu (`vi_du`, dạy mô hình ghi nhãn) + 2 lượt hỏi gần nhất. Temperature 0,3 · tối đa 400 token · hạn 15 s (`ai-config.json > llm`).
4. **App kiểm nhãn bằng mã:** `[TU_LIEU_BAO_TANG]` mọi con số phải có thật trong tư liệu · `[TRONG_PHAM_VI]` hiểu biết chung của mô hình · `[CHAO_HOI]` có chữ số / dài → câu chào cố định · `[NGOAI_PHAM_VI]`, `[KHONG_CO_NGUON]` hoặc **không ghi nhãn** → câu từ chối. Câu dài quá 520 ký tự bị cắt ở dấu hết câu.

⚠️ **Khác bản Claude:** `llm()` **không tìm được web** (tham số `fileSearch` máy chủ bỏ từ 26/08/2025) → câu ngoài tư liệu Bảo tàng là **hiểu biết của mô hình OrionStar**, không còn trích nguồn báo chí chính thống, không kiểm được đúng sai bằng nguồn. Prompt dặn "chỉ nói điều chắc chắn, không chắc thì từ chối".

⚠️ **CHƯA thử được trên máy tính** — `llm()` chỉ chạy trên robot. Lần đầu cắm robot phải thử bộ câu: bếp Hoàng Cầm, xe đạp thồ (tư liệu) · Hoàng thành Thăng Long, Vịnh Hạ Long, Tết Nguyên Đán (hiểu biết chung) · Xin chào · đạo nào tốt hơn, lãnh đạo hiện nay, Điện Biên Phủ nhờ nước ngoài, thời tiết, món ăn Pháp (phải từ chối). Xem `adb logcat -s BaoTang-AI`: dòng `llm status=… out=…` cho biết mô hình có ghi nhãn đúng không — nếu thường xuyên `không ghi nhãn → app chặn` thì phải chỉnh prompt / câu mẫu.

Robot mới mở app: SDK cần vài giây đến ~1 phút mới gọi được mô hình (`Voice.probe()` thăm dò). Màn AI hiện "Đang kết nối AI OrionStar…" cho tới khi thông.

Sửa luật, câu từ chối, câu mẫu: **chỉ sửa `Roboworld-Design-Kit/website-nova-baotang-app/js/ai-config.json`** rồi build lại.

(Bản Claude 11–18/11/2026 đã gỡ: file Kotlin gọi Claude, script nạp key, thư mục thử trên máy tính. Robot nào đã nạp key Claude thì key nằm thừa trong bộ nhớ app, app không đọc nữa.)

## 4. Sửa nội dung

| Muốn sửa | Sửa ở |
|---|---|
| Câu mời, lời thuyết minh ngắn khi dẫn đường, câu thoại (câu gợi ý AI tự lấy từ 25 câu) | `website-nova-baotang-app/js/data.js` (`[RBW]` = Roboworld soạn, chưa được Bảo tàng duyệt) |
| 25 câu hỏi, 5 tư liệu | **Không sửa tay** `js/quiz.js` / `js/tulieu.js` — sửa file khách hoặc bảng `SUA_LOI` trong `Roboworld-Design-Kit/scripts/make_data_nova_baotang.py` rồi chạy lại script (script tự kiểm mỗi câu có đúng 1 đáp án in đậm) |
| Luật AI, câu mẫu, câu từ chối | `js/ai-config.json` |
| Thời gian chờ | `CFG` đầu `js/app.js`; `pauseMs`/`returnMs` trong `data.js > nav` |

Xem trước trên Chrome: mở `website-nova-baotang-app/index.html` (thêm `#nav`, `#ai`, `#chat`, `#quiz`, `#quiz2`, `#docs`, `#doc3`, `#moving`). Mic trên trình duyệt = hộp gõ chữ; AI trên trình duyệt là mô phỏng.

Lỗi gõ trong file khách đã sửa khi hiển thị (nên báo khách): "Đội Việt Nam **Tuyền** truyền" → "Tuyên truyền" (Câu 5) · dấu cách thừa "tổ chức  của" (Câu 1) · ".." cuối Câu 11 · "bắn. **t**heo truyền thuyết" → "Theo" (standee Nỏ Liên Châu) · "súng được đúc" → "Súng" (Câu 16) · Câu 16 "Súng **thần cơ**" → "Súng **thần công**" (Phương Uyên chốt 11/11/2026, khớp phần Thông tin bổ sung).

## 5. Build & cài

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
%USERPROFILE%\.gradle\gradle-9.7.1\bin\gradle assembleDebug --offline
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell pm grant com.roboworld.baotang android.permission.RECORD_AUDIO
adb shell settings get global microphone        <- phải ra 1 (ra 0 thì: settings put global microphone 1)
```
Gradle tự chép web sang `assets/web` mỗi lần build. **Mở app bằng icon "Nova Bảo tàng LSQS" trên màn hình chính robot** (không dùng `adb am start`). Không `force-stop` app khi robot đang nói (làm sập AgentService).
Soi lỗi: `adb logcat -s BaoTang BaoTang-AI BaoTang-Voice BaoTang-Speech BaoTang-Web` · DevTools (`chrome://inspect`): `BaoTang.ask('câu hỏi')`, `NovaNative.aiInfo()`.

## 6. Kiến trúc (khung từ NovaNamDu, chuẩn Tây Hòa)

- AgentOS chỉ dùng để **nghe**: `isEnableVoiceBar=false`, `isDisablePlan=true`, `isEnableWakeFree` bật/tắt theo nút mic, đăng ký **SAY + KNOWLEDGE_QA**, **không đụng `isMicrophoneMuted`**. `Cai.CHE_DO_PORTAL = false` — bộ hoạch định AgentOS tự nói gì cũng bị cắt ở `onTTSResult` (kho Robot Portal dùng chung tài khoản, không kiểm soát được nội dung). Câu trả lời AI do app soạn qua `AgentCore.llm()` (`OrionAI.kt`).
- Mic `Voice.kt`: mở trễ 250 ms, im 1,8 s mới gửi, trần 15 s, 1 lần bấm = 1 câu. Vào màn AI bật xoay theo người (`DoiDien.kt`, 2,5 m).
- Lời nói: một cửa `Speech.speak()` (SkillApi). Chữ → lời đọc (`toSay()` trong app.js): "19/9/1954" → "ngày 19 tháng 9 năm 1954", "6 - 8 m" → "6 đến 8 mét", "3.700 kg" → "3700 ki lô gam", "thế kỷ XIII" → "thế kỷ 13", "LLVTND" → "Lực lượng vũ trang nhân dân".
- WebView chỉ được mở trang trong assets (chặn trang lạ, bài học 06/10/2026).

## 7. Tình trạng

- ✅ Build 11/11/2026 · ✅ Test tự động toàn bộ luồng trên Chrome (robot giả lập), không lỗi JS.
- ✅ 18/11/2026 build v1.1 (AI OrionStar). ⏳ Phần AI OrionStar **chưa thử trên robot** (mục 3).
- ⏳ **CHƯA cài lên Nova thật** (11/11 không có robot cắm cáp): cần thử mic, TTS đọc đoạn dài, robot đi thật, AI gọi từ mạng tại Bảo tàng.
- ⚠️ **appId đang MƯỢN của NovaPCCC** (`app_e3c7…`) — xin anh Trường appId riêng cho `com.roboworld.baotang`, thay ở `MainApplication.APP_ID` + `assets/actionRegistry.json`.
- ⚠️ Chỉ tiếng Việt (tài liệu khách chỉ có tiếng Việt). Khách hỏi tiếng Anh thì AI vẫn được dặn trả lời tiếng Việt, nhưng app không đổi ngôn ngữ đọc của robot — chưa thử robot đọc câu tiếng Anh thế nào.
- ⚠️ Thuê **02 Nova** → cài app + quét bản đồ cho cả 2 máy.
