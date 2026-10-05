# KidFocus — Trả lời mẫu "An toàn dữ liệu" (Play Console)

Cập nhật: 2026-10-03 · Soạn: Claude · Điền: Duong · Xác minh kỹ thuật: ChatGPT (mục cuối)
Áp dụng cho bản có W7 (con tự kể + màn khoá). Định nghĩa của Google: gửi dữ liệu cho **nhà cung cấp dịch vụ xử lý thay mặt bạn**
(Firebase, OpenRouter/nhà cung cấp model, RevenueCat) **không tính là "chia sẻ"**; vẫn phải khai là "thu thập".

## Câu hỏi chung
| Câu hỏi | Trả lời |
|---------|---------|
| Ứng dụng có thu thập hoặc chia sẻ loại dữ liệu bắt buộc nào không? | Có |
| Toàn bộ dữ liệu người dùng được mã hoá khi truyền? | Có |
| Người dùng có thể yêu cầu xoá dữ liệu? | Có — trong app (Cài đặt phụ huynh → Tài khoản & dữ liệu → Xoá tài khoản) hoặc email kidfocusstudio@gmail.com |
| URL xoá dữ liệu (nếu được hỏi) | https://kidfocus-studio.web.app/privacy#xoa-du-lieu (chỉ dùng sau khi Hosting riêng được duyệt và deploy) |

## Loại dữ liệu
| Nhóm → Loại | Thu thập | Chia sẻ | Tạm thời (ephemeral) | Bắt buộc/Tuỳ chọn | Mục đích |
|-------------|----------|---------|----------------------|-------------------|----------|
| Thông tin cá nhân → Tên | Có (tên hiển thị Google khi đăng nhập) | Không | Không | Tuỳ chọn | Chức năng ứng dụng, Quản lý tài khoản |
| Thông tin cá nhân → Địa chỉ email | Có | Không | Không | Tuỳ chọn | Chức năng ứng dụng, Quản lý tài khoản |
| Thông tin cá nhân → Mã người dùng | Có (Firebase UID, mã RevenueCat) | Không | Không | Tuỳ chọn | Chức năng ứng dụng, Quản lý tài khoản, Ngăn gian lận/bảo mật (hạn mức AI) |
| Thông tin tài chính → Lịch sử mua hàng | Có (trạng thái gói qua Google Play/RevenueCat) | Không | Không | Bắt buộc khi dùng RevenueCat; Duong đối chiếu Play Console | Chức năng ứng dụng; đối chiếu mục đích Analytics theo hướng dẫn RevenueCat |
| Ảnh và video → Ảnh | Không trong beta khi `ai_schedule_image_tiers=none`; khai lại nếu bật tính năng | — | — | — | — |
| Hoạt động trong ứng dụng → Nội dung khác do người dùng tạo | Có (lịch, tên hoạt động, nhật ký ngày, câu nhập AI, câu hỏi AI, ghi chú) | Không | Không (lịch/nhật ký được lưu khi đồng bộ; phần gửi AI là tạm thời) | Tuỳ chọn | Chức năng ứng dụng |
| Hoạt động trong ứng dụng → Lượt tương tác với ứng dụng | Có (phiên tập trung, kết quả học, đánh dấu hoàn thành — chỉ khi đồng bộ) | Không | Không | Tuỳ chọn | Chức năng ứng dụng |
| Âm thanh → Bản ghi giọng nói | **Không** (bộ nhận giọng hệ thống chuyển thành chữ; app không thu âm) | — | — | — | — |
| Vị trí, Danh bạ, Lịch, Tin nhắn, Sức khoẻ, Duyệt web | Không | — | — | — | — |
| Thông tin và hiệu suất ứng dụng (nhật ký sự cố, chẩn đoán) | Không thấy SDK tự gửi crash log/diagnostics trong dependency release đã giải quyết | — | — | — | — |
| Giá trị nhận dạng thiết bị hoặc giá trị nhận dạng khác | Có (Firebase installation ID; App Check/Play Integrity) | Không | Không | Theo Firebase/App Check | Chức năng ứng dụng, Ngăn gian lận/bảo mật |

Các hàng khác ghi "Tuỳ chọn" vì app dùng được offline, không đăng nhập; AI/đồng bộ chỉ khi phụ huynh đăng nhập. Riêng lịch sử mua hàng theo hướng dẫn RevenueCat cần đối chiếu lựa chọn Bắt buộc/Tùy chọn trong Play Console trước khi nộp.

## Xác minh kỹ thuật trước khi Duong điền
1. Đã kiểm `releaseRuntimeClasspath` trên branch `play-prep`: Firebase Auth/Firestore/Functions/App Check Play Integrity/Installations, RevenueCat 10.16.1, play-services-auth và credentials; không thấy Firebase Analytics, Crashlytics, Performance hoặc Sessions. [Firebase công bố FID do Installations thu](https://firebase.google.com/docs/android/play-data-disclosure), và [Google Play xếp FID vào Device or other IDs](https://support.google.com/googleplay/android-developer/answer/10787469). [RevenueCat](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety) ghi không thu crash log/diagnostics, nhưng yêu cầu khai Purchase history. Đây là kiểm dependency/mã nguồn, chưa kiểm AAB W7 đã ký vì W7 chưa merge.
2. `CloudSyncManager` chỉ ghi backup phiên/lịch/routine/hồ sơ/cài đặt; `DayLogSyncManager` chỉ ghi các mục dayLogs. Không thấy ghi câu nhập AI thô, bản nháp chưa áp dụng hoặc ảnh vào Firestore. Phần này là kiểm mã nguồn, không thay thế kiểm tra cấu hình production; xem rủi ro thông điệp lỗi provider trong `TASK_PLAY_PREP_REPORT.md`.
3. W7 `DayLogCloud.toDayLogCloud()` ghi `source=CHILD` cùng ngày, tên, loại và giờ vào subcollection `users/{uid}/dayLogs`. Khai nhóm "Hoạt động trong ứng dụng → Nội dung khác do người dùng tạo". Không khai bản ghi âm từ KidFocus vì app chỉ nhận chữ từ RecognizerIntent.
