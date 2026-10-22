# KidFocus — review BA/QA về chức năng và UI/UX

Ngày review: 22/10/2026. Bản đối chiếu: `main d0d6703`, Android `versionCode 8`, `1.0.0-rc.6`.

## Kết luận

**Chưa thể kết luận UI/UX đã ổn toàn bộ.** Các chức năng chính đã có, nhưng hành trình “chọn việc → đang làm → nghỉ → xong/bỏ qua → xem lại” chưa dùng cùng một trạng thái và quy tắc trên mọi màn. Vấn đề ưu tiên là quyền phụ huynh, quản lý đầy đủ lịch và bảo toàn phiên tập trung; chưa nên bắt đầu bằng đổi màu/icon toàn app.

Hai lượt đọc độc lập theo vai BA và QA được đối chiếu lại với code. Lượt thao tác web dùng dữ liệu khách tổng hợp, không đăng nhập, không gọi AI, không ghi dữ liệu cloud. Không thay đổi code, không chạy build/test tự động, không commit/push/deploy. Pixel chưa xuất hiện trong danh sách ADB, vì vậy **đây chưa phải G3 Android**; các vấn đề Android bên dưới là phát hiện theo đường đi code, cần kiểm chứng giao diện trên máy thật khi sửa.

## Điểm đã có nền tảng tốt

- Android buộc gắn Focus với hoạt động; có chọn hoạt động có sẵn và hoạt động tự do chỉ cho phiên hôm nay.
- Lịch ngày có thao tác bắt đầu từ task, bỏ qua hôm nay và Hoàn tác.
- Hoàn thành sớm trên Android có xác nhận và ghi nguồn `TIMER_DONE`.
- Lịch sử trẻ chỉ đọc; thao tác xóa lịch sử đặt trong phần phụ huynh.
- Luyện tư duy đã có cơ chế Back về danh mục bên trong nội dung học.
- Web có chế độ khách, cảnh báo lưu cục bộ, nhãn biểu mẫu, focus bàn phím và thanh điều hướng điện thoại. Các màn đã quan sát ở 390×844 không thấy tràn ngang; điều này không đại diện cho tất cả màn/font/kích thước.

## P1 — nên sửa trước đợt phát hành tiếp theo

### UX-01 — Màn tài khoản đi thẳng từ khu vực trẻ, không qua PIN

- **Luồng:** Home → avatar tài khoản → Đồng bộ dữ liệu. Từ đó có Đăng xuất và Xóa tài khoản/cloud. Có dialog xác nhận xóa, nhưng dialog không thay thế cổng phụ huynh. Việc xóa thành công còn phụ thuộc backend/xác thực gần đây; review không thực hiện xóa.
- **Bằng chứng:** `AppNavigation.kt:159,286`; `HomeScreen.kt:151`; `CloudSyncScreen.kt:73–80,207–217`.
- **Sửa tối thiểu:** bảo vệ route tài khoản và các thao tác đổi/xóa tài khoản bằng trạng thái mở khóa phụ huynh, kiểm tại destination thay vì chỉ một nút vào.
- **Nghiệm thu:** khởi động lạnh/khóa lại phiên phụ huynh → mở avatar phải qua PIN; hủy PIN không lộ thao tác tài khoản; thử mọi điểm vào và sau background.

### UX-02 — Dấu cộng trong lịch trẻ mở editor lịch tuần không qua PIN

- **Luồng:** Home → Lịch hôm nay → dấu cộng ở khoảng trống → TaskEdit → Lưu. Editor chọn ngày lặp và lưu lịch chung, không phải một hoạt động chỉ cho hôm nay.
- **Bằng chứng:** `AppNavigation.kt:352–354,403–425`; `DailyScheduleScreen.kt:234`; `TaskEditScreen.kt:293`; `ScheduleViewModel.kt:43`. Ý định W7 là trẻ không sửa lịch tuần.
- **Sửa tối thiểu:** sửa lịch tuần phải qua parent gate. Nếu trẻ thêm việc phát sinh, dùng cùng luồng hoạt động tự do cho phiên hôm nay.
- **Nghiệm thu:** không PIN thì không sửa lịch lặp; thêm việc tự do không làm xuất hiện lịch ở tuần sau; kiểm hôm nay, ngày trước và ngày sau.

### UX-03 — Màn sửa lịch chỉ hiện mục đầu tiên của mỗi loại có sẵn

- **Luồng:** AI/sync tạo hai HOMEWORK hoặc READING khác giờ/ngày → vào Sửa kế hoạch. `tasks.find` chỉ lấy mục đầu theo loại; phần cuối chỉ hiện `isCustom`. Mục thứ hai vẫn tồn tại và có thể phát lời nhắc, nhưng màn quản lý không cho sửa/xóa nó.
- **Bằng chứng:** `ScheduleScreen.kt:106–119`; `domain/schedule/ScheduleDraft.kt:33–36` đặt các loại có sẵn thành `isCustom=false`.
- **Sửa tối thiểu:** render tất cả task theo ID, có thể nhóm theo loại; tách rõ nút thêm mẫu với các mục đã tạo.
- **Nghiệm thu:** hai mục cùng loại đều thấy, sửa/xóa/tắt độc lập đúng ID; kiểm dữ liệu nhập AI và dữ liệu nhận từ sync.

### UX-04 — Web bắt đầu từ Hôm nay ghi đè phiên đang chạy, không lưu phiên trước

- **Đã tái hiện trên site thật bằng Guest, 390×844:** tạo “QA · Đọc sách” 5 phút → Bắt đầu → Hôm nay → Bắt đầu cùng mục lần nữa → Dừng sớm. Không có xác nhận; timer khởi động lại. Lịch sử chỉ có **1 lượt** của phiên thay thế.
- **Bằng chứng:** `web/app/src/main.tsx:261–280` thay `run` trực tiếp; `:537` gọi start không kiểm phiên hiện hành; `:320–353` chỉ lưu khi finish.
- **Sửa tối thiểu:** chọn đúng task đang chạy phải mở lại timer; chọn task khác phải hỏi kết thúc phiên cũ để chuyển, hoặc giữ phiên và hủy thao tác. Phiên cũ phải được lưu trước khi thay.
- **Nghiệm thu:** chạy A → Hôm nay → A không reset; A → B có lựa chọn rõ ràng; chuyển B lưu A đúng thời lượng; không double start.
- **Ảnh:** [Nhật ký chỉ còn phiên mới](ui-ux-2026-10-22/web-session-replaced-390.jpg).

## P2 — tính nhất quán và thao tác dễ gây nhầm

### UX-05 — Android Back về Home thiếu lối quay lại phiên đang chạy

- Focus rời composition chỉ unbind service; Home không hiển thị trạng thái phiên đang hoạt động. Nút Start mở chọn phiên mới; service có thể thay countdown cũ mà không hỏi. **Không kết luận mất log Android:** recorder có đóng log trước đó.
- Bằng chứng: `FocusScreen.kt:80–83`; `HomeScreen.kt:398,567`; `TimerService.kt:188`; `TimerDayLogRecorder.start`.
- Đề xuất: thẻ “Đang làm … · còn … · Quay lại” trên Home và Lịch; mọi điểm Start dùng chung kiểm tra phiên hiện hành.
- Nghiệm thu: Back/Home/thông báo rồi mở app đều về đúng timer; chọn task khác không thay phiên im lặng. Cần G3 để xác nhận toàn bộ vòng đời.

### UX-06 — Trạng thái task không thống nhất giữa Home và Lịch ngày

- Android Lịch ngày đọc actual/skip và hiển thị trạng thái, nhưng Home/chooser chỉ lọc theo thứ, giờ, enabled. Việc đã xong/bỏ qua vẫn giống việc chưa làm. Trên web, Today chỉ tra completion cho routine, task luôn có “Bắt đầu tập trung”.
- Bằng chứng: `HomeScreen.kt:114–118,342,470`; `DailyScheduleScreen.kt:207`; `web/app/src/main.tsx:923–954`.
- Đề xuất: một mô hình trạng thái ngày chung: Chưa làm / Đang làm / Tạm dừng / Đã xong / Bỏ qua. Vẫn cho làm lại nhưng ghi rõ “Làm thêm lần nữa”.
- Nghiệm thu: hoàn thành sớm, hoàn thành đủ giờ, skip, undo đều phản ánh cùng trạng thái ở Home, Lịch và chooser; không ép trẻ làm đúng thứ tự.

### UX-07 — Thời gian nghỉ riêng của task không dùng khi chạy Break

- Editor và Lịch hiển thị thời gian nghỉ của task, nhưng Break lấy cài đặt chung. Task nghỉ 15 phút, default 5 phút sẽ nhận 5 phút.
- Bằng chứng: `TaskEditScreen.kt:295`; `DailyScheduleScreen.kt:419`; `BreakScreen.kt:61–64`; `TimerViewModel.kt:138`.
- Đề xuất: truyền thời gian nghỉ theo context phiên; hoạt động tự do dùng default. Nếu sản phẩm chỉ dùng default thì bỏ trường nghỉ riêng và nhãn gây hiểu nhầm.
- Nghiệm thu: task 15 phút nghỉ phải chạy đúng 15; custom dùng default; hoàn thành sớm và Nghỉ ngay đều theo cùng quy tắc.

### UX-08 — Sửa task đã tắt tự bật lại

- `TaskEditScreen` lưu với `enabled=true`, kể cả task đang tắt. Đổi tên có thể vô tình bật lại báo thức.
- Bằng chứng: `TaskEditScreen.kt:296`; `ScheduleViewModel.kt:52`.
- Đề xuất: giữ trạng thái hiện tại khi sửa; task mới mới mặc định bật.
- Nghiệm thu: tắt → sửa tên/giờ → lưu vẫn tắt; chỉ toggle mới bật.

### UX-09 — Xóa lịch một chạm; lưu/xóa rời màn trước khi biết thành công

- TaskEdit xóa ngay không xác nhận/Undo. Save/delete khởi chạy coroutine rồi Back ngay, thiếu trạng thái đang xử lý và lỗi tại biểu mẫu.
- Bằng chứng: `TaskEditScreen.kt:286–301,313`; `ScheduleViewModel.kt:43–54,66`.
- Đề xuất: xác nhận tên và phạm vi “lịch lặp mọi tuần”, hoặc Undo có hiệu lực; chờ kết quả lưu, giữ draft khi lỗi.
- Nghiệm thu: hủy xóa không đổi dữ liệu; lỗi storage giữ màn/draft và có thử lại; double tap không tạo bản sao.

### UX-10 — Bỏ qua hôm nay không được đối chiếu khi phát lời nhắc

- Skip chỉ thêm log SKIP; receiver vẫn phát thông báo mà không kiểm occurrence đã skip/hoàn thành. Cần chốt với BA liệu hoàn thành sớm có nên nhận nhắc lại; riêng “Bỏ qua hôm nay” nên có hiệu lực lên nhắc việc.
- Bằng chứng: `DayLogViewModel.kt:79–94`; `alarm/TaskAlarmReceiver.kt:31`.
- Đề xuất: tra trạng thái theo task/profile/ngày trước khi nhắc; Undo phục hồi nhắc nếu còn thời gian.
- Nghiệm thu G3: skip trước giờ không bị nhắc, tuần sau vẫn nhắc; undo đúng occurrence, không nhân đôi báo thức.

## Quyết định sản phẩm/UX nên chốt, không gộp thành lỗi code

1. **Một cách bắt đầu thống nhất:** Android Home có sheet chọn thời lượng, Lịch ngày chạy ngay. Nên dùng cùng sheet tên hoạt động + thời lượng + Bắt đầu. Task nào cũng được chọn ngoài thứ tự. Nguồn: `HomeScreen.kt:346`; `DailyScheduleScreen.kt:384`; `AppNavigation.kt:342`.
2. **Nghỉ giữa phiên khác kết thúc lượt để nghỉ:** “Nghỉ ngay” hiện kết thúc lượt rồi vào Break, không có “tiếp tục task vừa làm”. Copy đã cảnh báo nên không coi là bug; cần nút/nhãn thể hiện rõ, và lựa chọn tiếp tục task sau nghỉ. Nguồn: `AppNavigation.kt:190–212`; `strings.xml:71`.
3. **Web và Android thiếu cùng ý nghĩa “xong”:** web chỉ có Dừng sớm (`completed=false`), chưa có hoàn thành sớm, task tự do cho phiên hôm nay, skip hay break tương đương Android. Đây là phạm vi web hiện tại, nhưng người dùng cùng tài khoản dễ tưởng tính năng đã mất. Cần bảng hành vi chung và lộ trình bổ sung, không gọi đây là regression khi chưa từng có trên web.
4. **“Đã xong” và “Nhật ký ngày” nên giải thích quan hệ:** đã dừng một phiên web, Đã xong có session nhưng Nhật ký ngày vẫn trống. Code web chỉ ghi `sessions`, không ghi `dayLogs`. Đã quan sát bằng Guest; không phải lỗi sync được chứng minh. Nên tự ghi actual như Android hoặc giải thích và cho chuyển sang ghi nhật ký. Nguồn: `main.tsx:320–353`; `w2.tsx:49–52`. [Ảnh nhật ký trống](ui-ux-2026-10-22/web-daylog-empty-after-focus-390.jpg).
5. **Màn khách trên điện thoại nhiều thông tin lặp:** header có Đăng nhập, banner lại có Đăng nhập và đoạn lưu dữ liệu dài chiếm đáng kể chiều cao. Nên giữ một cảnh báo ngắn có “Tìm hiểu”, đưa thao tác chính lên gần đầu màn. Không bỏ cảnh báo dữ liệu cục bộ.
6. **Điều hướng web chưa phản ánh tab vào URL:** trạng thái màn chỉ nằm ở `useState`, nên reload về Today và Back trình duyệt không quay qua các màn nội bộ. Nên đồng bộ route/tab, và bảo vệ khi đang có timer/draft. Nguồn: `main.tsx:107,425,865`.

## Rủi ro bố cục/vòng đời cần G3, chưa chấm fail từ screenshot

- Focus dùng Column không cuộn, dial lớn + mascot + nhiều nút: cần 320×568/360×640 và font 1.3×/1.5× để kiểm Stop/Pause/Done không bị cắt. Nguồn: `FocusScreen.kt:98–179`; `FocusTimeDials.kt:100`.
- Break luôn `startBreak(full duration)` trong `DisposableEffect(Unit)`; cần xoay máy/đổi cấu hình khi đang nghỉ để kiểm không reset thời gian. Nguồn: `BreakScreen.kt:61–64`.
- Cần test bàn phím, TalkBack, tương phản theme, trạng thái mạng chậm/offline, lỗi PIN và lỗi AI/quota. Không dùng kết quả unit test để thay cho chứng cứ usability.

## Kế hoạch sửa và nghiệm thu đề nghị

1. **Đợt 1 — bảo vệ và không mất tiến trình:** UX-01…05; thêm navigation tests cho mọi điểm vào parent route; test nhiều task cùng loại; test start khi đã có run; G3 Back/Home/notification.
2. **Đợt 2 — liên kết task với kết quả:** UX-06…10; dùng trạng thái ngày chung, đúng nghỉ theo task, giữ enabled, xóa có phục hồi/xác nhận, skip ảnh hưởng nhắc việc.
3. **Đợt 3 — đơn giản hóa màn:** thống nhất sheet bắt đầu; phân biệt Tạm dừng / Hoàn thành sớm / Kết thúc để nghỉ / Bỏ qua hôm nay; nối rõ Đã xong–Nhật ký ngày; giảm banner lặp.
4. **G3 với 5 hoạt động tổng hợp:** chọn việc số 4 trước → Focus → thêm giờ → hoàn thành sớm; việc số 2 → skip → undo; việc tự do hôm nay → nghỉ chủ động → tiếp tục; Back/Home/khóa mở không reset; mọi màn phản ánh đúng kết quả. Thử trên app và web, font thường và font lớn.

Các test hiện có bao phủ một số thành phần như EarlyFocusCompletion, FocusTimePolicy, DailyScheduleScreen, FocusHistory; chưa có bằng chứng đủ về end-to-end cho các luồng lỗi trên. Không thay đổi test/code trong lượt review này.

## Phạm vi và bằng chứng

- BA: đọc luồng điều hướng, task/focus/break/history/parent/daylog/learning.
- QA: đọc các điểm vào màn phụ huynh, danh sách lịch, editor, alarm, timer lifecycle và test liên quan.
- Kiểm tra trực tiếp web: đăng nhập/Guest, Today, lịch và biểu mẫu, Focus, start lại khi đang chạy, History, DayLogs; 390×844. Dữ liệu “QA · Đọc sách” chỉ ở Guest của trình duyệt review.
- Không thử xóa tài khoản, không dùng dữ liệu trẻ thật, không gọi OpenRouter/AI, không kiểm chứng sync hai thiết bị, không test mua hàng.
- Tham chiếu checklist web: https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md. Áp dụng các mục navigation/state, confirmation/undo, feedback và form; không coi các sở thích typography nhỏ là lỗi phát hành.
