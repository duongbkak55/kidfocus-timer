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

## Kiểm tra thiết bị

- Chờ Pixel 9 và Pixel 3a cùng kết nối. Cần thử thẻ xin quyền báo thức, thông báo lịch trên Pixel; đồng bộ sửa lịch, thêm nhật ký thực tế và xoá mềm offline rồi nối lại, xác nhận cả hai máy hội tụ và mục xoá không hồi sinh.

## Phát hành

- Chưa merge/push/deploy trong lúc chờ kiểm tra thiết bị. Không thêm permission, không đổi Room hoặc Functions.
