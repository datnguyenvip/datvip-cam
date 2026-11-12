# Camera HT — báo cáo thay đổi ngày 12/11/2026

Trạng thái: đã triển khai và đạt kiểm thử tự động; cần thử iPhone và Android thật trước khi nghiệm thu.

## Mã gốc và phạm vi bảo vệ

- Nhánh phát triển: `feature/camera-ht-language-guides-orientation`.
- Bản gốc iOS: `4bee65fdfa859bd49b72c768b0cc2ddecb958f9d`. Đã đọc trực tiếp Info.plist trong IPA đã ký: `com.datvip.cam`, phiên bản `1.1.0`, Build `162`.
- Android: mã nguồn giống hệt bản build thành công `dfe86b8`, versionCode 7. Chưa xác minh bản đang phân phối qua Google Play.
- Không sửa nhánh ổn định, không đổi Bundle ID/Package ID chính hoặc Product ID cũ, không đổi cấu trúc ảnh/video, mẫu dấu và dữ liệu người dùng.
- Không chạy ký/phát hành/upload App Store Connect, App Store hoặc Google Play.

## Đã thay đổi

1. Hoàn thiện nhãn Việt/Anh còn thiếu trong thư viện, xóa ảnh/video, mẫu dấu và mô tả quyền đọc thư viện trên iOS. Nhãn biểu tượng giữ `Camera HT`; tên đầy đủ trong thông tin ứng dụng theo ngôn ngữ. Giữ cơ chế nhận ngôn ngữ hệ thống và lưu lựa chọn thủ công sẵn có. Thêm kiểm tra danh mục dịch và các tham số trong bản dịch. Không dịch nội dung hay tên mẫu đã lưu.
2. Thêm một lựa chọn `Căn chỉnh khung hình / Composition Guides`, mặc định tắt và được ghi nhớ. Bật đồng thời lưới 3×3 và chỉ báo cân bằng. Chỉ báo xanh khi sai lệch không quá 2°, có lọc dao động; khi điện thoại gần nằm phẳng, giữ hướng giao diện và ẩn chỉ báo không xác định. Lớp này chỉ ở khung ngắm, không đưa vào bộ vẽ ảnh/video.
3. Bố cục camera, thanh công cụ, GPS, nút chụp/quay và dấu xem trước theo cảm biến ở hai hướng ngang và dọc, không dựa vào tín hiệu khóa xoay của hệ thống. Dùng vùng góc ±30° và thời gian giữ ít nhất 250 ms sau lọc để tránh đổi hướng liên tục. Video giữ hướng lúc bắt đầu quay trong suốt tệp; giao diện điều khiển vẫn xoay.
4. Ngắt khởi tạo quảng cáo và mua Remove Ads. Bỏ thư viện quảng cáo/mua hàng khỏi build. Mã và Product ID lịch sử được giữ ngoài phần biên dịch để đối chiếu. APK mới không chứa khai báo quyền BILLING, AD_ID hoặc cấu hình AdMob trong manifest.

## Kiểm thử tự động

Mã iOS mới: `398bef6`, giữ lớp xem trước camera khi xoay, tiếp nối sửa khung ngắm ngang ở `ec1ba2e`; kết quả xác nhận ở các mục cập nhật bên dưới. Các lượt `2f8a9e5` và `d324c9c` là bằng chứng của bản thử trước sửa lỗi này. Android được xác nhận trên `0ef1582` sau xử lý vùng tai thỏ/thanh điều hướng. Lựa chọn chạy riêng ảnh giao diện không được dùng để ký/phát hành.

- Danh mục bản dịch: đạt; mọi khóa nhãn hệ thống được gọi trong mã đều có bản dịch, đúng tham số. Có rà soát thêm các chuỗi giao diện ghi trực tiếp. Điều này chưa thay thế rà soát mọi màn hình trên điện thoại thật.
- Android: 14/14 bài đạt, không bỏ qua: cảm biến 3, ngôn ngữ 2, cài đặt 2, dấu ảnh 7. Kiểm tra lỗi release: 0 lỗi, còn 65 cảnh báo về phiên bản thư viện, hướng cố định, tài nguyên dư và cách viết giao diện. Đã build APK release, APK preview và AAB release. Bản preview có khối chữ ký APK để cài thử.
- iOS bản mới: build đạt; 17/17 bài chức năng và 4/4 bài giao diện đạt trên cùng mã `398bef6`, gồm giữ nguyên UIView xem trước qua sáu lần xoay trong cùng một lần mở app, khung ngắm 4:3 và các nút ở cả hai hướng ngang. Lượt đầu gặp lỗi khởi động bộ kiểm thử giao diện; lượt chạy lại riêng giao diện trên máy mới đạt. Chưa đo được khoảng đen của hình camera thật trên iPhone.
- Bằng chứng Android: https://github.com/datnguyenvip/datvip-cam/actions/runs/43161676741
- Kiểm thử giao diện iOS 4/4: https://github.com/datnguyenvip/datvip-cam/actions/runs/43171936089
- Build và kiểm thử chức năng iOS 17/17: https://github.com/datnguyenvip/datvip-cam/actions/runs/43168960915

## Tệp chính và commit

- `a629183`: thêm cảm biến, bố cục xoay, lưới/cân bằng, bản dịch; ngắt quảng cáo/mua hàng.
- `54c9923`: tách mã mua hàng Android cũ khỏi vùng build, ký bản preview, thêm kiểm tra bố cục iOS.
- `2f8a9e5`: kiểm tra bản dịch tự động, hoàn thiện chiều trục cảm biến từng nền tảng.
- `d324c9c`: xử lý hộp thoại hệ thống trước khi chụp ảnh bằng chứng, thêm lượt chạy riêng ảnh giao diện.
- `fc56f6c`, `0ef1582`: giữ vùng camera Android trong phần màn hình không bị tai thỏ/thanh hệ thống che; chặn ký/phát hành ở lượt chỉ kiểm tra ảnh giao diện.
- iOS: `CameraMotion.swift`, `ContentView.swift`, `CameraModel.swift`, `DatvipCamApp.swift`, `AppLanguage.swift`, `StampPreferences.swift`, `ui-en.json`, `InfoPlist.strings`, `project.yml` và các bài kiểm thử.
- Android: `CameraOrientation.kt`, `CompositionGuidesView.kt`, `MainActivity.kt`, `StampSettingsScreen.kt`, `Language.kt`, danh mục dịch, nhãn biểu tượng, manifest, cấu hình build và các bài kiểm thử.
- Quy trình kiểm thử: `.github/workflows/ios-ipa.yml`, `.github/workflows/build-android.yml`, `scripts/check-localization.py`.

PR bản nháp: https://github.com/datnguyenvip/datvip-cam/pull/3

## Build và cài thử

- Android: dùng Java 21, Gradle 8.13, Android SDK 36 / build-tools 36.0.0. Trong thư mục `android`, chạy `gradle testDebugUnitTest lintRelease assembleRelease assemblePreview bundleRelease`. APK preview được ký phát triển và dùng hậu tố `.kiemthu` đã có trong dự án; có thể cài song song, không đọc dữ liệu ứng dụng chính.
- iOS: trên máy Mac có Xcode 26.3 và XcodeGen, vào `ios`, chạy `xcodegen generate`, rồi mở dự án bằng Xcode để build/ký theo tài khoản của chủ ứng dụng. Có thể chạy workflow `ios-ipa.yml` trên nhánh phát triển, giữ `appstore=false`, `upload=false` để lấy IPA chưa ký cùng kết quả kiểm thử.
- IPA chưa ký không cài trực tiếp được trên iPhone; cần ký bằng hồ sơ hợp lệ hoặc build từ Xcode. Bản thử hiện tại có số build 69 theo bộ đếm workflow, không phải bản phát hành App Store và không ghi đè Build 162. Chưa tạo bản TestFlight.

## Chưa thể xác minh và điều kiện nghiệm thu

Chưa kiểm thử đầy đủ camera, khóa xoay, độ chính xác cảm biến, hướng ảnh/video, micro và chuyển hướng giữa lúc quay trên máy thật. Chưa thử cập nhật đè bằng chữ ký phát hành lên dữ liệu thực. Chưa xác minh metadata hiện tại trên App Store/Google Play. Chưa coi ba nhóm tính năng hoàn thành nghiệm thu.

Làm theo `DEVICE-TESTS.md` trên cả hai điện thoại. Chỉ phát hành sau khi có kết quả và chủ ứng dụng đồng ý.

## Cập nhật iOS: khung ngắm ngang bị thu nhỏ

Phản hồi máy thật cho thấy khung ngắm ngang nhỏ và dư nhiều vùng đen, trong khi ảnh lưu vẫn có tỷ lệ 4:3. Nguyên nhân: bố cục dùng một cột cho cả hai hướng, nên hàng nút và thanh chọn Ảnh/Video chiếm chiều cao của khung ngắm khi cầm ngang.

Commit `ec1ba2e` dùng bố cục hai cột ở hướng ngang: khung ngắm và cụm nút bên cạnh. Khung 4:3 có thể dùng hết chiều cao vùng camera trên iPhone 16 Pro Max thay vì bị trừ chiều cao thanh nút. Hướng dọc giữ bố cục cũ. Camera chỉ khởi động ở vòng đời màn hình, không cấu hình lại chỉ vì đổi bố cục ngang/dọc.

Đã bổ sung kiểm thử kích thước khung ngắm trên cả hai hướng ngang (ngoài kiểm tra các nút không bị khuất), gồm tỷ lệ 4:3 và chiều cao hiển thị. Không sửa bộ lưu ảnh/video, bộ vẽ dấu ảnh hoặc dữ liệu người dùng.

Lượt xác nhận: https://github.com/datnguyenvip/datvip-cam/actions/runs/43164301998

Kết quả: build đạt; 17/17 bài chức năng và 3/3 bài giao diện đạt. Kiểm thử kích thước khung ngắm ở cả hai hướng ngang đạt. Đã xem ảnh mô phỏng và xác nhận khung ngắm dùng hết chiều cao, giữ tỷ lệ 4:3, các nút nằm cạnh ảnh và không bị khuất.

Chưa phát hành và cần chủ ứng dụng thử lại bản sửa trên iPhone thật.

## Cập nhật iOS: khoảng đen lúc đổi hướng

Phản hồi tiếp theo trên iPhone thật: lớp camera đen một lúc khi mới xoay, trong khi lưới và dấu ảnh vẫn hiển thị. Mã trước đó đặt khung camera trong hai nhánh giao diện dọc/ngang khác nhau. Khi đổi nhánh, SwiftUI tháo UIView xem trước cũ và tạo UIView/lớp AVCaptureVideoPreviewLayer mới, phải chờ kết nối hình xem trước trở lại.

Commit `398bef6` chuyển sang một khung `AnyLayout` với khung camera ở cùng một vị trí trong cây giao diện. Chỉ thay trục sắp xếp dọc/ngang và đổi bố cục cụm nút; giữ UIView và lớp camera đang hoạt động. [Tài liệu AnyLayout của Apple](https://developer.apple.com/documentation/swiftui/anylayout) mô tả cơ chế đổi bố cục mà giữ trạng thái các view con.

Bổ sung bài kiểm thử xoay 6 lần trong cùng một lần mở app: dọc → ngang trái → ngang phải → dọc, lặp lại. Bài kiểm thử kiểm tra UIView xem trước giữ cùng mã định danh và nút chụp vẫn thao tác được. Các điều khiển cảm biến giả lập và mã định danh chỉ có trong bản DEBUG dùng kiểm thử, không có trong IPA Release giao cho chủ ứng dụng.

Lượt build và chức năng: https://github.com/datnguyenvip/datvip-cam/actions/runs/43168960915 — build đạt, 17/17 bài chức năng đạt. Bộ kiểm thử giao diện không khởi chạy do lỗi máy mô phỏng “Timed out while loading Accessibility”, trước khi chạy bất kỳ bài giao diện nào.

Lượt chạy lại riêng giao diện trên máy mới, cùng mã nguồn ứng dụng: https://github.com/datnguyenvip/datvip-cam/actions/runs/43171936089

Kết quả: 4/4 bài giao diện đạt, gồm bài giữ cùng UIView camera qua sáu lần xoay liên tiếp và kiểm tra nút chụp vẫn thao tác được. Đã xem trực tiếp ảnh hai bố cục ngang và xác nhận khung ngắm lớn 4:3 được giữ.

Máy mô phỏng không có luồng camera thật nên kiểm thử này chứng minh lớp xem trước được giữ liên tục, chưa đo được thời gian đen trên iPhone. Chủ ứng dụng cần thử lại dọc/ngang trái/phải nhiều lần trên máy thật. Không sửa cơ chế lưu ảnh/video, dấu ảnh, dữ liệu hoặc mã ứng dụng; không phát hành.
