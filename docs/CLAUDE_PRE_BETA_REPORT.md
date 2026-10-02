# Pre-beta — báo cáo kiểm tra

Ngày 2026-10-02. Nhánh `fix/pre-beta` tạo từ `main` tại `e500af0` trong worktree riêng; working tree gốc và `.omc` giữ nguyên.

## Thay đổi

- `aiSchedule` trên app: timeout mạng của callable và timeout coroutine đều 95 giây cho PARSE, LOG, ADVISE. `claimEarlyAccess` giữ giới hạn riêng 30 giây.
- PIN phụ huynh: xoá bốn ô ngay sau mỗi lần xác thực; lần nhập sai không chặn lần nhập tiếp theo, thông báo lỗi hết khi bắt đầu nhập lại.
- Manifest chỉ khai báo `SCHEDULE_EXACT_ALARM`; luồng xin quyền và nhánh `setAndAllowWhileIdle` hiện có vẫn giữ nguyên.
- DataStore: ghi thời điểm cập nhật cho mỗi `focus_extension_<logId>`; khi đọc map, dọn mục quá 60 ngày (kiểm tối đa một lần/ngày). Bản ghi W6 cũ chưa có thời điểm được tính 60 ngày từ lần đầu quan sát vì UUID không mã hoá thời gian; không đổi Room.

## Kiểm tra tự động

- Unit test mới: timeout mạng/coroutine 95 giây; nhập lại PIN sau một PIN sai; dọn gia hạn tập trung cũ, giữ mục mới và làm mới thời điểm khi cộng tiếp. **PASS**.
- `./gradlew testDebugUnitTest assembleDebug lintDebug` với JDK 17 và cấu hình Firebase client công khai tạm thời: **PASS**. APK debug chỉ có `SCHEDULE_EXACT_ALARM`, không có `USE_EXACT_ALARM` (kiểm merged Manifest và AAPT).
- `npm --prefix functions test`: **172/172 PASS**; `npm --prefix functions run lint`: **PASS** (Node 22, cài dependency cục bộ trong worktree).
- Sau merge trên `main`: chạy lại `./gradlew testDebugUnitTest assembleDebug lintDebug` với JDK 17: **PASS**; `npm --prefix functions test`: **172/172 PASS**; `npm --prefix functions run lint`: **PASS**.

## Kiểm tra thiết bị

- Pixel 9 (Android 17) kết nối qua ADB không dây, cài đè APK `fix/pre-beta` bằng `adb install -r` và giữ dữ liệu/phiên đăng nhập. APK dùng cấu hình Firebase client công khai tạm thời trong môi trường build, không ghi vào repo.
- Khi tắt special access `SCHEDULE_EXACT_ALARM`, Lịch ngày hiện đúng thẻ “For on-time reminders, allow Alarms & reminders” và nút “Open settings”. Nút mở đúng trang **Alarms and reminders** của KidFocus; bật quyền trên trang đó rồi quay về, thẻ biến mất. `dumpsys alarm` cho task thử cho thấy `window=0 exactAllowReason=permission`, chứng tỏ app đã lên lịch lại sau cấp quyền. Nhánh fallback `setAndAllowWhileIdle` khi không có quyền đã qua `AlarmSchedulerTest` (mock AlarmManager); không chờ thông báo trễ trên máy ở trạng thái bị từ chối.
- Tạo hoạt động synthetic `BetaAlarmTest` lúc 20:50 ngày 2026-10-02; alarm thực tế trong `dumpsys alarm` đặt đúng 20:50, thông báo trên Pixel ghi “Time to start BetaAlarmTest!” lúc khoảng 20:50:09. Không dùng nội dung lịch/nhật ký thật của trẻ.
- Sau khi Pixel 9 được mở khoá, thử một PIN sai rồi nhập ngay bốn số đúng **không bấm xoá**: vào được màn phụ huynh, xác nhận ô nhập tự xoá sau lỗi. PIN chỉ đối chiếu hash cục bộ của app debug, không in/lưu plaintext. Đã xoá `BetaAlarmTest` khỏi lịch và xoá mềm đúng log timer ngắn phát sinh khi chạm nhầm nút bắt đầu; timeline không còn mục thử, alarm lần sau cũng không còn trong `dumpsys alarm`.

## Known issues

- Sync 2 thiết bị chưa test thực tế, chỉ có unit test (`DayLogSyncTest`). Theo quyết định của Duong, bỏ bài G3 đồng bộ Pixel 9 ↔ Pixel 3a trong đợt pre-beta này; hành vi sửa lịch, thêm nhật ký và xoá mục offline→online trên hai máy còn cần kiểm chứng sau.

## Phát hành

- Duong đã duyệt merge/push `fix/pre-beta` sau khi Claude xác nhận G3 Pixel 9 PASS. Không deploy; không thêm permission, không đổi Room hoặc Functions.
- Đã merge `fix/pre-beta` (`a82aad0`) vào `main` bằng `--no-ff`; merge SHA: `f5806f98638f225b404200b9ab278bc33eab168d`.
