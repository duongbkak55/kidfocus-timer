# Báo cáo G3 — E PASS sau cấp quyền exact alarm; giữ lịch sử các vòng

Ngày: 2026-09-28–2026-09-29 · Người chạy: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

**Kết quả mới nhất, bản `8f4a835` ngày 2026-09-29: E PASS khi cấp quyền qua thẻ mới — notification tiếng Anh, tạo sau giờ hẹn 19 ms.** Lượt tham khảo chưa có quyền trễ 132.811 giây. Test/build/lint vòng 2 PASS (264 test, 0 lint errors). A và B/C/D/F/G/H/I giữ bằng chứng đã đạt ở các vòng trước; lần này chỉ chạy lại E và luồng quyền mới. Xem **Fixes G3 vòng 2 / Chạy lại E trên Pixel** cuối báo cáo. Các mục lỗi/PASS/FAIL cũ bên dưới là lịch sử, không thay bằng kết quả chưa chạy lại.

## Kết luận phiên gốc — trước sửa PIN

**G3 chưa PASS: A PASS, B FAIL, C–I SKIP ở cấp kịch bản đầy đủ.** Sau khi Duong mở khóa và yêu cầu tiếp tục, đã lưu hồ sơ lớp 1, tạo Homework 21:00/45 phút, xem timeline và kiểm tra task tách giữa hai hồ sơ. Tuy nhiên, nhập đúng PIN 2468 để mở “Child’s daily rhythm / Weekly schedule advice” lại quay về màn PIN; không vào được lịch thông minh để đặt anchors, xem finding hoặc Apply/Undo. Đây là **lỗi mức Cao**, cần Claude review trước nghiệm thu.

Cuối phiên, điện thoại tự khóa khi đang chuẩn bị lưu task thông báo sau 3 phút. Kiểm tra foreground ngay trước lưu không còn nút Save schedule, nên không gửi tap; sau đánh thức thấy yêu cầu vân tay và dừng toàn bộ lệnh thiết bị theo yêu cầu của Duong. Task Reading 22:20 **chưa lưu**. Chưa thực hiện force-stop/persistence. Không vượt qua khóa máy/chốt PIN hoặc sửa code để tiếp tục test.

Đã thu crash buffer và logcat lọc FATAL/AndroidRuntime/ANR. Hai fatal stack thuộc **uiautomator**, không phải KidFocus; không thấy crash/ANR KidFocus trong phần log thu được. Cỡ chữ cuối phiên đã xác minh là **1.0** theo task; rotation khôi phục giá trị ban đầu.

## Thiết bị và bản thử

| Mục | Kết quả |
|---|---|
| Thiết bị đã xác minh bằng getprop | Pixel 9, Android **17**, API **37** |
| Kết nối | Wireless pairing thành công bằng endpoint IP Duong cung cấp; adb tự kết nối sau pairing |
| Target duy nhất | `adb-4A150DLAQ0013S-aauTsY._adb-tls-connect._tcp` |
| Branch / code HEAD | `feature/smart-schedule-w4` / `ce0220ce466ec33d0d0bd7c9599c505ec29e479c`; W4 G2 PASS theo review Claude |
| Working tree | Có 19 đường dẫn tracked release/Learning thay đổi từ trước. APK build từ shared working tree này, **không phải checkout sạch chỉ chứa commit W4**. SHA-256 cả 19 đường dẫn trước/sau G3 không đổi; không quy nguyên nhân lỗi cho riêng commit W4 khi chưa so sánh checkout sạch |
| Build / cài APK | **PASS** — JDK 17, assembleDebug thành công trong 18 giây, 44 task (10 executed / 34 up-to-date); `adb install -r` trả Success |
| Phiên tiếp tục | Không build/cài/xóa dữ liệu lại; giữ APK và dữ liệu A đã tạo |
| Cấu hình dịch vụ | Build với biến môi trường Firebase và RevenueCat rỗng, xác minh generated BuildConfig rỗng. Không sửa cấu hình nguồn, không gọi OpenRouter/Firebase production |
| Dữ liệu thử | Chỉ clear `com.kidfocusstudio.timer.debug` ở đầu phiên onboarding; không xóa dữ liệu app khác |
| Logcat | Clear trước mở app lần đầu; không clear lại giữa hai phiên. Cuối phiên thu `logcat -d -b crash` và general logcat lọc `FATAL|AndroidRuntime|ANR` |
| Cài đặt màn hình | Ban đầu font_scale 0.85 / accelerometer_rotation 0 / user_rotation 0; đã thử font 1.3 và rotation 1; cuối phiên xác minh **1.0 / 0 / 0** |
| Quyền thông báo | Ban đầu POST_NOTIFICATIONS chưa cấp. Đã dùng nút có sẵn Manage daily routines → Enable notifications → Allow trên hộp thoại hệ thống. Không thêm permission vào code/manifest; không cấp quyền báo thức chính xác |

APK SHA-256 đã cài: `ec0dc83d4546ae4420709c589885ee6179e17d66d0a67d0a46ad230473b8b56b`.

[Log build offline](g3/09-offline-build.txt) · [Phiên kết nối đầu](g3/10-connected-session-status.txt) · [Phiên tiếp tục và trạng thái cuối](g3/26-resumed-session-notes.txt) · [Logcat thực tế](g3/89-before-fix-logcat-crash.txt).

## Kịch bản A–I phiên gốc — trước sửa PIN

SKIP nghĩa là chưa đủ các điều kiện kiểm tra của cả kịch bản; các phần đã thực hiện được ghi riêng, không dùng kết quả một phần để tính PASS toàn bộ.

| Kịch bản | Trạng thái | Bằng chứng / ghi chú |
|---|---|---|
| A — Khởi động, onboarding, PIN 2468, khu phụ huynh | **PASS** | Đi qua 4 trang onboarding → Home → tạo/xác nhận PIN → trở về Home → mở Parent settings, nhập lại PIN và vào khu phụ huynh. [Onboarding](g3/03-A-onboarding.png), [Home trước PIN](g3/04-A-home-before-pin.png), [Tạo PIN](g3/05-A-pin-setup.png), [Xác nhận PIN](g3/06-A-pin-confirm.png), [Home sau tạo PIN](g3/07-A-pin-created-home.png), [Khu phụ huynh](g3/08-A-parent-area.png) |
| B — Lớp 1, wake 06:15 / bed 22:30 T2–T6, ngủ thiếu mức Cao | **FAIL** | Hồ sơ Grade 1 đã lưu: [ảnh 11](g3/11-B-grade1.png). Mở Child’s daily rhythm → PIN → nhập đúng 2468 → trở lại PIN trống, lặp lại được: [ảnh 12](g3/12-B-smart-gate-after-pin.png). Chưa thể đặt anchors hoặc xem SLEEP_SHORT |
| C — Apply bed 21:15, snackbar Undo, Khôi phục lịch trước | **SKIP** | Chặn bởi lỗi B; chưa có finding/suggestion hoặc ảnh Apply/Undo/Restore |
| D — Trường 07:00–16:30, bài tập 21:00/45 phút, timeline, bed 00:30 | **SKIP** | **Đạt phần task/timeline:** lưu Homework 21:00, Weekdays, focus 45 phút/break 15 phút; timeline hiển thị đúng task. [Trước chỉnh](g3/13-D-homework-before.png), [21:00/45 phút](g3/14-D-homework-2100.png), [Đã lưu](g3/15-D-homework-saved.png), [Timeline](g3/16-D-timeline-without-anchors.png). Chưa thể thêm ca học/giờ ngủ hoặc kiểm tra LATE_HOMEWORK và qua nửa đêm vì B |
| E — Task sau 3 phút, alarm URI, thông báo đúng giờ | **SKIP** | Đã bật quyền thông báo qua UI: [ảnh 23](g3/23-E-notifications-enabled.png). Chuẩn bị Reading 22:20 để lưu lúc 22:17: [ảnh 24](g3/24-E-reading-time.png). Máy tự khóa trước tap lưu; **không lưu task**, chưa đọc alarm URI hoặc kiểm chứng thời điểm nhận thông báo |
| F — Hai hồ sơ, lịch/giờ giấc/góp ý tách riêng | **SKIP** | **Đạt phần task:** tạo bé thứ hai `3-B / Ages 4–5`; chuyển sang bé này thì Homework là template chưa thêm, chuyển về Child thì task 21:00/45 phút còn nguyên. [Hai hồ sơ](g3/19-F-second-profile.png), [Lịch bé thứ hai](g3/20-F-second-profile-empty-schedule.png), [Chọn lại Child](g3/21-F-child-selected.png), [Homework trở lại](g3/22-F-child-homework-returned.png). Chưa kiểm tra anchors/findings vì B |
| G — Nhập nhanh/Ảnh/Nhờ AI sắp lại ẩn khi chưa config | **SKIP** | Cấu hình dịch vụ đã tắt; Daily schedule quan sát được không có Nhập nhanh. Chưa vào được Smart weekly schedule để kiểm đủ bộ nút AI. “Choose photo” ở TaskEdit là ảnh minh họa lưu trên máy, không phải nút AI nhập lịch từ ảnh; không mở photo picker |
| H — Font 1.3, Góp ý/Timeline, xoay ngang rồi font 1.0 | **SKIP** | Đã đổi font 1.3, dump thấy chữ lớn; rotation 1 tạo UI bounds landscape; đã trả font 1.0, rotation 0. [Ảnh chuyển cỡ chữ](g3/17-H-font-change-transition.png), [Ảnh đang xoay](g3/18-H-rotation-transition.png) được chụp trước khi chuyển cảnh ổn định, **không dùng để kết luận layout PASS**. Chưa chụp Góp ý do B hoặc chụp lại layout ổn định trước khi máy khóa |
| I — Force-stop + mở lại giữ dữ liệu | **SKIP** | Chưa force-stop; dừng khi cần mở khóa máy. Không reboot. Không dùng việc chuyển hồ sơ/đánh thức máy thay cho kiểm thử persistence |

Ảnh được chụp bằng adb screencap, thao tác bằng uiautomator dump và input. Không lưu ảnh launcher/màn hình khóa, mã ghép đôi hoặc khóa adb vào repo.

## Lỗi ứng dụng và điểm chặn

### G3-01 — Cao: PIN đúng vẫn không mở được Smart weekly schedule

Tái hiện:

1. Hoàn thành A, tạo PIN 2468 và dùng chính PIN đó vào Parent settings thành công.
2. Manage child profiles → sửa Child thành Grade 1 → Save.
3. Parent settings → Child’s daily rhythm / Weekly schedule advice.
4. Nhập 2468: không báo PIN sai, nhưng quay lại màn PIN với các chấm trống. Nhập lại vẫn quay về PIN.

Kỳ vọng: mở màn Smart weekly schedule sau xác minh PIN, cho phép đặt giờ giấc và xem góp ý. Thực tế: vòng lặp PIN chặn B/C và phần anchors/findings của D/F/G/H. [Ảnh sau PIN](g3/12-B-smart-gate-after-pin.png).

Đối chiếu source của bản đã build: `PinEntryScreen.kt:64–67` gọi `resetPinVerification()` trước `onSuccess()`; `AppNavigation.kt:283–293` chỉ render SmartScheduleScreen khi `pinVerified` là true, nếu false lại điều hướng vào PIN. Đây là nguyên nhân phù hợp với hành vi quan sát, cần Claude xác nhận; **không sửa trong task G3**. Parent settings thông thường vẫn mở được bằng PIN; đây không phải yêu cầu nhập mã khóa thiết bị.

### G3-02 — Thấp: ngôn ngữ trộn trong luồng A

Onboarding/PIN hiển thị tiếng Việt, Home/Parent settings hiển thị tiếng Anh trong cùng phiên. Đối chiếu ảnh 03/05/06 với 04/08. Chưa kiểm tra locale hệ thống hoặc xác định thay đổi nào gây ra; ghi nhận để reviewer xác nhận, không quy là regression W4.

### Chặn môi trường — máy tự khóa

Đầu phiên kết nối và cuối phiên tiếp tục đều gặp khóa máy cần thao tác tay. Lần cuối, khoảng 22:17, dump trước lưu E chỉ còn SystemUI; không có KidFocus/Save schedule nên không gửi tap. Sau đánh thức xác nhận có “Fingerprint sensor”; dừng mọi lệnh adb tiếp theo. Không đổi timeout/khóa bảo mật hoặc nhập credential máy.

Để chạy phần còn lại, Duong cần mở khóa và giữ màn hình sáng đủ thời gian cho phép thử thông báo. B/C và các phần lịch thông minh vẫn cần xử lý lỗi G3-01 trong một task sửa code được giao riêng.

## Crash logcat và giới hạn bằng chứng

[logcat-crash.txt](g3/89-before-fix-logcat-crash.txt) chứa output crash buffer thực tế (14.015 byte) và 137 dòng general log khớp bộ lọc. Có hai fatal stack lúc 22:05:20 và 22:06:49:

```text
java.lang.IllegalStateException: UiAutomationService ... already registered!
  at com.android.commands.uiautomator.DumpCommand.run(DumpCommand.java:78)
  at com.android.commands.uiautomator.Launcher.main(Launcher.java:83)
```

Đây là crash của tiến trình công cụ dump UI. Một số lệnh dump đầu phiên tiếp tục bị chạy chồng khi lệnh trước chưa hoàn tất; đã chuyển sang thao tác UI tuần tự. Không tính lỗi công cụ này là crash KidFocus. Trong crash buffer và phần general log đã thu **không có stack/ANR KidFocus**; general buffer có thể xoay vòng và kịch bản SKIP chưa có coverage, nên không kết luận toàn bộ ứng dụng đã qua kiểm tra crash. Không clear log để che lỗi công cụ.

## Lịch sử kết nối, trạng thái dữ liệu và phạm vi commit

Các lần USB/initial wireless bị chặn trước đây giữ ở [00](g3/00-adb-authorization.txt), [01](g3/01-build-debug.txt), [02](g3/02-wireless-preflight.txt). Checksum build ban đầu ở 01 thuộc artifact cũ chưa cài; APK đã cài là build offline ở 09. Endpoint `.local` pair thất bại, endpoint IP Duong đưa pair thành công; không lưu mã pairing. Không chọn một thiết bị mạng khác được discovery.

Trạng thái debug app cuối phiên: hai hồ sơ Child/Grade 1 và 3-B/Ages 4–5; active Child; Homework đã lưu 21:00/45 phút; Reading 22:20 chỉ là draft chưa lưu; PIN thử 2468; quyền thông báo đã cấp qua UI, exact alarm giữ nguyên. Không tạo school/sleep anchors, không chạy AI/login/mua.

Commit lần tiếp tục chỉ cập nhật báo cáo này và thêm bằng chứng mới trong docs/g3/. Không stage code, APK, task/review chưa tracked hoặc .omc. Kiểm tra 19 hash code/config intentional từ trước: không đổi; không reset/stash/push/merge/deploy/reboot. Các ảnh H được ghi rõ là chuyển cảnh, không làm bằng chứng nghiệm thu layout.


## Fixes G3

Ngày: 2026-09-28 · Yêu cầu Claude: `CLAUDE_AI_SCHEDULE_G3_FIXES.md` · Branch: `feature/smart-schedule-w4` · Base trước sửa: `62b61d0`.

### F-G3-1 — Phiên phụ huynh và cổng lịch

- Thêm `parentUnlocked: StateFlow<Boolean>` riêng trong `SettingsViewModel`. PIN đúng mở phiên; `PinEntryScreen` vẫn tiêu thụ/reset `pinVerified` như trước, nhưng không xóa phiên. PIN sai không mở phiên; không có PIN không mở phiên lịch.
- Hai route SmartSchedule/QuickSchedule dùng `parentUnlocked && hasPinSet`, chờ settings tải xong rồi mới quyết định cổng. Route PIN vẫn dùng màn `PinEntryScreen` thật và cùng thao tác navigate/pop như trước. Tách các route này vào `ParentScheduleNavigation.kt` để app và test chạy chính cùng phần điều hướng.
- Khóa phiên khi bấm Back hoặc Back hệ thống ở ParentSettings, khi `ProcessLifecycleOwner` nhận ON_STOP, hoặc sau **5 phút không hoạt động**. Hằng số chỉ ở `SettingsViewModel.PARENT_INACTIVITY_TIMEOUT_MILLIS`; thao tác chạm/phím qua `MainActivity.onUserInteraction` gia hạn bộ đếm. Phiên chỉ ở bộ nhớ, không lưu qua khởi động lại.
- Giữ logic vào ParentSettings/Cloud/AI của HEAD trong commit. Working tree có thay đổi các callback này từ trước; chúng không được đưa vào commit sửa G3. HEAD gọi `parentGateRoute` ở DailySchedule nhưng thiếu định nghĩa; chỉ đưa định nghĩa helper có sẵn vào commit và dùng lại cho hai cổng lịch, tránh nhân đôi logic và tránh phụ thuộc vào hunk chưa commit.

### F-G3-2 — Chuỗi theo locale vi/en

`PinEntryScreen` đã dùng resource cho tiêu đề tạo/xác nhận/xác minh, lời hướng dẫn, PIN sai/không khớp và nút Back. Onboarding cũng có chuỗi hard-code nên đã đổi toàn bộ tiêu đề, mô tả, Next/Skip/Get started sang resource. Các key và bản dịch **đã có đầy đủ trong HEAD** ở `values/strings.xml` (vi) và `values-en/strings.xml`; dùng lại chúng là phương án ít thay đổi nhất. Không sửa/stage hai file resource đang có thay đổi khác từ trước.

### Kiểm tra tự động

| Kiểm tra | Kết quả |
|---|---|
| `ParentSessionTest` | **6/6 PASS** — tiêu thụ PIN giữ phiên, PIN sai, thiếu PIN, rời khu phụ huynh, lifecycle stop, hết hạn/gia hạn 5 phút bằng thời gian giả |
| `ParentScheduleNavigationTest` | **9/9 PASS** — PIN đúng/sai và đưa Activity vào nền rồi mở lại cho cả Smart/Quick; kiểm màn lịch thật hiển thị, route đích và không quay lại PIN; thêm kiểm locale PIN vi/en và onboarding vi/en |
| `testDebugUnitTest` | **245/245 PASS**, 37 suite, 0 failed/errors/skipped |
| `assembleDebug` | **PASS**, APK debug mới đã build; chưa cài lại lên Pixel |
| `lintDebug` | **PASS**, 0 lỗi, 189 cảnh báo. Dependency lifecycle-process dùng cùng version 2.8.4 với lifecycle hiện có; cảnh báo phiên bản mới và version catalog không làm mở rộng phạm vi nâng cấp dependency |
| `git diff --check` | **PASS**; kiểm tra lại cả phần stage trước commit |

Test điều hướng dùng Robolectric API 28, Compose UI test và `TestNavHostController`, **SettingsViewModel thật**, `PinEntryScreen`, `SmartScheduleScreen` và `QuickScheduleScreen` thật. Chỉ repository/dữ liệu lịch/access ViewModel được mock; không gọi OpenRouter/Firebase/Google login/mua. Test ON_STOP khởi tạo Startup provider thật từ merged manifest, cô lập singleton AndroidX giữa các Application test, dùng `ActivityScenario` chuyển RESUMED → CREATED → RESUMED và đợi trễ process lifecycle. Không giả kết quả bằng việc gọi trực tiếp `vm.onStop` trong test điều hướng.

Lệnh kiểm tra đã chạy với JDK 17 và cấu hình dịch vụ rỗng:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  FIREBASE_API_KEY= FIREBASE_APP_ID= FIREBASE_DEBUG_APP_ID= \
  FIREBASE_PROJECT_ID= FIREBASE_WEB_CLIENT_ID= \
  REVENUECAT_TEST_API_KEY= REVENUECAT_ANDROID_API_KEY= REQUIRE_RELEASE_SERVICES=false \
  ./gradlew testDebugUnitTest assembleDebug lintDebug
git diff --check
git diff --cached --check
```

[Log kiểm tra và tổng số test](g3/27-g3-fixes-checks.txt). Build tổng hợp **BUILD SUCCESSFUL in 1m**, 65 task (22 executed / 43 up-to-date). Generated BuildConfig xác nhận các key Firebase/RevenueCat rỗng. APK `app/build/outputs/apk/debug/app-debug.apk` SHA-256: `b8fefac42dd448dd24275b3d33717d48fdf7647011e534615142b3b9ba6f205f`.

### Phạm vi và bước nghiệm thu tiếp — tại thời điểm commit sửa PIN

- Không thêm permission, không đổi manifest/Room (**v5**), không thêm WorkManager/service nền. Không reset/stash/sửa `.omc`, push/merge/deploy/reboot.
- Các lệnh build/test chạy trên shared working tree, có **19 file tracked thay đổi từ trước** như phiên G3 gốc, không phải checkout sạch. Hash của 16 file ngoài ba file chồng phạm vi vẫn nguyên; ba file `app/build.gradle.kts`, `MainActivity.kt`, `AppNavigation.kt` được stage theo từng hunk G3. Giữ các thay đổi release/Learning/permission/callback Home khác ở working tree, ngoài commit. Các task/review và test Learning chưa tracked không được stage.
- **Chưa chạy lại G3 máy thật và chưa chuyển bảng A–I cũ thành PASS.** Theo yêu cầu, chờ Duong báo Pixel đã mở khóa và bật giữ màn hình sáng; sau đó cài APK bằng `adb install -r` giữ dữ liệu, chạy lại B, C, D (anchors), E, F (anchors/góp ý), G, H, I và thu ảnh/logcat mới. A giữ kết quả PASS cũ.

## G3 chạy lại sau Fixes G3 — 2026-09-28

Duong báo “pixel ok, test đi”. Đã chạy lại B–I trên **Pixel 9 / Android 17 / API 37**, cùng thiết bị wireless ở trên. **B/C/D/F/G/H/I PASS, E FAIL; G3 chưa PASS toàn bộ.** G3-01 đã được kiểm chứng hết vòng lặp PIN; lỗi còn lại cần Claude review, không sửa code trong lần chạy thiết bị này.

### Bản thử và chuẩn bị

- Branch `feature/smart-schedule-w4`, code HEAD **`a2ba634`**. Build `assembleDebug` với cùng cấu hình dịch vụ rỗng ở mục Fixes G3: **BUILD SUCCESSFUL in 2s**, 44 task (1 executed / 43 up-to-date). `adb install -r` trả **Success**, giữ dữ liệu/PIN, không clear app. [Log build và cài](g3/28-retest-build.txt).
- APK SHA-256 **`b8fefac42dd448dd24275b3d33717d48fdf7647011e534615142b3b9ba6f205f`**, khớp APK đã kiểm thử tự động sau sửa PIN. Generated BuildConfig xác minh key Firebase/RevenueCat rỗng; không thao tác AI, đăng nhập hoặc mua.
- APK vẫn build từ shared working tree có **19 file tracked thay đổi từ trước**. Hash cả 19 file không đổi trong lần chạy lại. Không coi đây là APK từ checkout sạch chỉ chứa commit W4/G3. Không sửa/stage code, `.omc` hoặc task/review/Learning chưa tracked.
- Locale thiết bị **en-GB**. Đầu phiên font_scale **1.0**, accelerometer_rotation **0**, user_rotation **0**. Quyền POST_NOTIFICATIONS đã được cấp ở phiên gốc; lần chạy lại không cấp quyền nào. Exact alarm appop cuối phiên: `No operations. Default mode: default`.
- Clear logcat một lần đầu phiên chạy lại, không clear sau đó; cuối phiên thu cả crash buffer và general log theo bộ lọc yêu cầu. [Trạng thái cuối](g3/90-retest-final-status.txt).

### Bảng nghiệm thu mới

| Kịch bản | Kết quả | Bằng chứng / hành vi trên máy |
|---|---|---|
| A — Onboarding/PIN ban đầu | **PASS giữ từ phiên gốc** | Không chạy lại onboarding hoặc xóa dữ liệu. PIN kiểm chứng lại bằng [màn tiếng Anh](g3/30-retest-pin-english.png) → [mở lịch thành công](g3/31-B-smart-opens-after-pin.png). Onboarding locale chỉ có bằng chứng test tự động ở Fixes G3, không thêm kết luận kiểm trên Pixel |
| B — Lớp 1, thức 06:15 / ngủ 22:30 T2–T6, ngủ thiếu Cao | **PASS** | [Đã lưu giờ giấc](g3/32-B-weekdays-0615-2230.png); [finding Cao 465/540 phút, đề xuất 21:15](g3/33-B-sleep-short-high.png). [Hai hồ sơ xác nhận Grade 1](g3/51-F-two-profiles-grade1-ages45.png) |
| C — Áp dụng, Hoàn tác, Khôi phục lịch trước | **PASS** | Áp dụng **đêm thứ Hai được chọn** → ngủ 21:15, finding của đêm đó biến mất; snackbar [Apply/Undo](g3/34-C-applied-undo-snackbar.png), [dump sau Apply](g3/34-C-after-apply-ui.xml). [Undo trả 22:30/cảnh báo](g3/35-C-undo-restored-warning.png). Áp dụng lần nữa → [21:15](g3/36-C-bed-2115-before-restore.png), bấm Restore previous schedule (within 7 days) → [22:30](g3/37-C-restore-bed-2230.png). Các đêm khác giữ finding độc lập; một Apply không thay cả tuần |
| D — Ca học, task muộn, timeline, qua nửa đêm | **PASS** | Đặt rồi lưu ca học 07:00–16:30 Weekdays ([editor](g3/38-D-school-0700-1630.png), [timeline đã lưu](g3/40-D-timeline-school-homework-sleep.png)). Homework có sẵn giữ 21:00, focus 45/break 15 phút; [cảnh báo muộn mức Medium](g3/39-D-late-homework-warning.png). Chỉnh riêng đêm thứ Hai [00:30](g3/41-D-monday-bed-0030.png), lưu; [timeline thứ Ba](g3/42-D-tuesday-sleep-0030-0615.png) hiển thị ngủ 00:30–06:15 và ca học, không chuyển sai sang đêm thứ Ba |
| E — Task sau khoảng 3 phút, URI alarm, thông báo đúng giờ | **FAIL** | Reading đổi sang [23:15](g3/45-E-reading-future-time.png), [lưu 23:12:06](g3/46-E-reading-saved.png), còn 174 giây trước giờ chạy. Alarm có origWhen 23:15:00 và URI đúng, nhưng NotificationRecord được tạo **23:17:10.091**, trễ **130.091 giây**. [Timing](g3/47-E-notification-timing.json), [Alarm](g3/48-E-alarm-uri.txt), [PendingIntent/URI](g3/48-E-pending-intent-uri.txt), [Record thông báo](g3/49-E-notification-record.txt). Không đánh PASS chỉ vì có nhận thông báo |
| F — Hai hồ sơ, giờ giấc/lịch/góp ý tách riêng | **PASS** | Tái sử dụng hai hồ sơ đã tạo trong G3 gốc, không tạo bé thứ ba. 3-B/Ages 4–5 có [anchors/trường trống](g3/52-F-second-profile-empty-anchors.png), [không findings](g3/53-F-second-profile-no-findings.png), [task chỉ là template chưa thêm](g3/54-F-second-profile-task-templates.png). Trở về Child thì [anchors/ca học](g3/55-F-child-anchors-returned.png) và [findings](g3/56-F-child-findings-returned.png) còn |
| G — Nút AI ẩn khi chưa config | **PASS** | Kiểm Daily schedule và cuộn hết Smart weekly schedule: không có Nhập nhanh / AI nhập ảnh / Nhờ AI sắp lại. [Ảnh](g3/60-G-smart-ai-controls-hidden.png), [nhãn qua toàn bộ các trang cuộn](g3/61-G-smart-visible-labels.txt). Choose photo trong TaskEdit là ảnh minh họa local, không phải AI import; không mở picker. Đưa app vào nền rồi mở lại và vào lịch: [yêu cầu PIN](g3/62-G-background-requires-pin.png) → nhập 2468 → [vào lịch, không lặp PIN](g3/63-G-reentry-after-pin.png). Quick UI bị ẩn nên không thử đường vào Quick trên Pixel; luồng Quick có test thật ở Fixes G3 |
| H — Font 1.3, xoay ngang, trả 1.0 | **PASS** | [Timeline portrait font 1.3](g3/70-H-timeline-font13.png), [landscape ổn định 2424×1080](g3/71-H-timeline-landscape-font13.png), [Góp ý font 1.3](g3/72-H-advice-font13.png). Chữ/nút đọc được, nội dung cuộn dọc bình thường; hàng ngày của timeline cuộn ngang. [Trả font 1.0](g3/73-H-advice-font10-restored.png); xác minh cuối **1.0 / rotation 0 / accelerometer 0**, không crash |
| I — Force-stop + mở lại giữ dữ liệu | **PASS** | [Home trước force-stop](g3/80-I-before-force-stop-home.png) → `am force-stop` → mở lại [Home](g3/81-I-after-force-stop-home.png); PIN vẫn xác minh được. [Hai hồ sơ](g3/82-I-profiles-persisted.png), [anchors và ca học](g3/83-I-anchors-persisted.png), [giờ từng ngày](g3/84-I-individual-days-persisted.png), [timeline task/sleep/school](g3/85-I-tuesday-tasks-and-anchors-persisted.png), [nhãn xác minh](g3/85-I-persistence-visible-labels.txt) còn. Kết thúc ở [Home](g3/86-I-final-home.png). Không reboot |

### Lỗi và xác minh sau sửa

**G3-01 — Cao, đã sửa và kiểm chứng trên Pixel.** Nhập PIN 2468 mở Smart weekly schedule, thao tác B/C/D/F/H thành công. App vào nền rồi trở lại thì yêu cầu PIN mới; nhập đúng lại mở lịch, không vòng lặp. Đây là kiểm chứng UI thật, bổ sung cho 15 test phiên/điều hướng trong commit sửa. Lần này không chạy chờ 5 phút idle trên Pixel; trường hợp hết hạn đã có test thời gian giả ở Fixes G3.

**G3-02 — Thấp, phần PIN đã kiểm chứng theo locale en-GB.** Màn xác minh PIN dùng tiếng Anh, đồng nhất với Parent settings/lịch. Onboarding không chạy lại để giữ dữ liệu A; vi/en của cả hai luồng đã kiểm bằng test ở commit sửa. Lỗi notification mới bên dưới nằm ngoài phạm vi F-G3-2.

**G3-03 — TB: thông báo tới trễ 2 phút 10 giây trong cấu hình hiện tại.** Tái hiện: Child → Reading → đặt 23:15 every day → Save schedule lúc 23:12:06 → theo dõi đến sau giờ chạy. Bộ chọn giờ dùng bước 5 phút nên lưu cách mốc 174 giây, phù hợp phép thử “sau 3 phút” với sai số thao tác 6 giây. Mốc alarm `origWhen=2026-09-28 23:15:00.000`, `window=+2m10s41ms`. PendingIntentRecord **`c626cb4`** khớp giữa AlarmManager và bảng PendingIntent; Intent đến TaskAlarmReceiver có **`kidfocus://task/1790608779858144/2`** (thứ Hai). Android 17 không in URI ngay trong alarm record nên dùng đối chiếu này, không thay bằng khẳng định từ source.

NotificationRecord Reading có `mCreationTimeMs=mUpdateTimeMs=1790612230091` → **23:17:10.091 GMT+7**, trễ **130.091 giây** so với `1790612100000`. Monitor chưa thấy record đến +91 giây; lần đọc đầu thấy ở +132 giây, nhưng độ trễ tính từ timestamp hệ thống, không tính từ thời điểm poll. [Bản đọc tại giờ chạy](g3/49-E-notification-at-due.txt) và [record nhận thực tế](g3/49-E-notification-record.txt) được giữ. Không chụp notification shade vì có thông báo của app khác; chỉ lưu record KidFocus và ảnh editor/task.

Source `AlarmScheduler.kt` có fallback `AlarmManager.set` khi không được exact alarm, và `setAndAllowWhileIdle` khi exact bị SecurityException. **Suy luận:** window dương trên alarm phù hợp đường báo thức không chính xác trong cấu hình quyền hiện tại; chưa đủ bằng chứng quy thành regression W4 hoặc xác định nhánh fallback cụ thể đã chạy. POST_NOTIFICATIONS vẫn granted, không cấp/thay exact alarm để làm phép thử PASS. Đề nghị Claude quyết định tiêu chí “đúng giờ” và hướng xử lý; lần này chỉ ghi lỗi.

**G3-04 — Thấp: notification hard-code tiếng Việt trên máy en-GB.** Cùng E, record có title “📚 Đến giờ rồi!” và text “Bắt đầu Reading nào! 🎯”; màn editor/lịch tiếng Anh. Đối chiếu `TaskAlarmReceiver.kt:57–58` còn hard-code. Ngoài PIN/onboarding đã giao sửa, không mở rộng sửa trong task chạy máy.

### Logcat, dữ liệu cuối và phạm vi commit

[logcat-crash.txt của lần chạy sau sửa PIN](g3/119-before-v2-logcat-crash.txt): crash buffer **rỗng**, general filter có **85 dòng** khởi động/kết thúc AndroidRuntime của công cụ UI; không có FATAL/ANR hoặc stack KidFocus. Không clear log để loại lỗi; kết luận chỉ áp dụng phần B–I đã chạy. [Log gốc trước sửa](g3/89-before-fix-logcat-crash.txt) được sao nguyên byte và các liên kết lịch sử đã chuyển sang bản này, giữ hai lỗi uiautomator của phiên cũ.

General buffer cuối phiên đã xoay vòng, phần lọc còn từ 23:26:07; log giữa phiên ở 43 bổ sung một đoạn trước đó. Crash buffer rỗng là kết quả thu riêng, không dùng general buffer còn lại để khẳng định toàn bộ sự kiện hệ thống trong cả phiên đều được lưu.

Giữa D, Quick Settings phủ app làm guard dừng trước tap thứ Ba. Read-only trust xác nhận `deviceLocked=0`; XML xác nhận Quick Settings, đóng shade rồi tiếp tục. Không phải mở khóa bằng credential hay vượt khóa máy. [44](g3/44-retest-partial-status.txt) và [43](g3/43-retest-logcat-crash-partial.txt) là **bằng chứng giữa phiên**, không phải kết quả cuối; đã hoàn tất phần còn lại sau đó.

Dữ liệu cuối: Child/Grade 1 active; 3-B/Ages 4–5 chưa đặt anchors/ca học/task. Child thức 06:15 T2–T6; ngủ đêm T2 00:30 (rạng sáng T3), các đêm T3–T6 22:30; cuối tuần unset. School session 07:00–16:30 weekdays; Homework 21:00/45 focus/15 break weekdays; Reading 23:15/30 focus/5 break every day; PIN 2468. **Đầu lần chạy lại đã thấy Reading 22:20 là task lưu sẵn**, khác ghi nhận draft trong phiên gốc; lần này giữ dữ liệu và đổi task đó cho E, không dùng báo cáo cũ làm bằng chứng persistence. Cỡ chữ/hướng màn hình cuối đã trả về **1.0 / 0 / 0**.

Commit nghiệm thu lần này chỉ gồm báo cáo và `docs/g3/`: ảnh adb screencap, UI/alarm/notification evidence của KidFocus, logcat và ghi chú. Không lưu credential/pairing key hoặc notification app khác. Không sửa code/permission/Room, không WorkManager/service, không thay timeout/khóa máy, không clear dữ liệu, không reboot/login/mua/AI call/push/merge/deploy. Kiểm tra liên kết/ảnh, hash 19 file và diff trước commit; raw log được giữ nguyên whitespace theo cách kiểm riêng đã dùng ở phiên gốc.

Kiểm tra docs: **80 liên kết tồn tại**, PNG mới có chữ ký/kích thước đúng (1080×2424, landscape 2424×1080); 19 hash ngoài task giữ nguyên. `git diff --check` phần chưa stage PASS. `git diff --cached --check` toàn bộ báo 8 dòng trailing whitespace trong **bản sao raw log cũ 89**, không có lỗi file khác. Giữ nguyên bằng chứng; bản sao khớp byte với `HEAD:docs/g3/logcat-crash.txt`. Kiểm tra tách phạm vi trước commit:

```sh
git diff --cached --check -- . ':!docs/g3/89-before-fix-logcat-crash.txt'
git -c core.whitespace=-blank-at-eol diff --cached --check -- docs/g3/89-before-fix-logcat-crash.txt
```

Cả hai lệnh **PASS**; chỉ bỏ kiểm khoảng trắng cuối dòng cho raw log lịch sử, không đổi cấu hình git lưu trên máy. Lần này không thay code nên không chạy lại unit/lint; kết quả 245 test và lint của APK ở mục Fixes G3 vẫn là lần kiểm code gần nhất.

## Fixes G3 vòng 2

Ngày: **2026-09-29** · Theo phần “Vòng 2” của `CLAUDE_AI_SCHEDULE_G3_FIXES.md` · Base **`d4c9fc1`** · Branch `feature/smart-schedule-w4`.

### F-G3-3 — Quyền Báo thức & lời nhắc trong luồng lịch

- Tách `ExactAlarmPermission` dùng chung cho Routine và lịch: kiểm `canScheduleExactAlarms` từ API 31, mở `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` với URI package của chính app; Android cũ không mở special access. Nếu hệ thống không có màn cài đặt, helper trả lỗi an toàn, thẻ lịch có thông báo để thử lại.
- Thẻ ở **Lịch ngày** và **Góp ý lịch tuần**, có nút Open settings và Dismiss this reminder. Chỉ hiện khi có ít nhất một task bật, chưa có quyền và chưa đóng. Trạng thái đóng lưu riêng trong **DataStore** (`schedule_alarm_reminder_dismissed`), dùng chung giữa hai màn vì quyền áp dụng cho cả app; không đưa vào Room hoặc thay TimerSettings/cloud payload. Lưu settings thông thường không xóa trạng thái đóng.
- `ScheduleAlarmPermissionViewModel` dùng chung ở AppNavigation, observer theo lifecycle Activity nằm **trên các route PIN**. Quay lại từ cài đặt: kiểm quyền hiện tại, ẩn thẻ khi được cấp, gọi `scheduleAll(repository.getEnabledTasks())` cho **mọi hồ sơ**, kể cả hồ sơ không đang chọn. Vẫn lên lịch lại nếu thẻ đã đóng. Thực hiện khi quyền chuyển sang được cấp hoặc lần resume đầu của ViewModel có quyền (bao gồm app bị tạo lại); resume với quyền không đổi giữ alarm đang chờ. Cổng PIN/ON_STOP không được nới để mở lịch sau special access.
- Fallback thiếu quyền đổi **`set` → `setAndAllowWhileIdle`**; có quyền vẫn dùng `setExactAndAllowWhileIdle`. Nếu quyền mất giữa lúc kiểm và gọi exact, giữ fallback `setAndAllowWhileIdle` trong catch SecurityException. Fallback vẫn không cam kết đúng giờ.
- Chọn observer tại AppNavigation để việc reschedule không phụ thuộc Smart screen còn ở foreground sau khi đi cài đặt; đây là thay đổi nhỏ nhất xử lý được cổng PIN đã sửa ở vòng 1. Không thêm broadcast receiver, permission, WorkManager/service hoặc thay thuật toán tính lần chạy.

Hành vi xin special access và kiểm lại khi resume đối chiếu [Android Developers](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms); đặc tính báo thức không chính xác/Doze đối chiếu [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms).

### F-G3-4 — Notification theo locale

`TaskAlarmReceiver` dùng resource cho title/text, giữ emoji và tên task qua placeholder. `RoutineNotificationManager` dùng resource cho hạn hoàn thành, timer hint và action Done/Đã xong. Thêm **8 key vi/en** cho notification và thẻ quyền, không thay các chuỗi release/Learning có sẵn ở working tree. Timer hint dùng `\u0020` giữ khoảng trắng trước dấu • khi Android biên dịch resource. Không đổi ID, PendingIntent, URI, channel hoặc hành vi nhận thông báo.

### Kiểm tra và phạm vi commit sửa

| Kiểm tra | Kết quả |
|---|---|
| `testDebugUnitTest` | **264/264 PASS**, 41 suite, 0 failures/errors/skipped; 19 test mới so với bản sửa vòng 1 |
| Test API alarm | Thiếu quyền → inexact allow while idle; có quyền → exact allow while idle; SecurityException → fallback; giữ 2 test URI/cancel cũ |
| Helper/DataStore/VM | Special access đúng package và flag, API cũ không yêu cầu quyền, cài đặt không có không crash; DataStore thật giữ dismissal qua wrapper/settings write; resume có quyền reschedule enabled tasks mọi hồ sơ, không reschedule thường xuyên khi quyền không đổi; dismissal không tắt reschedule |
| Compose/lifecycle | Thẻ thật → nút cài đặt; `ActivityScenario` CREATED → RESUMED với quyền đã cấp → ẩn thẻ và reschedule; nút đóng thật → persistence callback → ẩn. 9 test điều hướng/PIN thật của vòng 1 vẫn PASS |
| Notification | Notification thực tế trong Robolectric đúng vi/en cho task, deadline/timer/action Routine; không có timer thì không có hint thừa; giữ các test PendingIntent/occurrence cũ |
| `assembleDebug` | **PASS**, APK SHA-256 `4a3cae9645fe298bc68ce91b4eac5c422d0caeb7d6bb31e505d3242fe6057f1f` |
| `lintDebug` | **PASS**, 0 lỗi / 189 cảnh báo |
| `git diff --check`, kiểm staged | **PASS**; chỉ stage hunk G3 của 4 file chồng phạm vi và file G3 liên quan |

Lệnh giống mục Fixes G3: JDK 17, Firebase/RevenueCat env rỗng, `./gradlew testDebugUnitTest assembleDebug lintDebug`. Lần kiểm cuối **BUILD SUCCESSFUL in 49s**, 65 task (23 executed / 42 up-to-date). [Log kiểm tra vòng 2](g3/91-g3-v2-checks.txt). Generated BuildConfig xác minh các key dịch vụ rỗng; test không gọi OpenRouter/Firebase/Google login/mua.

Working tree vẫn có 19 đường dẫn tracked từ trước. **15 đường dẫn không chồng phạm vi giữ nguyên hash**, gồm Manifest/build/dependency; 4 đường dẫn chồng là AppNavigation, RoutineScreens, strings vi/en được dựng patch chỉ chứa delta G3 so với snapshot đầu task. Các thay đổi Home/permission thông báo Routine/release/Learning còn ngoài commit. Không reset/stash/sửa `.omc`, không thêm permission mới (**SCHEDULE_EXACT_ALARM đã có**; không USE_EXACT_ALARM), Room **v5**, không WorkManager/service/push/merge/deploy/reboot.

**Nghiệm thu máy tại thời điểm commit sửa: chưa chạy lại E.** Sau commit, cài APK giữ dữ liệu; đo lượt không có quyền hiện tại trước để lấy số liệu tham khảo, rồi dùng chính thẻ mới cấp quyền và đo lượt có quyền (kỳ vọng <10 giây, notification tiếng Anh). Chỉ kết luận E từ timestamp/ảnh trên Pixel, không dùng kết quả unit test thay phép đo máy.

### Chạy lại E trên Pixel — sau commit `8f4a835`

**E PASS với quyền exact alarm**, đúng yêu cầu vòng 2. Thiết bị vẫn Pixel 9 / Android 17 API 37 / en-GB, cùng wireless target; [cài APK giữ dữ liệu](g3/92-v2-device-install.txt) bằng `adb install -r` trả Success. APK SHA-256 khớp bản kiểm tra ở trên. Quyền ban đầu chưa cấp, nên đo lượt tham khảo trước rồi bật qua thẻ, tránh phải thu hồi quyền hoặc làm app bị hệ thống đóng giữa phép thử. Không đổi permission bằng adb appops; chỉ đọc trạng thái và thao tác UI special access của KidFocus.

| Lượt đo | Lưu task / mốc hẹn (GMT+7, 2026-09-29) | Alarm trước giờ hẹn | NotificationRecord | Kết quả |
|---|---|---|---|---|
| Chưa có quyền — tham khảo | Reading1 lưu **06:02:03**, hẹn **06:05:00**, lead **177 giây** | `window=+2m12s758ms`, flags `0x20`; appop default chưa cấp. [Alarm](g3/98-E-reference-alarm.txt), [URI đối chiếu](g3/98-E-reference-identity-proof.txt) | `mCreationTimeMs=mUpdateTimeMs=1790636832811` → **06:07:12.811**, trễ **132.811 giây**. [Timing](g3/98-E-reference-timing.json), [record](g3/98-E-reference-notification.txt) | **Số liệu tham khảo**, không coi fallback là exact. [Ảnh thông báo tiếng Anh](g3/99-E-reference-notification-english.png) |
| Đã có quyền — nghiệm thu E | Reading lưu **06:12:03**, hẹn **06:15:00**, lead **177 giây** | **`window=0 exactAllowReason=permission`**, flags `0x5`; appop allow. [Alarm](g3/108-E-exact-alarm.txt), [URI đối chiếu](g3/108-E-exact-identity-proof.txt) | `mCreationTimeMs=mUpdateTimeMs=1790637300019` → **06:15:00.019**, trễ **19 ms**. [Timing](g3/108-E-exact-timing.json), [record](g3/108-E-exact-notification.txt) | **PASS <10 giây**, [heads-up tiếng Anh thực tế](g3/109-E-exact-notification-english.png): “📚 It’s time!” / “Time to start Reading! 🎯” |

Task dùng lại Reading hiện có, cùng ID **1790608779858144**, Calendar day **3** (thứ Ba), URI **`kidfocus://task/1790608779858144/3`**. Tạm đặt tên Reading1 cho lượt tham khảo để tách Notification ID theo tên; trả tên Reading cho lượt exact. Đối chiếu PendingIntentRecord **`3e34885`** (tham khảo) và **`d4e47b6`** (exact) giữa [bảng intents lượt 1](g3/98-E-reference-pending-intents.txt)/[lượt 2](g3/108-E-exact-pending-intents.txt) với alarm tương ứng. Không chỉ suy URI từ source. Bộ chọn phút bước 5; tự chờ đến mốc hẹn trừ 3 phút rồi lưu, thao tác hoàn tất lệch 3 giây nên lead thực tế 177 giây ở cả hai lượt. [Giờ tham khảo](g3/96-E-reference-time-prepared.png), [chuẩn bị lưu](g3/97-E-reference-save-ready.png), [đã lưu](g3/98-E-reference-saved.png); [giờ exact](g3/106-E-exact-time-prepared.png), [chuẩn bị lưu](g3/107-E-exact-save-ready.png), [đã lưu](g3/108-E-exact-saved.png).

Độ trễ tính bằng timestamp hệ thống trừ `targetEpoch×1000`, không lấy lúc script poll thấy làm thời điểm giao thông báo. Record exact còn có `mInterruptionTimeMs=1790637301161` (+1.161 giây); script thấy record ở +1 giây và chụp được heads-up. Hai ảnh là **PNG nguyên bản từ adb screencap**, không cắt/chỉnh/tạo lại nội dung; không mở notification shade và không đưa thông báo app khác vào repo.

### Quyền và reschedule kiểm chứng trên máy

1. Khi chưa có quyền, thẻ xuất hiện ở cả [Góp ý](g3/94-v2-smart-exact-permission-card.png) và [Lịch ngày](g3/95-v2-daily-exact-permission-card.png).
2. Từ Lịch ngày → Open settings mở đúng trang **KidFocus → Alarms and reminders**, [switch tắt](g3/101-E-exact-settings-before-grant.png). Bật switch qua UI → [đã cấp](g3/102-E-exact-settings-granted.png).
3. Back trở về app, [thẻ Lịch ngày biến mất](g3/103-E-daily-card-hidden-after-grant.png). **Trước khi sửa/lưu task cho lượt exact**, đối chiếu [alarm trước cấp quyền](g3/100-E-before-permission-alarms.txt) với [sau trở lại app](g3/104-E-after-permission-alarms.txt): các task đã lưu, gồm Homework 21:00 và Reading các ngày sau, chuyển sang `window=0 exactAllowReason=permission`. [Appop đọc lại](g3/104-E-permission-status.txt) là allow. Đây là bằng chứng reschedule thực tế của luồng trở lại, không dùng việc Save task lần sau thay cho reschedule.
4. Vào lại khu phụ huynh vẫn phải nhập PIN 2468 do app đã đi nền; PIN đúng mở Smart, [thẻ Góp ý cũng ẩn](g3/105-E-smart-card-hidden-after-grant.png). Không bypass chốt PIN để xin quyền. Dismiss/persistence đã kiểm bằng DataStore thật và Compose ở test; không bấm Dismiss trên Pixel để giữ luồng cấp quyền qua thẻ.

**G3-03:** thiếu đường xin quyền trong lịch đã xử lý và kiểm trên máy; E với quyền đạt. Fallback chưa có quyền vẫn có thể trễ, đúng cảnh báo của thẻ; một mẫu 132.811 giây không phải cam kết độ trễ chung. **G3-04:** notification task tiếng Anh đã kiểm trên Pixel; Routine vi/en kiểm bằng notification thật trong Robolectric, không tạo thêm routine để mở rộng kịch bản E. F-G3-1/2 giữ kết quả Claude đã xác nhận.

### Kết thúc lượt máy vòng 2

Sau phép đo, trả task Reading về **23:15 every day, 30 focus / 5 break**, tên/ID như trước: [editor](g3/110-E-original-reading-time-restored.png), [lịch đã lưu lại](g3/111-E-reading-schedule-restored.png), [Home cuối](g3/112-E-final-home.png). Homework/anchors/ca học/hồ sơ/PIN giữ nguyên. Quyền exact giữ **allow** theo luồng người dùng vừa cấp; không thu hồi. Cỡ chữ/rotation giữ **1.0 / 0 / 0**, không thay timeout, khóa máy hoặc locale.

[Logcat vòng 2](g3/logcat-crash.txt) thu cuối phiên: **crash buffer rỗng**, general filter **86 dòng** runtime của công cụ UI, không FATAL/ANR hoặc stack KidFocus trong output thu được. [Ghi chú trạng thái cuối](g3/120-v2-device-final-status.txt), [alarm sau trả Reading 23:15](g3/113-E-restored-task-alarms.txt). Clear một lần sau cài APK, không clear để loại lỗi giữa các lượt. Log từ vòng trước sao nguyên byte sang [119](g3/119-before-v2-logcat-crash.txt) và liên kết lịch sử giữ đúng phiên. General buffer cuối còn từ 06:15:53, không dùng log đã xoay vòng để khẳng định mọi sự kiện của toàn phiên đều được lưu.

Không thay code sau commit sửa; hash **240 file tracked ngoài docs** giữ nguyên trong phần chạy máy. Commit bằng chứng tiếp theo chỉ `docs/g3/` + báo cáo; giữ 19 thay đổi tracked có từ trước ở working tree và các task/review/Learning chưa tracked. Không thêm permission/Room/WorkManager/service, không clear dữ liệu/reboot/Google login/mua/AI call/push/merge/deploy. A/B/C/D/F/G/H/I không chạy lại toàn bộ trên APK vòng 2; giữ bằng chứng các vòng trước theo đúng phạm vi Claude giao. Lượt này máy mở khóa, màn hình sáng/đang sạc và app foreground; không dùng hai số đo này làm kiểm thử Doze hoặc bảo đảm SLA trên mọi thiết bị.

Kiểm bằng chứng trước commit: **116 liên kết tồn tại**, 18 ảnh PNG mới đúng chữ ký/kích thước 1080×2424; log lịch sử 119 khớp byte với bản trước; 240 hash code/config ngoài docs không đổi. Full staged diff báo trailing whitespace và blank line cuối trong **record notification raw lượt tham khảo 98, dòng 86** (dòng chỉ có hai dấu cách từ output hệ thống). Giữ nguyên record; phần còn lại kiểm chuẩn và record này kiểm riêng **PASS**:

```sh
git diff --cached --check -- . ':!docs/g3/98-E-reference-notification.txt'
git -c core.whitespace=-blank-at-eol,-blank-at-eof diff --cached --check -- docs/g3/98-E-reference-notification.txt
```

Không thay cấu hình git lâu dài; kiểm code ở commit sửa vẫn `git diff --check` và staged check chuẩn PASS, không áp ngoại lệ này cho code.
