# Báo cáo G3 — Đã chạy A, dừng để Duong mở khóa điện thoại

Ngày: 2026-09-28 · Người chạy: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

## Kết luận

**G3 chưa hoàn tất: A PASS; B–I SKIP.** Đã ghép đôi wireless, build/cài APK debug và chạy onboarding, tạo PIN thử 2468, nhập lại PIN để vào khu phụ huynh. Khi chuẩn bị sửa hồ sơ cho B, điện thoại tự chuyển sang màn hình khóa và yêu cầu vân tay/mã mở khóa. Dừng thao tác theo yêu cầu của Duong khi cần thao tác tay; không thử nhập mã khóa máy hoặc vượt qua màn hình khóa.

**Cần Duong mở khóa điện thoại, giữ KidFocus ở phía trước và cho tiếp tục B–I.** Hồ sơ mặc định vẫn là `Child / Ages 4–5`; chưa đặt lớp 1, giờ giấc hoặc task. Không tính G2/unit test thay cho G3. Chưa có đủ bằng chứng để nghiệm thu Smart Weekly Schedule trên máy thật.

## Thiết bị và bản thử

| Mục | Kết quả |
|---|---|
| Thiết bị đã xác minh bằng getprop | Pixel 9, Android **17**, API **37** |
| Kết nối | Wireless pairing thành công bằng endpoint IP Duong cung cấp; adb tự kết nối sau pairing |
| Target duy nhất | `adb-4A150DLAQ0013S-aauTsY._adb-tls-connect._tcp` |
| Branch / code HEAD | `feature/smart-schedule-w4` / `ce0220ce466ec33d0d0bd7c9599c505ec29e479c`; W4 G2 PASS theo review Claude |
| Working tree | Có 19 đường dẫn tracked release/Learning thay đổi từ trước. APK build từ shared working tree này, **không phải checkout sạch chỉ chứa commit W4**. Không sửa những đường dẫn đó; SHA-256 trước/sau của cả 19 đường dẫn không đổi |
| Build | **PASS** — JDK 17, `assembleDebug`, BUILD SUCCESSFUL trong 18 giây; 44 task, 10 executed / 34 up-to-date |
| Cấu hình dịch vụ | Build với biến môi trường Firebase và RevenueCat rỗng, kiểm tra giá trị generated BuildConfig rỗng. Không sửa file cấu hình nguồn; không đăng nhập Google, mua gói hoặc gọi OpenRouter/Firebase production |
| Cài APK | **PASS** — `adb install -r app/build/outputs/apk/debug/app-debug.apk` trả Success |
| Dữ liệu thử | Chỉ `pm clear com.kidfocusstudio.timer.debug` để chạy onboarding mới; không xóa dữ liệu cá nhân của app khác |
| Mở app | `com.kidfocusstudio.timer.debug/com.kidfocus.timer.MainActivity` |
| Logcat đầu phiên | Đã chạy `adb logcat -c` trước mở app |
| Logcat cuối phiên | **SKIP** — dừng toàn bộ lệnh thiết bị khi xác định cần Duong mở khóa. Chưa thu crash buffer hoặc lọc FATAL/AndroidRuntime/ANR; không kết luận “không crash” |

APK SHA-256 đã cài: `ec0dc83d4546ae4420709c589885ee6179e17d66d0a67d0a46ad230473b8b56b`.

[Log build offline và cấu hình thử](g3/09-offline-build.txt) · [Ghi chú phiên kết nối và lý do dừng](g3/10-connected-session-status.txt).

## Kịch bản A–I

| Kịch bản | Trạng thái | Bằng chứng / ghi chú |
|---|---|---|
| A — Khởi động, onboarding, PIN 2468, khu phụ huynh | **PASS** | Đi qua 4 trang onboarding → Home → tạo và xác nhận PIN 2468 → trở về Home → mở Parent settings, nhập lại PIN → vào khu phụ huynh. [Onboarding](g3/03-A-onboarding.png), [Home trước PIN](g3/04-A-home-before-pin.png), [Tạo PIN](g3/05-A-pin-setup.png), [Xác nhận PIN](g3/06-A-pin-confirm.png), [Home sau tạo PIN](g3/07-A-pin-created-home.png), [Khu phụ huynh](g3/08-A-parent-area.png) |
| B — Lớp 1, wake 06:15 / bed 22:30 T2–T6, ngủ thiếu mức Cao | **SKIP** | Đã mở Manage child profiles; chưa sửa hồ sơ. Màn hình khóa chặn bước tiếp theo |
| C — Apply bed 21:15, snackbar Undo, Khôi phục lịch trước | **SKIP** | Chưa đặt dữ liệu B, chưa Apply/Undo/Restore |
| D — Trường 07:00–16:30, bài tập 21:00/45 phút, timeline, bed 00:30 | **SKIP** | Chưa tạo ca học/task hoặc kiểm tra timeline qua nửa đêm |
| E — Task sau 3 phút, alarm URI, thông báo đúng giờ | **SKIP** | Chưa tạo task, chưa kiểm tra dumpsys alarm hoặc nhận thông báo |
| F — Hai hồ sơ, dữ liệu tách riêng | **SKIP** | Chưa tạo hồ sơ thứ hai hoặc chuyển qua lại |
| G — Nhập nhanh/Ảnh/Nhờ AI sắp lại ẩn khi chưa config | **SKIP** | Cấu hình dịch vụ đã tắt để thử offline, nhưng chưa mở đủ màn hình để kiểm chứng nút AI ẩn |
| H — Font 1.3, Góp ý/Timeline, xoay ngang rồi font 1.0 | **SKIP** | Chưa thay font scale/rotation; không có cài đặt thử cần hoàn nguyên |
| I — Force-stop + mở lại giữ dữ liệu | **SKIP** | Chưa chạy kịch bản persistence. Không reboot máy |

Ảnh được chụp bằng `adb exec-out screencap -p`; thao tác app bằng `uiautomator dump` và `input`. Trước mỗi tap theo nhãn, xác nhận KidFocus đang ở foreground. Không lưu ảnh launcher/màn hình khóa hoặc mã ghép đôi/khóa adb vào repo.

## Lỗi và điểm chặn

1. **Chặn môi trường — cần thao tác tay, không phải lỗi app:** điện thoại tự vào màn hình khóa khi phiên chạy đang ở Child profiles. UI dump tiếp theo không có KidFocus foreground nên không gửi tap sửa hồ sơ. Lệnh đánh thức và đưa KidFocus ra trước vẫn hiển thị khóa máy có yêu cầu vân tay. Diagnostic window có `showing=true`; power trước khi đánh thức có `mWakefulness=Dreaming`. Đã dừng, chờ Duong mở khóa. Không đổi timeout/khóa bảo mật của điện thoại.
2. **Thấp — quan sát ngôn ngữ trộn, cần reviewer xác nhận:** onboarding/PIN hiển thị tiếng Việt, còn Home/Parent settings hiển thị tiếng Anh trong cùng phiên. Tái hiện sau xóa dữ liệu debug: chạy onboarding → tạo PIN → vào Parent settings; đối chiếu ảnh 03/05/06 với 04/08. Chưa kiểm tra locale hệ thống hoặc xác định phạm vi lỗi; không ghi đây là lỗi W4 đã xác nhận, không sửa code.

Chưa tìm thấy lỗi chức năng trong phần A đã thực hiện. Không có crash logcat thực tế để trích dẫn; yêu cầu thu `logcat -d -b crash` và lọc `FATAL|AndroidRuntime|ANR` vẫn còn phải làm khi tiếp tục phiên. Không tạo file crash giả hoặc suy từ việc mở app thành công rằng toàn bộ phiên không crash.

## Những lần kết nối trước

- USB: ban đầu adb liệt kê máy, sau đó đọc thông tin trả `device not found` / `device still authorizing`; dừng trước cài APK. [Bằng chứng USB](g3/00-adb-authorization.txt), [build ban đầu chưa cài](g3/01-build-debug.txt). Checksum trong log build cũ thuộc artifact trước khi build lại offline, không phải APK hiện đã cài.
- Wireless lần đầu: inventory và mDNS trống; dừng để lấy endpoint. [Bằng chứng discovery](g3/02-wireless-preflight.txt).
- Wireless lần này: endpoint `.local` pair thất bại; endpoint IP Duong cung cấp pair thành công, đúng Pixel 9 đã xác minh. Không lưu mã pairing vào tài liệu hoặc commit. Một thiết bị mạng khác được discovery nhưng không được chọn hay thao tác.

## Tiếp tục và phạm vi commit

Sau khi Duong mở khóa: kiểm tra lại đúng thiết bị và app; giữ PIN/dữ liệu A hiện có, chạy B–I; chụp ảnh trước/sau bước chính; thu logcat cuối phiên. Nếu cần thao tác tay mới thì dừng và ghi lại. Giữ các giới hạn không sửa code, không production AI/Firebase, không Google login/mua, không reboot/push/merge/deploy.

Commit lần này chỉ gồm báo cáo này và bằng chứng mới trong `docs/g3/` (6 ảnh A, log build offline, ghi chú kết nối/dừng). Các bằng chứng USB/wireless cũ được giữ nguyên. Không stage code, APK, task/review chưa tracked hoặc `.omc`; không reset/stash. Đã kiểm tra hash của 19 đường dẫn tracked thay đổi có sẵn: không có thay đổi ngoài tài liệu G3 do task này.
