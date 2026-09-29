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

- Route trẻ `DailySchedule` giữ giao diện lịch ngày W4, chỉ thêm liên kết nhỏ **Thực tế & So sánh** qua PIN. Timeline ngày hai làn **Kế hoạch | Thực tế** chỉ ở route phụ huynh `DayLogs`; chọn ngày, chạm kế hoạch để nhập thực tế, sửa/xoá, thêm Phát sinh/Giờ ngủ/Giờ thức. Làn Kế hoạch dùng `TaskVisual`; chỉnh giờ dùng Material3 TimePicker.
- Sửa kế hoạch mở màn lịch cũ, áp cho mọi tuần. Không có bảng kế hoạch riêng từng tuần hoặc lựa chọn “chỉ tuần này”.
- So sánh theo tuần, điều hướng tuần trước/tuần này/tuần sau; ±10 phút tính đúng giờ, muộn/sớm và dài/ngắn, phát sinh, bỏ lỡ chỉ trên ngày có dữ liệu và mục đã đến giờ. Ngày trống hiện Chưa ghi. Mục chưa có end không được coi đã hoàn thành đủ thời lượng.
- Tổng tuần: phần trăm hoàn thành, độ trễ trung bình, giờ ngủ thực tế/kế hoạch trung bình, phút ngủ thiếu, top 3 sai lệch, mũi tên so với tuần trước. Chỉ tính ngày có dữ liệu thực tế.
- Chuỗi mới dùng resources vi/en. Controls timeline nằm trong vùng cuộn để sử dụng trên màn nhỏ/chữ lớn.

### Sync từng bản ghi

- `users/{uid}/dayLogs/{UUID}`; listener chỉ xử lý `documentChanges`, cửa sổ mặc định từ hôm nay − 60 ngày. Bỏ qua REMOVED do rời cửa sổ; xoá thật vẫn dùng tombstone. Mở ngày/tuần cũ tải riêng khoảng ngày đó; giữ mọi log cũ local. Transaction kiểm bản mới nhất trước upload, updated_at mới hơn thắng. Collection chưa tồn tại/trống không xoá local.
- Theo dõi account và owner ngoài schema Room; không upload bản ghi của tài khoản trước sang tài khoản sau. Acknowledgement gồm nội dung để tombstone/tie có cùng timestamp vẫn được upload. Thử lại khi local đổi, có server snapshot mới hoặc Sync now; lỗi sync hiển thị trong timeline.
- Document backup chính giữ nguyên payload. Khi backup cũ không chứa scheduled_task_id được tải xuống, giữ liên kết session local cùng ID. Liên kết session này chưa được bổ sung vào backup chính; log riêng vẫn chứa task_id.
- `firestore.rules` hiện có owner-only recursive match `/users/{userId}/{document=**}`, đã bao phủ dayLogs. Không cần thay rules; repo không cấu hình emulator/rules test.

## Quyết định khi khớp spec với code

1. Thời lượng kế hoạch cho log timer là **focusDurationMinutes**; breakDurationMinutes thuộc pha nghỉ, không được gộp vào một phiên thực tế tập trung. Giữ mô hình ScheduledTask hiện có.
2. Giờ ngủ dùng quy ước anchors hiện có: ngủ 00:30 của một đêm thuộc ngày lịch kế tiếp. Task muộn qua nửa đêm được ghép với occurrence gần nhất; không ghép một log ban ngày vào kế hoạch đêm trước chỉ vì cùng task ID.
3. Ngủ chưa có end có thể ghép giờ thức tiếp theo trong 24 giờ để tính thời lượng; không có giờ thức/end thì không bịa thời gian ngủ hay thiếu ngủ.
4. Routine là một tick tại thời điểm hoàn thành (duration 0), không suy đoán giờ bắt đầu hoặc gán task_id. Routine không có mục tương ứng trong ScheduledTask/anchors được hiển thị Phát sinh.
5. Lịch sử so sánh sử dụng **lịch tuần chung hiện tại**. Không thêm snapshot hoặc override theo tuần. Thời gian lưu theo phút như spec; nếu process bị kill giữa phiên thì log chưa có end giữ trạng thái chưa ghi kết thúc để phụ huynh sửa tay.

## Fixes G2 W5a — theo review commit 85bc5db

- **F-W5A-1 (P1):** khôi phục `DailyScheduleScreen` từ W4 `868993b`. Giữ WeekStrip, giờ hiện tại/mục đã qua, `TaskVisual`, ô trống thêm việc tại giờ này và các callback cũ. Chỉ bổ sung một liên kết nhỏ vào `DayLogs` qua cổng PIN. Tách timeline phụ huynh thành `DayLogsScreen`; plan cards dùng `TaskVisual` với photo/emoji của task hoặc icon anchor. Không đổi quyền PIN/session hiện có.
- **F-W5A-2 (P2):** listener dùng các document thay đổi và query date ≥ hôm nay − 60 ngày; bỏ lần get toàn collection trùng với snapshot khởi tạo. Giữ dữ liệu cũ local; khi phụ huynh mở ngày/tuần ngoài cửa sổ thì fetch giới hạn đúng khoảng đó, giữ LWW/owner/tombstone.
- **F-W5A-3 (P2):** thay hai ô text HH:mm bằng Material3 TimePicker 24 giờ; vẫn cho end trống hoặc bỏ end đã chọn. End < start vẫn nghĩa là ngày kế tiếp; chuỗi mới có vi/en.
- Test điều hướng dùng PIN thật để mở `DayLogsScreen`/So sánh thật, kiểm khoá lại khi app ra nền. Compose test mới xác nhận màn trẻ có WeekStrip/TaskVisual, không có nút Phát sinh/Ngủ/Thức/Sửa kế hoạch/So sánh riêng và liên kết phụ huynh chuyển đúng ngày đang chọn. Hai test sync mới kiểm query 60 ngày, fetch lịch sử có giới hạn, chỉ documentChanges và không xoá local khi REMOVED.
- Không đổi Room/schema, Manifest/permission, Functions, firestore.rules hoặc `.omc` trong các fixes. Chờ Claude review lại G2; dev không tự tuyên bố G2 PASS.

## Kiểm tra tự động

| Kiểm tra | Kết quả sau fixes |
| --- | --- |
| `./gradlew testDebugUnitTest` | **284/284 PASS**, 43 suite; 0 failure/error/skipped |
| `./gradlew assembleDebug` | **PASS** |
| `./gradlew lintDebug` | **PASS**, 0 lỗi, 172 cảnh báo; không bỏ qua lint |
| `./gradlew assembleDebugAndroidTest` | **PASS**; MigrationTestHelper đã chạy trên Pixel với APK 85bc5db, Room không đổi trong fixes |
| `git diff --check` và kiểm file liên quan | **PASS** |

Lần kiểm đầy đủ sau fixes: **BUILD SUCCESSFUL in 25s**, 96 task (6 executed / 90 up-to-date). [Số liệu XML test/lint và hash APK sau fixes](g3/w5a/31-review-fixes-checks.json). Lần trước review: 281/281 test, 183 cảnh báo, [bằng chứng cũ](g3/w5a/01-automated-checks.json).

APK sau fixes SHA-256: `28237ea18e8308a78f81512391a352275e3655c183eb3f2ee1fc02371b6293f7`.

Test bao phủ migration giữ cả sáu bảng cũ; kế hoạch lặp qua tuần/nửa đêm; timer/routine/profile/stop/manual/tombstone; so sánh ±10/muộn/sớm/thời lượng/chưa ghi/phát sinh/ngủ/top 3; sync bản mới/tombstone/tie/owner/cửa sổ/lịch sử; ViewModel nhập tay và PIN/navigation thật. MigrationTestHelper trong Robolectric chạy SQLite và validate schema thật; có instrumentation riêng trên Pixel. Hộp thoại TimePicker dành cho G3 trên Pixel; không dùng fixture dialog Robolectric để khẳng định UI PASS.

Môi trường: JDK 17, toolchain committed W4, SDK 35; Firebase/RevenueCat/OpenRouter key rỗng. Không đăng nhập Google, không mua, không gọi provider thật.

## G3 phần mới trên Pixel

Lần wireless ban đầu mất kết nối, bằng chứng [02-device-preflight](g3/w5a/02-device-preflight.txt) giữ lại theo lịch sử. Sau khi Duong cắm USB, chạy trên **Pixel 9 / Android 17 (API 37)**, app debug riêng, máy mở khoá. Không reboot, không xoá dữ liệu, không đăng nhập/mua/call provider. USB có lúc mất transport; dừng thao tác và chỉ tiếp tục khi ADB nhận lại thiết bị mở khoá.

### Hoàn tất G3 đang dở — APK 85bc5db, trước khi cài bản sửa

APK SHA-256 `1c29e42435dcb861b8771309d919fa638111157cecd7f0a9547868c02216939e`, cài `adb install -r` giữ dữ liệu.

| Mục | Kết quả và bằng chứng |
| --- | --- |
| Migration thực tế v5→v6 | **PASS**. Sáu bảng cũ giữ nguyên count/hash các cột cũ: 2 profile, 2 task, các bảng còn lại trống; cột session mới nullable. [DB trước](g3/w5a/03-db-before.json), [DB sau](g3/w5a/03-db-after-migration.json), [install](g3/w5a/04-usb-install.txt) |
| Migration instrumentation | **PASS — 1 test trên Pixel**. MigrationTestHelper dùng DB fixture riêng, giữ hàng của cả sáu bảng; không chèn fixture vào DB app đang dùng. [Kết quả](g3/w5a/06-device-migration-test.txt) |
| Timer một task | **PASS start/pause/resume/Stop**. Homework 45 phút, start 09:11, dừng sau khoảng 82 giây; đúng 1 UUID, task/profile giữ nguyên, end 09:12. Không chạy hết 45 phút và không khẳng định natural completion/session trên máy; các nhánh đó có unit test. [Ảnh start](g3/w5a/10-task-timer-start.png), [pause](g3/w5a/12-timer-paused.png), [dừng](g3/w5a/13-task-timer-ended.png), [dữ liệu](g3/w5a/14-timer-stop-data.json) |
| Sửa tay | **PASS**. Sửa Homework thành 21:08–21:55, source MANUAL; giữ UUID/profile/task/created_at, updated_at tăng. [Hộp sửa](g3/w5a/17-manual-editor-filled.png), [đã lưu](g3/w5a/18-manual-actual-saved.png), [dữ liệu](g3/w5a/19-manual-edit-data.json) |
| Thêm Phát sinh | **PASS**. G3 walk 16:00–16:30, MANUAL/OTHER, task_id null, bản ghi riêng. [Hộp nhập](g3/w5a/20-extra-editor.png), [đã lưu](g3/w5a/21-extra-saved.png), [dữ liệu](g3/w5a/25-extra-data.json) |
| So sánh + tuần trước | **PASS**. Tuần 28/9–4/10 có 1 ngày dữ liệu: Homework đúng giờ (8 phút), dài hơn 2 phút, G3 walk là Phát sinh. Ngày trống Chưa ghi/No data. Tuần 21–27/9 có 0 ngày dữ liệu, không đánh Bỏ lỡ. [Tổng tuần](g3/w5a/22-comparison-current-week.png), [chi tiết](g3/w5a/23-comparison-day-details.png), [tuần trước](g3/w5a/24-comparison-previous-week.png) |
| Logcat | **Không thấy crash/ANR của KidFocus** trong crash buffer/AndroidRuntime:E/ActivityManager:E đã thu. Có 1 crash ứng dụng khác, loại khỏi artifact để không đưa thông tin app khác vào repo. [Kết quả đã lọc](g3/w5a/26-old-apk-crash-logcat.txt) |

APK cũ vẫn có regression giao diện trẻ F-W5A-1 theo review; kết quả G3 dữ liệu trên không thay thế review UI. Không sửa task kế hoạch hoặc profile trên Pixel. Bản sao DB thô chỉ ở `/tmp`, artifact repo chỉ gồm số liệu kiểm, log đã lọc và ảnh app.

### Chạy lại UI sau fixes

**PENDING sau commit sửa:** cài APK sửa bằng `-r`, kiểm màn trẻ WeekStrip/TaskVisual/ô trống và liên kết PIN; timeline phụ huynh, TimePicker sửa tay/Phát sinh và So sánh. Bổ sung ảnh và logcat riêng; không dùng ảnh APK cũ để khẳng định UI bản sửa đạt.

## Checklist trước triển khai

- [ ] Claude review G2 W5a; Duong nghiệm thu G3 phần mới, migration từ dữ liệu v5 và profile separation trên Pixel.
- [ ] Kiểm hai thiết bị: offline edit → reconnect, updated_at mới hơn, xoá mềm không hồi sinh, đổi tài khoản không upload dữ liệu owner cũ.
- [ ] Trên emulator/staging khi được cấu hình: owner đọc/ghi dayLogs được phép; unauthenticated/UID khác bị từ chối. Recursive owner rule hiện có đủ phạm vi, chưa deploy thay đổi nào.
- [ ] Kiểm ngủ/thức qua nửa đêm và summary tuần có ngày trống trên dữ liệu nghiệm thu; xác nhận lịch chung áp cho mọi tuần.
- [x] W5a không đổi Functions/Remote Config, không cần deploy Functions cho phần này. W5b LOG/AI/rules từ thực tế chưa triển khai.
- [x] W5a chưa push/merge/deploy; chỉ commit file W5 trên branch feature. Không thêm permission, không sửa `.omc`, không stash/reset.
- [x] Diff 19 file tracked đang sửa dở ở working tree gốc vẫn byte-identical với snapshot trước merge main; main/W5 sử dụng worktree riêng.
