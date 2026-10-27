# XiaomiGpt — phân tích APK bằng REA, đợt 1

## Kết luận có ảnh hưởng tới thiết kế

APK cung cấp thêm vị trí mã cụ thể cho đường media/Bluetooth, nhưng chưa chứng minh một app bên ngoài có thể thay Xiao AI hoặc lấy camera trên firmware 1.8.12.0.

Không chọn MIShow làm API camera chỉ từ việc service được exported. Trong các method đã đọc, `startDemo(String)` là hàm rỗng, còn `getConnectedNode()` chuyển tiếp sang khởi tạo AIDL.

Có command O95 liên quan media, Wi-Fi, AI và livestream để nghiên cứu tiếp. Tên `SetAIAssistant` không có nghĩa đổi được backend; các field thấy được là voice wakeup, camera access, collaboration wakeup và tone.

Giữ bản Kotlin hiện tại là probe kiểm tra phần cứng. Chưa gửi command O95 hoặc token tới kính dựa trên những phát hiện tĩnh này.

## 1. Định danh và tính toàn vẹn đầu vào

| Thuộc tính | Kết quả |
| --- | --- |
| File người dùng cung cấp | `eco-andriod-glass.apk` |
| Kích thước | 402,681,690 byte |
| SHA-256 | `8757e57eb498071daccf4ff09b98be8072a2cc5c2f293a638605887c1a89d7a8` |
| Package tự khai | `com.xiaomi.superhexa` |
| Version tự khai | 3.1.27 / versionCode 31027 |
| SDK tự khai | min 29, target 31 |
| Class / resource count theo REA | 43,704 / 4,768 |
| Kiểm tra `apksigner` | Exit 1; v3 integrity check không đạt, `CHUNKED_SHA512 digest mismatch` |

REA không tự xác minh chữ ký; kiểm tra chữ ký dùng riêng Android Build Tools 36.0.0. Log tại `_archive/rea/apk-signature-check.txt`. Lỗi integrity không đủ để kết luận có mã độc hoặc xác định ai đã sửa file, nhưng không thể xác nhận đây là APK Xiaomi nguyên bản. Cũng chưa đối chiếu với app đang cài trên điện thoại.

Hash đầu vào sau các phép đọc vẫn trùng trước phân tích. Không cài, chạy, sửa hay ký lại APK này. Những phát hiện bên dưới chỉ áp dụng cho đúng file có hash trên, chưa phải chứng nhận cho firmware kính.

## 2. Công cụ và Evidence

- Đã đọc skill `reverse-engineer-anything` và hướng dẫn Android của skill.
- REA MCP ban đầu báo thiếu `REA_JADX_MCP_JAR`; không sửa cấu hình MCP/Codex.
- Tiếp tục qua CLI công khai `rea-agents@6.7.0`, cấu hình biến môi trường cho từng tiến trình.
- JADX headless MCP 0.7.1; JAR SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`, đối chiếu metadata chính thức của REA trước sử dụng.
- Evidence JSON đầy đủ được giữ trong `_archive/rea/evidence/`. Xem [REA_EVIDENCE_INDEX_v1.json](REA_EVIDENCE_INDEX_v1.json) để đối soát operation, Evidence ID, representation, SHA-256 của từng record và hash APK.
- Một số method dùng smali fallback; representation và tình trạng source được giữ trong index. Không coi decompile là thực thi trên thiết bị.

Script chạy lại: `scripts/rea-apk.ps1`; script tạo index: `scripts/index-rea-evidence.ps1`.

## 3. MIShow: có Binder, chưa có đường camera đã chứng minh

Manifest khai báo:

- Service `com.superhexa.supervision.feature.miwearglasses.mishow.O95ConnectInfoService`.
- Action `com.xiaomi.superhexa.ACTION_INTERCONNECTION_MISHOW`.
- `exported=true`, `enabled=true`.
- Permission `com.xiaomi.superhexa.permission.ACCESS_MISHOW`, protection level `normal`.

`O95ConnectInfoService.onBind(Intent)` gọi `startService(...)` và trả `this.binder`. Không thấy kiểm tra whitelist caller ngay trong method này; đây không phải kết luận mọi method hay đường gọi đều không có kiểm tra.

Binder class `O95ConnectInfoService$binder$1` có `enterAutoShow`, `exitDemo`, `getConnectedNode`, `startDemo`. Đã đọc:

| Method | Quan sát |
| --- | --- |
| `startDemo(String)` | Smali chỉ `return-void`; method này không mở camera |
| `getConnectedNode()` | Ghi log rồi gọi `O95ConnectInfoService.access$initAidl(...)`; không trả byte ảnh cho caller trong method này |

Chưa đọc đầy đủ `initAidl`, `enterAutoShow` hoặc đầu bên kia của AIDL. Không suy từ tên MIShow rằng đây chính là HyperConnect camera bridge; chưa có đủ bằng chứng nối hai cơ chế đó.

## 4. Wi-Fi và đường truyền media

Class `...bluetooth.command.o95.EnableWiFiAP`:

- `getPackedId()` trả **88**.
- `getSystem()` tạo `SystemProtos.System` rỗng; phần đóng gói còn nằm ở base class/protocol.

Mã 88 là giá trị của method này trong APK 3.1.27. Không gửi byte `88` trực tiếp vào BLE; còn framing, layer lệnh, pairing, mã hóa và trạng thái kết nối chưa xác minh. Ghi chép cộng đồng nói tới type 101 có thể thuộc lớp giao thức hoặc phiên bản khác; chưa đủ cơ sở coi hai số là cùng một trường.

Class search còn tìm thấy `RequestMediaFileList`, `RequestMediaFile`, `SyncMediaFileList`, `ConfirmMediaFile`, `GetMediaFileSummary`, `StartGlassLiveStreaming`, `StopLiveLiveStreaming`, `RequestRecord` và `SendMisPacket` trong nhóm command O95.

`MediaExplorerO95ViewModel` có model `O95HostFilesList`, result set và operator `IDeviceOperator<O95StateLiveData>`. Đã inventory method/field, chưa trace đầy đủ luồng gọi hoặc decode transport của các command media. Chưa xác định được cách tải ảnh mới tự động mà không import thủ công.

## 5. Trợ lý và yêu cầu đa phương thức

`SetAIAssistant` có các field:

- `cameraAccess: boolean`;
- `collaborationWakeup: boolean`;
- `voiceWakeup: boolean`;
- `currentTone: Integer`.

Đã đọc thêm `getSystem()`: các field trên được gán vào `SystemProtos.SystemSetting.AIAssistant`, với `currentTone` thành `selectedTone`. Không có field URL, model hoặc API credential trong payload method này. Đây là cấu hình wakeup/quyền camera/tone, không phải một điểm đổi backend ChatGPT đã tìm được. Kết luận chỉ giới hạn ở class/method đã kiểm tra, không chứng minh toàn APK không có cấu hình backend khác.

`RequestAivsMultiModal` có `payload`, `requestId`, `transactionId`, `cmd`, `needResponse`. Đã đọc `getAivs()`: tạo `AivsRequestInfo`, chuyển transaction/request ID thành byte array, gắn chuỗi payload nếu có, rồi bọc trong `MultiModal` với trường `cmd` và `Aivs.setMultiModel(...)`. Đây là cấu trúc đóng gói cụ thể, chưa phải schema payload hoặc đường ảnh đã biết. Chưa trace caller để xác định hướng yêu cầu/phản hồi và cách lấy byte ảnh. Không suy rằng một payload chuỗi tùy ý có thể đổi LLM.

## 6. Phạm vi chưa kiểm tra

- Runtime trên Xiaomi 15 Ultra / Android 16 và firmware kính 1.8.12.0.
- Giao thức pairing đầy đủ, dispatcher receive và stream audio/camera thực tế.
- Native libraries, nội dung động, reflection và mọi class ngoài nhóm đã chọn.
- Sự kiện nhấn giữ càng kính có được chuyển sang ứng dụng bên ngoài không.
- Khả năng dùng tài khoản ChatGPT chính thức cho luồng hoàn chỉnh.

## 7. Quyết định triển khai sau đợt này

1. Bàn giao probe Kotlin để ghi nhận camera, micro và kênh media thật trên thiết bị; hướng dẫn tại [PROBE_TEST_GUIDE_v1.md](PROBE_TEST_GUIDE_v1.md).
2. Nếu Camera2/HyperConnect không cung cấp hình từ kính, tập trung tiếp vào call chain `RequestMediaFileList` / `RequestMediaFile`, phần nhận sự kiện và handshake O95.
3. Đối chiếu APK với nguồn Xiaomi hợp lệ hoặc package đang cài trước khi dựa vào behavior tĩnh của file này để thay đổi thiết bị.
4. Chỉ nối hội thoại AI và chọn thao tác kích hoạt sau khi có đường lấy ảnh thực sự hoạt động. Bản chỉ voice chưa đạt mục tiêu.
