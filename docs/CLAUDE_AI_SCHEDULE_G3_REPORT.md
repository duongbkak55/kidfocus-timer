# Báo cáo G3 — Máy thật: bị chặn ở USB authorization

Ngày: 2026-09-28 · Người chạy: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

## Kết luận

**G3 chưa thực hiện được; không kết luận PASS/FAIL ứng dụng.** Dừng tại bước chuẩn bị vì adb báo `device still authorizing` sau khi kết nối ban đầu hiện `device`. Task G3 yêu cầu dừng nếu cần xác nhận USB/thao tác tay; không thử tiếp sau khi đọc kết quả này. Duong cần kiểm tra điện thoại và bấm **Allow USB debugging** nếu hộp thoại đang hiện, rồi cho chạy tiếp G3.

Đã đọc `CLAUDE_AI_SCHEDULE_TASK_G3.md` và review W4 G2 PASS. Làm trên `feature/smart-schedule-w4`, HEAD code `ce0220c`; không sửa code, không đăng nhập Google, không mua, không gọi OpenRouter/Firebase production, không push/merge/deploy hoặc reboot. Không xoá dữ liệu, không đổi mạng/font/xoay màn trên điện thoại.

## Chuẩn bị và thiết bị

| Mục | Kết quả |
|---|---|
| Thiết bị từ `adb devices -l` đầu phiên | Pixel 9, product/device `tokay`, serial `4A150DLAQ0013S`; trạng thái ban đầu `device` |
| Model qua getprop | Không lấy được: adb trả `device ... not found`; không suy thêm từ thông số máy |
| Android version / API | **Chưa xác minh** — hai lệnh getprop cũng trả device not found |
| Đọc trạng thái màn hình | adb báo **device still authorizing**; chưa quan sát trực tiếp hộp thoại USB hoặc màn hình khoá |
| Package debug | Batch đọc chuẩn bị đã được gửi trả về `com.kidfocusstudio.timer.debug` tồn tại; không cài/ghi đè/xoá package |
| Build APK | **PASS** — `assembleDebug`, JDK 17, BUILD SUCCESSFUL trong 10 giây, 44 task (1 executed/43 up-to-date) |
| Cài APK | **SKIP** — chưa gọi `adb install -r` do dừng ở USB authorization |
| Ảnh / UI dump | **SKIP** — không có ảnh PNG hoặc uiautomator dump trong lần thử này |
| Logcat crash / ANR | **SKIP** — chưa clear/thu logcat; không thể kết luận không có crash |

Lệnh build đã được khởi chạy song song với batch chuẩn bị đọc thiết bị, trước khi nhận lỗi authorization. Sau khi dừng thao tác điện thoại chỉ đợi kết quả build cục bộ và viết tài liệu. Không gửi thêm lệnh adb, kể cả lệnh đọc/logcat.

APK: `app/build/outputs/apk/debug/app-debug.apk` (chưa cài trong lần này). Build trong shared working tree trên branch w4, vẫn có các thay đổi release/Learning intentional từ trước; không sửa/chọn thêm code trong task G3.

SHA-256 APK: `daaf839817ac972eb6a357f9cd385dbcf6dc7743a3ee62ea566b61391ab3a01f`.

Bằng chứng:

- [Kết quả adb và lý do dừng](g3/00-adb-authorization.txt).
- [Log build debug và checksum](g3/01-build-debug.txt).

## Kịch bản A–I

| Kịch bản | Trạng thái | Ảnh / ghi chú |
|---|---|---|
| A — Khởi động, onboarding, PIN thử 2468, khu phụ huynh | **SKIP** | Chưa mở app/tạo PIN; bị chặn ở chuẩn bị USB |
| B — Lớp 1, wake 06:15 / bed 22:30 T2–T6, SLEEP_SHORT Cao | **SKIP** | Chưa thao tác; chưa có ảnh |
| C — Apply bed 21:15, snackbar Undo và Khôi phục lịch trước | **SKIP** | Chưa thao tác; chưa có ảnh |
| D — Trường 07:00–16:30, bài tập 21:00/45 phút, timeline và bed 00:30 | **SKIP** | Chưa thao tác; chưa có ảnh |
| E — Task sau 3 phút, alarm URI và thông báo đúng giờ | **SKIP** | Chưa tạo task/đọc alarm/quan sát thông báo |
| F — Hai hồ sơ, dữ liệu tách riêng | **SKIP** | Chưa tạo/chuyển hồ sơ |
| G — Nhập nhanh/Ảnh/Nhờ AI sắp lại ẩn khi chưa config | **SKIP** | Chưa mở màn hình; không gọi production |
| H — Font 1.3, Góp ý/Timeline, xoay ngang rồi font 1.0 | **SKIP** | Không đổi font hoặc rotation; không cần khôi phục cài đặt |
| I — Force-stop + mở lại giữ dữ liệu (thay reboot) | **SKIP** | Chưa force-stop/mở app; không reboot |

## Lỗi và việc cần thao tác tay

**Chặn môi trường, chưa quy mức lỗi ứng dụng:** kết nối USB/authorization không ổn định. Tái hiện trong lần này: adb devices ban đầu liệt kê máy ở trạng thái device → getprop trả không tìm thấy thiết bị → dumpsys window báo vẫn đang authorizing. Một lệnh đọc package trong cùng batch có kết quả, nhưng không dùng kết quả đó để bỏ qua yêu cầu dừng của task.

Duong cần kiểm tra/cấp xác nhận USB debugging trên điện thoại. Nếu không có hộp thoại, kiểm tra trạng thái kết nối cáp/USB debugging bằng tay trước khi cho chạy tiếp. Không suy rằng Duong đã xác nhận từ việc máy có lúc trả lời adb.

Chưa tìm thấy lỗi app vì **chưa chạy A–I**. Không có crash logcat thực tế để trích dẫn. Không dùng kết quả unit/G2 thay G3, không tạo ảnh/log giả.

## Phạm vi commit

Chỉ `docs/g3/00-adb-authorization.txt`, `docs/g3/01-build-debug.txt` và báo cáo này. Không commit task/review có sẵn, APK, code hoặc `.omc`. Giữ nguyên 19 đường dẫn tracked intentional từ trước; không reset/stash.
