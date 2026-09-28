# Báo cáo W4 — AI sắp lại lịch + áp dụng dần

Ngày: 2026-09-28 · Implementer: Codex · Reviewer/tổng chỉ huy: Claude (Cowork) · Nghiệm thu: Duong

## Trạng thái

- Branch `feature/smart-schedule-w4` tạo trực tiếp từ HEAD W3 G2 `ed19868`.
- Hoàn thành phạm vi A–D và kiểm tra tự động của `CLAUDE_AI_SCHEDULE_TASK_W4.md`. Commit W4 là commit chứa báo cáo này.
- Không reset/stash, không sửa `.omc`, không push/merge/deploy, không đổi production/secrets, không gọi OpenRouter thật trong test.
- Code module hoàn tất, chờ Claude review và Duong nghiệm thu; G3 máy thật, golden model thật, triển khai và closed test chưa thực hiện.

## Triển khai

### Functions ADVISE

- `aiSchedule` phân nhánh `mode: ADVISE`; PARSE chữ/ảnh W2–W3 giữ contract hiện có. Input ADVISE whitelist độc lập, ≤60 task với ref tạm `t0…`, ngày/giờ, loại/thời lượng, wake/bed/school không label, finding từ engine, số đếm routine 7 ngày, ghi chú ≤500 và năm tag cố định. Không có DB/profile ID, tên bé, ảnh hoặc lịch sử completion chi tiết. Ghi chú/tags/stats có thể bỏ qua, mặc định rỗng.
- Model `ai_schedule_advise_model`, fallback model PARSE; giá `ai_schedule_advise_cost`, default 2. Dùng cùng quota account/model/global, đóng băng ngày ledger để refund; ADVISE không tăng `scheduleParses` và không bị cap riêng của PARSE free. Tier free/early/premium dùng theo ngân sách chung, auth/cờ tổng/App Check giữ W2–W3.
- Provider: `max_tokens=1500`, `temperature=0.2`, JSON schema có từng biến thể op và field tương ứng, `data_collection: deny`, deadline 25 giây. Android timeout 30 giây. Server kiểm lại JSON, summary ≤600, ≤10 proposal, reason ≤200, fixes thuộc finding input, tags cố định, ref/ngày phải tồn tại, duration nguyên 1..120.
- Chặn sửa trường, MOVE/RESIZE đặt hoạt động vào ca trường, SET_BED/SET_WAKE trong ca trường, kể cả ca qua nửa đêm/CN→T2. Bed MON 00:30 thuộc đêm thứ Hai, nên kiểm ca trường trên đồng hồ thứ Ba. Chặn REMOVE nhóm STUDY (quyết định bảo thủ bên dưới). Sai input bị chặn trước quota; lỗi provider/schema/output hoàn account/global/model credit và reservation, chỉ log mã lỗi.
- Prompt tiếng Việt, trung tính, ưu tiên finding HIGH; note/tên hoạt động/stats là dữ liệu, không phải lệnh. Không chẩn đoán/kê thuốc/khuyên y khoa; dấu hiệu sức khoẻ gợi ý hỏi bác sĩ nhi. Schema/validator không bị mở rộng theo note.

### Android duyệt và áp dụng

- Nút **Nhờ AI sắp lại · N credit** ở Góp ý lịch tuần khi AI bật và có finding; không giới hạn tier ảnh. Form có năm chip, ghi chú tuỳ chọn, nhắc bỏ tên/trường và giải thích dữ liệu gửi. Tài khoản chưa đăng nhập dùng luồng Google hiện có; giá/lượt ADVISE tính từ ngân sách credit/question, không từ cap PARSE.
- Draft/summary/proposals chỉ trong ViewModel. Phản hồi cũ bị loại khi đổi profile rồi quay lại, hoặc lịch đã đổi trong khi chờ. Lỗi quota/network giữ ghi chú và tags.
- `ApplyScheduleUseCase.previewAdvice` dùng đúng hàm biến đổi/validate của `applyAdvice`, không đọc/ghi store, snapshot hoặc alarm. Chặn import giả, chạm trường, xoá STUDY và tăng HIGH theo rule/ngày như W1. UI giữ dòng bị loại với lý do, không cho chọn áp dụng. “Sửa được hoặc cải thiện” được tính bằng Advisor trên máy, không tin câu `fixes` do model tự khai.
- Mỗi dòng hợp lệ có checkbox và nút Áp dụng; nút chung áp dụng các mục đã chọn. Mặc định chưa chọn dòng nào. REMOVE phải xác nhận riêng, kể cả khi nằm trong nhóm chọn. Mục xa >30 phút chỉ có đường Áp dụng dần.
- Thêm `SetWake`, `RemoveTask`; MOVE + RESIZE cùng task/ngày được gộp đúng, không mất ref khi tách task lặp. Ref còn lại được cập nhật sau áp dụng từng dòng, nên có thể tiếp tục duyệt mà không phải gọi AI lại. Chọn các dòng mâu thuẫn đặt giờ/thời lượng khác nhau hoặc vừa xoá vừa sửa cùng ngày bị chặn; nhóm chọn được validate lại để chặn xung đột giữa các dòng vốn hợp lệ riêng lẻ.
- Save vẫn snapshot local → một Room transaction → cancel/reschedule alarm → observer sync W1. Undo snackbar 10 giây và Khôi phục lịch trước trong 7 ngày giữ nguyên. Apply/Undo nhiều dòng, xoá task thật, scope profile và rollback được kiểm bằng Room thật. Sự kiện của profile cũ không hiện snackbar hành động trên profile mới.

### Kế hoạch dần và sync

- Một kế hoạch ngủ **hoặc** thức đang hoạt động trên mỗi profile, key `schedule_plan_<profileId>`. JSON có kind, ngày bắt đầu, giờ ban đầu từng ngày, target, nextStep, nextOn. Hằng số tập trung ở `SchedulePlan`: 15 phút/bước, 3 ngày/bước, direct limit 30 phút. Bước cuối có thể nhỏ hơn 15 phút để đến đúng đích.
- Bấm Áp dụng dần kiểm cả đích và mọi bước trung gian, áp dụng bước 1 qua use case rồi lưu bước kế tiếp. Banner hiển thị bước/tổng số bước, nhóm ngày và giờ; khi tới hạn có nút Áp dụng/Bỏ kế hoạch. Không tự lưu khi tới hạn, không thêm alarm, WorkManager hoặc service nền.
- Ngày hạn dùng LocalDate; khoảng giờ dùng đường ngắn qua nửa đêm. Áp dụng trễ chỉ tiến một bước và đặt hạn tiếp sau 3 ngày từ ngày thực sự duyệt, không chạy bù nhiều bước. Đổi profile không mang kế hoạch qua profile khác; clock hiện hữu cập nhật khi màn hình được theo dõi.
- Đường ghi anchors DataStore tự huỷ kế hoạch nếu giờ ngủ/thức liên quan khác giá trị chờ, kể cả sửa tay/import/remote/Undo. Sửa commute hoặc anchor không liên quan giữ kế hoạch. Khi ghi anchors lỗi, Room store bù cả anchors và kế hoạch đã bị huỷ cùng snapshot theo cơ chế W1.
- Sync `settings.schedulePlans` và observer revision dùng debounce hiện hữu. Remote thiếu key/profile hoặc dữ liệu hỏng giữ local; `profile: null` là dấu huỷ được sync. Kế hoạch remote không khớp anchors hiện tại bị loại, tránh hồi sinh kế hoạch sau sửa tay. Snapshot không sync.

### Privacy và golden

- Privacy vi/en bổ sung dữ liệu ADVISE, ref tạm, finding, số đếm routine, note/tags tuỳ chọn, không lưu/log nội dung trên server; phân biệt đề xuất tạm với lịch/kế hoạch đã duyệt và sync.
- `functions/test/fixtures/schedule-advise.vi.json` có **12 tình huống giả**, không có dữ liệu trẻ thật: ngủ thiếu lớp 1, bài tập muộn, màn hình gần bed, chồng chéo, lệch cuối tuần, 4–5 tuổi không ngủ trưa, focus dài, ca trường cố định, bed qua nửa đêm, đổi wake, REMOVE màn hình và note injection.
- Mỗi case có `mustFix` ruleId, trường không đổi và không lời khuyên y khoa. Expected proposal được kiểm offline phía JS và bằng **Advisor + Apply dry-run Android thật**; mustFix phải biến mất sau áp dụng hợp lệ. Các bước dần được test riêng.
- `eval:schedule -- --advise` chạy suite ADVISE riêng khi có key; kiểm schema, sửa được rule, không thêm HIGH/chạm trường và mẫu ngôn ngữ y khoa nguy hiểm. Bộ tính rule JS của eval phục vụ các ràng buộc golden; engine Android vẫn là nguồn quyết định khi dùng app. Eval thiếu key exit 1 trước network. Chưa chạy eval có key.
- Regex ngôn ngữ không chứng minh mọi câu đều an toàn hoặc trung tính; Claude/Duong phải xem lời giải thích thật trong nghiệm thu. Test mock/schema không chứng minh chất lượng tư vấn model thật.

## Quyết định khi spec/code khác nhau

1. **STUDY là category:** code không có enum `STUDY` hay provenance “do nhà trường”. Giữ enum hiện có, không tăng Room, chặn REMOVE **mọi `TaskCategory.STUDY`** cả server/client (gồm HOMEWORK/READING/LEARNING_GAMES/CUSTOM…). MOVE/RESIZE vẫn được sau guard trường/HIGH. Bảo thủ hơn spec, tránh suy nguồn gốc từ tên tự do. Server cũng nhận token STUDY cho validation/compatibility.
2. **Prompt tối đa 30 phút/bước và target >30:** không loại đích xa ở validator, vì phần C cần đích để dựng kế hoạch. UI không cho áp dụng trực tiếp dòng xa; bước thực tế 15 phút, đều re-validate. Không gửi target xa thẳng vào save từ UI.
3. **Không thêm rule ngủ trưa:** W1 không có rule thiếu nap. Case 4–5 tuổi không ngủ trưa dùng ngưỡng ngủ hiện hữu `SLEEP_SHORT`; note chỉ là ngữ cảnh, không sinh finding hay lời khuyên y khoa mới.
4. **Thời lượng compact:** `durationMin` gửi là focus + break (≤150), để server biết toàn bộ khoảng chiếm lịch dù whitelist không có field break. RESIZE là focus 1..120; Android giữ break và kiểm lại đầy đủ. Không sửa schema task.
5. **Một plan/profile, bước 1 ngay khi duyệt:** bám key JSON singular của task. Muốn tạo plan khác phải Bỏ kế hoạch cũ. Ngày hạn là ngày lịch, không epoch alarm; không chạy catch-up hay notification nền.
6. **Không transaction xuyên Room/DataStore:** giữ cơ chế W1, có bù khi lỗi ghi anchors. Ghi trạng thái tiến kế hoạch xảy ra sau save lịch; nếu process chết/ghi plan lỗi ở khoảng này, kế hoạch có thể bị huỷ để tránh tự ghi đè. Lịch đã lưu vẫn có snapshot/Undo; không thêm journal/service/migration. Whole-document sync last-write-wins của W1 vẫn là giới hạn hiện hữu.

## Kiểm tra cuối

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug
npm --prefix functions test
npm --prefix functions run lint
env -u OPENROUTER_API_KEY npm --prefix functions run eval:schedule -- --advise
git diff --check
git diff --cached --check
adb devices
```

| Kiểm tra | Kết quả |
|---|---|
| Android unit/build/lint | **PASS** — 230 test, 0 failure/error/skipped; BUILD SUCCESSFUL, 58 giây |
| Android lint | **PASS** — 0 error/fatal; 206 warning toàn working tree; không warning trong file Advice/Plan/Apply/SmartScheduleViewModel |
| Functions | **PASS** — 112/112, mock provider + Firestore giả; không production calls |
| Functions lint | **PASS** |
| Golden offline | **PASS** — 12 expected case, kiểm ràng buộc JS và dry-run/Advisor Android |
| Eval không key | Exit **1** với thông báo yêu cầu key, trước network (hành vi kỳ vọng) |
| Whitespace working tree/staged | **PASS** |
| Manifest/Room | Manifest giữ nguyên byte baseline của phiên; W4 không stage Manifest, không thêm permission; Room **v5**, không entity/schema/migration |
| Máy thật/model thật | Không có thiết bị adb; chưa chạy G3 hoặc eval có key |

Test mới bao phủ input/output/ref/op/school qua nửa đêm/REMOVE STUDY/giá/refund/tier/note injection; Android lọc dòng xấu, nhóm chọn tạo HIGH và giờ mâu thuẫn, rebase sau split, Apply/Undo nhiều dòng trên Room thật, lỗi storage giữ plan/snapshot/alarms, kế hoạch 15/3 ngày, due/late, midnight, wake, sửa tay/Undo huỷ, đổi profile và phản hồi cũ, sync thiếu key/profile/null/malformed.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`; unit report `app/build/reports/tests/testDebugUnitTest/index.html`; lint `app/build/reports/lint-results-debug.html`. Local Node 24.14; runtime deploy vẫn Node 22 của package. Một lỗi compile test do constructor FirebaseFunctionsException không public đã sửa bằng mock; kết quả trong bảng là lượt cuối sau mọi sửa code.

## Checklist deploy bổ sung — CHƯA thực hiện

Giữ toàn bộ checklist W2–W3: Auth/App Check/secrets/provider retention/quota/privacy. Thứ tự review → backend → config → eval/smoke → beta; phiên implement này không triển khai.

- [ ] Claude review G2 W4; Duong nghiệm thu APK/G3. Merge w1→w4 vào main theo thứ tự sau nghiệm thu, không lấy thay đổi release/Learning đang unstaged của workspace này vào commit W4.
- [ ] Dùng Node 22 và lockfile; chạy test/lint tại môi trường deploy. Giữ `ENFORCE_APP_CHECK=true`, Play Integrity/App Check đúng bản Play ký và secret hiện hữu; không tắt enforcement để vượt smoke.
- [ ] Redeploy `aiSchedule` và `getAiConfig` trước phát hành client W4:

```sh
firebase deploy --only functions:aiSchedule,functions:getAiConfig --project kid-focus-app
```

- [ ] W4 không cần rules/Room migration hoặc redeploy claim/webhook/deleteAccount. `aiChat` không đổi semantics; redeploy cùng đợt nếu muốn getAiConfig/usage chat cùng version public config.
- [ ] Backup/merge hai key Remote Config, giữ mọi key/condition W2–W3 khác. Cache server tối đa 5 phút:

| Key mới | Template beta | Fallback code |
|---|---|---|
| `ai_schedule_advise_cost` | `2` | `2` |
| `ai_schedule_advise_model` | `google/gemini-2.5-flash-lite` | model `ai_schedule_model` PARSE |

- [ ] Giữ `ai_schedule_enabled` và `ai_enabled` theo quyết định beta; `getAiConfig` phải trả giá ADVISE đúng. Rollback toàn AI lịch bằng `ai_schedule_enabled=false`; offline findings/plan đã duyệt vẫn dùng được. Không có cờ ADVISE riêng trong task này.
- [ ] Trong môi trường riêng có key, Duong chạy `npm --prefix functions run eval:schedule -- --advise` (override `AI_SCHEDULE_ADVISE_MODEL` nếu cần). Có phí, không qua quota Firebase, không CI. Gate 12/12 ràng buộc, không mới HIGH/chạm trường; xem bằng mắt lời giải thích, sức khoẻ/note injection và tính hữu ích. Giữ gate chữ/ảnh W2–W3 trước mở beta.
- [ ] Máy thật: PIN/sign-in, bật/tắt AI, free/early/premium/hết trial, hiện giá đúng, ADVISE trừ 2/configured cost, hết quota và refund lỗi. Free hết cap PARSE vẫn dùng ADVISE nếu còn budget; thiếu App Check bị chặn.
- [ ] Kiểm checkbox từng dòng, Sửa được/Đã loại, REMOVE cancel/confirm, apply nhóm mâu thuẫn, ref sau tách task, stale sync, đổi profile khi chờ response và trở lại. Apply/Undo đúng alarm/sync/profile, snackbar 10 giây và restore 7 ngày.
- [ ] Kiểm plan bed/wake >30 phút: bước 1 15 phút, banner đúng hạn, các ngày có giờ ban đầu khác nhau, target qua nửa đêm/CN→T2, mở lại app và đổi timezone/ngày. Không mở màn hình thì không tự áp dụng; mở trễ không catch-up. Sửa tay/import/Undo huỷ, Bỏ kế hoạch sync qua thiết bị thứ hai; remote client cũ thiếu key giữ local, remote null huỷ đúng profile.
- [ ] Kiểm lỗi mạng/storage/process kill giữa save lịch và tiến plan: không ghi đè lịch sửa tay, Undo còn dùng được; xem giới hạn xuyên storage và whole-document sync ở trên.
- [ ] Publish privacy vi/en bổ sung ADVISE, rà Data safety theo mapping W2: lịch/finding/count/note là nội dung tự tạo tuỳ chọn; draft xử lý tạm chỉ khi retention provider thực tế đáp ứng, còn lịch/plan sync và quota là dữ liệu lưu lâu dài. Không khai cả luồng ephemeral. Closed test/release theo checklist hiện hữu; không tự tăng version/upload trong W4.

## Giữ working tree

- 35 đường dẫn W4 được stage rõ ràng, gồm báo cáo này. Hai file strings trùng baseline chỉ stage patch W4; phần Learning cũ vẫn unstaged.
- 17 file tracked baseline khác giữ nguyên SHA-256, gồm Manifest/app build/toolchain/navigation/release và Learning. Bỏ riêng đoạn resource W4 thì hai file strings khớp hash baseline.
- Các tài liệu task/design/context/review có sẵn, file untracked của phiên khác và `.omc` không nằm trong commit. Sau commit giữ nguyên 19 đường dẫn tracked intentional còn thay đổi như đầu phiên.
