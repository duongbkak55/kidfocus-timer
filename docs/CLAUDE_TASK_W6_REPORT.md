# W6 — Chỉnh thời gian Tập trung

Branch: `feature/w6-focus-time`, tạo từ `main` tại `71ec458`. Code commit: `fabaaf3`. Phạm vi theo `CLAUDE_TASK_W6_FOCUS_TIME.md`.

## Đã triển khai

- Tập trung nhanh: chọn 5–120 phút theo nấc 5 phút bằng mặt đồng hồ hoặc nút −5/+5, có phản hồi rung; mặc định lấy từ cài đặt, chỉ áp dụng cho phiên đang tạo. Việc trong lịch vẫn dùng thời lượng đã lưu.
- Trong phiên Focus đang chạy hoặc tạm dừng: thêm +5 phút bằng nút lớn; mặt đồng hồ chỉ nhận thao tác sau khi giữ một giây và tự khoá sau ba giây. Giới hạn tổng 120 phút và giới hạn thêm trong phiên theo cài đặt phụ huynh (0/15/30/60, mặc định 30). Service nhận lệnh qua binder/Intent, cập nhật thời gian còn lại và thông báo; Break không được cộng.
- Nhật ký W5 giữ giờ kết thúc thực tế. Số phút tự thêm được lưu trong DataStore theo ID log timer của phiên có việc trong lịch, không đổi Room. `TASK_OVERRUN` không tính phần vượt được người dùng chủ động cộng; log đã sửa tay không dùng metadata đó. Metadata này hiện chỉ có trên thiết bị tạo log, chưa đồng bộ giữa thiết bị.
- Thêm loại việc `TEST_PRACTICE` trong nhóm học tập, picker và nhãn; miễn `FOCUS_TOO_LONG`, cấm đề xuất `REMOVE` qua ADVISE, giới hạn 120 phút. Cập nhật bộ loại việc và prompt PARSE của Functions.

Giới hạn 120 phút là **thời gian tập trung** của task trong app. Payload ADVISE hiện gửi `durationMin` bằng thời gian tập trung cộng giờ nghỉ, nên giữ trần đầu vào chung 150 phút để task tập trung 120 phút vẫn được tư vấn; đề xuất `RESIZE` và PARSE vẫn giới hạn 120 phút. Đây là cách giữ tương thích với payload hiện hữu mà không đổi hợp đồng API.

## Kiểm tra

- `./gradlew testDebugUnitTest assembleDebug lintDebug`: PASS (JDK 17).
- `npm --prefix functions test`: PASS 172/172; `npm --prefix functions run lint`: PASS.
- `git diff --check`: PASS.
- Compose/Robolectric: chọn phút theo nấc, nhấn giữ 0,8 giây vẫn khoá, giữ 1,1 giây mở chế độ chỉnh, tự khoá sau 3 giây, không có thao tác giảm. Unit: trần tổng/phiên, tắt cài đặt, pause rồi cộng, phần thời gian còn lại và quá giờ.
- PARSE text live eval một lượt do prompt đổi: `google/gemini-2.5-flash`, `data_collection: deny`, 26/26 câu đạt (100%); trung bình 775,5 token vào, 138,4 token ra và 0,00057861 USD/lượt. Dùng câu synthetic; không gửi dữ liệu trẻ thật, không lưu/in key.

## G3 Pixel

PASS trên Pixel 9 (Android 17) qua ADB không dây. Pixel 3a không xuất hiện trong ADB; dùng Pixel 9 đang kết nối và tài khoản thử trên máy. Cài APK bằng `adb install -r` để giữ dữ liệu.

1. Lúc 08:42 chọn 50 phút trong hộp thoại Tập trung nhanh; [`01_choose_50_min.png`](g3/w6/01_choose_50_min.png).
2. Bắt đầu, bấm +5 hai lần. UI hiện “Added 10 minutes”; thời gian còn lại và thông báo hệ thống tăng lên gần 60 phút (`59:41` khi đo). [`02_extended_10_min.png`](g3/w6/02_extended_10_min.png), [`03_running_60_min.png`](g3/w6/03_running_60_min.png).
3. Khoá màn hình bằng phím nguồn; sau bốn giây, thông báo vẫn đếm (`58:50`). Sau khi Duong mở khoá bằng vân tay, app trở về Focus và tiếp tục đếm (`50:19`); [`04_after_lock_unlock.png`](g3/w6/04_after_lock_unlock.png). Không nhập PIN khoá máy.
4. Bấm Dừng trên màn Focus. Màn Thực tế & So sánh có log “Free focus timer”, `08:42` đến `08:52`, nhãn “From timer”; [`05_actual_log_stopped.png`](g3/w6/05_actual_log_stopped.png). Thông báo timer không còn trong danh sách thông báo hiện hành. Cài đặt “Hide time” được đưa về trạng thái ban đầu sau khi chụp bằng chứng.

Không thấy app crash trong luồng G3. Chưa thử trên Pixel 3a vì máy đó không kết nối; Pixel 9 đã bao phủ toàn bộ kịch bản yêu cầu.

## Ràng buộc và triển khai

- Không thêm permission, không đổi Room schema, không sửa `.omc` hay 19 file đang sửa dở ở working tree gốc. Không push, merge hoặc deploy W6.
- Không có thay đổi Firebase Functions/Remote Config nào được triển khai từ nhánh này. Cần review G2, nghiệm thu G3 rồi mới xem xét phát hành.
