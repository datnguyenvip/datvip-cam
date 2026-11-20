# Kiểm thử Camera HT trên thiết bị thật

Trạng thái: chưa xác nhận trên thiết bị thật. Bài kiểm thử cảm biến và ảnh chụp máy mô phỏng không thay thế bước này.

## Bản cài thử

- Android: bản `preview` đã ký bằng khóa phát triển, nhãn **Camera HT Thử**, dùng mã thử `.kiemthu` có sẵn trong dự án để cài song song. Mã ứng dụng chính `com.datvip.cam` không thay đổi. Bản thử có vùng dữ liệu riêng; không dùng bản này để kết luận dữ liệu bản đang phát hành đã được di chuyển thành công.
- iOS: IPA chưa ký không thể cài trực tiếp trên iPhone. Có thể dùng mã nguồn để build và ký qua Xcode bằng tài khoản được phép, hoặc ký IPA bằng công cụ và hồ sơ của chủ ứng dụng. Chưa tải lên TestFlight/App Store Connect. Khi thử cập nhật đè lên bản hiện tại, phải dùng đúng chữ ký và mã ứng dụng; không gỡ ứng dụng trước khi kiểm tra dữ liệu.

## Ghi nhận cho mỗi nền tảng

Ghi loại máy, phiên bản hệ điều hành, commit/bản cài thử và kết quả từng dòng dưới đây. Chụp màn hình lỗi, kèm ảnh/video gốc nếu hướng hoặc dấu ảnh bị sai.

| Bước | Thao tác | Kết quả cần có | iPhone | Android |
|---|---|---|---|---|
| 1 | Mở mới với hệ thống tiếng Việt / tiếng Anh / ngôn ngữ khác | Việt / Anh / Anh tương ứng | Chưa thử | Chưa thử |
| 2 | Đổi Việt ↔ Anh; đóng hoàn toàn rồi mở lại | Giữ lựa chọn; các màn hình phụ, lỗi, chia sẻ đúng ngôn ngữ | Chưa thử | Chưa thử |
| 3 | Tạo mẫu và ghi chú tiếng Việt có dấu rồi đổi ngôn ngữ | Nội dung và tên mẫu giữ nguyên | Chưa thử | Chưa thử |
| 4 | Mở cài đặt trên dữ liệu mới | Căn chỉnh khung hình mặc định tắt | Chưa thử | Chưa thử |
| 5 | Bật Căn chỉnh khung hình; đóng/mở ứng dụng | Cả lưới 3×3 và cân bằng cùng bật; giữ lựa chọn | Chưa thử | Chưa thử |
| 6 | Nghiêng máy nhẹ, giữ thẳng, đặt máy phẳng | Chỉ báo mượt; xanh trong khoảng ±2°; ẩn khi không xác định được đường chân trời | Chưa thử | Chưa thử |
| 7 | Bật khóa xoay hệ thống; xoay dọc → ngang trái → ngang phải → dọc | Toàn bộ bố cục camera và nút điều khiển theo máy; không chập chờn gần góc chéo | Chưa thử | Chưa thử |
| 8 | Chụp ở từng hướng, cả camera trước và sau | Ảnh đúng chiều, dấu/GPS/logo đúng vị trí, không có lưới/cân bằng | Chưa thử | Chưa thử |
| 9 | Bắt đầu quay ở từng hướng, quay có/không có micro | Video đúng chiều, dấu ảnh/GPS/âm thanh hoạt động; không có lớp căn chỉnh | Chưa thử | Chưa thử |
| 10 | Xoay máy khi đang quay | Nút điều khiển xoay; video giữ khung hình/hướng lúc bắt đầu, dấu không mất | Chưa thử | Chưa thử |
| 11 | Thử thư viện, xóa, chia sẻ, mẫu dấu, watermark, quyền truy cập và zoom nếu có trên bản gốc | Không giảm chức năng so với bản đang phát hành | Chưa thử | Chưa thử |
| 12 | Cập nhật bằng bản được ký đúng lên dữ liệu hiện tại | Ảnh/video, mẫu, ghi chú, logo và thiết lập còn nguyên | Chưa thử | Chưa thử |
| 13 | Mở camera và cài đặt khi có/không có mạng | Không có quảng cáo hoặc Remove Ads | Chưa thử | Chưa thử |

Ngôn ngữ của hộp thoại xin quyền hệ điều hành do hệ điều hành quyết định; nội dung mô tả đã có bản Việt và Anh. Đổi ngôn ngữ bên trong ứng dụng không tự đổi ngôn ngữ hệ thống.

## Giới hạn cần giữ rõ

- Chưa có bằng chứng kiểm thử khóa xoay trên iPhone hoặc Android thật.
- Chưa kiểm tra cập nhật đè lên dữ liệu thực bằng chữ ký phát hành.
- Chưa xác nhận bản Android đang có trên Google Play khớp versionCode 7; chỉ xác nhận được bản build thành công và mã nguồn tương ứng.
- Chưa xác minh hoặc chỉnh metadata trên App Store/Google Play.
- Không phát hành cho đến khi chủ ứng dụng kiểm tra và đồng ý.

Tham khảo cơ chế cảm biến: [Apple Core Motion](https://developer.apple.com/documentation/coremotion/cmmotionmanager), [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [CameraX VideoCapture rotation](https://developer.android.com/reference/androidx/camera/video/VideoCapture).
