# Báo cáo W3 — Nhập lịch bằng ảnh

Ngày: 2026-09-28 · Implementer: Codex · Reviewer: Claude (Cowork) · Nghiệm thu: Duong

## Trạng thái

- Branch `feature/smart-schedule-w3` tạo trực tiếp từ HEAD W2 G2 `4258799`.
- Hoàn thành A–D và kiểm tra tự động của `CLAUDE_AI_SCHEDULE_TASK_W3.md`.
- Commit cục bộ chứa báo cáo này; không push, merge hoặc deploy. Không đổi production/secrets, không gọi OpenRouter thật trong test.
- Chờ Claude review, golden model thật và Duong nghiệm thu máy thật; chưa tuyên bố chất lượng đọc ảnh đạt gate 90%.

## Triển khai

### Android

- Màn Nhập nhanh có nút **Ảnh** khi config/tier cho phép; chọn **Thư viện** bằng `PickVisualMedia(ImageOnly)` hoặc **Chụp** bằng `TakePicture`. Không có Photo Picker thì contract dùng trình chọn tài liệu hệ thống. Không xin quyền camera/thư viện.
- FileProvider không exported, chỉ chia sẻ thư mục cache chụp ảnh `schedule_capture/`, authority theo application ID (hỗ trợ cả debug suffix). Không mở toàn bộ cache/files ra ngoài.
- Xử lý ảnh trên IO: đọc kích thước và decode có sampling, áp dụng cả 8 orientation EXIF (kể cả mirror), thu cạnh dài ≤1600 px, JPEG quality 80. Nếu vượt 1.000.000 byte, giảm quality rồi thu thêm khi cần. Re-encode từ pixel, không sao chép EXIF/GPS/comment/camera metadata.
- Thumbnail, nhắc che tên/trường, số credit ảnh và checkbox đồng ý trước khi gửi. **Cắt** có khung chữ nhật kéo 4 góc và slider có nhãn cho TalkBack/điều chỉnh chính xác. Cắt tạo JPEG mới, xóa bản trước và yêu cầu xác nhận lại.
- Có thể gửi ảnh không kèm chữ, hoặc thêm chữ về giờ vào/ra học trong cùng request. `currentSchool` chỉ gửi days/start/end, không gửi label trường, tên/profile ID/ảnh đại diện.
- File ảnh đã xử lý bị xóa sau mọi lần gửi, kể cả quota/network/parse/tier error. Ảnh camera gốc bị xóa ngay sau chuẩn bị, hoặc khi hủy/lỗi launch. Bỏ ảnh, đổi profile, back và ViewModel clear đều dọn file. Kết quả chuẩn bị ảnh cũ bị loại và xóa nếu profile đã đổi. File bỏ lại do process chết được dọn ở lần khởi tạo processor tiếp theo, không xóa cache của processor khác còn hoạt động. Ảnh gốc từ thư viện không bị sửa/xóa.
- Reply đi vào đúng draft/preview W2: chọn từng dòng, confidence, questions, sửa ngày/giờ/thời lượng, cảnh báo xung đột, stale guard, Apply/Undo W1/W2. Không thêm DB/table/migration hoặc logic ADVISE.
- `IMAGE_TIER_REQUIRED` có thông báo riêng và refresh config. Config cũ/chưa tải hoặc tài khoản không đủ tier ẩn nút ảnh. Usage tính theo giá ảnh/chữ và giới hạn PARSE riêng khi server trả về.

### Functions và Remote Config

- `aiSchedule` mở whitelist cho `image` và `currentSchool`; giữ các validation W2. Ảnh là base64 thuần, canonical, ≤1.400.000 ký tự; bytes decode ≤1.000.000, có JPEG SOI/marker và EOI. Không nhận data URL/PNG/base64 hỏng/mảng ảnh. Validate ảnh/context trước resolve identity và reserve quota.
- Tier mặc định `early,premium`; tier khác bị `permission-denied / IMAGE_TIER_REQUIRED` trước quota/provider. Model vision và giá ảnh cấu hình riêng; default `google/gemini-2.5-flash-lite` / 3 credit. Request dùng text + `image_url` JPEG; không gửi image base64 lần nữa trong text context.
- Prompt riêng cho TKB Việt Nam: Sáng/Chiều, Tiết 1–5, Chào cờ, Sinh hoạt lớp, môn học → chỉ ca `anchors.school`, không tạo task môn học trong trường. Giờ phải có trong ảnh/chữ/currentSchool phù hợp; không suy giờ từ số tiết. Thiếu giờ hoặc chỉ có giờ cả ngày nhưng chưa rõ ca sáng/chiều → hỏi lại. Lịch gia đình → tasks như W2.
- `max_tokens` PARSE hạ 8000 → **2000** cho cả chữ và ảnh. Giữ temperature 0, JSON schema, validator server/client, deadline, App Check và `provider.data_collection: deny` của W2. Server không lưu/log ảnh/chữ, chỉ log mã lỗi.
- Ledger hiện có thêm `scheduleParses`; reserve trong cùng transaction account/global, lưu dấu PARSE trong reservation. Thành công giữ count; mọi lỗi refund cả credit/count/model/global, idempotent, dùng đúng ngày cố định qua nửa đêm. Chat không tăng count và vẫn dùng chung ngân sách credit.
- `ai_schedule_free_daily_parses`: chỉ giới hạn thêm tier free. Code default **0 = không giới hạn thêm**, giữ W2. Template beta = **3**; early/premium không bị cap riêng. `usage.remainingScheduleParses` trả khi free cap đang bật; UI không hiển thị số lượt cao hơn cap.
- `getAiConfig` trả image tiers/cost và free cap để Android quyết định hiển thị; không cần gửi vision model cho client.

### Privacy và golden

- `docs/privacy-policy.html` vi/en mô tả chọn/chụp/cắt/xác nhận, resize/re-encode bỏ metadata trên máy, xử lý tạm qua Firebase/OpenRouter, không lưu ảnh trên server/log/sync, xóa cache và khuyến nghị che tên/trường.
- `functions/scripts/make-timetable-fixtures.js` tự vẽ **12 JPEG giả** bằng SVG + Sharp, không tải ảnh ngoài/không có dữ liệu trẻ. Sharp là dev dependency cho tạo fixture, không dùng trong handler runtime.
- 10 TKB trường: ca sáng, chiều, hai ca, ngày xen kẽ, T7, thiếu giờ, giờ bằng chữ, giờ từ currentSchool, một ca thiếu giờ, giờ cả ngày cần hỏi. 2 lịch gia đình: bảng và kiểu chữ viết tay. Có JSON kỳ vọng tại `functions/test/fixtures/schedule-images.vi.json` và JPEG đã commit để test offline không cần chạy generator.
- `eval:schedule` chạy cả 26 câu W2 + 12 ảnh; `--images`/`--text` chọn riêng. Biến model ảnh `AI_SCHEDULE_VISION_MODEL`, model chữ `AI_SCHEDULE_MODEL`. Chỉ gọi provider khi có key; gate days/start ≥90% và questions theo **từng suite**, không dùng điểm chữ che điểm ảnh. Partial case còn ca hợp lệ vẫn phải hỏi phần thiếu. Script có phí, không đi qua quota Firebase; không nằm trong `npm test`.

## Quyết định khi spec/code khác nhau

1. **1600 thay vì 1280:** task W3 cụ thể ưu tiên design tổng. JPEG ~80 và giới hạn 1 MB giữ đủ đọc chữ; có fallback nén/thu thêm để luôn đạt giới hạn.
2. **1 MB và 1.4 MB base64:** dùng 1.000.000 byte / 1.400.000 ký tự, không dùng MiB. Base64 ảnh Android tối đa khoảng 1.333.336 ký tự; server kiểm tra cả hai giới hạn trước quota.
3. **currentSchool không label:** chỉ days/start/end là đủ chọn ca; giảm gửi thông tin cá nhân từ nhãn nhập tay. Không đổi schema Room hay model anchors W1.
4. **Lỗi gửi ảnh:** giữ text và preview W2 để thử lại/áp dụng, nhưng xóa file ảnh sau lần gửi lỗi theo yêu cầu W3. Muốn gửi lại ảnh phải chọn/chụp lại. Crop cancel giữ ảnh đang review; nút Bỏ ảnh/back xóa ảnh.
5. **Free cap chưa chốt:** code default 0 giữ hành vi W2; template 3 đúng task, chưa publish. Duong chốt giá trị khi deploy; không tự thay production.
6. **Process death:** không thể thực thi cleanup khi process đã bị kill. Tổ chức cache theo owner trong process và dọn owner bỏ lại ở lần mở tiếp theo; không thêm service/permission hay persistence cho draft.
7. **Manifest/baseline:** W3 chỉ thêm provider. Phần bỏ `USE_EXACT_ALARM` và thay đổi release/Learning có sẵn giữ unstaged; không mang vào commit W3. App build chỉ stage dependency EXIF, không stage các nâng toolchain có sẵn.

## Kiểm tra và bằng chứng

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug lintDebug
npm --prefix functions test
npm --prefix functions run lint
npm --prefix functions run fixtures:schedule
env -u OPENROUTER_API_KEY npm --prefix functions run eval:schedule -- --images
git diff --check
git diff --cached --check
adb devices
```

| Kiểm tra | Kết quả |
|---|---|
| Android unit/build/lint | **PASS** — 209 tests, 0 failure/error/skipped; APK debug build được |
| Android lint | **PASS** — 0 error/fatal; 200 warning toàn working tree, không warning mới ở file ảnh |
| Functions | **PASS** — 72/72 tests; mock fetch + Firestore giả, không production calls |
| ESLint | **PASS** |
| Fixture generator | **PASS** — 12 JPEG và expected JSON; đã xem ảnh mẫu hai ca để kiểm tra chữ/giờ bố cục |
| Eval thiếu key | **PASS** — all / --images / --text đều exit 1 trước network, yêu cầu key; không chạy eval có key |
| Whitespace working tree/staged | **PASS** |
| Permission | Source working tree giữ nguyên 7 uses-permission baseline; staged manifest giữ nguyên permission của W2. APK merged không có CAMERA/READ_MEDIA/READ_EXTERNAL_STORAGE/RECORD_AUDIO; permission SDK khác vẫn có theo dependency hiện hữu |
| Room | Giữ version **5**; không sửa database/entity/schema/migration |
| Máy thật | `adb devices` không có thiết bị; chưa nghiệm thu camera/picker/crop/PIN/App Check production |

Android mới kiểm tra pixel xoay/mirror EXIF, metadata thật trước/sau, downscale ảnh lớn, nén ảnh noise >1 MB, base64 size, crop, decode/crop lỗi, cache capture/orphan/owner; ViewModel kiểm consent, ảnh-only/ảnh+chữ, chung preview, lỗi tier/network, hủy camera/lỗi launch, remove/back/clear, đổi profile và late result; config cũ/free/early/premium/guest kiểm điều kiện ẩn nút.

Functions mới kiểm ảnh sai/oversize và currentSchool sai trước quota, tier deny/allow cấu hình, vision model/credit/2000 token/multimodal prompt, refund ảnh, free cap đồng thời (3/6 thành công), chat vẫn dùng được, default không cap, early/premium exempt, ngày mới và refund đúng ledger; 12 expected JSON/JPEG được kiểm offline. **Các test không chứng minh model thật đọc đúng ảnh.**

Artifact: `app/build/outputs/apk/debug/app-debug.apk`; unit report `app/build/reports/tests/testDebugUnitTest/index.html`; lint `app/build/reports/lint-results-debug.html`. Local npm dùng Node 24.14; deployment Functions vẫn theo Node 22 của package.

## Checklist deploy bổ sung — CHƯA thực hiện

Giữ checklist App Check/Auth/secrets/quota/privacy W2. Thứ tự: Claude review + Duong duyệt → backend → config → golden/smoke → beta.

- [ ] Claude review commit W3 và Duong nghiệm thu APK; không merge/push/deploy từ phiên implement này.
- [ ] Dùng Node 22, cài từ lockfile, chạy test/lint trong môi trường deploy. Giữ Secret Manager và `ENFORCE_APP_CHECK=true`; xác minh Play Integrity/App Check theo W2, không tắt để vượt smoke.
- [ ] Redeploy `aiSchedule` và `getAiConfig` trước khi phát hành client/cấu hình ảnh:

```sh
firebase deploy --only functions:aiSchedule,functions:getAiConfig --project kid-focus-app
```

- [ ] W3 không yêu cầu redeploy claim/webhook/deleteAccount hoặc đổi rules. Gateway quota chung cũng được dùng bởi aiChat: nếu muốn chat/getAiConfig trả usage cap đồng nhất ngay sau upgrade, redeploy `aiChat` cùng đợt; semantics chat không đổi. Không đổi App Check của aiChat ngoài review riêng.
- [ ] Backup Remote Config production; merge bốn key W3 dưới đây, không ghi đè model chat/conditions/key khác. Server đọc defaultValue và cache 5 phút. Duong chốt cap free trước publish:

| Remote Config key | Template beta | Code fallback |
|---|---|---|
| `ai_schedule_vision_model` | `google/gemini-2.5-flash-lite` | giống template |
| `ai_schedule_image_cost` | `3` | `3` |
| `ai_schedule_image_tiers` | `early,premium` | `early,premium` |
| `ai_schedule_free_daily_parses` | `3` | `0` — không cap thêm |

- [ ] Giữ `ai_schedule_enabled`/`ai_enabled` theo kế hoạch beta W2; kiểm `getAiConfig` trả image tiers/cost, tier/expiry và remainingScheduleParses đúng. Client với config cũ phải ẩn ảnh. Rollback riêng ảnh: `ai_schedule_image_tiers=""`; rollback PARSE toàn bộ: `ai_schedule_enabled=false`.
- [ ] Kiểm endpoint vision hỗ trợ JPEG + JSON schema + `data_collection: deny`; xác minh retention thực tế trước khai ephemeral. Trong môi trường riêng có key, Duong chạy tay `npm --prefix functions run eval:schedule -- --images` rồi suite chữ; lưu điểm để Claude review. Gate **days/start ≥90% từng suite**, ảnh thiếu giờ phải hỏi, TKB trường không được tạo task môn học. Không đưa key/model response/ảnh thật vào repo/log CI.
- [ ] Máy thật: Photo Picker trên API mới và fallback thiết bị cũ; có/không có camera handler; cancel/launch lỗi; portrait/landscape EXIF và mirror; ảnh lớn/PNG đầu vào; crop bốn góc/slider, font lớn/TalkBack, crop cancel/consent reset; checkbox bắt buộc trước gửi; ảnh-only và ảnh+chữ.
- [ ] Tài khoản free/early/premium/expired trial: ẩn nút đúng config, forged free image bị server deny không trừ credit, ảnh thành công trừ 3, failed parse hoàn credit/count, free lần thứ 4 bị cap khi config=3; chat vẫn theo budget chung; kiểm quota ngày mới.
- [ ] Apply ảnh dùng preview W2: confidence/questions/cảnh báo, stale sync, đúng profile, alarm + sync, snackbar Undo 10s và restore 7 ngày. Đổi profile/back trong lúc chuẩn bị/gửi không nhận draft cũ; kiểm cache bị dọn sau thành công/lỗi/hủy và restart sau process kill. Ảnh không có trong Firestore hoặc log.
- [ ] Publish privacy vi/en, cập nhật Data safety cho Photos và đối chiếu retention/provider thực tế; closed test/release theo checklist hiện hữu, W3 không tự tăng version/upload.

## Data safety bổ sung

| Luồng W3 | Gợi ý khai báo |
|---|---|
| Ảnh lịch gửi qua Firebase/OpenRouter | **Photos and videos → Photos**; optional; app functionality; xử lý tạm (ephemeral) nếu Firebase/OpenRouter/provider chỉ giữ trong RAM đủ phục vụ request |
| Resize/crop/strip EXIF và cache trên máy | Xử lý cục bộ; không dùng GPS metadata, không gửi ảnh khi phụ huynh chưa xác nhận; ảnh gốc thư viện giữ nguyên |
| Lịch đã Apply/Firestore và quota UID | Giữ mapping W2; dữ liệu lịch/quota có lưu lâu dài, không đánh dấu chúng là ephemeral cùng ảnh |

Ảnh truyền ra khỏi thiết bị vẫn phải được xét trong form dù xử lý tạm. Ngoại lệ sharing cho service provider cần thỏa điều kiện thực tế; Duong xác nhận trước nộp. Nguồn: [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

## Giữ working tree

- Không reset/stash, không sửa `.omc`, không push/merge/deploy. Lưu baseline trước sửa và đối chiếu hash của 15 file cũ không trùng W3: giữ nguyên từng byte.
- Bốn file trùng là app build, Manifest, strings vi/en: stage patch W3 riêng; các thay đổi release/Learning có sẵn vẫn unstaged. Tài liệu task/design/context/review có sẵn và file phiên khác không nằm trong commit.
- Sau commit vẫn còn 19 đường dẫn tracked thay đổi baseline intentional. Báo cáo này và file mới/W3 là phần commit, không dọn working tree của phiên khác.
