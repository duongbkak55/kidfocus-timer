# Play prep — chờ G2

Ngày 2026-10-03. Branch `play-prep`, worktree `KidFocusTimer-play-prep`, base `main` `032c9a5`. Commit nội dung `cb78b35`. Chưa push, merge, deploy Hosting hoặc sửa Play Console. Nội dung mô tả W5–W7 để dùng sau khi bản có W7 được phát hành; W7 hiện chưa nằm trong `main` của branch này và G3 màn khóa còn chờ Duong.

## File và nội dung

- `docs/privacy-policy.html`: ngày mới, email `kidfocusstudio@gmail.com`, anchor `#xoa-du-lieu`; mục 3a giới hạn Nhập nhanh **lịch tuần** sau PIN, ghi rõ nhập ảnh đang tắt ở beta; mục 3c VI/EN mô tả Nhật ký hôm nay và Con tự kể, giọng nói qua dịch vụ nhận dạng, lối vào màn khóa do phụ huynh bật/tắt, dữ liệu gửi AI và dữ liệu được lưu/đồng bộ. Mục 4 VI và đoạn EN nêu ngoại lệ Con tự kể khỏi PIN. Mục 1–2/đoạn EN cũng thêm nhật ký và so sánh kế hoạch–thực tế. Phần xóa dữ liệu và liên hệ EN được làm rõ.
- `docs/index.html`, `docs/terms.html`: thay email cũ còn xuất hiện trong tài liệu công khai. Kiểm `rg` trên các trang public `docs/index.html`, `docs/privacy-policy.html`, `docs/terms.html`, `site/` và `store-listing/`: **không còn email cũ**. Task gốc giữ tên email cũ làm chỉ dẫn nội bộ.
- `firebase.json`: Hosting phục vụ `site/`, `cleanUrls: true`, header `Cache-Control: public, max-age=300`. `scripts/build-privacy-site.sh` sao chép chính sách sang `site/privacy.html`; `site/index.html` chuyển tới `/privacy`. URL kỳ vọng: `https://kid-focus-app.web.app/privacy`; URL xóa dữ liệu: `https://kid-focus-app.web.app/privacy#xoa-du-lieu`.
- `store-listing/vi-VN`, `en-US`: giữ title; short/full description thêm lịch tuần, nhật ký thực tế, Con tự kể và thời gian tập trung linh hoạt. Không quảng cáo nhập ảnh đang tắt. Câu giữ màn hình sáng ghi rõ phụ huynh có thể tắt cài đặt này; lối vào màn khóa được ghi “trên thiết bị hỗ trợ” vì G3 thiết bị còn chờ.
- `store-listing/release-notes/beta-1.vi.txt`, `.en.txt`: release notes hai ngôn ngữ.
- `docs/launch/TASK_PLAY_PREP.md` và `DATA_SAFETY.md`: đưa task gốc vào branch để Claude review được; cập nhật bảng Data safety theo 3 kết quả kiểm tra và email/URL liên hệ, đồng thời đánh dấu URL Hosting chỉ được dùng sau khi deploy.

| Tệp | Số ký tự | Giới hạn |
|---|---:|---:|
| VI short | 76 | 80 |
| EN short | 74 | 80 |
| VI full | 2.226 | 4.000 |
| EN full | 2.306 | 4.000 |
| VI release notes | 368 | 500 |
| EN release notes | 431 | 500 |

## Khai báo Play soạn sẵn

**Foreground service — VI:** Đồng hồ tập trung và giải lao do người dùng bắt đầu cần tiếp tục đếm giờ và hiển thị thông báo khi trẻ rời ứng dụng. Người dùng có thể dừng phiên; dịch vụ dừng khi phiên kết thúc. Subtype `specialUse` là `timer`. Nếu Play yêu cầu video, dùng V3 trong `SCREENSHOT_TASK.md` sau khi video đã được quay và kiểm tra.

**Foreground service — EN:** A user-started focus or break timer must keep counting and show an ongoing notification when the child leaves the app. The user can stop it, and the foreground service ends when the session finishes. The `specialUse` subtype is `timer`.

Manifest chỉ khai `SCHEDULE_EXACT_ALARM`, không có `USE_EXACT_ALARM`. `ExactAlarmPermission` mở luồng xin quyền; `AlarmScheduler` và `RoutineAlarmScheduler` dùng `setAndAllowWhileIdle` khi không có quyền exact alarm. Không thay đổi permission.

## Xác minh Data safety (3 mục Claude giao)

1. **SDK release:** `releaseRuntimeClasspath` có Firebase Auth 22.3.1, Firestore 24.11.1, Functions 20.4.0, App Check Play Integrity 17.1.2, Firebase Installations 17.2.0, RevenueCat Purchases 10.16.1, Play Services Auth 21.2.0 và AndroidX Credentials 1.3.0. Không thấy Firebase Analytics, Crashlytics, Performance hoặc Sessions trong cấu hình đã giải quyết. Firebase Auth/Functions xử lý IP và metadata; App Check/Play Integrity xử lý bằng chứng toàn vẹn thiết bị; Installations thu Firebase installation ID. **Khai “Giá trị nhận dạng thiết bị hoặc giá trị nhận dạng khác: Có”** cho chức năng ứng dụng và chống gian lận/bảo mật, vì [Google Play nêu rõ Firebase installation ID thuộc loại này](https://support.google.com/googleplay/android-developer/answer/10787469). Không thấy SDK tự gửi crash log/chẩn đoán ứng dụng; RevenueCat cũng [ghi không thu crash log/diagnostics](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety). Firebase có [bảng công bố dữ liệu cho từng SDK](https://firebase.google.com/docs/android/play-data-disclosure); [Play Integrity nêu attestation token và thông tin thiết bị](https://developer.android.com/google/play/integrity/terms). Đây là kiểm dependency và mã nguồn, chưa kiểm một AAB W7 đã ký vì W7 chưa merge.
2. **Firestore và AI:** `CloudSyncManager` lưu phiên, lịch, routine, hồ sơ và cài đặt vào `users/{uid}/backups/current`; `DayLogSyncManager` lưu mục nhật ký vào `users/{uid}/dayLogs`. Không thấy đường ghi câu nhập LOG/PARSE thô, bản nháp chưa áp dụng hay ảnh vào Firestore; Functions dùng `internalAiQuota`/`entitlements` cho hạn mức/quyền lợi. Câu nhập và ảnh (nếu bật) đi qua callable tới provider để xử lý tạm thời. Cần reviewer xem riêng việc `functions/index.js` ghi **thông điệp lỗi provider** khi request thất bại: mã nguồn không chủ đích log câu nhập, nhưng không thể chứng minh provider sẽ không lặp lại một phần nội dung trong lỗi.
3. **Day logs/Con tự kể:** W7 `DayLogCloud.toDayLogCloud()` đồng bộ ngày, tên, loại, giờ và `source=CHILD` trong subcollection `dayLogs`. Do đó khai **Hoạt động trong ứng dụng → Nội dung khác do người dùng tạo: Có**; dữ liệu được lưu khi đăng nhập, phụ huynh có thể sửa/xóa. Không khai bản ghi âm do app dùng RecognizerIntent và không xin `RECORD_AUDIO`.

**Điểm cần Duong đối chiếu khi điền Play:** bảng `docs/launch/DATA_SAFETY.md` đã sửa “Lịch sử mua hàng” và “Ảnh” theo hành vi beta. [Hướng dẫn RevenueCat](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety) yêu cầu khai lịch sử mua hàng, không xử lý tạm thời, và xem là bắt buộc khi SDK được dùng; Duong cần đối chiếu trạng thái mua và mục đích Analytics trong Play Console. Ảnh nhập lịch đang bị `ai_schedule_image_tiers=none` chặn ở server, nên khai theo **hành vi thực tế của bản phát hành**, không chỉ vì mã nguồn có khả năng ảnh.

## Kiểm tra và deploy chờ duyệt

- `bash scripts/build-privacy-site.sh` và `cmp docs/privacy-policy.html site/privacy.html`: PASS; `firebase.json` parse JSON: PASS.
- Mở trang `site/privacy.html` trên máy bằng trình duyệt: render VI/EN đúng, thấy mục 3c, liên hệ mới và anchor xóa dữ liệu; không thay đổi giao diện app.
- Đếm ký tự bằng Python: cả 6 tệp listing/release notes trong giới hạn; `git diff --check`: PASS.
- Chờ G2 của Claude và duyệt của Duong. Lệnh deploy riêng Hosting khi được duyệt: `firebase deploy --only hosting --project kid-focus-app`. Chưa chạy lệnh này, chưa đụng Functions/rules/Remote Config.
