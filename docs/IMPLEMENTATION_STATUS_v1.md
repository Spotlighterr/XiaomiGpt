# XiaomiGpt — trạng thái triển khai 0.1.0

Ngày: 2026-10-27.

> Tài liệu lịch sử của 0.1.0. Trạng thái trợ lý mới và xác nhận âm thanh từ người dùng nằm tại [Assistant preview](ASSISTANT_PREVIEW_v1.md).

## Đã triển khai

- Project Android Kotlin, min SDK 31, compile/target SDK 36.
- Camera2: liệt kê camera, xem trước, chụp JPEG, đối chiếu nguồn bằng hình ảnh và chia sẻ ảnh thủ công vào ChatGPT chính thức nếu app đã cài nhận intent này.
- Bluetooth audio: chọn đường đàm thoại, ghi 3 giây và phát lại; kiểm tra nguồn micro thực tế, hủy khi route thay đổi.
- Wi-Fi: GET cố định tới `/v1/filelists` cổng 8080 trên IP nội bộ cùng Wi-Fi, không gửi token hoặc theo redirect; chỉ báo HTTP status.
- Ghi nhận các key media/camera/assistant Android chuyển tới Activity khi app đang mở.
- Xuất báo cáo bằng thao tác của người dùng; không đính kèm ảnh, âm thanh, MAC, token hoặc body file-list.
- Dừng camera và hủy phép thử đang chạy khi rời màn hình; vùng ảnh riêng không được backup/transfer tự động.
- Toolchain Windows riêng trong `_archive/toolchain`, Gradle Wrapper với SHA-256 distribution đã ghim; script build/package.

## Kiểm tra đã chạy

| Kiểm tra | Kết quả |
| --- | --- |
| `testDebugUnitTest` | 3 test, 0 failure, 0 error |
| `lintDebug` | 0 error; 4 cảnh báo có phiên bản Gradle/library mới hơn |
| `assembleDebug` | Thành công |
| `apksigner verify --verbose` cho APK tự build | Thành công, chữ ký v2 |
| Camera/Bluetooth trên Xiaomi 15 Ultra + M2442G1 | Chưa chạy |
| ChatGPT đọc ảnh được chia sẻ trên điện thoại | Chưa chạy |

Các test JVM kiểm tra giới hạn host/subnet cho HTTP probe và việc không xác nhận ảnh cũ cho camera mới. Không dùng các test này làm bằng chứng phần cứng tương thích.

APK bàn giao: `releases/xiaomigpt-probe-v0.1.0.apk`; checksum tại `releases/SHA256SUMS.txt`. Đây là APK debug của project, không phải APK Xiaomi sửa lại.

## Chưa triển khai / chưa xác minh

- Pairing hoặc đăng nhập Xiaomi riêng, protobuf/BLE command và tự mở hotspot.
- Tự phát hiện/tải ảnh vừa chụp trên kính.
- Camera liên thiết bị có cung cấp cho app này hoặc ChatGPT trên HyperOS thực tế hay không.
- Thay wake word, nhận nút Xiao AI, hỏi đáp khi điện thoại trong túi/chạy nền.
- Nhận dạng tiếng Anh, hội thoại AI, đăng nhập ChatGPT và phản hồi TTS.

Giữ mục tiêu camera + voice + thao tác trực tiếp trên kính. Không coi bản probe này là bản sản phẩm hoàn thành.

Hướng dẫn thử: [PROBE_TEST_GUIDE_v1.md](PROBE_TEST_GUIDE_v1.md).
