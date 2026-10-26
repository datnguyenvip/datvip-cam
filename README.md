# Datvip Cam
Camera đóng dấu thời gian, GPS, logo và watermark lên ảnh.

- `android/` – bản Android (Kotlin, CameraX). Mở bằng Android Studio.
- `ios/` – bản iOS (SwiftUI). Project Xcode được tạo tự động bằng XcodeGen.
- `.github/workflows/ios-ipa.yml` – build file IPA chưa ký trên GitHub Actions.

## Lấy file IPA chưa ký
1. Vào tab **Actions** → **Build unsigned IPA** → **Run workflow**.
2. Đợi chạy xong, tải artifact **DatvipCam-unsigned-ipa**.
3. IPA chưa ký không cài thẳng vào iPhone được. Dùng Sideloadly hoặc AltStore để ký bằng Apple ID của bạn rồi cài (bản ký miễn phí hết hạn sau 7 ngày).
