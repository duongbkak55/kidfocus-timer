# W5b — AI ghi thực tế và góp ý từ dữ liệu thực tế

Ngày: 2026-09-29 · Dev: Codex · Reviewer: Claude · Nghiệm thu: Duong.

Branch **feature/smart-schedule-w5**, worktree **KidFocusTimer-w5a**, tiếp nối W5a `b4a2bbb` (code fixes `5245e5e`). Đã đọc phần **Re-review** trong `CLAUDE_AI_SCHEDULE_W5A_REVIEW.md`: W5a G2/G3 PASS. Chỉ thực hiện W5b theo task W5 và giao việc mới của Duong.

**Trạng thái:** phần triển khai và kiểm tự động PASS; **G3 Pixel chưa chạy vì ADB không thấy thiết bị**. Không coi test host hoặc build instrumentation là G3 PASS. Chưa push/merge/deploy.

## LOG và credit

- `aiSchedule` nhận `mode: LOG`; văn bản 1–2000 ký tự, ngày ISO, ageBand, locale, requestId và kế hoạch với ref tạm `p0…`. Input/output kiểm schema, giới hạn số mục/câu hỏi, ngày/giờ/category/confidence, ref có thật và đúng loại/ngày, không nhân đôi mục giống nhau. Không nhận profile ID hoặc UUID log từ client.
- Prompt hiểu hôm nay/hôm qua/tối qua, giờ số/chữ, khoảng giờ, suy giờ còn lại khi có một mốc + thời lượng rõ; chỉ thời lượng thì hỏi giờ bắt đầu. Không lấy giờ kế hoạch làm giờ thực tế; end không biết để null. “Không đi học thêm” không tạo mục đã học hoặc xoá kế hoạch. Các mục đủ dữ liệu vẫn được preview bên cạnh câu hỏi cho mục thiếu.
- Provider chỉ nhận văn bản, ngày, kế hoạch ref tạm, ageBand và locale; không nhận requestId, ID hồ sơ, ID task hoặc UUID log. `data_collection: deny`, temperature 0, token limit 2500; dùng secret backend hiện có, không thêm key trong APK/repo/log.
- Remote Config **`ai_schedule_log_cost` mặc định 1**, giới hạn 1–100, được trả qua `getAiConfig`. LOG dùng model `ai_schedule_model`, quota câu hỏi/credit/pool global/early access hiện có và dùng chung lượt `ai_schedule_free_daily_parses` với PARSE. Không tạo ledger riêng.
- Giữ ngày quota tại lúc reservation. Provider lỗi, JSON/schema/ref/giờ sai → refund reservation cùng ngày: credit, câu hỏi, lượt schedule, model count và global pool. Log lỗi chỉ có code `AI_LOG_FAILED`/`AI_REFUND_FAILED`, không ghi input/response/key. Câu hỏi hợp lệ cũng là một lần xử lý AI có phí; lỗi ghi Room sau khi đã nhận preview không phải lỗi provider.

## Ghi nhanh ở màn phụ huynh

- “Ghi nhanh hôm nay” nằm trong route **DayLogs**, sau cổng PIN; màn lịch trẻ W4 không đổi. Ngày đang chọn hiện ngay trên ô nhập. Dùng hồ sơ và ageBand thật, không ghi khi chưa có hồ sơ.
- Gõ tối đa 2000 ký tự, hoặc dùng **RecognizerIntent** `vi-VN`; không thêm `RECORD_AUDIO`/permission. Không có ứng dụng nhận giọng nói thì báo và tiếp tục gõ. Không thực hiện đăng nhập Google trong task này.
- Preview hiện từng mục, ngày, start/end (qua nửa đêm có +1), mục kế hoạch khớp hoặc Phát sinh và câu hỏi. Confidence **<0.6 bỏ chọn sẵn**, cùng ngưỡng W2; phụ huynh kiểm tra/chọn lại trước khi lưu. Mục kế hoạch ngủ/thức dùng nhãn vi/en.
- **Lưu** ghi ngay các mục đã chọn, source **AI**, UUID mới, profile cố định và task_id từ ref đã xem. Kiểm kế hoạch còn khớp trước khi ghi; đổi hồ sơ/ngày/tài khoản bỏ preview, credit cũ và Undo. Kết quả async cũ không trở lại sau khi đã đổi ngữ cảnh. Chặn double tap trong lúc xử lý.
- Lưu một lô bằng Room transaction; **Hoàn tác** chỉ tombstone đúng lô vừa lưu. Nếu một mục trong lô đã sửa/sync thay đổi, cả Undo bị từ chối, không xoá một phần hoặc ghi đè sửa tay. Sync `dayLogs` của W5a tự nhận source AI/tombstone, không thêm payload vào document backup chính.
- Undo giữ lô gần nhất trong ViewModel của route. Đi So sánh rồi quay lại vẫn giữ Undo; đổi ngữ cảnh hoặc mất process/route thì không có Undo bền vững. Mục đã lưu vẫn sửa/xoá bằng timeline như W5a.
- **P3:** DayLogs và WeeklyComparison dùng `ArrowBack` tự đảo theo RTL, có mô tả Back/Quay lại theo locale. SmartSchedule/QuickSchedule đã có mũi tên.

## Rule thực tế và ADVISE

- Dùng cùng matcher của So sánh, lịch tuần chung hiện tại, ngày/nửa đêm và tombstone của W5a; không thêm kế hoạch riêng từng tuần.
- **BED_DRIFT — HIGH:** start ngủ thực tế muộn **≥30 phút** so kế hoạch trên **≥3 ngày** trong cửa sổ 7 ngày. Đếm ngày/occurrence đã ghép, không nhân đôi nhiều log cùng ngày.
- **TASK_OVERRUN — MEDIUM:** thời lượng thực tế **>150%** focusDurationMinutes trên ≥3 occurrence. Đúng 150% chưa cảnh báo; thiếu end không bịa thời lượng; nhiều log trùng occurrence chỉ lấy một match như So sánh.
- **OFTEN_SKIPPED — MEDIUM:** ≥3 occurrence bỏ lỡ, chỉ ngày có dữ liệu và mục đã đến giờ. Ngày trống/chưa đến giờ không tính bỏ lỡ. Chỉ áp cho ScheduledTask; không suy đoán bỏ lỡ anchors từ việc không ghi ngủ/thức.
- SmartSchedule theo dõi Room logs và cập nhật finding; ADVISE nhận **actualStats** tổng hợp `recordedDays`, `bedLateDays`, các task ref tạm với `completed/overrun/missed/averageDelayMin`. Không gửi log thô, tên thực tế, ngày từng log, UUID/profile ID. Backend từ chối trường ngoài aggregate schema/ref không có trong kế hoạch; client W4 không gửi actualStats vẫn dùng mặc định rỗng.
- Các rule mới được hiển thị/giải thích và đưa vào ADVISE. Không thêm suggestion tự động hoặc giả vờ thay đổi kế hoạch đã “sửa” lịch sử thực tế; kiểm HIGH/school/study và cơ chế áp dụng từng bước W4 giữ nguyên.

## Quyết định để khớp spec với code

1. Output entry thêm **`date`** ngoài các trường task liệt kê. Bắt buộc để một câu có cả hôm qua/hôm nay hoặc tối qua ngủ sau nửa đêm lưu đúng ngày. Ref kế hoạch có thêm date/category để kiểm ghép; client gửi thêm kế hoạch hôm trước khi văn bản nhắc hôm qua/tối qua. Đây chỉ là metadata giao tiếp; dùng cột date/category Room v6 sẵn có, không đổi schema.
2. “Hôm nay” lấy ngày đang chọn làm ngày tham chiếu, đúng input `date`; preview luôn hiện ngày. End < start thuộc ngày kế. Ref đêm trước có thể khớp actual bắt đầu trước 06:00 ngày kế nếu kế hoạch từ 18:00 trở đi, cùng quy ước matcher W5a.
3. Cửa sổ rule là **7 ngày lịch gần nhất, gồm hôm nay**; chỉ ngày có dữ liệu góp vào số đếm. Không tìm lùi vô hạn để đủ 7 ngày đã nhập. Chọn cách này để giữ thống nhất rule/matcher hiện có và tránh khẳng định vấn đề gần đây từ log quá cũ.
4. Thời lượng thực tế tập trung so với **focus**, không gộp break; giữ quyết định W5a. “Không đi học thêm” không tạo giờ giả; nếu ngày đó có ghi việc khác và task đã đến giờ, So sánh/rule mới suy ra bỏ lỡ. Chỉ một câu phủ định không tạo một log trạng thái mới ngoài mô hình đã duyệt.
5. Không tăng Room: database vẫn **v6**, entities, migration, schema export, sessions và manifest nguyên như W5a. Chỉ thêm DAO transaction cho lô AI và Undo.

## Kiểm tự động

Môi trường JDK 17, Android SDK hiện có; Node **24.14.0** (package Functions khai báo runtime 22, không đổi engines/dependencies/lockfile). Firebase/RevenueCat/OpenRouter build config được đặt rỗng; không gọi production/OpenRouter thật.

| Lệnh | Kết quả |
| --- | --- |
| `./gradlew testDebugUnitTest assembleDebug lintDebug` | PASS — **300/300 test, 46 suite**, 0 failure/error/skipped; lint **0 lỗi, 172 cảnh báo** |
| `./gradlew assembleDebugAndroidTest` | PASS — APK instrumentation chứa fixture offline; chưa chạy trên Pixel |
| `npm --prefix functions test` | **144/144 PASS**, fake provider và FakeFirestore |
| `npm --prefix functions run lint` | PASS |
| `node functions/scripts/eval-schedule-log.js` | **25/25 PASS** offline |
| `git diff --check` và staged diff | PASS |

[Bằng chứng kiểm và hash APK](g3/w5b/01-automated-checks.json), [25 câu LOG](g3/w5b/03-log-offline-eval.txt), [Functions lint](g3/w5b/04-functions-lint.txt), [Functions test](g3/w5b/05-functions-tests-summary.txt), [Android kiểm](g3/w5b/06-android-checks.txt).

Test mới bao phủ ngưỡng BED_DRIFT/150%/skip/no-data/chưa đến giờ, qua nửa đêm/cửa sổ tuần/deleted/duplicate occurrence, từ Room flow tới actualStats ADVISE không log thô; mapper ref/ngày/giờ/category/confidence, low-confidence selection, profile/ngày/tài khoản/async/race/stale plan; luồng UI qua PIN thật gõ/preview hai mục/low-confidence bỏ chọn/chọn lại/Lưu/Undo trên host; transaction Save/Undo giữ dữ liệu cũ và từ chối ghi đè sửa tay; LOG quota/cost/early/refund/gates/default public config và input/output sai.

**Giới hạn eval:** 25 câu thực tế tiếng Việt có annotation ngày/giờ/ref và provider response giả. Chạy qua validator và handler/quota thật trong test, so với annotation; chứng minh hợp đồng, an toàn và regression. **Không phải phép đo chất lượng hiểu ngôn ngữ của model thật**. Không gọi OpenRouter để đánh giá model trong task này.

## G3 Pixel — chưa chạy

[ADB/mDNS tại thời điểm kiểm](g3/w5b/02-adb-status.txt): không có thiết bị. Chưa cài APK W5b, chưa có screenshot mới hoặc logcat crash của W5b, chưa thay dữ liệu Pixel. Đã nhờ Duong nối lại USB/mở khoá qua câu hỏi trong task; nếu có “Allow USB debugging” hoặc cần thao tác tay, dừng để Duong xử lý. Không reboot/reset/uninstall/clear app.

Đã build `DayLogQuickEntryG3Test` (androidTest): real MainActivity, cổng PIN/navigation dùng chung, DayLogs/So sánh/QuickDayLog ViewModel/Room thật; chỉ provider và account/config là fixture trong **test APK**, không có bypass offline trong APK app. Test opt-in cần PIN app hiện có, không nhập PIN mở khoá điện thoại và không tạo/sửa kế hoạch hoặc profile.

Kịch bản đã chuẩn bị: gõ **“Hôm nay làm bài từ 21h08 đến 21h55 và đọc sách từ 23h15 đến 23h45.”** → ≥2 mục khớp Homework/Reading → Lưu source AI → xem So sánh → quay lại Hoàn tác → So sánh trở về số liệu cũ; kiểm các log cũ còn nguyên. Ảnh preview/saved/comparison/undo và result.json được tạo trên máy nếu test chạy. Fixture này kiểm UI/Room trên phần cứng, không thay thế đánh giá model thật hay cloud quota/sync.

## Checklist trước triển khai (chỉ ghi, chưa thực hiện)

- [ ] Claude review G2 W5b, Duong nghiệm thu G3 Pixel nhập ≥2 mục/Lưu/Undo/So sánh, chụp ảnh vào `docs/g3/w5b`, thu crash logcat có lọc. Test gõ tay dùng fixture offline như mô tả; thử RecognizerIntent bằng tay khi được phép, không thêm RECORD_AUDIO.
- [ ] Khi đã được duyệt triển khai, deploy **aiSchedule** và **getAiConfig** tại `asia-southeast1` cùng module `schedule-log.js`/`schedule-advise.js`; không bỏ module mới khi đóng gói. Không cần function LOG mới.
- [ ] Remote Config: bổ sung **ai_schedule_log_cost = 1**; xác nhận `ai_schedule_enabled`, `ai_enabled`, `ai_schedule_model`, `ai_schedule_free_daily_parses`, cap credit/global, early access và guest policy hiện có. Public config và UI phải cùng cost; rollout chỉ sau review, có thể tắt bằng cờ schedule hiện có.
- [ ] Dùng môi trường test được duyệt để kiểm model tương thích schema LOG/date/null end, quota PARSE+LOG dùng chung, early grant và refund lỗi/thời điểm qua ngày. Runtime Node 22 của Functions chưa được chạy cục bộ ở task này.
- [ ] Secret OpenRouter vẫn ở Secret Manager/backend; ENFORCE_APP_CHECK giữ cấu hình đã duyệt. Không đưa secret vào client/artifact/log.
- [ ] Firestore rules hiện có owner-only recursive match đã bao phủ `users/{uid}/dayLogs/{id}`; W5b không đổi rules. Kiểm owner chéo tài khoản, offline→reconnect và AI tombstone không hồi sinh trên hai thiết bị ở môi trường test được duyệt (checklist W5a còn mở).
- [x] Không thêm permission/WorkManager/service; không đổi schema Room hoặc kế hoạch riêng từng tuần.
- [x] Không push/merge/deploy, không gọi production/OpenRouter thật, không đăng nhập/mua/reboot.
- [x] Không reset/stash/sửa `.omc`; 19 file tracked dở trong working tree gốc byte-identical, SHA-256 diff **667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725**. Chỉ stage file W5b trên worktree W5.
