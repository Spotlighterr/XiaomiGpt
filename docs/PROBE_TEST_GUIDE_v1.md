# XiaomiGpt Probe 0.1.0 — hướng dẫn thử trên thiết bị

Bản này dùng để quyết định đường kết nối cho app chính. Chưa thay Xiao AI, chưa có đăng nhập hay hội thoại ChatGPT tự động.

## Cài và mở

1. Dùng APK `releases/xiaomigpt-probe-v0.1.0.apk` được build từ source của repo này. Checksum tại `releases/SHA256SUMS.txt`.
2. Giữ app Xiaomi đang quản lý kính và kết nối Bluetooth của kính như bình thường.
3. Mở XiaomiGpt Probe, bấm **Cấp quyền và làm mới**. Quyền micro chỉ được hỏi khi bấm thử micro.

## Thử camera / HyperConnect

1. Chọn camera, bấm **Mở camera**. Danh sách lấy từ Camera2 của Android, không phải danh sách kính được xác thực.
2. Nếu Xiaomi 15 Ultra có tùy chọn camera liên thiết bị trong Control Center, thử chuyển sang camera kính khi bản xem trước đang mở.
3. Di chuyển kính trong khi điện thoại đứng yên và ngược lại để xác định nguồn ảnh.
4. Bấm **Chụp ảnh kiểm tra**. Kiểm tra ảnh tĩnh có đúng góc nhìn kính không; preview đúng chưa đủ chứng minh ảnh tĩnh cũng đúng.
5. Chỉ khi đúng, đánh dấu **Tôi xác nhận ảnh vừa chụp đúng là góc nhìn từ kính**.
6. Nếu cần thử ChatGPT đọc ảnh, bấm **Gửi ảnh sang ChatGPT**. Bạn vẫn phải thao tác trong app ChatGPT. Đây là phép thử chia sẻ ảnh thủ công, chưa phải luồng sản phẩm.

Nếu chỉ thấy camera điện thoại hoặc không có tùy chọn HyperConnect, ghi nhận kết quả đó. Không gọi camera điện thoại là camera kính. Tùy chọn hệ thống có thể chỉ hiện với một số app hoặc chế độ video; app thử không ép được HyperOS cung cấp tính năng này.

## Thử micro Bluetooth

1. Trong danh sách đường đàm thoại Bluetooth, chọn thiết bị kính.
2. Bấm **Ghi 3 giây rồi phát lại**. Chờ thông báo xác nhận micro Bluetooth rồi nói một câu tiếng Anh.
3. Nghe bản phát lại có ra từ kính và có đúng giọng vừa nói không.
4. Nếu không có kính trong danh sách, kiểm tra âm thanh cuộc gọi ở cài đặt Bluetooth rồi làm mới.

App kiểm tra đường AudioRecord thực tế, hủy nếu chuyển sang micro khác. Âm thanh chỉ giữ trong RAM, không lưu file hoặc gửi server. Peak thể hiện mức tín hiệu, không chứng minh nhận dạng tiếng Anh hay chất lượng hội thoại.

## Thử Wi-Fi

1. Chỉ thử khi app Xiaomi đã mở kết nối Wi-Fi media với kính, chẳng hạn trong thao tác import ảnh.
2. Nhập IP thực tế của kính. `192.168.43.1` chỉ là ví dụ, không được app coi là IP đã phát hiện.
3. Bấm **Kiểm tra danh sách ảnh**. App chỉ gửi `GET /v1/filelists` tới cổng 8080, trên một Wi-Fi cùng subnet.

App không tự mở hotspot/pairing, không gửi mật khẩu/token hoặc chữ ký giả định. Không đi theo redirect, không đổi mạng mặc định của cả tiến trình, không đọc/lưu nội dung danh sách file. HTTP 2xx chỉ chứng minh endpoint có phản hồi, chưa xác nhận schema hoặc đã lấy được ảnh.

## Thử thao tác kính và xuất báo cáo

Để XiaomiGpt ở trước màn hình, thử các nút/gesture và xem phần thao tác. Chỉ ghi các key liên quan media, volume, camera và assistant; không ghi phím nhập văn bản. Nguồn của key chưa được tự xác thực là kính. Không có key cũng chưa chứng minh firmware không có kênh sự kiện khác.

Sau khi thử, bấm **Xuất báo cáo kiểm tra** và gửi nội dung trong cuộc trò chuyện này. Báo cáo không tự gửi đi và không kèm ảnh, bản ghi âm, MAC, mật khẩu Wi-Fi, token hoặc body HTTP.

## Giới hạn cần giữ rõ

- Camera2 có thể không thấy camera kính dù ứng dụng hệ thống dùng được HyperConnect.
- Không có reverse pairing, BLE command hay lời hứa điều khiển nút Xiao AI trong bản này.
- App ngừng camera, hủy phép thử audio/HTTP khi rời màn hình; chưa chạy trợ lý nền.
- Metadata model/firmware của kính trong báo cáo là cấu hình người dùng cung cấp, không phải firmware app tự đọc được.
- Ảnh thử nằm trong vùng riêng của app; chỉ được chia sẻ khi người dùng bấm gửi ảnh. Không có quyền đọc toàn bộ thư viện.

## Build lại

Android Studio: JDK 17, SDK 36, mở repo và dùng Gradle Wrapper 8.13.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Máy Windows chưa có toolchain có thể dùng `scripts/bootstrap-toolchain.ps1`, rồi `scripts/build.ps1`. Script tải bộ công cụ vào `_archive/toolchain` và kiểm tra checksum trước khi giải nén; không sửa PATH hoặc cấu hình SDK toàn hệ thống.
