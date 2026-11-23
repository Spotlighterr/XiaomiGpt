# XiaomiGpt Link — tích hợp tài khoản và kính, bản phát triển v1

Ngày: 23/11/2026. Nhánh: `feature/xiaolink-account-integration`.

PO cho phép kết hợp XiaomiGpt với XiaoLink, dùng ChatGPT và Gemini bằng tài khoản, gọi qua kính và đọc ảnh camera; yêu cầu Antigravity CLI cùng viết code. PO xác nhận điện thoại hiện tại là **Sony Xperia 1 V**, thay Xiaomi 15 Ultra. Android hiện tại và firmware kính chưa đọc được từ thiết bị.

## Kết quả đã có

- Project Android độc lập tại `integrations/xiaolink/`, app **XiaomiGpt Link Dev**, ID `io.github.spotlighterr.xiaomigpt.integrated`, version `0.4.0-dev`. Giữ app cũ và mọi release cũ.
- Kết nối Xiaomi account/WearAuthV2/SPP, pin/status, sự kiện AIVS, micro mSBC và ảnh type 3/4 từ XiaoLink.
- Nhận dạng tiếng Việt/Anh ngoại tuyến bằng Vosk, không mở micro điện thoại để thay thế micro kính.
- ChatGPT: giữ đăng nhập trình duyệt PKCE và kiểm ID token từ XiaomiGpt; tải model theo tài khoản, gửi Responses SSE với `store:false`, `stream:true`; thêm payload `input_image` từ JPEG vừa nhận.
- Gemini: cầu nối Python dùng tài khoản Google đang đăng nhập trong Antigravity CLI, qua ACP image block. Không cần Gemini API key. Đây là tài khoản Antigravity CLI, không phải nhúng phiên web Gemini vào Android.
- Có bộ chọn model ChatGPT và danh sách model Gemini do máy chủ cho phép; bộ chọn ngôn ngữ, tải speech model và bật/tắt camera cho câu hỏi.
- Tổng hợp trả lời bằng Android TTS ngoại tuyến, chọn đúng Bluetooth communication device theo địa chỉ kính và kiểm tra đường phát; mất route hoặc Stop thì không tiếp tục đọc câu trả lời cũ.
- Generation của lượt hỏi chặn kết quả inference cũ; Stop/disconnect kết thúc MultiModal, hủy mobile HTTP và vô hiệu kết quả cũ. Tác vụ CLI trên server có thể hoàn tất rồi mới nhả slot; không tuyên bố hủy compute server tức thì.
- Không ghi raw voice hoặc JPEG AI xuống điện thoại; ngữ cảnh hội thoại chỉ trong bộ nhớ. Kho cấu hình chuỗi/credential dùng Android Keystore, backup và device transfer có exclusion rõ.
- Bỏ log raw auth/bind và response cloud có secret. Luồng tài khoản mới không dùng tool `capture_photo` báo thành công sớm của AI engine cũ.

## Đóng góp Antigravity CLI

CLI đã chạy trên máy chủ trong checkout riêng và viết `account_bridge/`: HTTP auth, validation, adapter Gemini ACP, kiểm tra permission/hook, 13 tests và contribution notes. Codex đọc lại, thêm deadline khi ghi pipe cho ảnh lớn, giới hạn JSON/output, che ACP error body, thêm regression test và nối vào Android. Bản đã review có **14 tests máy chủ**.

`agy acp` phiên được xác minh là **1.5.15**. Camera bytes đi vào content block `type:image`, không giả lập bằng chuỗi tên file. Per-request workspace có hook từ chối công cụ, không nạp customization mặc định và từ chối callback xin quyền. Credential vẫn do CLI chính thức quản lý; không đọc/copy token Google/OpenAI vào bridge.

Bridge cố ý không dùng Codex CLI làm backend ChatGPT vì contributor chưa xác lập được cấu hình loại bỏ đầy đủ công cụ thực thi trong phiên đã kiểm tra. Điều này không chặn ChatGPT trên Android: app dùng đăng nhập ChatGPT native và HTTP Responses riêng, không khai báo công cụ chạy shell.

Nguồn đóng góp: `integrations/xiaolink/account_bridge/CONTRIBUTION.md`. Log CLI gốc nằm trong `_archive/integration-evidence/antigravity-contribution.log`.

## Kết quả kiểm chứng

| Hạng mục | Kết quả |
| --- | --- |
| Android `testDebugUnitTest` | 73 cases: **71 passed, 2 skipped, 0 failed/errors** |
| Hai case bỏ qua | RTMP ffmpeg theo đường dẫn macOS/Linux của upstream; không phải camera kính đã thử |
| Android `lintDebug` | **0 errors, 83 warnings** |
| Android `assembleDebug` | PASS |
| Chữ ký APK | PASS, v2 |
| ZIP native alignment | PASS, `zipalign -c -P 16 4` |
| ELF PT_LOAD của ba thư viện arm64 | Đều có alignment 16.384 byte |
| Account bridge trên Linux | **14 tests, OK** |
| HTTP health | 200 |
| Status không có bearer | 401 |
| Status có bearer | 200 |
| Gemini đọc ảnh qua HTTP từ máy Windows tới server | 200, nhận đúng màu và chữ trong ảnh giả lập |
| Điện thoại qua ADB | Chưa thấy thiết bị tại thời điểm kiểm tra |
| ChatGPT sign-in/ảnh trực tiếp trên Sony | Chưa thử |
| Gọi bằng kính → mic kính → AI → loa kính | Chưa thử trên Sony |
| Ảnh chụp mới trên kính thật tới model | Chưa thử |

Ảnh thử có nửa trên xanh, nửa dưới vàng và chữ `TEST 42`. Gemini trả lời đúng; lần thử qua LAN ghi khoảng **6,47 giây**. Đây là phép thử ảnh tổng hợp tới provider, không phải bằng chứng camera M2442G1 hoạt động hay thời gian đáp ứng trên kính.

Log build cuối: `_archive/INTEGRATION_BUILD_FINAL_2026-11-23.log`. Evidence JSON, SHA-256, log tests và ảnh tổng hợp nằm trong `_archive/integration-evidence/`. Không đưa token/private connection file vào Git.

## Cầu nối phát triển đang chạy

Một user service chạy bridge trên máy chủ riêng, bind địa chỉ LAN riêng, yêu cầu bearer token. Không enable khởi động cùng máy và không sửa các service cũ. Đây không phải public release hay public endpoint.

Thông tin nhập app nằm trong file riêng `_archive/integration-evidence/bridge-connection.txt`; file và token bị Git ignore, được giới hạn ACL Windows. Không chép nội dung token vào chat, báo cáo công khai hoặc commit. Điện thoại phải truy cập được cùng mạng riêng hoặc có đường mạng riêng tương ứng.

Bridge xóa thư mục tạm/log theo request. Không tuyên bố vô hiệu toàn bộ lưu lịch sử/retention của chính CLI hoặc Google. Thử app-data-dir riêng làm phiên tài khoản không khả dụng, nên không sử dụng và không copy credential để lách giới hạn.

## Nghiệm thu tiếp trên Sony Xperia 1 V

1. Cắm USB, bật Gỡ lỗi USB, chấp nhận khóa ADB trên điện thoại. Đọc model/Android hiện tại trước khi cài.
2. Cài APK local dev vào package riêng. Không gỡ app cũ, không reset/bind lại kính đang hoạt động.
3. Pair Bluetooth và lấy khóa qua tài khoản Xiaomi đang sở hữu kính. Xác minh auth/pin/status.
4. Đăng nhập ChatGPT trên Sony, kiểm tra model tài khoản và hỏi ảnh mới. Kết nối bridge Gemini bằng thông tin riêng, thử cùng một ảnh khác để loại ảnh cũ.
5. Tải Vosk và giọng đọc ngoại tuyến. Xác minh từng thao tác chạm/giữ/bấm trên kính, xác định cmd, kiểm micro/loa.
6. Thử khi app nền, rồi screen-off/locked, dừng giữa lúc lấy ảnh và reconnect. Ghi kết quả từng điều kiện riêng.
7. Lấy xác nhận trực tiếp của PO về luồng vật lý. Chưa tạo tag, GitHub release/pre-release hay upload APK asset khi chưa đạt gate.

Gói voice image hiện chưa có bằng chứng request ID phản hồi được firmware echo; khi một FIRST_IMAGE rất cũ tới đúng lúc bắt đầu capture mới vẫn cần đối soát trên kính. Code chặn ảnh thiếu, quá lớn, continuation mồ côi và HTTP response cũ; không suy ra từ đó đã chứng minh tuyệt đối độ mới của ảnh trên firmware thật.

## Build và quyền sử dụng nguồn

Chạy `scripts/build-integrated.ps1` từ root với toolchain có sẵn, hoặc wrapper trong standalone project. XiaoLink gốc GPL-3.0; nguồn account/speech XiaomiGpt có MIT notice riêng. Combined project giữ license GPL và provenance; app cũ vẫn MIT. Không phát hành bằng cách gỡ bỏ attribution của bên nào.

Tài liệu chính thức đã đối chiếu: [ChatGPT plan inputs/limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations), [Antigravity CLI](https://antigravity.google/docs/cli/reference), [Antigravity permissions](https://antigravity.google/docs/permissions).
