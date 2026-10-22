# Fix UX P0/P1 — báo cáo cho Claude review

Branch: `fix/ux-p0p1`, từ `main d0d6703`. Worktree: `/Users/duongnguyen/.codex/worktrees/ux-p0p1/KidFocusTimer`.

Nguồn yêu cầu: [Review BA/QA](UI_UX_BA_QA_REVIEW_2026-10-22.md), UX-01…04. File review gốc và hai ảnh bằng chứng đi kèm được đưa vào commit này.

## Hành vi sau sửa

### UX-01 — cổng PIN cho tài khoản và dữ liệu cloud

- Route `CloudSync` dùng cùng parent gate tại destination: Home avatar, phần phụ huynh, trang gói, mở lại/back stack đều qua một cổng. Khi chưa có PIN phải thiết lập trước, không hiện màn tài khoản.
- Chỉ sau PIN đúng mới có đăng nhập/tạo tài khoản/reset mật khẩu/đồng bộ/đăng xuất/xóa cloud. Dialog chọn gộp dữ liệu khách cũng chỉ hiện trong phiên phụ huynh đã mở khóa.
- Giữ xác nhận “Xóa vĩnh viễn” hiện có. Không thử xóa tài khoản thật.
- Về khu vực trẻ sẽ khóa phiên phụ huynh; cơ chế ON_STOP và timeout cũ vẫn được dùng. Gate chỉ điều hướng khi chính màn đó đang ở trên cùng, tránh màn đang đóng tự mở lại PIN.
- PIN destination được URI-encode để editor có tham số giữ đúng ID/giờ qua màn PIN.

### UX-02 — dấu cộng của trẻ không sửa lịch tuần

- Dấu cộng trên Lịch ngày mở “Chỉ phiên hôm nay”: nhập tên, chọn số phút và bắt đầu ngay bằng `startFocusForActivity`. Không ghi `ScheduledTask`.
- Có lựa chọn riêng “Lặp hằng tuần (phụ huynh)”; lựa chọn này đi qua gate của editor trước khi mở biểu mẫu lịch tuần.
- Editor cũng ghi rõ phạm vi lặp hằng tuần. Khi tạo mới, phụ huynh có thể chuyển sang “Chỉ phiên hôm nay”.
- Cả danh sách Sửa kế hoạch và mọi đường vào editor có tham số đều được bảo vệ tại destination.
- **Giải thích phạm vi:** “Chỉ phiên hôm nay” là hoạt động tự do bắt đầu ngay, đúng phương án tối thiểu trong review; không phải đặt một lịch riêng theo ngày. Mô hình lịch tuần hiện không có ngày áp dụng riêng. Không giả tạo lịch một lần bằng thứ trong tuần, không dùng nhật ký thực tế làm kế hoạch, không đổi schema Room hoặc sync.

### UX-03 — mỗi hoạt động cùng loại quản lý theo ID

- Mỗi nhóm loại liệt kê tất cả hoạt động đã lưu, sắp theo giờ/phút/ID, có edit và toggle riêng. Không còn `find` rồi chỉ hiện một mục mỗi loại.
- Giữ danh sách mẫu để thêm nhiều mục cùng loại; mẫu ghi “+ Thêm …”, tách với hoạt động đã lưu. Hoạt động custom vẫn có hàng riêng.
- Sửa/xóa mục thứ hai dùng đúng ID; sửa mục đang tắt giữ trạng thái tắt. Việc giữ `enabled` là thay đổi kèm trực tiếp để editor không làm thay đổi hành vi ngoài ý muốn.

### UX-04 — web không thay phiên đang chạy một cách im lặng

- Ngoài màn Focus có thẻ phiên hiện tại và “Quay lại phiên tập trung”, có trên cả điện thoại và desktop.
- Chọn lại đúng hoạt động đang chạy mở lại timer, không reset. Chọn hoạt động khác mở xác nhận; hủy giữ nguyên phiên.
- Dừng sớm hoặc rời tài khoản cũng hỏi trước. Xác nhận sẽ lưu phiên cũ với thời lượng đã thực sự tập trung, `completed=false`, đúng task/profile, rồi mới chuyển việc/rời tài khoản.
- Lưu thất bại: giữ phiên tạm dừng, hiện lỗi ngay trong dialog, cho thử lại. Không xóa trạng thái trước khi ghi thành công. Một session ID được dùng xuyên suốt các lần thử lưu để tránh nhân đôi khi kết quả ghi không rõ ràng.
- Thời gian tạm dừng không cộng vào focus. Hoàn thành tự nhiên vẫn chỉ ghi một session `completed=true`; nếu vừa hết giờ trong lúc đang hỏi đổi việc thì đóng xác nhận đã hết hiệu lực.
- Dialog có focus bàn phím, Tab giữ trong dialog, Escape hủy. Reload/đi link ngoài có cảnh báo rời trang của trình duyệt khi timer còn chạy.
- **Giới hạn:** timer vẫn chạy trong tab hiện tại; chưa bổ sung phục hồi timer sau đóng hẳn tab/process hoặc nếu người dùng bỏ qua cảnh báo rời trang. Đây không phải điều kiện UX-04 trong review. Lịch sử đã lưu vẫn được giữ.

## Test và kiểm tra

- `testDebugUnitTest`: **387/387 PASS**, 0 fail/0 skip; gồm **11 test mới** (9 navigation/scope + 2 list/editor).
- `assembleDebug`: **PASS**.
- `npm --prefix web test`: **30/30 PASS**, 0 fail/0 skip; gồm **8 Playwright test mới**, chạy ở localhost với Chromium/Brave headless.
- `lintDebug`: **PASS**, 0 error, 205 warning trong báo cáo lint; không mở rộng task này sang dọn dependency/resource warning.
- `git diff --check` và `git diff --cached --check`: **PASS**.
- Lệnh Android dùng JDK 17, `./gradlew testDebugUnitTest assembleDebug lintDebug --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8'` để giới hạn bộ nhớ trên máy này. Không thay đổi cấu hình Gradle trong repo.
- Bộ đầy đủ đã bắt lỗi gọi API Android khi mã hóa route trong test JVM thuần; đổi sang `java.net.URLEncoder` giữ cùng kết quả route và chạy lại xanh. Không sửa test cũ để né lỗi.

- Android: test unit + Compose/Robolectric, build debug, lint debug.
- Test mới `UxParentRoutesTest`: màn tài khoản bị ẩn trước PIN; sai PIN không gọi thao tác cloud; xác nhận xóa; khóa phiên làm mất dialog xóa; về Home rồi vào lại phải PIN; không có PIN phải thiết lập; dấu cộng ở hôm nay/ngày trước/ngày sau; phiên tự do không ghi lịch; chọn tuần qua PIN; parent chọn phạm vi; editor có tham số giữ ID, giờ và enabled.
- Test mới `UxScheduleListTest`: edit/toggle đúng từng task cùng loại, lưu mục thứ hai không sửa mục đầu, xóa đúng mục thứ hai.
- Playwright mới `web-focus-session.test.js`: return/switch/cancel/save trên 390 và 1280px, lỗi lưu rồi thử lại, rời Guest, hoàn thành tự nhiên, loại thời gian pause, cảnh báo trước khi rời trang, hết giờ trong lúc mở xác nhận.
- Test Guest cũ được cập nhật có chủ đích để bấm xác nhận Dừng sớm, giữ các assertion về dữ liệu lưu và offline. Không nới assertion dữ liệu và không sửa shared/sync hay bộ bài Luyện tư duy.
- Browser test dùng dữ liệu tổng hợp ở localhost/Guest; chặn request ngoài origin, không cung cấp config Firebase production và không gọi AI.

### Đối chiếu test với từng lỗi

| Lỗi | Test chính |
|---|---|
| UX-01 | `UxParentRoutesTest`: tài khoản trước/sau PIN đúng-sai, xác nhận xóa, khóa khi dialog đang mở, về Home rồi vào lại, chưa có PIN |
| UX-02 | `UxParentRoutesTest`: dấu cộng ở ba ngày qua route production; phiên hôm nay không ghi lịch; weekly qua PIN; editor mới chọn lại phạm vi; phục hồi route có tham số |
| UX-03 | `UxScheduleListTest`: edit/toggle/save/delete mục thứ hai đúng ID; `restoredParameterizedEditorNeedsPinAndKeepsExactIdAndDisabledState` |
| UX-04 | 8 Playwright test trong `web-focus-session.test.js`; test Guest/offline cũ tiếp tục kiểm dữ liệu session sau xác nhận |

## Ảnh bản sửa

- [390px — quay lại phiên](ux-p0p1/web-active-return-390.png)
- [390px — xác nhận đổi việc](ux-p0p1/web-confirm-switch-390.png)
- [1280px — quay lại phiên](ux-p0p1/web-active-return-1280.png)
- [1280px — xác nhận đổi việc](ux-p0p1/web-confirm-switch-1280.png)

## Chờ Claude/G3

- Chưa tuyên bố G3 Pixel PASS: lượt này dùng Compose/Robolectric và browser tự động, chưa cài/thao tác máy thật.
- Trên Pixel kiểm avatar/tài khoản, background rồi quay lại, dấu cộng, chuyển phạm vi, hai hoạt động cùng loại và chỉnh mục thứ hai.
- Các mục P2 còn lại trong review (resume timer Android, trạng thái Home, nghỉ theo task, skip ảnh hưởng alarm, xác nhận xóa lịch…) chưa triển khai trong task bốn lỗi này; riêng giữ enabled khi edit đã sửa kèm như trên.
- Không thêm permission, không đổi version/schema Room, không sửa Functions, không đụng cây dirty gốc/.omc. Chưa push/merge/deploy.
