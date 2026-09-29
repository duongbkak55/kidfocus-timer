# W5a — Nhật ký thực tế và so sánh lịch tuần

Ngày: 2026-09-29 · Dev: Codex · Reviewer: Claude · Nghiệm thu: Duong.

Branch `feature/smart-schedule-w5`, tạo từ HEAD W4 `868993b111e30f748d95ebdfa5e4cedaab666e37`, trong worktree riêng `KidFocusTimer-w5a`. Chỉ thực hiện W5a theo `CLAUDE_AI_SCHEDULE_TASK_W5.md`.

## Việc 1 — Merge và push W1–W4

- Theo chẩn đoán của Claude, chỉ sửa test `functions/test/schedule.test.js`: identity của phép giữ quota nhận `quotaDate: "2026-09-28"`, khớp ngày cố định của harness. Không sửa gateway hay code production. Commit W4 **`868993b`**.
- Dùng worktree riêng `KidFocusTimer-main-integration`, fetch origin, merge `--no-ff` W4 vào main. Merge đầu **`42bce7a`**, merge bản sửa test **`db6ce60a2a6c1b96552109e2275944ad00751f3b`**. Không có conflict; giữ hai commit main cũ.
- Sau bản sửa: `./gradlew testDebugUnitTest assembleDebug lintDebug` **PASS** — **252 test / 37 suite, 0 failure/error/skipped**; lint **0 lỗi, 158 cảnh báo**. `npm --prefix functions test` **112/112 PASS**. Provider key rỗng, không gọi OpenRouter thật.
- Đã push thường, không force: main **`db6ce60`**; W1 **`9d31af1`**; W2 **`4258799`**; W3 **`ed19868`**; W4 **`868993b`**. Đã đối chiếu remote refs.
- Workflow **Build & Publish Release AAB — SUCCESS**: [run 36505455584](https://github.com/duongbkak55/kidfocus-timer/actions/runs/36505455584), commit main `db6ce60`. Job kết thúc `2026-09-29T01:03:09Z`; upload artifacts `kidfocus-release-aab` (ID `11006914285`) và `kidfocus-release-apk` (ID `11007600281`). Không deploy Firebase.

## Phạm vi đã thực hiện

### Room v6 và dữ liệu

- Migration `5→6` chỉ `ALTER TABLE sessions ADD COLUMN scheduled_task_id INTEGER` nullable, tạo bảng `day_log_entries` đúng các cột trong task và index `(profile_id,date)`. Schema `6.json` được export. Đối chiếu schema: các bảng cũ giữ nguyên, sessions chỉ thêm một cột.
- UUID, ngày ISO, phút trong ngày, nguồn TIMER/ROUTINE/MANUAL/AI và xoá mềm. AI chỉ là giá trị enum của mô hình; chưa có luồng W5b.
- Sửa/xoá trong transaction, giữ UUID/profile/created_at, tăng updated_at đơn điệu theo bản ghi. Bản xoá thắng khi bằng timestamp; các tie còn lại có thứ tự ổn định để hai máy hội tụ.

### Tự ghi từ timer/routine

- Timer bắt đầu từ mục lịch truyền task ID qua cả binder và Intent; timer tự do để task_id null. Ghi start khi bắt đầu, end khi dừng hoặc hoàn thành, kể cả Stop từ notification. Pause/resume tiếp tục một bản ghi; break không tạo mục tập trung mới.
- Dùng service timer hiện có, không thêm service/WorkManager/permission. Hàng đợi lệnh tránh Start/Stop/completion chồng nhau; service ghi session một lần khi hoàn thành và giữ profile/task đã chọn lúc bắt đầu. Không tạo log/session khi chưa có hồ sơ thật.
- Routine tick ghi thời điểm hoàn thành, source ROUTINE, UUID ổn định theo routine/occurrence; tick lại không nhân đôi hoặc hồi sinh mục đã sửa/xoá. Không backfill lịch sử.
- Phụ huynh sửa/xoá log timer đang chạy thì thời điểm kết thúc tự động không ghi đè sửa tay. Đổi profile không chuyển log đang chạy sang bé khác.

### Timeline và so sánh

- Timeline ngày hai làn **Kế hoạch | Thực tế**; chọn ngày, chạm kế hoạch để nhập giờ thực tế với mặc định kế hoạch, sửa/xoá thực tế, thêm Phát sinh/Giờ ngủ/Giờ thức. Sửa thực tế và màn So sánh dùng cổng PIN/parentUnlocked hiện có.
- Sửa kế hoạch mở màn lịch cũ, áp cho mọi tuần. Không có bảng kế hoạch riêng từng tuần hoặc lựa chọn “chỉ tuần này”.
- So sánh theo tuần, điều hướng tuần trước/tuần này/tuần sau; ±10 phút tính đúng giờ, muộn/sớm và dài/ngắn, phát sinh, bỏ lỡ chỉ trên ngày có dữ liệu và mục đã đến giờ. Ngày trống hiện Chưa ghi. Mục chưa có end không được coi đã hoàn thành đủ thời lượng.
- Tổng tuần: phần trăm hoàn thành, độ trễ trung bình, giờ ngủ thực tế/kế hoạch trung bình, phút ngủ thiếu, top 3 sai lệch, mũi tên so với tuần trước. Chỉ tính ngày có dữ liệu thực tế.
- Chuỗi mới dùng resources vi/en. Controls timeline nằm trong vùng cuộn để sử dụng trên màn nhỏ/chữ lớn.

### Sync từng bản ghi

- `users/{uid}/dayLogs/{UUID}`; đọc/merge từng bản ghi, transaction kiểm bản mới nhất trước upload, updated_at mới hơn thắng, giữ tombstone. Collection chưa tồn tại/trống không xoá local.
- Theo dõi account và owner ngoài schema Room; không upload bản ghi của tài khoản trước sang tài khoản sau. Acknowledgement gồm nội dung để tombstone/tie có cùng timestamp vẫn được upload. Thử lại khi local đổi, có server snapshot mới hoặc Sync now; lỗi sync hiển thị trong timeline.
- Document backup chính giữ nguyên payload. Khi backup cũ không chứa scheduled_task_id được tải xuống, giữ liên kết session local cùng ID. Liên kết session này chưa được bổ sung vào backup chính; log riêng vẫn chứa task_id.
- `firestore.rules` hiện có owner-only recursive match `/users/{userId}/{document=**}`, đã bao phủ dayLogs. Không cần thay rules; repo không cấu hình emulator/rules test.

## Quyết định khi khớp spec với code

1. Thời lượng kế hoạch cho log timer là **focusDurationMinutes**; breakDurationMinutes thuộc pha nghỉ, không được gộp vào một phiên thực tế tập trung. Giữ mô hình ScheduledTask hiện có.
2. Giờ ngủ dùng quy ước anchors hiện có: ngủ 00:30 của một đêm thuộc ngày lịch kế tiếp. Task muộn qua nửa đêm được ghép với occurrence gần nhất; không ghép một log ban ngày vào kế hoạch đêm trước chỉ vì cùng task ID.
3. Ngủ chưa có end có thể ghép giờ thức tiếp theo trong 24 giờ để tính thời lượng; không có giờ thức/end thì không bịa thời gian ngủ hay thiếu ngủ.
4. Routine là một tick tại thời điểm hoàn thành (duration 0), không suy đoán giờ bắt đầu hoặc gán task_id. Routine không có mục tương ứng trong ScheduledTask/anchors được hiển thị Phát sinh.
5. Lịch sử so sánh sử dụng **lịch tuần chung hiện tại**. Không thêm snapshot hoặc override theo tuần. Thời gian lưu theo phút như spec; nếu process bị kill giữa phiên thì log chưa có end giữ trạng thái chưa ghi kết thúc để phụ huynh sửa tay.

## Kiểm tra tự động

| Kiểm tra | Kết quả |
| --- | --- |
| `./gradlew testDebugUnitTest` | **281/281 PASS**, 42 suite; 0 failure/error/skipped (252 test cũ + 29 test W5a) |
| `./gradlew assembleDebug` | **PASS** |
| `./gradlew lintDebug` | **PASS**, 0 lỗi, 183 cảnh báo (dependency/resources/plurals và các cảnh báo dự án); không bỏ qua lint |
| `./gradlew assembleDebugAndroidTest` | **PASS**, APK test migration đã build; chưa chạy trên Pixel |
| `git diff --check` và kiểm staged | **PASS** |

Lần kiểm đầy đủ cuối **BUILD SUCCESSFUL in 3m 18s**, 96 task (19 executed / 77 up-to-date). [Số liệu từ XML test/lint và hash APK](g3/w5a/01-automated-checks.json).

APK debug SHA-256: `1c29e42435dcb861b8771309d919fa638111157cecd7f0a9547868c02216939e`.

Các test mới bao phủ: MigrationTestHelper giữ cả sáu bảng cũ; lịch lặp qua tuần/nửa đêm; tự ghi timer/routine/profile/stop/manual/tombstone; so sánh ±10/muộn/sớm/thời lượng/chưa ghi/phát sinh/ngủ/top 3; sync collection thiếu/upsert/bản mới/tombstone/tie/đổi owner; ViewModel nhập tay và luồng PIN/navigation thật.

MigrationTestHelper chạy SQL SQLite và validate schema thật trong Robolectric; chỉ AssetManager đọc exported schema từ disk được thay cho APK assets. Có thêm instrumentation MigrationTestHelper dành cho Pixel, build APK test riêng.

Test điều hướng nhập PIN qua màn PIN thật và mở Daily/Comparison thật. Với nút Phát sinh, test gọi semantics action của nút thật và xác minh callback ViewModel. Hộp thoại sửa giờ không được tuyên bố đã nghiệm thu UI bởi Robolectric: fixture input/window không ổn định khi mở dialog, có thể khiến clock giả chạy đến timeout PIN. Save/edit/delete được kiểm ở ViewModel/repository; hiển thị và thao tác hộp thoại trên máy thật còn ở G3.

Môi trường kiểm: JDK 17, toolchain committed của W4 (không lấy các sửa release/toolchain đang dở), SDK 35; Firebase/RevenueCat/OpenRouter key rỗng. Không đăng nhập Google, không mua, không gọi production.

## G3 phần mới trên Pixel

**BLOCKED — kết nối wireless debugging mất trước khi cài APK.** ADB từng thấy Pixel 9, đọc được package debug cũ (`versionCode=4`, target SDK 36) và database `kidfocus_sessions.db`; preflight có lúc máy khoá, sau đó `deviceLocked=0`. Khi chuẩn bị đọc bản sao database để kiểm migration, transport biến mất; `adb devices -l` và `adb mdns services` đều không còn Pixel. [Trạng thái cuối](g3/w5a/02-device-preflight.txt).

Không cài APK W5a, không chạy instrumentation, chưa có ảnh hoặc logcat của W5a; không dùng log/ảnh G3 W4 để khẳng định W5a PASS. Lệnh chuẩn bị dừng ngay ở kiểm kết nối, trước force-stop/đọc bản sao database. Không reboot, không xoá dữ liệu/đổi timeout/khóa máy, không đăng nhập/mua/call production. Đã đề nghị Duong cung cấp địa chỉ IP:cổng kết nối hiện tại.

| Mục G3 W5a | Trạng thái |
| --- | --- |
| Cài giữ dữ liệu v5→v6, kiểm dữ liệu cũ | **BLOCKED** |
| Timer một task: start/end/task/profile | **BLOCKED** |
| Sửa tay một mục thực tế | **BLOCKED** |
| Thêm một mục Phát sinh | **BLOCKED** |
| Xem So sánh và tuần trước, thu ảnh/crash logcat | **BLOCKED** |

Tiếp tục khi ADB kết nối lại và máy mở khoá: cài APK trên bằng `adb install -r`, chạy migration instrumentation và bốn bước UI theo task, chụp ảnh/logcat mới dưới `docs/g3/w5a/`, bổ sung kết quả vào báo cáo. Chưa đề nghị merge W5a.

## Checklist trước triển khai

- [ ] Claude review G2 W5a; Duong nghiệm thu G3 phần mới, migration từ dữ liệu v5 và profile separation trên Pixel.
- [ ] Kiểm hai thiết bị: offline edit → reconnect, updated_at mới hơn, xoá mềm không hồi sinh, đổi tài khoản không upload dữ liệu owner cũ.
- [ ] Trên emulator/staging khi được cấu hình: owner đọc/ghi dayLogs được phép; unauthenticated/UID khác bị từ chối. Recursive owner rule hiện có đủ phạm vi, chưa deploy thay đổi nào.
- [ ] Kiểm ngủ/thức qua nửa đêm và summary tuần có ngày trống trên dữ liệu nghiệm thu; xác nhận lịch chung áp cho mọi tuần.
- [x] W5a không đổi Functions/Remote Config, không cần deploy Functions cho phần này. W5b LOG/AI/rules từ thực tế chưa triển khai.
- [x] W5a chưa push/merge/deploy; chỉ commit file W5 trên branch feature. Không thêm permission, không sửa `.omc`, không stash/reset.
- [x] Diff 19 file tracked đang sửa dở ở working tree gốc vẫn byte-identical với snapshot trước merge main; main/W5 sử dụng worktree riêng.
