# XiaomiGpt Link

[English](README.md) | **Tiếng Việt**

Ứng dụng Android mã nguồn mở dành cho **Xiaomi AI Glasses**, được phát triển như một bản fork của [XiaoLink](https://github.com/mahdi-malv/xiaolink). Ứng dụng cho phép bạn trò chuyện rảnh tay bằng tiếng Việt và tiếng Anh qua micro, camera và loa của kính, đồng thời hỗ trợ nhiều nhà cung cấp AI và lệnh điều khiển điện thoại trực tiếp.

## Tính năng chính

- **Kết nối trực tiếp với kính (từ XiaoLink):** Xác thực WearAuthV2 qua Bluetooth SPP, thu âm micro kính, chụp ảnh/quay video, xem pin và đồng bộ ảnh qua Wi-Fi Direct.
- **4 tùy chọn AI trong cùng 1 ứng dụng:**
  - **ChatGPT:** Đăng nhập trực tiếp bằng tài khoản ChatGPT trên điện thoại, hỗ trợ gửi kèm ảnh từ camera kính.
  - **Gemini Live (API Key):** Nghe trực tiếp âm thanh từ kính và trả lời bằng giọng nói AI tự nhiên, tự gọi camera khi cần.
  - **Gemini Bridge:** Kết nối tới cầu nối Python tự host (`integrations/xiaolink/account_bridge`) để dùng tài khoản Google qua CLI.
  - **DeepSeek (API Key):** Phản hồi văn bản nhanh (`deepseek-chat`, `deepseek-reasoner`), tự động chuyển sang ChatGPT/Gemini nếu câu hỏi cần nhìn ảnh.
- **Nhận dạng và đọc giọng nói:** Dùng Google Speech qua đường dẫn âm thanh Android 13+ (dự phòng bằng Vosk ngoại tuyến), phát trả lời cuốn chiếu từng câu qua loa Bluetooth A2DP của kính, chỉnh được tốc độ đọc và ngắt lời ngay khi giữ gọng kính.
- **Camera thông minh:** Chế độ Tự động chỉ chụp ảnh khi câu hỏi có ý định nhìn, đọc hoặc dịch; tự nâng lên ảnh nét cao `960x720` khi cần đọc chữ nhỏ; giữ lại ngữ cảnh ảnh vừa chụp trong 3 phút cho câu hỏi nối tiếp và hiển thị ảnh ngay trong khung chat.
- **Lệnh rảnh tay:** Hỏi giờ, kiểm tra pin kính, chụp ảnh/quay video/đồng bộ kính, chỉ đường Google Maps, gọi điện theo danh bạ, mở ứng dụng, hẹn giờ, đặt báo thức, điều khiển nhạc và mở đoạn chat mới.
- **Quản lý hội thoại và đa ngôn ngữ:** Tự lưu nhiều đoạn chat, tự mở đoạn mới sau 15 phút nghỉ, hỗ trợ ngữ cảnh riêng (System Context) và đổi nhanh giao diện **Tiếng Việt / English**.

---

## Hướng dẫn cài đặt và sử dụng

### Bước 1: Build và cài ứng dụng

Yêu cầu JDK 17 trở lên và Android SDK.

```sh
cd integrations/xiaolink
./gradlew assembleDebug
```

Trên Windows, có thể chạy trực tiếp từ thư mục gốc:

```powershell
.\scripts\build-integrated.ps1 -Tasks assembleDebug
```

File APK sau khi build nằm tại: `integrations/xiaolink/app/build/outputs/apk/debug/app-debug.apk`. Cài đặt file này lên điện thoại Android (khuyến nghị Android 13 trở lên để dùng Google Speech trực tiếp từ micro kính).

### Bước 2: Kết nối kính Xiaomi AI Glasses

1. Ghép đôi kính trong **Cài đặt Bluetooth** của điện thoại trước.
2. Mở app **XiaomiGpt Link**, vào tab **Kính** (hoặc **Thêm -> Kết nối kính**).
3. Đăng nhập tài khoản Xiaomi đang gắn với kính để lấy khóa kết nối, chọn kính và bấm **Kết nối**.
4. Nếu app Xiaomi Glasses gốc đang giữ kết nối SPP và gây xung đột, hãy **Buộc dừng (Force Stop)** app gốc rồi bấm kết nối lại (không cần gỡ app gốc và không cần reset kính).

### Bước 3: Thiết lập AI và giọng nói

1. Vào tab **Trợ lý**, bấm biểu tượng **Cài đặt** ở góc trên bên phải.
2. Chọn 1 hoặc nhiều AI muốn dùng:
   - **ChatGPT:** Bấm **Đăng nhập ChatGPT**, hoàn tất đăng nhập trên trình duyệt, quay lại app bấm **Tải model** và chọn model.
   - **Gemini Live:** Nhập Gemini API Key (lấy từ Google AI Studio), chọn giọng đọc AI và bấm **Lưu**.
   - **Gemini Bridge:** Chạy `python -m account_bridge` trên máy chủ riêng, sau đó nhập địa chỉ URL và mã kết nối vào app rồi bấm **Lưu và kết nối**.
   - **DeepSeek:** Nhập DeepSeek API Key, chọn `deepseek-chat` hoặc `deepseek-reasoner` và bấm **Lưu**.
3. *(Khuyên bật)* Bật **Tự chuyển AI dự phòng khi gặp lỗi** nếu bạn cấu hình từ 2 AI trở lên.
4. *(Cho chế độ ChatGPT / DeepSeek / Gemini Bridge)* Bấm **Cài đặt TTS** để tải gói giọng đọc **Tiếng Việt ngoại tuyến** trong cài đặt Android (nếu máy chưa có sẵn). Bấm **Tải Vosk dự phòng** nếu muốn dùng nhận dạng khi mất mạng.
5. Chỉnh **Tốc độ đọc TTS** theo sở thích (mặc định `1.15x`) và nhập **Ngữ cảnh riêng** nếu muốn AI luôn trả lời theo phong cách cụ thể.

### Bước 4: Cách dùng hằng ngày qua kính

- **Hỏi AI:** Chạm giữ gọng kính cho đến khi nghe tín hiệu gọi trợ lý, sau đó nói câu hỏi rồi dừng khoảng 1,6 giây để app tự gửi và đọc câu trả lời qua loa kính.
- **Ngắt lời để hỏi câu mới:** Khi kính đang đọc câu trả lời, chỉ cần chạm giữ gọng kính lại để cắt ngang và hỏi ngay câu tiếp theo.
- **Đổi ngôn ngữ giao diện:** Bấm nút **VI / EN** ngay góc trên bên phải màn hình **Kính** hoặc **Trợ lý**.
- **Xem lại lịch sử chat:** Trong tab **Trợ lý**, bấm biểu tượng menu góc trên bên trái để mở danh sách các cuộc trò chuyện đã lưu, hoặc bấm **+** để tạo đoạn chat mới.

### Bước 5: Danh sách lệnh rảnh tay nhanh

Các lệnh dưới đây chạy trực tiếp trên điện thoại và kính mà không cần chờ AI:

- **Kính:** *"Pin còn bao nhiêu phần trăm?"*, *"Trạng thái kính"*, *"Chụp một tấm ảnh"*, *"Quay video đi"*, *"Dừng quay video"*, *"Đồng bộ ảnh"*.
- **Hội thoại:** *"Đoạn chat mới"*, *"Chủ đề mới"*.
- **Điện thoại:** *"Mấy giờ rồi?"*, *"Chỉ đường đến Hồ Gươm"*, *"Mở YouTube"*, *"Gọi cho Mẹ"*, *"Hẹn giờ 5 phút"*, *"Đặt báo thức 7 giờ 30 sáng"*.
- **Nhạc và âm lượng:** *"Dừng nhạc"*, *"Phát nhạc"*, *"Qua bài"*, *"Bài trước"*, *"Tăng âm lượng"*, *"Giảm âm lượng"*.

### Mẹo dùng ổn định khi tắt màn hình và tiết kiệm pin

- **Chạy nền ổn định:** Đưa ứng dụng vào danh sách không tối ưu hóa pin trong cài đặt Android. Nếu muốn lệnh mở bản đồ hoặc mở app chạy được ngay cả khi tắt màn hình, bật **Mở khóa mở rộng (Extend Unlock / Smart Lock)** và thêm kính vào mục **Thiết bị tin cậy**.
- **Tiết kiệm pin kính:** Giữ chế độ Camera ở mức **Tự động** (chỉ chụp khi câu hỏi có nhu cầu nhìn, đọc hoặc dịch) và tắt **Ảnh nét cao (960x720)** nếu không thường xuyên đọc chữ nhỏ.

---

## Cấu trúc thư mục

- `integrations/xiaolink/`: Mã nguồn ứng dụng Android chính (Kotlin + Jetpack Compose) và cầu nối `account_bridge/` (Python).
- `app/`: Bản thử nghiệm XiaomiGpt ban đầu (giữ nguyên).
- `scripts/build-integrated.ps1`: Script hỗ trợ build nhanh trên Windows.

## Giấy phép

- Ứng dụng trong `integrations/xiaolink/` phát hành theo giấy phép **GPL-3.0** (`integrations/xiaolink/LICENSE`), giữ nguyên ghi nhận nguồn của XiaoLink và phần mã MIT của XiaomiGpt (`integrations/xiaolink/THIRD_PARTY_INTEGRATION.md`).
- Mã nguồn bản gốc ở thư mục root theo giấy phép **MIT** (`LICENSE`).

## Tham khảo (References)

Dự án được xây dựng dựa trên mã nguồn và tài liệu nghiên cứu của các tác giả và cộng đồng sau:

- **mahdi-malv**: [XiaoLink](https://github.com/mahdi-malv/xiaolink) (nền tảng fork chính: giao thức WearAuthV2, Bluetooth SPP, giải mã mSBC, sự kiện và camera AIVS, đồng bộ thư viện).
- **idootop / Del Wang**: [mi-gpt](https://github.com/idootop/mi-gpt), [MigicCube](https://github.com/idootop/MigicCube) và [HyperSight](https://del.wang/projects/product/ase).
- **Mentra Community**: [Tài liệu dịch ngược Xiaomi AI Glasses trong MentraOS](https://github.com/Mentra-Community/MentraOS/blob/dev/agents/xiaomi_glasses_reverse_engineering.md).
- **moyslav**: [xiaomi-ai-glasses](https://github.com/moyslav/xiaomi-ai-glasses).
- **jacuboffsky**: [xiaomi-glasses-network-fix](https://github.com/jacuboffsky/xiaomi-glasses-network-fix).
- **zero2005x**: [RokidAIAssistant](https://github.com/zero2005x/RokidAIAssistant).
- **Alpha Cephei**: [Vosk](https://github.com/alphacep/vosk-api).
- **Java Native Access**: [JNA](https://github.com/java-native-access/jna).
