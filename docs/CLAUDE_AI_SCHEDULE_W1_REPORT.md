# Báo cáo W1 — Smart Weekly Schedule (P0 + P1)

Ngày: 2026-09-28 · Implementer: Codex · Reviewer: Claude (Cowork)

## Phạm vi và trạng thái

Triển khai A–F của `CLAUDE_AI_SCHEDULE_TASK_W1.md`, hoàn toàn offline ở phần tư vấn.
Branch: `feature/smart-schedule-w1`. Không push, không merge main.

- `DayCodec` chuyển Calendar-int / DayOfWeek / routine mask; bỏ `ScheduledTask.daysLabel` tiếng Việt. UI dùng helper resource vi/en hiện có.
- Anchors từng profile lưu JSON `schedule_anchors_<profileId>` trong DataStore hiện có. Upload dưới `settings.scheduleAnchors` (map profileId → object); remote thiếu key hoặc profile giữ dữ liệu local. Không sync snapshot/photo URI.
- Parent Settings có thẻ mở “Giờ giấc của bé / Góp ý lịch tuần”, route kiểm tra PIN riêng. Nhập thức/ngủ theo nhóm ngày hoặc từng ngày; thêm/sửa/xóa ca học, chọn ngày và thời gian, nhập thời gian di chuyển mỗi chiều.
- Timeline động với min(wake, 06:00), max(bed, 22:00), block ngủ/trường chỉ đọc, khoảng trống theo hợp các interval. Giữ hiển thị task ngoài biên mặc định, xử lý task/block qua nửa đêm và nhãn ngày trước/sau.
- Default task mới: EXERCISE 06:45; SLEEP 20:45, mọi ngày. Không cập nhật task đã lưu.
- ID task/routine mới dùng timestamp × 1000 + random, có bảo vệ trùng ID trong cùng process; cả routine preset cũng dùng ID mới. Schema không đổi.
- Alarm task có URI `kidfocus://task/{id}/{dow}` và request code hash Long. Hủy đủ 7 ngày, cả PendingIntent legacy và dạng URI; update/delete/remote reschedule dọn alarm cũ. Có fallback khi quyền exact alarm thay đổi.
- Rule engine Kotlin thuần: 8 rule, severity, ngày, taskIds, params; UI dựng message từ resource, sắp theo severity. Đọc completion 14 ngày và suy ra MISSED từ routine thực tế. Đồng hồ cập nhật theo phút khi màn hình được theo dõi.
- Gợi ý 1 chạm: đổi bed, dời STUDY, dời màn hình, rút focus. Chỉ sửa ngày bị chọn; task lặp được tách khi cần và dùng ID mới. Validate lại và chặn tăng finding Cao theo rule/ngày trước khi ghi.
- Apply: snapshot JSON local → một Room transaction → reschedule task bị ảnh hưởng → observer sync/debounce hiện có. Snackbar Hoàn tác 10 giây và nút khôi phục snapshot trong 7 ngày. Undo không tạo snapshot lồng, bảo vệ khỏi ghi đè chỉnh sửa mới.

## File W1 tạo/sửa

Đã đối chiếu staged diff: đúng 38 file dưới đây, không có file ngoài W1.

- `app/build.gradle.kts`
- `app/src/main/java/com/kidfocus/timer/alarm/AlarmScheduler.kt`
- `app/src/main/java/com/kidfocus/timer/data/cloud/CloudSyncManager.kt`
- `app/src/main/java/com/kidfocus/timer/data/datastore/SettingsDataStore.kt`
- `app/src/main/java/com/kidfocus/timer/data/di/DataModule.kt`
- `app/src/main/java/com/kidfocus/timer/data/repository/RoutineRepository.kt`
- `app/src/main/java/com/kidfocus/timer/data/repository/ScheduledTaskRepository.kt`
- `app/src/main/java/com/kidfocus/timer/data/schedule/RoomScheduleStore.kt`
- `app/src/main/java/com/kidfocus/timer/data/schedule/ScheduleAnchorsRepository.kt`
- `app/src/main/java/com/kidfocus/timer/data/schedule/ScheduleJson.kt`
- `app/src/main/java/com/kidfocus/timer/domain/model/ScheduledTask.kt`
- `app/src/main/java/com/kidfocus/timer/domain/model/TaskType.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ApplyScheduleUseCase.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/DayCodec.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleAdvisor.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleAnchors.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleIds.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleTimeline.kt`
- `app/src/main/java/com/kidfocus/timer/ui/components/ScheduleLabels.kt`
- `app/src/main/java/com/kidfocus/timer/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/kidfocus/timer/ui/navigation/NavRoutes.kt`
- `app/src/main/java/com/kidfocus/timer/ui/navigation/SmartScheduleGate.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/DailyScheduleScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/ParentSettingsScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/SmartScheduleScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/viewmodel/ScheduleViewModel.kt`
- `app/src/main/java/com/kidfocus/timer/ui/viewmodel/SmartScheduleViewModel.kt`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/kidfocus/timer/alarm/AlarmSchedulerTest.kt`
- `app/src/test/java/com/kidfocus/timer/data/schedule/RoomScheduleStoreTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ApplyScheduleUseCaseTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/DayCodecTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ScheduleAdvisorTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ScheduleAnchorsMapTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ScheduleTimelineTest.kt`
- `app/src/test/java/com/kidfocus/timer/ui/schedule/SmartScheduleGateTest.kt`
- `docs/CLAUDE_AI_SCHEDULE_W1_REPORT.md`

## Kiểm tra nghiệm thu

Kết quả cuối trên mã đã stage, JDK Temurin 17.0.19:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug
git diff --check
git diff --cached --check
```

Gradle: **BUILD SUCCESSFUL**, 1 phút 14 giây, 65 task (22 executed / 43 up-to-date).
Tổng **164 test pass**, 0 failure/error/skipped; **44 test mới W1** trong 8 suite.
Lint: 0 error/fatal, 199 warning trên toàn working tree; không khẳng định mọi warning có sẵn từ trước.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Test report: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lint report: `app/build/reports/lint-results-debug.html`.

Lần đầu shell dùng Java 11 nên Gradle bị chặn; chạy lại bằng JDK 17. Một lần tải dependency test Maven bị “No route to host”; lần tải lại dùng IPv4 cho JVM tạm thời, không sửa cấu hình repo. Lệnh cuối ở trên chạy thành công với cấu hình dự án hiện có.

1. `./gradlew testDebugUnitTest assembleDebug lintDebug`: **PASS**, sử dụng JDK 17.
2. `git diff --check` và `git diff --cached --check`: **PASS**, không có lỗi whitespace.
3. Unit mới: round-trip 7 ngày + 128 mask; mỗi rule ≥ 2 case; midnight/CN→T2; apply/undo, task split/resize, stale state, snapshot 7 ngày; Room thật rollback SQL sau lần ghi đầu và bù anchors khi DataStore lỗi; sync thiếu key; alarm hash collision + legacy cancel; PIN gate.
4. Case l1 wake 06:15 / bed 22:30 ngày đi học: 465 phút < 540 phút → SLEEP_SHORT Cao. Bed đề xuất 21:15; apply đưa finding về 0; undo khôi phục nguyên trạng.
5. Room giữ version 5, không sửa entity/schema/migration. Manifest giữ nguyên byte so với baseline của phiên này. `git diff app/src/main/AndroidManifest.xml` vẫn có thay đổi cũ bỏ USE_EXACT_ALARM; thay đổi đó không thuộc W1 và không nằm trong commit.
6. Không gọi AI/Functions, không thêm permission, không sửa `.omc`.

## Quyết định khi spec và code thực tế khác nhau

- **Room và DataStore không chung transaction:** giữ đúng một transaction SQL, ghi anchors sau SQL trong transaction và bù anchors bằng NonCancellable nếu có lỗi; restore snapshot cũ khi apply lỗi, alarm chỉ chạy sau commit. Không tăng schema để chuyển anchors vào Room. Có test Room thật và lỗi DataStore sau ghi. Đây là bù lỗi trong process, không phải atomic xuyên hai storage khi process chết đột ngột.
- **Routine không lưu MISSED:** DB hiện chỉ lưu completion ON_TIME/LATE. Engine nhận pattern routine + observation Kotlin thuần, suy ra thiếu completion sau deadline, chỉ từ ngày tạo routine và ngày có lặp. Rule đếm ngày khác nhau, theo từng routine sáng (<12:00), trong 7 ngày gần nhất; dữ liệu đọc 14 ngày.
- **Bed sau 00:00:** model không có ngày cộng thêm. Dùng quy ước giờ bed sớm hơn wake của ngày đó thuộc đêm sau nửa đêm; nếu thiếu wake, dùng mốc trưa để phân biệt. UI giải thích rõ MON 00:30 là rạng sáng TUE; school end≤start thuộc ngày kế tiếp.
- **FOCUS_TOO_LONG:** W1 yêu cầu ngưỡng một chỗ và không thêm gateway. Dùng hằng offline 15/20/30/40/40 phút cho 2-3/4-5/l1/l2/l3; không thêm Remote Config. Tất cả focus timer task được kiểm tra. SCREEN gồm LEARNING_GAMES, GAME_TIME, TV_TIME; các timer khác không có metadata màn hình nên không suy đoán thêm.
- **ID cùng millisecond:** công thức random đơn thuần vẫn có thể trùng khi tạo nhiều item. Giữ công thức gốc và thêm monotonic guard trong process; không thay ID dữ liệu đã tồn tại. Không khẳng định thuật toán này đảm bảo duy nhất toàn cầu.
- **Gợi ý học muộn:** `bed−90` chỉ đảm bảo đúng với block ≤30 phút. Với block dài, chọn sớm hơn giữa bed−90 và bed−60−duration để áp dụng xong hết LATE_HOMEWORK. Không tự đổi ngày Calendar của activity sau nửa đêm khi việc dời sang ngày trước chưa có contract; finding vẫn hiện để phụ huynh sửa tay.
- **Khôi phục snapshot:** ngoài hạn 7 ngày, chặn undo khi lịch đã có thay đổi mới để bảo vệ chỉnh sửa tay/sync của phụ huynh. Đây là phương án ít thay đổi nhất, không thêm version lịch/migration.
- **Nguồn ngủ:** giữ nguyên ngưỡng nhóm tuổi mà W1 giao. AASM phân nhóm 1–2 và 3–5 khác nhóm 2–3 của app, và khuyến nghị trẻ nhỏ tính cả ngủ trưa. UI nêu đây là ngưỡng ứng dụng và nhắc ngủ trưa, không gọi kết quả là chẩn đoán. Nguồn: [AASM Child Sleep Duration Health Advisory](https://aasm.org/advocacy/position-statements/child-sleep-duration-health-advisory/).
- **Thư viện test:** thêm Robolectric 4.16.1 chỉ cho unit test để kiểm tra Room và PendingIntent thật trên JVM; không tăng dependency runtime. Cấu hình theo [Robolectric](https://robolectric.org/getting-started/).

## Giới hạn và việc còn lại

- Không có thiết bị adb kết nối: chưa chạy manual Compose/PIN/snackbar trên máy thật. Reviewer cần thử nhóm/từng ngày, ca học qua đêm, font lớn, undo nhanh và chuyển profile.
- R5 ID/alarm đã xử lý theo W1; CloudSyncManager vẫn dùng whole-document snapshot/last-write-wins hiện có. ID mới giảm va chạm nhưng không ngăn mất update do hai thiết bị ghi document đồng thời. Không mở rộng wave này thành CRDT/tombstone/merge protocol.
- Snapshot + Room + DataStore không bảo đảm crash atomicity khi process bị kill giữa các bước; cần journal/recovery nếu yêu cầu nâng lên ở wave sau.
- Khôi phục một snapshot cho mỗi profile; không thêm lịch sử nhiều bản. Gợi ý khó xác định (overlap, thiếu thời gian rảnh, pattern sáng, jetlag) được hiển thị để phụ huynh chỉnh tay.
- P2–P4 (AI, text/giọng/ảnh, proposal nhiều bước) chưa triển khai theo đúng ngoài phạm vi.

## Giữ working tree và commit

Đã lưu baseline trước khi sửa. Chỉ stage W1; với file trùng phạm vi, stage patch chênh lệch baseline→W1 để các thay đổi release/Learning có sẵn nằm ngoài commit. Không dùng `git add -A`. Đã kiểm tra: đúng 19 đường dẫn tracked còn unstaged như baseline; 14 file không trùng phạm vi giữ nguyên từng byte; toàn bộ bản vá trước W1 vẫn reverse-check thành công (kiểm tra chỉ đọc). Manifest/schema/migration không xuất hiện trong staged diff.

Các tài liệu context/design/task có sẵn, `.omc`, file mới do phiên khác tạo và thay đổi không thuộc W1 không được stage. Không reset/stash/checkout file không liên quan. Không push.


## Fixes G2

Ngày: 2026-09-28 · Review nguồn: `docs/CLAUDE_AI_SCHEDULE_W1_FIXES.md`, commit `6ab06e3`.
Phạm vi duy nhất của commit tiếp theo: F1–F3, test hồi quy và mục báo cáo này.

- **F1:** `MORNING_LATE_PATTERN` chỉ đánh giá routine có ít nhất một completion của chính routine đó trong cửa sổ 14 ngày (today−13 đến today, gồm hai đầu). Routine chưa được tick bị bỏ qua; routine có bằng chứng sử dụng vẫn suy ra MISSED theo deadline như trước.
- **F2:** action hoàn thành và contentIntent dùng requestCode hash Long như RoutineAlarmScheduler, cùng data URI `kidfocus://routine/{id}/{occurrenceDate}/complete` và `/open`. ID và ngày trong extras của action hoàn thành được giữ đúng, không bị FLAG_UPDATE_CURRENT ghi đè bởi routine/ngày khác.
- **F3:** ageBand không nhận diện được dùng ngưỡng `4-5` (sleep 600 phút / focus 20 phút); RoutinePattern dùng mapNotNull + runCatching, bỏ mask ngoài 0..127; lời gọi advisor trong ViewModel được bọc runCatching, lỗi trả findings rỗng và ghi log cảnh báo, giữ state flow để cập nhật tiếp.

File thay đổi:

- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleAdvisor.kt`
- `app/src/main/java/com/kidfocus/timer/service/RoutineNotificationManager.kt`
- `app/src/main/java/com/kidfocus/timer/ui/viewmodel/SmartScheduleViewModel.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ScheduleAdvisorTest.kt`
- `app/src/test/java/com/kidfocus/timer/service/RoutineNotificationManagerTest.kt` (mới)
- `app/src/test/java/com/kidfocus/timer/ui/viewmodel/SmartScheduleViewModelTest.kt` (mới)
- `docs/CLAUDE_AI_SCHEDULE_W1_REPORT.md` (chỉ bổ sung mục này)

Test hồi quy:

- Routine 0 completion với 7 ngày đã qua deadline không có MORNING_LATE_PATTERN; biên 13/14 ngày, future và completion của routine khác không kích hoạt sai. Điều chỉnh test suy ra MISSED cũ để có completion chứng minh đang sử dụng tick.
- Robolectric: hai ID lớn có cùng 32 bit thấp tạo hai PendingIntent complete/open riêng biệt, kiểm tra hash/URI/component/action/extras; ngày occurrence mới không ghi đè extras ngày cũ.
- Advisor với ageBand lạ/rỗng cho cùng kết quả với `4-5`, kiểm tra cả ngưỡng ngủ và focus.
- ViewModel với mask −1/128/Int.MAX_VALUE bỏ qua routine hỏng, mask 0/127 vẫn xử lý được; ageBand cũ vẫn có finding theo fallback; cố tình gây exception advisor để kiểm tra findings rỗng + log và phục hồi ở lần cập nhật kế tiếp.

Kết quả kiểm tra G2:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug
git diff --check
git diff --cached --check
```

- **PASS** — BUILD SUCCESSFUL trong 55 giây; 65 task (24 executed / 41 up-to-date).
- **172 test pass**, 0 failure/error/skipped; thêm 8 test mới so với W1 (và cập nhật 1 test cũ theo điều kiện F1). Ba suite trực tiếp: ScheduleAdvisorTest 22, RoutineNotificationManagerTest 2, SmartScheduleViewModelTest 3.
- Lint: 0 error/fatal, 203 warning trên toàn working tree; assembleDebug tạo APK thành công.
- `git diff --check` và `git diff --cached --check`: sạch.
- Giữ nguyên từng byte toàn bộ 19 file tracked có thay đổi sẵn lúc bắt đầu G2; chỉ stage đúng 7 file liệt kê ở trên. Phần W1_REPORT trước mục Fixes G2 không bị chỉnh sửa.
- Cùng branch `feature/smart-schedule-w1`; commit mới chỉ F1–F3. Không push, không sửa Manifest/Room/permission, không gọi AI, không sửa `.omc` hoặc thực hiện backlog ngoài F1–F3.
