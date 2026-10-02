# W7 — báo cáo tiến độ

Ngày 2026-10-03. Branch `w7-child-entry`, worktree `KidFocusTimer-w7`, base `main` `032c9a5`. Chỉ Phần 0 F-SIGNIN-1 được triển khai; chờ Claude review G2 trước Phần 1–2. Không push, merge hay deploy.

Commit Phần 0: `224385102cf28e6fece009df04b47ae43bf897c5` — sửa phân biệt App Check/sign-in, refresh và retry cùng requestId, chuỗi VI/EN và unit test.

## Phần 0 — F-SIGNIN-1

### Điều tra

- Pixel 9 đang cài `com.kidfocusstudio.timer.debug` bản `1.0.0-rc.2`, `DEBUGGABLE`, `installerPackageName=null`; lần cập nhật gần nhất 2026-10-02 20:36:50 giờ Việt Nam. Không thấy package release trên máy. Đây là build debug cài trực tiếp, không phải bản từ Google Play.
- Đọc `firebase functions:log --only aiSchedule --project kid-focus-app` trong phạm vi log còn giữ được. Các request ngày 2026-10-02 lúc 16:10:20, 16:22:48 và 16:23:23 UTC có metadata `auth=VALID`, `app=MISSING`; một request lúc 12:28:17 UTC có `auth=VALID`, `app=VALID`. Chỉ ghi các trạng thái xác minh, không lưu log thô, token, ID tài khoản hay nội dung nhập.
- `functions/.env.kid-focus-app` đặt `ENFORCE_APP_CHECK=true`. Firebase Functions SDK tại `common/providers/https.js` từ chối callable bằng `unauthenticated` khi App Check là `MISSING` và enforcement bật, kể cả khi Auth là `VALID`. Server chỉ phát `SIGN_IN_REQUIRED` nếu không có UID trong handler; các request trên bị chặn trước handler. Vì vậy **xác nhận có request bị App Check từ chối**, còn `SIGN_IN_REQUIRED` không phù hợp với các request có `auth=VALID` này. Log không có requestId/timestamp của ảnh lỗi để ghép một-một với lần bấm của Duong; kết luận về chính lần bấm đó là rất có khả năng, chưa chứng minh tuyệt đối.
- `getAiConfig` không có cùng App Check enforcement nên trial/quota vẫn tải được, dù `aiSchedule` bị chặn. Debug build dùng `DebugAppCheckProviderFactory`; token debug chưa được đăng ký hoặc chưa gửi hợp lệ là khả năng cần Duong kiểm tra thủ công.

### Bản sửa

- `ScheduleAiApi` gửi PARSE text/ảnh, LOG và ADVISE qua một hàm chung. Với `UNAUTHENTICATED` khi tài khoản đã đăng nhập và không có `SIGN_IN_REQUIRED`, app gọi `getIdToken(true)` và `getAppCheckToken(true)`, sau đó thử lại tối đa một lần. Cả hai lần gửi cùng payload và cùng `requestId`; không thêm credit hay thay đổi server/enforcement. Nếu refresh/thử lại vẫn lỗi, trả `APP_VERIFY`.
- `AiErrorMapper` phân biệt `SIGN_IN_REQUIRED` hoặc tài khoản đã đăng xuất với lỗi xác minh ứng dụng. Quick Schedule, Quick Day Log và Smart Schedule/Advice dùng cùng mapper. Chuỗi `APP_VERIFY` đã thêm cho tiếng Việt và Anh.
- Unit test kiểm các nhánh mapper, retry đúng một lần với cùng requestId, không retry khi cần đăng nhập, lỗi refresh/retry, và ba ViewModel hiển thị `APP_VERIFY`.

### Kiểm tra và việc còn lại

- `./gradlew testDebugUnitTest lint assembleDebug assembleDebugAndroidTest`: **PASS** trên JDK 17. Không chạy test production/OpenRouter hoặc UI tự động trên máy thật.
- `git diff --check`: **PASS**.
- G2: chờ Claude review commit Phần 0. Phần 1–2 và G3 chưa bắt đầu.

### Hướng dẫn debug token cho Duong

Nếu dùng bản debug cài qua ADB cho G3: mở app trên Pixel 9 khi có mạng, tìm debug App Check secret trong logcat **trên máy của Duong**, rồi vào Firebase Console → App Check → chọn đúng Firebase Android app mà bản debug khởi tạo trong `kid-focus-app` → Manage debug tokens và thêm token đó. Không dán token vào chat, repo, báo cáo hoặc ảnh chụp. Chạy lại app sau khi đăng ký và thử PARSE; bản release cho thử nghiệm nên cài qua Google Play để Play Integrity cấp App Check token.

## Phần 1–2

Chưa triển khai theo thứ tự G2 đã giao. Quick Settings tile và widget màn khoá đều nằm trong phạm vi Phần 2 sau khi Phần 0 được duyệt.
