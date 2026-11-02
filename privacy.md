# Chính sách quyền riêng tư — DatVip Cam

Cập nhật: 02/11/2026. Áp dụng cho ứng dụng Android DatVip Cam (`com.datvip.cam`), phiên bản 1.0.1 và 1.0.2.

Nhà phát triển: Nguyễn Đắc Đạt. Liên hệ về quyền riêng tư và hỗ trợ: **chobanchotoi@gmail.com**.

## 1. Mục đích và dữ liệu được xử lý

DatVip Cam giúp chụp ảnh, quay video và đóng dấu ngày giờ, vị trí, ghi chú công việc, logo lên nội dung do người dùng tạo. Ứng dụng không yêu cầu tạo tài khoản, không có máy chủ lưu ảnh/video của nhà phát triển và không tích hợp quảng cáo hoặc công cụ phân tích hành vi.

- **Camera, ảnh và video:** được xử lý trên thiết bị để xem trước, chụp, quay, đóng dấu và lưu. Ảnh logo do người dùng chọn được sao chép vào vùng lưu trữ riêng của ứng dụng để dùng lại.
- **Micro:** dùng để ghi âm kèm video khi được cấp quyền. Người dùng có thể chọn quay không tiếng khi từ chối quyền micro.
- **Vị trí chính xác, địa chỉ, độ cao và hướng:** khi được cấp quyền vị trí, ứng dụng nhận vị trí từ dịch vụ định vị Android để hiển thị, đóng dấu và tạo liên kết bản đồ. Cảm biến thiết bị được dùng để tính hướng la bàn. Ứng dụng dừng đăng ký cập nhật vị trí khi màn hình ứng dụng chuyển sang trạng thái tạm dừng; không yêu cầu quyền vị trí nền.
- **Ghi chú, nhiệm vụ, mẫu công việc và thiết lập:** được lưu trên thiết bị để hiển thị lại và đóng dấu theo lựa chọn của người dùng.

## 2. Dịch vụ hệ thống và dữ liệu có thể rời thiết bị

Để tự lấy địa chỉ từ tọa độ, ứng dụng gọi dịch vụ Geocoder của Android. Dịch vụ do thiết bị/hệ điều hành cung cấp có thể gửi tọa độ tới nhà cung cấp dịch vụ địa chỉ qua mạng. Nhà phát triển DatVip Cam không vận hành dịch vụ này và không kiểm soát chính sách lưu giữ của nhà cung cấp. Tắt quyền vị trí sẽ ngăn ứng dụng nhận các cập nhật vị trí mới. Chỉ ẩn dòng tọa độ hoặc địa chỉ trong khung đóng dấu không đồng nghĩa tắt định vị hoặc việc tra cứu địa chỉ.

Khi người dùng chọn mở bản đồ, chia sẻ vị trí, chia sẻ liên kết hoặc ảnh/video, nội dung đã chọn được chuyển tới ứng dụng hoặc người nhận do người dùng chọn. Liên kết bản đồ có thể chứa tọa độ chính xác. Ảnh/video đã đóng dấu có thể thể hiện thời gian, địa chỉ, tọa độ, tên và nội dung công việc. Ảnh cũng có thể lưu tọa độ/liên kết bản đồ trong siêu dữ liệu. Hãy kiểm tra nội dung trước khi chia sẻ. Dữ liệu được chuyển tới ứng dụng khác chịu chính sách của ứng dụng/dịch vụ đó.

Nhà phát triển không tự tải ảnh, video, bản ghi âm hoặc ghi chú lên máy chủ và không bán dữ liệu người dùng. Nếu bạn tự gửi email hỗ trợ, nhà phát triển nhận địa chỉ email và nội dung bạn gửi để trả lời, xử lý yêu cầu; không cần gửi nội dung hiện trường nhạy cảm để báo lỗi.

## 3. Lưu trữ, sao lưu và xóa dữ liệu

Ảnh và video được lưu trong thư viện thiết bị, thường tại `Pictures/DatvipCam` và `Movies/DatvipCam`. Thiết lập, mẫu, logo riêng và tệp xử lý tạm được lưu trong vùng dữ liệu ứng dụng. Dữ liệu này được giữ để phục vụ chức năng cho tới khi người dùng xóa hoặc thay thế. Video đang xử lý có thể được giữ để khôi phục việc xuất video.

Bạn có thể xóa ảnh/video bằng thư viện của ứng dụng hoặc trình quản lý ảnh/tệp của Android; xóa mẫu và thay logo trong cài đặt; xóa dữ liệu ứng dụng hoặc gỡ ứng dụng để xóa dữ liệu riêng. Gỡ ứng dụng không nhất thiết xóa ảnh/video đã lưu trong thư viện chung. Các bản sao đã chia sẻ hoặc được dịch vụ sao lưu thư viện lưu lại cần được xóa riêng tại nơi nhận hoặc dịch vụ đó.

Android và các ứng dụng thư viện có thể sao lưu dữ liệu theo cài đặt thiết bị/tài khoản của bạn. DatVip Cam không cung cấp dịch vụ đồng bộ đám mây riêng. Bạn quản lý việc sao lưu, khôi phục và xóa bản sao qua cài đặt hệ thống/dịch vụ tương ứng.

Nếu đã gửi dữ liệu qua email hỗ trợ, bạn có thể yêu cầu xóa bằng cách liên hệ email ở trên; nhà phát triển xử lý yêu cầu sau khi xác minh người yêu cầu và thông báo nếu có nghĩa vụ pháp luật cần giữ lại dữ liệu. Nhà phát triển không có quyền truy cập từ xa để xóa nội dung chỉ nằm trên thiết bị của bạn.

## 4. Quyền lựa chọn và bảo vệ dữ liệu

Bạn có thể cấp hoặc thu hồi quyền camera, micro và vị trí trong Cài đặt Android. Từ chối quyền có thể làm tính năng tương ứng không hoạt động. Dữ liệu riêng của ứng dụng được bảo vệ bằng cơ chế phân vùng ứng dụng của Android; ảnh/video trong thư viện chung chịu quyền truy cập và cài đặt của thiết bị. Hãy bảo vệ thiết bị và cân nhắc người nhận trước khi chia sẻ dữ liệu có vị trí hoặc nội dung công việc.

## 5. Trẻ em và thay đổi chính sách

DatVip Cam được thiết kế cho người dùng ghi nhận công việc, không hướng tới trẻ em. Chính sách sẽ được cập nhật khi cách xử lý dữ liệu thay đổi; ngày cập nhật được ghi ở đầu trang. Mọi câu hỏi hoặc yêu cầu về quyền riêng tư xin gửi tới **chobanchotoi@gmail.com**.

