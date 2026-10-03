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
- APK debug được build với Firebase Android config công khai trong `local.properties` bị git ignore, không đưa config/secret vào commit hoặc báo cáo. Cài/cập nhật `adb install -r` lên Pixel 9 qua wireless sau sửa mic và sau sửa lỗi lưu: **Success**, giữ dữ liệu. App Check debug token Pixel 9 đã được đăng ký đúng app; xem diễn biến G3 bên dưới.
- Pixel 9 có activity Google (`com.google.android.tts`) xử lý `android.speech.action.RECOGNIZE_SPEECH`, xác nhận bằng `cmd package resolve-activity`. Việc nhận chữ trong lúc máy khoá vẫn cần Duong thử trên UI; chưa coi đây là G3 PASS.
- Checklist G3 tại `docs/W7_G3_CHECKLIST.md`. Theo yêu cầu debug tiếp của Duong, đã thao tác UI trực tiếp với câu tổng hợp trên Pixel 9; các mục widget màn khoá và mic khi khóa vẫn cần kiểm tra riêng. Samsung không có máy để thử shortcut.
- Tình huống câu “đi ngủ lúc 11 giờ 10 tối”: trước 23:10 hôm nay, mục bị coi là tương lai và không lưu; checklist hướng dẫn thử sau 23:10 hoặc nói “tối qua”.

### G3 phản hồi trên Pixel 9 — bấm Gửi nhưng không thấy mục đã làm

- Sau phản hồi của Duong, kiểm tra chỉ **số lượng và nguồn** bản ghi trong Room của bản debug trên Pixel 9: chưa có bản ghi nguồn `CHILD`. Không đọc/in nội dung nhập.
- Log `aiSchedule` gần lúc thử (2026-10-03 06:41:17 và 06:42:16 giờ Việt Nam) ghi `auth=VALID`, `app=MISSING`. Function vẫn bật `ENFORCE_APP_CHECK=true`; Firebase Callable SDK từ chối request thiếu App Check trước handler. Đây là nguyên nhân rất có khả năng khiến LOG không lưu, chưa thể ghép request cụ thể với mỗi lần bấm của Duong vì log không chứa requestId công khai.
- Bản debug có kho lưu Debug App Check cục bộ; không in/lưu token trong báo cáo hay log. Ở bước điều tra này, G3 mục nhập/lưu **chưa PASS**; kết quả sau sửa nằm bên dưới.
- Kiểm tra tiếp: Logcat hiện tại không còn dòng `DebugAppCheckProvider`, nhưng kho private của app trên Pixel 9 có debug secret. Đã chuyển secret trực tiếp vào clipboard macOS, không hiện trong tool output/chat/repo. Firebase app ID của APK debug khớp **KidFocus Android Play Debug** (`com.kidfocusstudio.timer.debug`) trong project `kid-focus-app`.
- Duong thử lại nhưng chưa lưu. Log request lúc 2026-10-03 07:11:24 giờ Việt Nam vẫn `auth=VALID, app=MISSING`. Kiểm danh sách App Check của đúng app lúc đó chỉ có token “Pixel 3a release smoke”; chưa có token Pixel 9. Theo yêu cầu Duong tự debug, đã đăng ký debug secret đang nằm trên Pixel 9 bằng Firebase CLI với nhãn “Pixel 9 W7 debug”, không in/lưu secret. Danh sách sau đó có 2 token. Thử độc lập `exchangeDebugToken` qua API chính thức: HTTP 200 và trả App Check token, không in token. Lần LOG thiếu App Check ở 07:11 diễn ra **trước** lúc đăng ký (07:17); request sau đó đã có `app=VALID` như ghi bên dưới.
- ADB wireless mất kết nối ngắn rồi tự phục hồi, không reboot/nhập PIN. Sau khi đăng ký token, cài test APK và chạy một test chẩn đoán **chỉ** gọi `FirebaseAppCheck.getAppCheckToken(true)` trong chính bản debug trên Pixel 9: **PASS, 1 test**. Test không in token, không gọi `aiSchedule` hay OpenRouter và đã được xóa khỏi source sau khi chạy. Firebase SDK của app hiện lấy được App Check token.

### G3 tự debug — App Check đã qua, phát hiện thêm lỗi lưu CHILD

- Mở lại Pixel 9 bằng ADB khi máy đang mở khóa và thử câu tổng hợp; Cloud Functions ghi `auth=VALID, app=VALID`. App Check đã qua, nhưng app vẫn báo lưu thất bại. Log chẩn đoán tạm chỉ ghi công đoạn và lớp lỗi, không ghi văn bản, token hay dữ liệu trẻ: `stage=storage type=IllegalArgumentException`.
- Nguyên nhân: `DayLogRepository.addChildBatch()` và `undoChildBatch()` đi qua DAO batch chỉ nhận `source=AI`. Một mục `CHILD` hợp lệ bị `require` chặn trước khi lưu. Đổi transaction DAO thành batch chung cho `AI`/`CHILD`, vẫn yêu cầu các mục cùng nguồn, giữ tính nguyên tử và điều kiện chống Hoàn tác khi mục đã bị sửa. Repository kiểm đúng nguồn cho từng đường gọi. Thêm unit test Room thật: lưu hai mục `CHILD`, sau đó Hoàn tác tạo tombstone; test bao phủ đúng lỗi trên code cũ và qua sau sửa. Xóa log chẩn đoán tạm khỏi APK cuối.
- Chạy lại trên Pixel 9 qua wireless với một câu tổng hợp về đọc sách hôm qua lúc 19h: màn hiện hoạt động lúc 19:00; kiểm cơ sở dữ liệu chỉ theo `source/deleted/count` thấy **1 mục CHILD đang hoạt động**. Không đọc nội dung các mục khác. Một lượt thử Hoàn tác bị gián đoạn khi máy chuyển sang ứng dụng khác, không tạo thêm mục. Khi máy rảnh, thử lại với một câu tổng hợp khác: nút **Undo** xuất hiện, đã bấm và kiểm cơ sở dữ liệu có **1 mục CHILD tombstone**; mục CHILD đầu tiên vẫn hoạt động. Hoàn tác **PASS trên Pixel và unit test Room**. Một mục thử tổng hợp hiện còn trong tài khoản test.
- `./gradlew testDebugUnitTest lint assembleDebug`: **PASS** sau sửa DAO, thêm test và xóa log chẩn đoán tạm; `git diff --check`: **PASS**. APK cuối đã cài lại bằng `adb install -r`: **Success**, giữ dữ liệu. Không push/merge/deploy, không gọi OpenRouter trong test tự động, không dùng dữ liệu trẻ thật.

Không gọi OpenRouter/production trong test tự động; không đăng nhập/mua hàng, không reboot Pixel. Worktree gốc với 19 file sửa dở và `.omc` không bị chỉnh.
