# F-FOCUS-1 — Giữ màn hình sáng khi đồng hồ chạy

Branch `fix/focus-keep-screen` từ main `d775692`, worktree riêng. Chỉ sửa UI/DataStore/test, không thêm permission, không sửa Room, không push/merge/deploy.

FocusScreen và BreakScreen đặt `LocalView.current.keepScreenOn` khi timer đang RUNNING và tùy chọn của phụ huynh bật. Compose `DisposableEffect` bỏ cờ khi pause, stop, tắt tùy chọn hoặc rời màn. Tùy chọn **Giữ màn hình sáng khi tập trung** nằm trong Cài đặt phụ huynh, mặc định bật; lưu bằng DataStore, có chuỗi vi/en.

Kiểm cục bộ: Robolectric/Compose test xác nhận cờ bật lúc chạy, tắt khi pause, tắt tùy chọn và rời composition; test DataStore xác nhận mặc định bật và lựa chọn tắt được lưu. `testDebugUnitTest assembleDebug lintDebug` với JDK 17 PASS: **302/302** unit tests, assemble PASS, lint **0 lỗi / 166 cảnh báo**. `git diff --check` PASS.

G3 Pixel 9 qua wireless: Duong bật kết nối, thiết bị mở khóa, `adb install -r` hai lần giữ dữ liệu. Timeout ban đầu **60 giây**, đặt tạm **30 giây**. Màn Focus RUNNING vẫn Awake sau hơn 2 phút; [ảnh](g3/focus-keep-screen/retest-running-after-two-minutes.png) và [power](g3/focus-keep-screen/retest-running-power.txt) ghi **153 giây Awake**. Phiên trong UI là 25 phút (mức tối thiểu cấu hình là 5 phút); phép thử quan sát 2 phút đầu, không thay dữ liệu timer để ép phiên 2 phút.

Lần G3 đầu phát hiện lỗi thật: sau PAUSED, Android vẫn giữ Window `FLAG_KEEP_SCREEN_ON` và `mHoldScreenWindow` trên MainActivity dù helper chỉ bỏ `LocalView.keepScreenOn`; [power trước sửa](g3/focus-keep-screen/paused-power.txt). Sửa thêm clear Window flag trong `DisposableEffect`, test cả View và Window flag. Lần cài APK sau sửa: RUNNING giữ cờ, PAUSED trả `mHoldScreenWindow=null` ngay, [ảnh pause](g3/focus-keep-screen/retest-paused-start.png) và [power sau sửa](g3/focus-keep-screen/retest-paused-power.txt).

Phép kiểm auto-off còn giới hạn thiết bị: Pixel được cắm sạc giữa thử nghiệm, hệ thống bật **Stay awake while charging** (`15`) và screensaver/AOD. Sau khi tắt Stay awake tạm thời, máy tự khóa và vào `DOZE`; app không còn giữ màn hình. [Trạng thái](g3/focus-keep-screen/retest-paused-sleep.txt). **Kết luận G3 về app: đạt** vì giữ sáng khi RUNNING và nhả cả View/Window flag khi PAUSED, cho phép hệ thống tự khóa. Chưa xác nhận display **OFF** vật lý vì AOD của Pixel vẫn hiện; đây là giới hạn của phép đo, không đánh đồng `DOZE` với `OFF`. Không nhập PIN điện thoại hoặc reboot. Crash buffer lọc theo KidFocus không có dòng tương ứng tại thời điểm thu [log](g3/focus-keep-screen/kidfocus-crash-filter.txt). Đã khôi phục chính xác các cài đặt hệ thống: timeout `60000`, Stay awake `15`, screensaver `1`, AOD `1`.

Kiểm bổ sung qua wireless ngày 2026-10-01 khi máy đã khóa: tạm đặt timeout 30 giây, tắt Stay awake, AOD và screensaver, chờ 65 giây; `dumpsys display` báo **`mState=OFF`**, power báo `mHoldingDisplaySuspendBlocker=false` ([bằng chứng](g3/focus-keep-screen/retest-paused-display-off.txt)). Đây là kiểm trạng thái sau lần PAUSED→khóa trước đó; vì máy đã khóa lúc bắt đầu, nó không thay thế phép đo chuyển tiếp trực tiếp từ UI PAUSED sang OFF. Đã xác nhận lại và khôi phục bốn cài đặt gốc (`60000`, `15`, `1`, `1`); không mở khóa, nhập PIN hay reboot.

Working tree gốc có 19 file tracked đang sửa dở, không reset/stash/sửa `.omc`.
