# F-FOCUS-1 — Giữ màn hình sáng khi đồng hồ chạy

Branch `fix/focus-keep-screen` từ main `d775692`, worktree riêng. Chỉ sửa UI/DataStore/test, không thêm permission, không sửa Room, không push/merge/deploy.

FocusScreen và BreakScreen đặt `LocalView.current.keepScreenOn` khi timer đang RUNNING và tùy chọn của phụ huynh bật. Compose `DisposableEffect` bỏ cờ khi pause, stop, tắt tùy chọn hoặc rời màn. Tùy chọn **Giữ màn hình sáng khi tập trung** nằm trong Cài đặt phụ huynh, mặc định bật; lưu bằng DataStore, có chuỗi vi/en.

Kiểm cục bộ: Robolectric/Compose test xác nhận cờ bật lúc chạy, tắt khi pause, tắt tùy chọn và rời composition; test DataStore xác nhận mặc định bật và lựa chọn tắt được lưu. `testDebugUnitTest assembleDebug lintDebug` với JDK 17 PASS: **302/302** unit tests, assemble PASS, lint **0 lỗi / 166 cảnh báo**. `git diff --check` PASS.

G3 Pixel qua wireless: **chưa chạy**. ADB tìm thấy thiết bị qua mạng nhưng trả `unauthorized` khi kết nối; hiện không có transport đã được thiết bị cho phép. Đã dừng trước cài APK hoặc đổi thời gian tự tắt màn hình, nhờ Duong chấp nhận yêu cầu debugging trên Pixel hoặc cung cấp thông tin pairing. Không nhập PIN điện thoại, không reboot. Khi được kết nối, cần lưu timeout hiện tại, đặt 30 giây, cài APK của branch này bằng `adb install -r`, chạy Focus và Break đủ lâu để kiểm màn hình không tắt, pause 1 phút để kiểm màn hình tắt bình thường, chụp bằng chứng/logcat, rồi khôi phục timeout cũ.

Working tree gốc có 19 file tracked đang sửa dở, không reset/stash/sửa `.omc`.
