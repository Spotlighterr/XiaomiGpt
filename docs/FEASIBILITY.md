# Khả thi và phạm vi XiaomiGpt

Ngày kiểm tra: 2026-10-27. Mục tiêu: M2442G1, firmware 1.8.12.0, Xiaomi 15 Ultra, Android 16.

> Tiến độ mới: người dùng đã xác nhận âm thanh hoạt động. Luồng trợ lý đăng nhập ChatGPT và xử lý giọng nói cục bộ đã được triển khai trong 0.2.0-assistant-preview, chưa xác minh hội thoại end-to-end; xem [Assistant preview](ASSISTANT_PREVIEW_v1.md). Các mục chờ bên dưới là phân tích ở thời điểm đầu, không phải toàn bộ trạng thái mã hiện tại.

> Cập nhật phạm vi: người dùng yêu cầu cả camera kính và kích hoạt trực tiếp trên kính, đồng thời xác nhận tài khoản Plus hoặc Pro. Phương án chỉ mở ChatGPT Voice chưa đủ. Đang research trước triển khai; xem [RESEARCH_v1_2026-10-27.md](RESEARCH_v1_2026-10-27.md) cho các nguồn bổ sung và thứ tự kiểm chứng mới. Nội dung bên dưới giữ lại làm phân tích ban đầu.

## 1. Những gì đã xác nhận từ mi-gpt

Nguồn: https://github.com/idootop/mi-gpt

Commit đã đọc: `1aea52ef27e79ca705f597559f73b8ef6b7287b1`.
Bản clone phục vụ đọc nằm ở `_archive/upstream-mi-gpt`, được loại khỏi Git của XiaomiGpt.
Không chạy mã nguồn, script cài đặt hay đăng nhập của repo này.

| Bằng chứng mã nguồn | Cơ chế |
| --- | --- |
| `src/services/speaker/base.ts` | Khởi tạo MiNA và MiIOT; điều khiển đánh thức/phát âm thanh qua dịch vụ dành cho loa |
| `src/services/speaker/speaker.ts`, `getMessages()` | Đọc bản ghi hội thoại qua `MiNA.getConversations()`, lấy `record.query` |
| `src/services/openai.ts` | Dùng OpenAI SDK và Chat Completions |
| `.env.example` | Cấu hình `OPENAI_API_KEY`, model và endpoint |
| `docs/compatibility.md` | Danh sách thiết bị là các mẫu loa/màn hình Xiao AI; không nêu M2442G1 |
| `README.md` | Tác giả thông báo ngừng bảo trì |

Kết luận: có thể tham khảo cách tổ chức hội thoại và tách phần AI/phát tiếng.
Không có bằng chứng để bê nguyên giao thức điều khiển loa sang kính.
Repo cũng không giải quyết việc nhận tiếng Anh độc lập với bộ nhận giọng nói Xiao AI.

Các vị trí đối soát:

- https://github.com/idootop/mi-gpt/blob/1aea52ef27e79ca705f597559f73b8ef6b7287b1/src/services/speaker/base.ts
- https://github.com/idootop/mi-gpt/blob/1aea52ef27e79ca705f597559f73b8ef6b7287b1/src/services/speaker/speaker.ts
- https://github.com/idootop/mi-gpt/blob/1aea52ef27e79ca705f597559f73b8ef6b7287b1/src/services/openai.ts
- https://github.com/idootop/mi-gpt/blob/1aea52ef27e79ca705f597559f73b8ef6b7287b1/.env.example
- https://github.com/idootop/mi-gpt/blob/1aea52ef27e79ca705f597559f73b8ef6b7287b1/docs/compatibility.md

## 2. Hai cách đáp ứng yêu cầu dùng tài khoản chính thức

### A. XiaomiGpt tự xử lý hội thoại, đăng nhập ChatGPT chính thức

Tài liệu OpenAI hiện có Sign in with ChatGPT cho app mã nguồn mở và người dùng Plus/Pro đủ điều kiện.
Luồng đăng nhập và quyền tiêu thụ hạn mức là hai phần riêng biệt.
Không mặc định mọi tài khoản đăng nhập thành công đều được phép gọi mô hình.

- Đăng nhập trên trình duyệt hệ thống với OAuth, PKCE, state và nonce.
- Đăng ký client đúng tên XiaomiGpt và đúng host; không mượn client ID của ứng dụng khác.
- Kiểm tra ID token và quyền được cấp, lưu credential bảo mật, hỗ trợ refresh/logout.
- Dùng danh sách model thực tế trả về cho tài khoản.
- Gọi Responses API bằng OAuth access token; xử lý SSE đến sự kiện hoàn tất.
- Bản preview yêu cầu `store: false`, `stream: true`, tự gửi lịch sử cần thiết trong `input`.
- Luồng này chưa hỗ trợ audio/video input và transcription API. Vì vậy tiếng Anh cần STT/TTS trên Android hoặc một dịch vụ được chọn riêng.

Đây là hỏi đáp bằng model qua đăng nhập chính thức; không đồng nhất với ChatGPT Voice trong app ChatGPT.
Chưa triển khai hoặc xác minh đăng nhập thực tế cho XiaomiGpt.

Nguồn:

- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations

### B. XiaomiGpt gọi app ChatGPT chính thức

Người dùng đăng nhập và dùng Voice trong app ChatGPT. XiaomiGpt hỗ trợ mở app, thiết lập và chẩn đoán kết nối.
Phải kiểm tra intent/shortcut được bản ChatGPT đã cài cung cấp trước khi khẳng định có thể mở thẳng Voice.
Việc mở app không chứng minh Voice đã bắt đầu, hoặc micro đang lấy từ kính.
XiaomiGpt không kiểm soát audio routing và trạng thái hội thoại bên trong ứng dụng khác.

Không có bằng chứng đã thu thập nào cho phép cam kết nút Xiao AI trên kính sẽ gọi được ChatGPT.

## 3. Cần xác minh trên phần cứng

Bluetooth là hướng thử nghiệm, chưa phải khả năng đã được xác nhận với firmware 1.8.12.0.

1. Ghép kính qua Bluetooth; kiểm tra profile cuộc gọi và media mà điện thoại thực sự nhận được.
2. Liệt kê communication devices; xác định kính có xuất hiện như thiết bị giao tiếp hai chiều hay không.
3. Nếu app tự thu âm, chọn communication device và kiểm tra `AudioRecord.routedDevice` khi ghi; không coi việc đã ghép đôi hoặc phát nhạc được là bằng chứng micro hoạt động.
4. Thử câu tiếng Anh, kiểm tra transcript và nghe câu trả lời từ kính.
5. Kiểm tra bấm/nút/gesture của kính có gửi sự kiện tới Android hay chỉ gọi Xiao AI nội bộ.
6. Thử khóa màn hình, chuyển app, ngắt Bluetooth và cuộc gọi đến; không tự chuyển sang thu micro điện thoại mà không báo.

Không thực hiện thay firmware, downgrade, root hoặc sửa app Xiaomi trong giai đoạn xác minh này.
Các khẳng định về ADB đã bị vá, hệ điều hành kính hoặc HTTP API từ nội dung người dùng gửi vẫn là thông tin cần đối soát trên đúng firmware.

Nguồn Android:

- https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager
- https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)
- https://developer.android.com/reference/android/media/AudioRecord#getRoutedDevice()

## 4. Quyết định đang chờ thông tin

- Chọn A: đăng nhập trong XiaomiGpt, hoặc B: gọi app ChatGPT chính thức.
- Gói tài khoản: người dùng đã xác nhận Plus hoặc Pro; quyền thực tế của luồng tích hợp vẫn cần kiểm tra.
- Tình trạng ghép Bluetooth và bản app ChatGPT đang cài.

Chỉ kết luận hỗ trợ M2442G1 sau khi kiểm tra end-to-end trên kính thật.
