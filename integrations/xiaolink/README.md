# XiaomiGpt Link (Fork từ XiaoLink)

Bản fork của [mahdi-malv/xiaolink](https://github.com/mahdi-malv/xiaolink), giữ nguyên lõi kết nối kính Xiaomi AI Glasses và mở rộng thêm cửa sổ chat AI đa nhà cung cấp (**ChatGPT**, **Gemini Live**, **Gemini Bridge**, **DeepSeek**), nhận dạng giọng nói Google Speech qua Android 13+ (dự phòng Vosk), camera thông minh, lệnh điều khiển điện thoại/kính và giao diện song ngữ **Tiếng Việt / English**.

Xem hướng dẫn cài đặt và sử dụng chi tiết tại [README gốc của dự án](../../README.md).

## Build nhanh

```sh
./gradlew testDebugUnitTest assembleDebug
```

File APK đầu ra: `app/build/outputs/apk/debug/app-debug.apk`.

## Giấy phép và tham khảo

- Dự án gốc: [mahdi-malv/xiaolink](https://github.com/mahdi-malv/xiaolink) (GPL-3.0, xem `LICENSE`).
- Thành phần tích hợp từ XiaomiGpt giữ ghi nhận MIT (xem `THIRD_PARTY_INTEGRATION.md`).
- Thư viện nhận dạng ngoại tuyến: [Vosk](https://github.com/alphacep/vosk-api) (Apache-2.0) và [JNA](https://github.com/java-native-access/jna).
