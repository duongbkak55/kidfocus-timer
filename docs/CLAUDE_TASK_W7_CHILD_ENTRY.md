# W7 — Fix lỗi "Sign in" + Con tự nhập + Nhập từ màn hình khóa

Người giao: Claude (tổng chỉ huy) · Người làm: ChatGPT/Codex · Ngày: 2026-10-02
Base: main 032c9a5. Làm trên worktree mới `KidFocusTimer-w7` (branch `w7-child-entry`).
Quyết định của Duong (chốt): (1) bản con nhập KHÔNG cần bố mẹ duyệt, lưu luôn; (2) dùng CHUNG quota của tài khoản bố mẹ, không thêm cap riêng; (3) làm W7 TRƯỚC khi mở closed test.

Thứ tự: Phần 0 (bug) → G2 nhanh → Phần 1, 2 → G2 → G3. Commit nhỏ theo từng phần.

---------------------------------------------------------------------
## Phần 0 — F-SIGNIN-1 (P1, làm trước)

Hiện tượng (ảnh của Duong, màn Quick entry): bấm Phân tích với "đi ngủ lúc 11 giờ 10 tối" → lỗi đỏ
"Sign in with Google to use AI schedule entry…", trong khi cùng màn hiện "AI trial: 59 days left · 12 credits left today"
(tức `account.isSignedIn == true`, usage load được).

Nguyên nhân nghi ngờ (Claude): lỗi đỏ đến từ `QuickScheduleViewModel` map MỌI `UNAUTHENTICATED` → `SIGN_IN`.
Callable v2 có `enforceAppCheck` trả về `UNAUTHENTICATED` khi App Check token không hợp lệ. `getAiConfig` (load usage)
không bắt buộc App Check giống aiSchedule → usage vẫn hiện. Khả năng cao máy đang chạy build không có App Check hợp lệ:
release APK cài qua adb (chưa từ Play → Play Integrity fail) hoặc debug build có debug token chưa đăng ký.

Việc cần làm:
0.1 Điều tra (read-only): `firebase functions:log --only aiSchedule` (hoặc Cloud Logging) quanh thời điểm lỗi hôm nay;
    xác nhận đó là App Check reject hay `SIGN_IN_REQUIRED`. Hỏi/kiểm tra build trên máy Duong (debug/release, nguồn cài).
    Ghi kết luận vào report. KHÔNG log/commit App Check debug token, ID token.
0.2 Client — phân biệt lỗi, áp dụng cho cả 3 ViewModel (QuickSchedule, QuickDayLog, SmartSchedule/Advice):
    - `UNAUTHENTICATED` + message chứa `SIGN_IN_REQUIRED` hoặc `account.isSignedIn == false` → SIGN_IN (như cũ).
    - `UNAUTHENTICATED` khi đã sign-in và message KHÁC → trước tiên `getIdToken(true)` + `FirebaseAppCheck.getAppCheckToken(true)`
      rồi retry đúng 1 lần với CÙNG requestId (server đã idempotent theo requestId; không trừ credit 2 lần).
      Vẫn lỗi → error mới `APP_VERIFY` với chuỗi VI/EN:
      VI: "Không xác minh được ứng dụng. Hãy cài/cập nhật KidFocus từ Google Play rồi thử lại."
      EN: "Couldn't verify the app. Please install or update KidFocus from Google Play and try again."
    - Gom logic map lỗi vào 1 hàm chung (vd `AiErrorMapper`) + unit test cho các nhánh.
0.3 Server: không đổi hành vi enforce. (Nếu thấy cần, chỉ thêm log mã lỗi App Check, không log token.)
0.4 Nếu nguyên nhân là debug build: viết hướng dẫn ngắn trong report để Duong tự đăng ký debug token trong Firebase Console
    (Duong làm tay, không paste token vào chat/repo).

---------------------------------------------------------------------
## Phần 1 — Con tự nhập ("Con kể việc vừa làm")

Mục tiêu: trẻ ghi lại việc ĐÃ làm trong ngày (LOG mode) mà không cần PIN. Con KHÔNG được sửa lịch tuần, không xem
Smart Schedule/so sánh/advice — những màn đó giữ nguyên PIN.

1.1 Điểm vào: thêm 1 nút lớn "🎤 Con kể việc vừa làm" trên màn hằng ngày của con (DailyScheduleScreen / Home của con).
    CHỈ thêm nút, KHÔNG viết lại layout màn con (bài học W5a P1).
1.2 Màn `ChildQuickLogScreen` (route mới, không qua parentGate):
    - 1 ô nhập (≤300 ký tự cho con), nút mic dùng RecognizerIntent như hiện tại (không thêm RECORD_AUDIO), nút "Gửi".
    - Gọi LOG mode hiện có (aiSchedule, cùng server, cùng validator + grounding). Không đổi prompt/enum server → không cần deploy.
    - Kết quả: hiển thị dạng thẻ to, chữ đơn giản: "🛏️ Đi ngủ · 23:10". Mục bị preview bỏ chọn (confidence <0.6, giờ tương lai,
      không grounded) thì KHÔNG lưu, hiện "Chưa hiểu: …" + gợi ý nói lại có giờ.
    - Lưu luôn (không duyệt) các mục hợp lệ → `day_log_entries`. Snackbar "Đã lưu" + "Hoàn tác" 10s.
    - Câu hỏi làm rõ từ AI (questions) hiển thị cho con dạng 1 dòng, con có thể nói lại.
1.3 Nguồn dữ liệu: thêm `DayLogSource.CHILD` (add-only). Bắt buộc parse enum chịu lỗi: giá trị lạ → fallback `MANUAL`
    (cả Room `toEntry()` và Firestore sync) để sau này thêm giá trị không làm crash. Room: không đổi schema (source là String).
1.4 Bố mẹ: trong DayLogs/Comparison, mục do con nhập có nhãn nhỏ "Con nhập"; bố mẹ sửa/xoá như mục thường.
1.5 Quota: dùng chung quota tài khoản bố mẹ (LOG = 1 credit), không thêm cap riêng. Hết credit → thông báo thân thiện cho con
    "Hôm nay hết lượt rồi, nhờ bố mẹ nhé" (không hiện số tiền/gói).
1.6 Chưa đăng nhập / AI tắt / App Check lỗi → màn con hiện "Nhờ bố mẹ bật tính năng này" (không có nút sign-in ở màn con).
1.7 Chống spam: chặn gửi khi đang busy; debounce; requestId mỗi lần gửi như hiện tại.

---------------------------------------------------------------------
## Phần 2 — Nhập từ màn hình khóa

Duong chốt 2026-10-03: làm CẢ HAI lối vào — Quick Settings tile (2.1) VÀ widget màn khoá/màn chính (2.6). Cả hai bắt buộc trong W7, cùng mở LockScreenLogActivity.

2.1 Quick Settings tile "KidFocus · Kể việc" (`TileService`, không cần permission mới). Khi bấm:
    mở `LockScreenLogActivity` qua `startActivityAndCollapse(PendingIntent)` (API 34+) / Intent (API thấp hơn).
2.2 `LockScreenLogActivity`: `setShowWhenLocked(true)`, KHÔNG `setTurnScreenOn`, KHÔNG `requestDismissKeyguard`.
    Activity riêng, `excludeFromRecents`, `taskAffinity` riêng, `noHistory`; không có điều hướng sang phần còn lại của app.
    Nội dung = đúng UI của ChildQuickLogScreen (tái sử dụng composable), cho profile ĐANG ACTIVE.
2.3 Quyền riêng tư trên màn khóa (bắt buộc):
    - Chỉ hiện ô nhập + kết quả của CHÍNH lần nhập vừa rồi. Không hiện lịch, lịch sử, danh sách việc, tên đầy đủ,
      số credit, email tài khoản. Profile chỉ hiện avatar/emoji.
    - Nếu thiết bị có nhiều profile: KHÔNG cho chọn profile trên màn khóa; dùng profile active, hoặc hiện
      "Mở khoá máy để chọn bé" nếu chưa có profile active.
    - Nút "Hoàn tác" chỉ áp dụng cho mục vừa lưu trong phiên đó.
    - Sau khi lưu hoặc rời màn → finish(); không lưu text nháp ra disk.
2.4 Mic trên màn khóa: RecognizerIntent có thể không hiện trên keyguard → nếu lỗi/ActivityNotFound thì ẩn nút mic,
    vẫn cho gõ. Ghi kết quả thực tế vào report (G3).
2.5 Cài đặt bố mẹ (sau PIN): công tắc "Cho phép con ghi từ màn hình khóa" (mặc định BẬT theo yêu cầu Duong); TẮT thì tile
    hiển thị trạng thái unavailable và activity chỉ báo "Bố mẹ đã tắt".
2.6 Widget "🎤 Kể việc" (AppWidgetProvider, 1 nút to, không hiện dữ liệu nào):
    - Dùng được trên màn hình chính VÀ trên màn hình khoá (Pixel Android 16 QPR2+ lock screen widgets; Samsung One UI 8):
      khai báo `android:widgetCategory="home_screen|keyguard"`, kích thước nhỏ (2x1), previewLayout + description VI/EN.
    - Bấm → mở LockScreenLogActivity (PendingIntent immutable). Widget KHÔNG hiển thị tên con, lịch, credit; nội dung tĩnh.
    - Công tắc ở 2.5 tắt → widget hiện "Bố mẹ đã tắt".
    - Shortcut kiểu nút camera ở góc màn khoá: Pixel chỉ cho chọn shortcut hệ thống → KHÔNG làm. Samsung cho gán app vào
      shortcut màn khoá: ghi nhận trong report nếu có máy Samsung để thử, không code riêng.
2.7 Không thêm permission mới, không thêm thông báo cố định mới.

---------------------------------------------------------------------
## Ràng buộc chung (giữ nguyên các rule dự án)
- Không key/secret trong repo/APK/log; không `:free` model; giữ `data_collection: deny`.
- Không đổi server enum/validator trong W7 (nếu buộc phải đổi → dừng, báo Claude; deploy server trước app).
- Room add-only; không reset/stash 19 file dirty; không sửa .omc; chỉ `git add` file liên quan.
- Không push/merge/deploy khi chưa có Duong duyệt. Không gọi production/OpenRouter trong test tự động
  (G3 thật được phép tối đa 6 lần LOG trên tài khoản test của Duong).
- Không login Google/mua hàng trong device test tự động; không reboot máy; không dữ liệu trẻ thật.

## Gate
- G2 (Claude review): unit test AiErrorMapper; test fallback enum; test child flow lưu đúng mục selected, bỏ mục <0.6/tương lai;
  test LockScreenLogActivity không có route sang màn khác; `./gradlew testDebugUnitTest lint` xanh.
- G3 do DUONG TỰ TEST TAY (chốt 2026-10-03). ChatGPT không chạy G3 tự động trên máy thật; thay vào đó:
  * Cài build lên Pixel 9 qua wireless adb (chỉ install, không thao tác UI, không login).
  * Build phải qua được App Check, nếu không G3 sẽ dính lại lỗi "Sign in": debug build → in hướng dẫn để Duong tự đăng ký
    debug token trong Firebase Console (không paste token vào chat/repo); hoặc release qua Play internal testing/internal app sharing.
  * Viết `docs/W7_G3_CHECKLIST.md` cho Duong: các bước (a)–(e) dưới đây, mỗi bước có kết quả mong đợi + ô PASS/FAIL + chỗ ghi chú.
  * Duong điền checklist, gửi lại; ChatGPT sửa lỗi nếu có.
- Các bước G3 (Pixel 9): (a) Quick entry parent không còn lỗi "Sign in" giả (ghi rõ build + nguồn cài);
  (b) con nhập "đi ngủ lúc 11 giờ 10 tối" → lưu 1 mục 23:10, parent thấy nhãn "Con nhập";
  (c) khoá máy → kéo QS → tile → nhập → lưu, xác nhận không lộ dữ liệu khác, Back không vào được app;
  (d) tắt công tắc ở Parent Settings → tile/widget báo unavailable; (d2) thêm widget lên màn khoá Pixel 9 (nếu Android 17 hỗ trợ) → bấm khi máy khoá → mở được màn nhập không cần mở khoá, ghi rõ có/không yêu cầu mở khoá; (e) hết quota → thông báo cho con đúng.
  Ảnh chụp lưu vào `_to_delete/` hoặc thư mục ngoài repo, không commit ảnh có dữ liệu.
- Report: `docs/CLAUDE_TASK_W7_REPORT.md` (commit list, kết luận F-SIGNIN-1, kết quả G2/G3, rủi ro còn lại).
