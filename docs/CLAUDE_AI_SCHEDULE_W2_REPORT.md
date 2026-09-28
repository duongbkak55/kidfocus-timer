# Báo cáo W2 — Nhập nhanh lịch + Early Access AI

Ngày: 2026-09-28 · Implementer: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

## Trạng thái

- Branch `feature/smart-schedule-w2`, tạo trực tiếp từ HEAD W1 `9d31af1` (G2).
- Hoàn thành A–E của `CLAUDE_AI_SCHEDULE_TASK_W2.md`. Chỉ PARSE text; giọng nói được hệ thống chuyển thành chữ.
- Commit cục bộ W2; chưa push/merge/deploy. Chưa thay secret hoặc cấu hình production. Không làm W3/W4.
- G3 review và nghiệm thu máy thật của Duong còn chờ; kết quả unit/mock không thay thế đánh giá model thật.

## Những gì đã triển khai

### Functions, quota và Early Access

- `aiSchedule`: `asia-southeast1`, timeout/memory/instance/concurrency giống `aiChat`; bắt buộc đăng nhập mặc định. Input chỉ whitelist requestId/text/ageBand/today/locale/current và guestId dự phòng; không có field tên bé, ảnh, mode ADVISE hoặc profile ID.
- Kiểm tra text ≤2000 ký tự, current ≤60 mục, ngày thật, giờ hợp lệ. OpenRouter nhận prompt tiếng Việt, `temperature: 0`, `response_format: json_schema`, `provider.data_collection: deny`. Request provider có deadline 25 giây, Android chờ tối đa 30 giây.
- Validator server kiểm tra cấu trúc và mọi mục: MON..SUN, HH:mm, tên 1..60, duration nguyên 5..120, confidence 0..1, tối đa 30 task; enum lạ → CUSTOM. Sai schema/JSON/provider → hoàn quota và `AI_PARSE_FAILED`; chỉ log mã lỗi.
- Tái dùng reserve/complete/refund của gateway. PARSE trừ `ai_schedule_parse_cost`; chia sẻ quota tài khoản/global với chat.
- `claimEarlyAccess`: auth + cờ mở; transaction ghi epoch milliseconds `earlyAccessUntil` một lần. Có field cũ thì giữ nguyên, kể cả đã hết hạn; merge giữ Premium. Premium ưu tiên trước early; early đang hạn dùng 15 credit/ngày; hết hạn về free.
- `getAiConfig` và reply có `usage.tier`, `usage.earlyAccessUntil`; config trả cờ schedule, early access và giá PARSE. Cờ AI tổng `ai_enabled=false` cũng tắt PARSE.
- `aiSchedule`/`claimEarlyAccess` dùng `defineBoolean("ENFORCE_APP_CHECK", {default: true})`. App Check của `aiChat` giữ nguyên `false` như trước.
- `remoteconfig.template.json` có đủ 7 key W2 và global cap 500. Rules hiện có đã `allow read, write: if false` cho `/entitlements/**` và `/internalAiQuota/**`; không cần sửa rules.

### Android

- Nút Nhập nhanh ở Lịch ngày và Góp ý lịch tuần, ẩn khi config báo tắt hoặc chưa có cấu hình W2. Route có kiểm tra PIN độc lập; từ Lịch ngày luôn đi qua parent gate.
- Ô nhập nhiều dòng, 3 ví dụ vi/en, nút nhận giọng bằng `ACTION_RECOGNIZE_SPEECH`, ưu tiên vi-VN và thử ngôn ngữ máy khi trình nhận giọng báo lỗi. Hủy chủ động không mở lại nhận giọng. Không có activity nhận giọng thì ẩn mic.
- Có giải thích dữ liệu gửi AI và luồng Google sign-in hiện có. Khi auth đổi sang tài khoản đăng nhập và enrollment mở, gọi claim best effort/idempotent; lỗi và timeout claim không làm gián đoạn UI hoặc observer tài khoản.
- Draft chỉ trong ViewModel. Mỗi task có ID `ScheduleIds.newId()`, profile hiện tại, duration theo draft, break=0, isCustom theo enum; không gửi thông tin profile sang AI. Mỗi giờ thức/ngủ và ca học là một dòng riêng, có checkbox/sửa ngày, giờ và thời lượng task. Confidence <0.6 bỏ chọn sẵn, nhãn “Cần kiểm tra”; questions hiển thị cho phụ huynh.
- Preview gộp các mục đang chọn vào lịch hiện có, giữ commute/các ngày không chọn, thêm ca học và bỏ trùng ca giống hệt. Advisor so sánh trước/sau, đánh dấu mục tạo OVERLAP/SLEEP_SHORT mới, kể cả qua nửa đêm/CN→T2. Chỉ gọi advisor hai lần cho mỗi lần đánh giá, không chạy lại cho từng dòng.
- Hai dòng giờ thức/ngủ đặt giờ khác nhau cho cùng ngày bị chặn để không âm thầm ghi đè. Lịch đổi do sync/chỉnh tay thì phải tạo draft mới. Đổi profile xóa nội dung/draft; phản hồi cũ không ghi đè dù quay lại profile ban đầu.
- Áp dụng thêm AddTask/SetAnchors qua use case W1: snapshot, transaction Room, alarm, observer sync; có snackbar Hoàn tác 10 giây, nút khôi phục và snapshot 7 ngày như W1. Lỗi giữ draft/text để thử lại.
- Dòng dùng thử lấy expiry/tier từ usage, số lượt = min(remainingQuestions, remainingCredits / parseCost), nên vẫn đúng khi giá PARSE thay đổi.

### Privacy và store

- Privacy vi/en bổ sung xử lý chữ nhập lịch, nhận giọng của thiết bị/Google, Firebase Functions/OpenRouter, không ghi nội dung vào server/log, không huấn luyện, không gửi trường tên bé và phân biệt draft tạm với lịch đã áp dụng/đồng bộ.
- Store vi-VN/en-US thêm đúng 2 dòng về Nhập nhanh và tư vấn offline, nêu điều kiện đăng nhập/AI được bật.

## Quyết định khi spec và code khác nhau

1. **Quy tắc Cao:** AddTask và SetAnchors thuộc luồng nhập do phụ huynh duyệt, cho lưu sau cảnh báo preview. SetAnchors giữ semantics nhập giờ thủ công của W1 (`saveAnchors` vốn không chặn finding Cao). MoveTask/ResizeTask/SetBed của gợi ý offline vẫn giữ guard W1; không cho trộn gợi ý offline với import để lách guard. Comment và test nằm trong ApplyScheduleUseCase. Không tự loại task vì trùng lịch.
2. **Schema maps thưa:** wake/bed chỉ chứa ngày được nói, nên schema dùng `strict: false`; server và Android vẫn validate độc lập. Không điền giờ/ngày mặc định vào draft thiếu thông tin. Reject cả draft sai schema, refund, thay vì âm thầm mất một mục.
3. **Bật cờ sau deploy:** template beta đặt schedule/enrollment=true theo task; fallback server và Android là false nếu key/API chưa tồn tại hoặc config chưa tải được. Tránh hiện nút gọi callable chưa deploy. Schedule không phụ thuộc build flag ENABLE_AI_CHAT.
4. **Guest:** contract Android luôn yêu cầu sign-in theo D. Server có guestId dự phòng để có thể bật `ai_schedule_guest_enabled` sau này; mặc định false, guest bị chặn trước quota/provider.
5. **Manifest:** Android mới cần package visibility để xác định trình nhận giọng. Chỉ thêm `<queries>` cho action; không thêm permission, không thay version Room (5). Thay đổi bỏ USE_EXACT_ALARM đã có sẵn trước W2 vẫn ở ngoài commit.
6. **Refund gateway hiện có:** Firestore merge một map đã bỏ key vẫn giữ reservation cũ. Sửa refund ghi lại document đọc trong transaction để key thật sự mất, retry dùng lại ID sau lỗi được. W2 giữ ngày quota trong identity của yêu cầu để refund đúng ledger khi qua nửa đêm. Không đổi cách tính phí/thành công của chat.
7. **Không chung transaction Room/DataStore:** giữ cơ chế snapshot/bù lỗi W1, không thêm migration/journal. Không bảo đảm atomic xuyên hai storage nếu process chết giữa các bước. Toàn bộ test chạy trong working tree chung có thay đổi release/P0 sẵn; commit W2 không mang các thay đổi đó.
8. **Tên trong chữ tự nhập:** không có field tên bé trong request; UI yêu cầu không nhập tên/trường và prompt không lặp dữ liệu cá nhân. Không tuyên bố có bộ lọc nhận diện mọi tên riêng trong văn bản tự do. Current chỉ tối đa 60 tên hoạt động/ngày/giờ/thời lượng, không gửi profile name/id/photo, anchors snapshot hay routine history.

## Kiểm tra & bằng chứng

Các lệnh cuối:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug
npm --prefix functions test
npm --prefix functions run lint
git diff --check
git diff --cached --check
```

| Kiểm tra | Kết quả |
|---|---|
| Android unit + assembleDebug + lintDebug | **PASS** — 194 test; 0 failure/error/skipped |
| Lint Android | **PASS** — 0 error/fatal, 206 warning trên toàn working tree |
| Functions node --test | **PASS** — 53/53, fetch mock + Firestore giả, không gọi OpenRouter/Firebase production |
| ESLint Functions | **PASS** |
| Whitespace working tree + staged | **PASS** |
| Manifest/Room | Quyền giống baseline (7 uses-permission); Room version 5, không sửa entity/schema/migration |
| Eval script không có key | `env -u OPENROUTER_API_KEY npm --prefix functions run eval:schedule` → exit 1 với thông báo yêu cầu key, trước mọi network call |
| Máy thật | `adb devices` không có thiết bị; chưa manual UI/giọng/PIN/App Check production |

- Android mới: mapper/task/anchors/profile; confidence biên 0.6; preview task/task, task/school, giờ ngủ/ngày thức tiếp theo, school qua CN→T2; giờ nhập xung đột; AddTask+anchors+undo với Room thật, rollback và giữ profile khác; lỗi quota/network/parse giữ text; stale apply; đổi profile và phản hồi cũ; claim lỗi/timeout/cờ đóng.
- Functions mới: parse hợp lệ, 12 loại schema lỗi, JSON hỏng, provider HTTP/network lỗi đều refund; unknown enum; chặn guest/input/ảnh/name metadata; quota early/free/premium/guest, giá credit/global cap/duplicate; claim đồng thời/idempotent/closed/expiry cũ; ledger qua nửa đêm.
- Golden `functions/test/fixtures/schedule-parse.vi.json`: **26 câu tiếng Việt + JSON kỳ vọng**. Test offline xác nhận validate/normalize expected JSON; **chưa đo độ hiểu ngôn ngữ của model thật**.
- `eval:schedule`: manual, có key mới chạy; in % days/start/duration và tỷ lệ hỏi lại khi thiếu dữ liệu; task/anchor/school đều được xét, thiếu/thừa mục tính sai. Gate lỗi nếu response lỗi, days/start <90% hoặc thiếu questions trong các case cần hỏi. Không kiểm tra câu chữ giải thích/tên hoạt động; Duong vẫn phải xem chất lượng preview.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; unit report: `app/build/reports/tests/testDebugUnitTest/index.html`; lint report: `app/build/reports/lint-results-debug.html`.

## Checklist deploy cho Duong — CHƯA thực hiện

Thứ tự bắt buộc: cấu hình tài khoản/App Check → deploy Functions → kiểm tra callable → publish Remote Config beta → smoke máy thật → mở beta.

### 1. Trước deploy

- [ ] Claude review G3 commit W2; Duong duyệt bản máy thật. Không merge/push từ phiên implement này.
- [ ] Chọn đúng Firebase project `kid-focus-app`, Android application ID `com.kidfocusstudio.timer`, Functions region `asia-southeast1`; xác minh các BuildConfig Firebase/Google sign-in của bản phát hành đúng project.
- [ ] Kiểm tra OPENROUTER_API_KEY trong Secret Manager còn enabled và runtime service account có quyền đọc. Không đưa key vào Android/repo/log. Không cần đổi secret trong W2; RevenueCat secret/webhook giữ nguyên.
- [ ] Trên OpenRouter, kiểm tra chính sách không lưu/không huấn luyện và endpoint model chấp nhận JSON schema, `data_collection: deny`; không bật prompt logging cho account/provider. Chỉ publish beta sau golden eval model thật.
- [ ] Backup Remote Config/rules production hiện có. Giữ schedule/enrollment tắt trong lúc chuẩn bị; chỉ merge các key W2 và global cap, giữ model chat/conditions/key khác của production. Gateway hiện đọc **defaultValue server**, không dùng targeting client cho các key này.

### 2. App Check trước khi bật AI lịch

- [ ] Play Console → App integrity: lấy **SHA-256 app signing certificate** của bản do Play ký (không lấy nhầm upload key).
- [ ] Firebase Android app/App Check: đăng ký SHA-256, cấu hình Play Integrity; liên kết Play Integrity API tới đúng Cloud/Firebase project. Thử bản cài qua closed testing của Google Play.
- [ ] Debug build đã có DebugAppCheckProvider. Chạy trên máy/emulator, lấy debug token ở log riêng → App Check → Manage debug tokens → thêm token. Không ghi token trong repo/báo cáo hay bản release.
- [ ] Đặt deployment param `ENFORCE_APP_CHECK=true` (default true); không tắt param để vượt smoke test. Hai callable mới phải từ chối thiếu/sai App Check; aiChat vẫn theo chính sách cũ.

Nguồn: [Firebase Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider), [Firebase debug provider](https://firebase.google.com/docs/app-check/android/debug-provider).

### 3. Deploy Functions và rules

- [ ] Dùng Node.js 22 cho pipeline Functions, cài dependency từ lockfile, chạy lại test/lint trước deploy.
- [ ] Deploy hai callable mới và hai callable dùng code quota/config chung (aiChat cần redeploy để nhận tier early/global cap mới):

```sh
firebase deploy --only functions:aiSchedule,functions:claimEarlyAccess,functions:getAiConfig,functions:aiChat --project kid-focus-app
```

- [ ] Không cần deploy revenueCatWebhook/deleteAccount cho W2. Không đổi secret hoặc App Check của aiChat.
- [ ] So sánh rules đang deploy với `firestore.rules`: client tuyệt đối không được ghi entitlements/quota. File repo đã deny cả read/write; W2 không sửa rules. Chỉ deploy rules nếu production chưa có các deny tương ứng, sau review riêng của Duong.
- [ ] Với token App Check đúng: guest PARSE → unauthenticated; enrollment đóng → claim failed-precondition; thiếu/sai token App Check bị chặn trước handler.

### 4. Publish Remote Config beta

- [ ] Publish các giá trị: `early_access_open=true`, `ai_early_access_days=60`, `ai_early_daily_credits=15`, `ai_global_daily_credits=500`, `ai_schedule_guest_enabled=false`, `ai_schedule_model=google/gemini-2.5-flash-lite`, `ai_schedule_parse_cost=1`, `ai_schedule_enabled=true`; cờ tổng `ai_enabled` phải true.
- [ ] Chờ cache server tối đa 5 phút; kiểm tra getAiConfig trả cờ/tier/expiry/usage đúng. Nút không phụ thuộc ENABLE_AI_CHAT.
- [ ] Khi lên production: tắt early_access_open để ngừng nhận người mới; grant đã cấp giữ đến hết hạn. Muốn rollback PARSE: tắt ai_schedule_enabled, giữ tư vấn offline W1.

### 5. Golden/model và smoke máy thật (Duong chạy tay)

- [ ] Trong môi trường riêng, đặt OPENROUTER_API_KEY rồi chạy `npm --prefix functions run eval:schedule` (có thể đặt AI_SCHEDULE_MODEL). Script gọi provider trực tiếp, có phí và **không đi qua quota Firebase**; không chạy trong CI/test tự động. Lưu % days/start/duration để Claude/Duong review; days/start ≥90%, case thiếu thông tin phải hỏi lại.
- [ ] Tài khoản test riêng: Google sign-in lần đầu → trial 60 ngày/15 credit; sign-out/sign-in và claim lại không gia hạn. Premium vẫn ưu tiên; tài khoản đã hết early về free. Tắt enrollment không xóa grant cũ.
- [ ] Nhập câu ví dụ T3/T5 18h/60 phút bằng gõ và giọng; ca trường sáng/chiều; thức/ngủ; câu thiếu giờ/ngày. Kiểm tra checkbox confidence thấp, sửa giờ/ngày/thời lượng, questions và cảnh báo mới.
- [ ] Apply → task/anchors đúng profile, alarm hoạt động và sync cập nhật; snackbar undo trong 10 giây và khôi phục 7 ngày. Chuyển profile khi chờ response; đồng bộ làm đổi lịch thì apply phải báo stale.
- [ ] Lỗi mạng, hết quota, model lỗi: text/draft giữ nguyên. Trình nhận giọng không có/không hỗ trợ vi-VN, hủy nhận giọng, font lớn, back/navigation và thiết bị chưa đặt PIN.
- [ ] Theo dõi `_global` daily ledger, quota tài khoản và log chỉ mã lỗi. Đối chiếu dùng thử/ngày đã dùng, tuyệt đối không ghi nội dung lịch vào log khi debug production.
- [ ] Publish privacy policy và store text, cập nhật Data safety, upload bản closed test/version release theo checklist release riêng (W2 không tăng version hoặc tự upload).

## Gợi ý Data safety cho Duong

Đây là mapping phần W2; giữ các khai báo Firebase/Auth/RevenueCat/sync hiện có. Đối chiếu nhà cung cấp thực tế trước khi nộp.

| Dữ liệu/luồng | Gợi ý khai báo |
|---|---|
| Chữ nhập lịch và compact current gửi PARSE | App activity → Other user-generated content; optional, app functionality; xử lý tạm thời nếu các provider chỉ giữ RAM để đáp ứng request |
| Lịch đã Apply và đồng bộ Firestore | Cùng loại nội dung; lưu lâu dài theo sync hiện có, không đánh dấu cả loại dữ liệu là ephemeral chỉ vì PARSE tạm thời |
| UID, quota, thời hạn Early Access | User IDs và app interactions theo khai báo tài khoản/AI hiện có; không ephemeral; functionality/fraud prevention |
| Nhóm tuổi | Xem xét Other personal info cho age band theo nhóm tuổi của app; chỉ gửi nhóm, không ngày sinh/tên profile |
| Âm thanh | KidFocus không nhận/lưu/upload file audio. Nhận giọng do app hệ thống/Google; xác minh luồng/dịch vụ nhận giọng của bản phát hành |
| Nhà cung cấp/mã hóa/xóa | Firebase/OpenRouter là processors; chỉ chọn ngoại lệ “service provider” về sharing khi thỏa điều kiện hợp đồng/chính sách; HTTPS, optional AI, xóa tài khoản xóa entitlement và quota cá nhân |

Dữ liệu truyền off-device dù xử lý tạm vẫn cần trả lời form; nhãn sharing có ngoại lệ service provider theo điều kiện. Nguồn: [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469). Chính sách routing cần đối chiếu: [OpenRouter data controls](https://openrouter.ai/blog/insights/zero-data-retention/).

## File trong commit W2

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/kidfocus/timer/data/di/DataModule.kt`
- `app/src/main/java/com/kidfocus/timer/data/remote/GeminiApi.kt`
- `app/src/main/java/com/kidfocus/timer/data/remote/ScheduleAiApi.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ApplyScheduleUseCase.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleAdvisor.kt`
- `app/src/main/java/com/kidfocus/timer/domain/schedule/ScheduleDraft.kt`
- `app/src/main/java/com/kidfocus/timer/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/kidfocus/timer/ui/navigation/NavRoutes.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/DailyScheduleScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/QuickScheduleScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/screens/SmartScheduleScreen.kt`
- `app/src/main/java/com/kidfocus/timer/ui/viewmodel/QuickScheduleViewModel.kt`
- `app/src/main/java/com/kidfocus/timer/ui/viewmodel/ScheduleAccessViewModel.kt`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/kidfocus/timer/data/schedule/RoomScheduleStoreTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ApplyScheduleUseCaseTest.kt`
- `app/src/test/java/com/kidfocus/timer/domain/schedule/ScheduleDraftTest.kt`
- `app/src/test/java/com/kidfocus/timer/ui/viewmodel/QuickScheduleViewModelTest.kt`
- `app/src/test/java/com/kidfocus/timer/ui/viewmodel/ScheduleAccessViewModelTest.kt`
- `docs/CLAUDE_AI_SCHEDULE_W2_REPORT.md`
- `docs/privacy-policy.html`
- `functions/eslint.config.js`
- `functions/index.js`
- `functions/package.json`
- `functions/schedule.js`
- `functions/scripts/eval-schedule.js`
- `functions/test/ai-config.test.js`
- `functions/test/fixtures/schedule-parse.vi.json`
- `functions/test/schedule.test.js`
- `remoteconfig.template.json`
- `store-listing/en-US/full_description.txt`
- `store-listing/vi-VN/full_description.txt`

## Giữ working tree

- Không reset/stash, không sửa `.omc`, không push, không deploy hay gọi OpenRouter thật.
- Lưu baseline trước sửa. 15 file tracked cũ ngoài phần trùng W2 giữ nguyên từng byte. Bốn file trùng là Manifest, AppNavigation, strings vi/en: chỉ stage patch W2; phần release/Learning có sẵn giữ unstaged.
- Các tài liệu task/design/context/launch và file mới của phiên khác có sẵn không nằm trong commit. Sau commit vẫn có đúng 19 đường dẫn tracked với thay đổi cũ chưa stage; đó là trạng thái intentional ban đầu.
- Chờ Claude G3, model golden live/App Check smoke sau deploy và Duong nghiệm thu cuối ngày; chưa tuyên bố beta sẵn sàng phát hành.
