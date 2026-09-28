# Báo cáo G3 — FAIL ở chốt PIN; các phần còn lại chưa nghiệm thu đủ

Ngày: 2026-09-28 · Người chạy: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

## Kết luận

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

[Log build offline](g3/09-offline-build.txt) · [Phiên kết nối đầu](g3/10-connected-session-status.txt) · [Phiên tiếp tục và trạng thái cuối](g3/26-resumed-session-notes.txt) · [Logcat thực tế](g3/logcat-crash.txt).

## Kịch bản A–I

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

[logcat-crash.txt](g3/logcat-crash.txt) chứa output crash buffer thực tế (14.015 byte) và 137 dòng general log khớp bộ lọc. Có hai fatal stack lúc 22:05:20 và 22:06:49:

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
