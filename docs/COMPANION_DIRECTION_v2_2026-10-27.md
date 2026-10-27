# Hướng app kết nối riêng và phản hồi thử nghiệm

Ngày: 2026-10-27. Cập nhật sau khi người dùng thử bản 0.1.0 trên thiết bị.

## Phản hồi đã nhận

- Không thấy tùy chọn HyperConnect trong Control Center; app chưa lấy được camera kính.
- App thấy Bluetooth; người dùng nghĩ micro có thể đã hoạt động nhưng chưa chắc chắn.
- Giao diện nhiều phép thử kỹ thuật, khó sử dụng.

Đây là kết quả người dùng báo, chưa có báo cáo route hoặc log kèm theo. Không chuyển nhận xét micro thành kết luận đã xác minh end-to-end.

## Đính chính HyperConnect

[Tài liệu Xiaomi](https://hyperos.mi.com/continuity/abilities/ab0007) mô tả chức năng camera liên thiết bị trong cuộc gọi video/livestream được hỗ trợ: mở Control Center khi đang gọi, bấm biểu tượng camera (相机球), rồi chọn thiết bị. Kính cần kết nối điện thoại và dùng cùng tài khoản Xiaomi; WLAN/Bluetooth được bật. Xiaomi lưu ý hỗ trợ phụ thuộc app bên thứ ba.

Đây không phải nút HyperConnect luôn có trong Control Center. Hướng dẫn trước đã đặt quá nhiều kỳ vọng vào việc app Camera2 thông thường có thể dùng cơ chế này. Chưa xác minh bản probe được hệ thống hỗ trợ. Không tiếp tục yêu cầu người dùng tìm một nút chung trong CC; cũng không suy ra lỗi ROM hay lỗi người dùng chỉ từ việc không thấy nút.

## Có ai làm app kết nối kính thay thế chưa?

| Dự án | Bằng chứng sơ cấp đã đọc | Giới hạn áp dụng |
| --- | --- | --- |
| [Visor / macedonga](https://github.com/macedonga/visor) | README mô tả đăng nhập/pairing Meta, đồng bộ ảnh qua Wi-Fi Direct, live view và trợ lý dùng model riêng | Dành cho Meta; yêu cầu ownership/session của Meta và API credential cho AI. Không áp dụng trực tiếp giao thức này cho Xiaomi |
| [CyanBridge / FerSaiyan](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK) | Có Android companion, workflow kết nối và media cho HeyCyan; các thiết bị khác được phân loại theo mức hỗ trợ | [Ma trận hiện công bố](https://cyanbridgelabs.com/smart-glasses/compatibility) không liệt kê M2442G1; không khẳng định nó hỗ trợ kính của người dùng |
| [RokidAIAssistant / zero2005x](https://github.com/zero2005x/RokidAIAssistant) | App Kotlin và luồng ảnh/voice qua SDK Rokid | Phụ thuộc SDK và khả năng chạy app phía kính trên mẫu Rokid phù hợp |
| [Ghi chép Xiaomi của MentraOS](https://github.com/Mentra-Community/MentraOS/blob/dev/agents/xiaomi_glasses_reverse_engineering.md) | Các quan sát app Xiaomi mở hotspot và tải media | Ghi chép nghiên cứu, chưa có quy trình pairing/authentication hoàn chỉnh cho M2442G1 |

Kết luận: mô hình app kết nối thay thế có tiền lệ. Chưa có bằng chứng tìm được cho một giải pháp công khai hoàn chỉnh trên M2442G1 firmware 1.8.12.0.

## Phương hướng

Chuyển trọng tâm nghiên cứu từ tìm camera kính trong Camera2 sang giao thức companion O95:

1. Xác định kết nối và session xác thực Bluetooth/BLE/SPP mà kính thực sự yêu cầu.
2. Đọc được model, pin hoặc trạng thái cơ bản để chứng minh client kết nối đúng kính.
3. Nhận sự kiện media mới và lấy danh sách file qua kênh được xác minh.
4. Tải ảnh mới, rồi kiểm tra lệnh chụp/camera và thao tác gọi trợ lý.
5. Ghép ảnh + giọng nói + ChatGPT thành trải nghiệm sử dụng từ kính.

Giai đoạn đầu vẫn dùng app Xiaomi cho việc quản lý kính/firmware. Khả năng cùng tồn tại hai session chưa được kiểm chứng; không suy từ Bluetooth audio hoạt động sang việc đã có kênh điều khiển độc quyền.

REA đã xác định được các class `RequestMediaFileList`, `RequestMediaFile`, `SyncMediaFileList` và handshake SV trong APK cung cấp. Cần tiếp tục lần theo caller, dispatcher và framing trước khi triển khai command thật. Không dùng một opcode hoặc tên class làm bằng chứng đủ để kết nối.

## Bản 0.1.1 sửa ngay vấn đề sử dụng

- Màn hình chính tách kết nối âm thanh, thử micro và trạng thái camera.
- Không yêu cầu tìm HyperConnect trong CC; không tự nhận camera điện thoại là camera kính.
- Chỉ xin quyền Bluetooth khi làm mới; micro/camera xin khi dùng chức năng tương ứng.
- Thử micro có các giai đoạn chuẩn bị, thu, phát lại và xác nhận của người nghe; hiển thị mức tín hiệu.
- Phân biệt route Bluetooth đã được Android xác nhận với người dùng nghe đúng bản phát lại từ kính.
- Giữ các phép thử camera/IP/key trong chẩn đoán nâng cao, mặc định đóng.

Bản này cải thiện cách kiểm tra. Nó chưa triển khai pairing O95 độc lập hoặc sửa được đường camera kính.
