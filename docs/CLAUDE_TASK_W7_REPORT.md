# W7 — báo cáo tiến độ

Ngày 2026-10-03. Branch `w7-child-entry`, worktree `KidFocusTimer-w7`, base `main` `032c9a5`. Sau commit Phần 0, Duong yêu cầu làm tiếp Phần 1–2 rồi mới test. Không push, merge hay deploy.

Commit Phần 0: `224385102cf28e6fece009df04b47ae43bf897c5` — sửa phân biệt App Check/sign-in, refresh và retry cùng requestId, chuỗi VI/EN và unit test. Commit `78491d5541160135bbb8ee81e73dd9467f864026` — thêm task spec và báo cáo. Phần 1: `b9478ab` — child quick log. Phần 2: `912fe90` — tile, widget và activity riêng. Theo phản hồi của Duong, `56491d1` — bật lại mic ở màn khoá bằng Google RecognizerIntent.

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
- G2 Phần 0: Duong cho triển khai tiếp Phần 1–2 trước khi có review riêng. G2 toàn W7 vẫn chờ Claude.

### Hướng dẫn debug token cho Duong

Nếu dùng bản debug cài qua ADB cho G3: mở app trên Pixel 9 khi có mạng, tìm debug App Check secret trong logcat **trên máy của Duong**, rồi vào Firebase Console → App Check → chọn đúng Firebase Android app mà bản debug khởi tạo trong `kid-focus-app` → Manage debug tokens và thêm token đó. Không dán token vào chat, repo, báo cáo hoặc ảnh chụp. Chạy lại app sau khi đăng ký và thử PARSE; bản release cho thử nghiệm nên cài qua Google Play để Play Integrity cấp App Check token.

## Phần 1 — Con kể việc vừa làm

- Nút lớn trên Daily Schedule của con mở route ChildQuickLog không qua PIN; không đổi layout lịch ngày cũ. Màn có ô ≤300 ký tự, RecognizerIntent, Gửi, thẻ kết quả, câu hỏi làm rõ và Hoàn tác 10 giây.
- Gọi LOG hiện có với cùng quota tài khoản, không đổi Functions. Chỉ lưu mục confidence ≥0,6, giờ không ở tương lai và planRef khớp request. Mục bị loại hiện “Chưa hiểu” và gợi ý thêm giờ. Không có profile active, chưa đăng nhập, AI tắt hoặc App Check lỗi thì nhờ bố mẹ; hết quota có thông báo riêng cho con.
- Thêm nguồn `CHILD` vào Room string/Firestore mà không đổi schema; nguồn lạ fallback `MANUAL` khi đọc cả Room và Cloud. Màn DayLogs và So sánh có nhãn “Con nhập”; bố mẹ sửa/xoá như mục thường, sửa vẫn giữ nhãn nguồn.

## Phần 2 — Lối vào khi khoá máy

- Quick Settings tile và widget 2×1 (home/keyguard) cùng mở `LockScreenLogActivity`. Activity không export, riêng task affinity, excludeFromRecents, showWhenLocked; không có NavHost/route vào phần còn lại. Widget và tile không hiện dữ liệu cá nhân.
- Màn khoá dùng duy nhất profile active, chỉ avatar, ô nhập và kết quả phiên hiện tại. Không có lịch, lịch sử, tên đầy đủ, email hoặc số credit. Cài đặt sau PIN mặc định bật; tắt thì tile unavailable, widget báo “Bố mẹ đã tắt”, màn khoá không cho nhập. Không thêm `<uses-permission>`, service nền hay schema Room.
- **Điều chỉnh sau phản hồi của Duong:** Trẻ cần nói được trên màn khóa. Bỏ `noHistory` vì [Android không trả ActivityResult](https://developer.android.com/guide/topics/manifest/activity-element) cho activity có cờ đó, còn [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent) trả chữ qua ActivityResult. Mic hiện trên cả route con và màn khóa; kết quả nhận dạng được điền vào ô nhập, trẻ bấm Gửi để ghi LOG. Activity vẫn không export, không vào recents, riêng task, `FLAG_SECURE`, đóng khi Back/lưu/rời màn (trừ lúc đang chờ ứng dụng nhận giọng nói trả kết quả), không lưu nháp ra disk. Không thêm `RECORD_AUDIO` hoặc permission khác. Cần G3 kiểm tra Google recognizer có hoạt động khi Pixel 9 còn khóa hay yêu cầu mở khóa.

## Kiểm tra và G3

- `./gradlew testDebugUnitTest lint assembleDebug assembleDebugAndroidTest`: **PASS** (330 unit tests). Sau sửa mic: `./gradlew testDebugUnitTest lint assembleDebug`: **PASS**. Có unit test nguồn lạ Room/Cloud, CHILD round-trip, chọn/lọc mục, Hoàn tác, profile/account đổi, giới hạn ký tự, activity isolation và cờ cho phép nhận ActivityResult. `git diff --check`: **PASS**.
- APK debug được build với Firebase Android config công khai trong `local.properties` bị git ignore, không đưa config/secret vào commit hoặc báo cáo. Cài/cập nhật `adb install -r` lên Pixel 9 qua wireless sau sửa mic: **Success**, giữ dữ liệu. Chưa xác nhận debug App Check token đã được đăng ký; Duong cần làm theo hướng dẫn ở trên nếu AI bị từ chối.
- **G3 chưa chạy:** theo task, Duong tự thao tác UI, không chạy tự động. Checklist tại `docs/W7_G3_CHECKLIST.md`. Widget màn khoá phụ thuộc Pixel 9/OS có cho thêm hay không; cần ghi thực tế. Samsung không có máy để thử shortcut.
- Tình huống câu “đi ngủ lúc 11 giờ 10 tối”: trước 23:10 hôm nay, mục bị coi là tương lai và không lưu; checklist hướng dẫn thử sau 23:10 hoặc nói “tối qua”.

Không gọi OpenRouter/production trong test tự động; không đăng nhập/mua hàng, không reboot Pixel. Worktree gốc với 19 file sửa dở và `.omc` không bị chỉnh.
