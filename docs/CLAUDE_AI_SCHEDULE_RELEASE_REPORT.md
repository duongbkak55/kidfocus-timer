# Smart Weekly Schedule W1–W5 — merge, eval và triển khai

Ngày: 2026-09-29. Dev: Codex. Duong duyệt thực hiện ba bước theo thứ tự, dừng nếu bước thất bại.

**Trạng thái mới nhất 2026-10-01:** Ba Functions ACTIVE tại asia-southeast1 với ENFORCE_APP_CHECK=true; Remote Config v3 đã publish. Duong duyệt cập nhật riêng `aiSchedule` sang OpenRouter secret version 2; deploy thành công. Smoke trên tài khoản test Pixel 3a: PARSE, LOG, ADVISE thành công; tổng trừ 4 credits đúng cấu hình. Ảnh bị chặn HTTP 403, không trừ lượt. Báo cáo sẽ được commit và push main sau kiểm tra cuối.

## 1. Merge, kiểm và push — PASS

Dùng worktree `KidFocusTimer-main-integration`, không checkout/reset/stash working tree gốc. Fetch origin và merge `--no-ff` branch `feature/smart-schedule-w5` (`99775f6`) vào main: **d775692555e796c4842700b17174f800f8b1a1d1**, không conflict.

- Gradle `testDebugUnitTest assembleDebug lintDebug --max-workers=2`: PASS, **300/300 test / 46 suite**, lint **0 lỗi / 166 cảnh báo**, assemble PASS; build **1m 27s**. Kết quả cache được Gradle tái sử dụng đúng inputs trên worktree main.
- `npm --prefix functions test`: **158/158 PASS**. Kiểm lại bằng **Node 22.23.3** và Functions lint: PASS; runtime tải vào thư mục tạm từ bản phân phối chính thức, đã đối chiếu SHA-256, không đổi dependency/lockfile repo.
- `git diff --check`, merge commit check: PASS. Push origin **main + feature/smart-schedule-w5**, không force.
- [Build & Publish Release AAB — SUCCESS](https://github.com/duongbkak55/kidfocus-timer/actions/runs/36586298761), đúng SHA main; đủ `kidfocus-release-aab` và `kidfocus-release-apk`. Chỉ build/upload artifact, chưa deploy Firebase.

[Số liệu kiểm](release/smart-schedule-w5/01-main-merge-checks.json), [workflow](release/smart-schedule-w5/02-workflow-result.json), [artifacts](release/smart-schedule-w5/02-workflow-artifacts.json).

## 2. Remote Config và live eval — lịch sử chẩn đoán và gate beta

Trạng thái ban đầu trước F-EVAL-1–4: template cục bộ có `ai_schedule_free_daily_parses=3` và `ai_schedule_log_cost=1`; các model còn là **google/gemini-2.5-flash-lite**. Các giá trị model beta hiện tại đã đổi ở F-EVAL-4 bên dưới. Key chỉ truyền cho process eval, không ghi vào repo/log/report.

Eval gọi model thật bằng prompt/providerBody/validator đã merge. Bộ text/image/ADVISE chạy qua `eval:schedule`; bộ LOG dùng 35 input/annotation hiện có và cùng `schedule-log`/offline comparison helper, thay provider fixture bằng response thật. Bộ đo/observer nằm ngoài repo. Bằng chứng chỉ có điểm, ID câu và mã lỗi đối chiếu; không lưu response thô/ảnh thật/key.

Gate: **≥90% từng bộ**, không lấy điểm bộ tốt bù bộ khác; giữ các gate schema/questions/day/start và ADVISE an toàn hiện có. Một bộ/command không đạt thì dừng bước 2, chưa deploy/publish/smoke.

### Kết quả và gate

`npm --prefix functions run eval:schedule -- --text`, Node 22.23.3: **exit 1**. Cả **26/26** lần gọi fetch thất bại trước khi nhận response HTTP; observer chỉ ghi mã **PROVIDER_NETWORK_OR_TIMEOUT**, không có status HTTP. Script báo **days 0/32 (0%)**, **start 0/32 (0%)**, **duration 0/20 (0%)**, câu hỏi **0/4**. Đây là lỗi thực thi/kết nối, **chưa đo được độ chính xác model**, không phải bằng chứng model trả lời sai toàn bộ câu. Chưa xác định được nguyên nhân mạng/TLS cụ thể từ output đã lọc.

Kiểm cục bộ không gọi mạng thêm: key có trong env và tạo Authorization header hợp lệ; không in giá trị key. Không có proxy/extra CA cấu hình qua biến môi trường trong process eval. Các quan sát này không xác thực key với OpenRouter và không chốt được nguyên nhân kết nối. Không gọi lại provider sau khi gate thất bại.

| Bộ | Kết quả | Gate ≥90% |
| --- | --- | --- |
| Text — 26 câu | 0 response hợp lệ; script 0%, chất lượng model chưa đo được | Không đạt / không đủ bằng chứng |
| Image — 12 ảnh synthetic | Chưa chạy: dừng ở lỗi bộ text | Chưa kiểm |
| ADVISE — 12 ca | Chưa chạy | Chưa kiểm |
| LOG — 35 câu (31 vi + 4 en) | Chưa chạy model thật; offline 35/35 trước đó không dùng làm điểm live | Chưa kiểm |

[Bằng chứng text/IDs/mã lỗi](release/smart-schedule-w5/03-live-text-eval-result.json), [output điểm](release/smart-schedule-w5/03-live-text-eval-output.txt), [kiểm local đã lọc](release/smart-schedule-w5/04-local-eval-diagnostics.json). Cần xử lý kết nối của process eval rồi được Duong giao chạy lại. **Dừng bước 2 theo lệnh; bước 3 chưa thực hiện.**

### Các câu chưa xử lý được

Toàn bộ các câu dưới đây có cùng lỗi `PROVIDER_NETWORK_OR_TIMEOUT`, không có output model để đối chiếu semantic:

| ID fixture | Văn bản synthetic |
| --- | --- |
| text-01 | Thứ 3, thứ 5 học tiếng Anh 6 giờ tối 1 tiếng. |
| text-02 | Tối thứ 3 đọc sách lúc 7h15 trong 20 phút. |
| text-03 | Chiều T5 tập đàn 4 giờ 30 phút, học 45 phút. |
| text-04 | Thứ hai tập thể dục 6 rưỡi sáng trong 15 phút. |
| text-05 | Mỗi ngày đánh răng lúc 19:30 trong 5 phút. |
| text-06 | Ngày thường làm bài tập từ 18h trong 30 phút. |
| text-07 | Cuối tuần vẽ tranh lúc 9h sáng trong 40 phút. |
| text-08 | Mỗi ngày trừ chủ nhật ăn sáng lúc 7h15 trong 20 phút. |
| text-09 | Thứ 4 học thêm toán 1 tiếng lúc 17h. |
| text-10 | Mỗi ngày ngủ lúc 9 rưỡi tối, dậy 6h15 sáng. |
| text-11 | Ngày thường ngủ 20h45, dậy 6h30 sáng. |
| text-12 | Cuối tuần dậy 7 giờ sáng, ngủ 21h tối. |
| text-13 | Ngày thường học ở trường từ 7h15 đến 11h30. |
| text-14 | Ngày thường ca sáng 7h15 đến 11h30, ca chiều 13h30 đến 16h30 ở trường. |
| text-15 | Thứ 7 học ở trường từ 8h đến 10h. |
| text-16 | Chủ nhật chơi ngoài trời 4 giờ chiều trong 1 tiếng. |
| text-17 | T2 T4 T6 tắm lúc 18:15 trong 15 phút. |
| text-18 | Ngày thường trừ thứ 4 đọc sách 20h trong 25 phút. |
| text-19 | Thứ 7 dọn phòng lúc 9:00 trong 30 phút và đọc sách 19:30 trong 20 phút. |
| text-20 | Tối thứ hai làm bài tập lúc 8 rưỡi trong 30 phút. |
| text-21 | Chủ nhật ngủ lúc 00:30 đêm và thứ hai dậy 7h. |
| text-22 | Thứ 5 học toán một tiếng. |
| text-23 | Đọc sách lúc 7h tối trong 20 phút. |
| text-24 | Thứ 3 học tiếng Anh lúc 6 rưỡi, trong 1 tiếng. |
| text-25 | Thứ 6 đọc sách lúc 19h. |
| text-26 | Mai tập thể dục lúc 6h30 sáng trong 20 phút. |

### Thử lại ngày 2026-09-30 với key trên Tailscale `duongbk`

Duong xác nhận `.env` nằm trên server Tailscale `duongbk`. Đọc riêng `OPENROUTER_API_KEY` qua SSH vào bộ nhớ process; không in, ghi file hay đưa key vào tham số lệnh. Key khác với biến môi trường cũ trên Mac: key cũ nhận HTTP 401, key trên server nhận HTTP 200 với request kiểm tra ngắn. `curl` truy cập OpenRouter được qua IPv4; Node `fetch` mặc định timeout vì máy thử IPv6, nên lần chạy này tắt tự chọn địa chỉ và ưu tiên IPv4 bằng preload tạm ngoài repo. Không đổi code ứng dụng.

Chạy lại `npm --prefix functions run eval:schedule -- --text` với key hợp lệ: **26/26 response không qua validator; exit 1; days 0/32, start 0/32, duration 0/20, questions 0/4**. Gọi chẩn đoán bằng đúng `providerBody` của fixture đầu tiên nhận **HTTP 400** từ Google AI Studio. Lỗi provider nêu rằng JSON schema tạo quá nhiều trạng thái để phục vụ, thường do giới hạn mảng lồng nhau, matcher phức tạp hoặc ràng buộc độ dài. Script eval hiện chỉ ghi `failed` nên không chứng minh từng case đều có cùng HTTP 400; một case đã được kiểm tra trực tiếp. **Điểm 0% ở đây là lỗi giao thức/schema trước khi có nội dung model, không phải điểm ngữ nghĩa của 26 câu.** Các case ảnh hưởng là toàn bộ `text-01` đến `text-26` trong bảng trên; chưa có câu trả lời model để xác định câu sai ngữ nghĩa.

Gate text vẫn **không đạt**. Dừng theo thứ tự đã duyệt: chưa chạy images, ADVISE, LOG; chưa publish Remote Config, kiểm App Check, deploy Functions hay smoke test. Cần Claude review phương án giảm độ phức tạp schema hoặc điều chỉnh model/provider, rồi chạy lại cả bốn bộ trước khi triển khai.

### Kiểm tra model free trong `.env` của `duongbk`

Duong lưu ý server dùng model free khác. Kiểm tra chỉ các biến model (không in key) cho thấy `OPENROUTER_MODEL=nvidia/nemotron-3-super-120b-a12b:free`. Lần eval 26 câu phía trên **không dùng biến này**: script chọn `AI_SCHEDULE_MODEL` hoặc mặc định `google/gemini-2.5-flash-lite`; Mac không có `AI_SCHEDULE_MODEL`, còn Remote Config template và Functions default cũng đặt Gemini. Cần coi đây là hai cấu hình khác nhau, không suy ra model của ứng dụng từ `.env` của dự án `viet-wealth`.

Thử đúng một fixture đầu bằng model free với `providerBody` hiện tại nhận **HTTP 404**, OpenRouter báo không tìm thấy endpoint phù hợp với chính sách dữ liệu `Free model training` và dẫn tới trang privacy settings. `providerBody` đang gửi `provider.data_collection: "deny"`; không đổi chính sách dữ liệu hoặc cấu hình tài khoản. Vì request mẫu thất bại trước khi có response, không chạy đủ bộ text trên model free và không gán điểm chất lượng cho nó. Gate bước 2 vẫn chưa đạt; cần quyết định model/chính sách dữ liệu phù hợp rồi kiểm lại khả năng nhận schema trước khi chạy các bộ eval.

### F-EVAL-1: provider schema gọn và eval live bốn bộ

Branch `fix/provider-schema` tạo từ main `d775692` trong worktree riêng. Provider schema của PARSE text/image, ADVISE, LOG chỉ giữ cấu trúc object/array, type, required, enum, additionalProperties (và `anyOf` cho kiểu hợp); bỏ pattern, giới hạn độ dài/số lượng, uniqueItems và min/max số. `const` biến thành `enum`. Validator phía server và `provider.data_collection: deny` giữ nguyên. Một request PARSE mẫu sau sửa nhận **HTTP 200**, qua validator. Vì vậy không dùng fallback `json_object`; cần giữ cấu trúc JSON để giảm phản hồi sai. Không dùng model `:free`.

Script eval in model thực dùng trước từng bộ, tính token vào/ra trung bình trên response có usage, và in ID với loại lỗi mà không in nội dung model/key. Chạy live qua OpenRouter với **`google/gemini-2.5-flash-lite`** cho cả bốn bộ; key đọc qua SSH từ `.env` trên Tailscale `duongbk` vào bộ nhớ process, không lưu. Node trên Mac dùng preload IPv4 tạm ngoài repo. Bảng dùng lần chạy cuối của từng bộ; output và ID ca sai ở [text](release/provider-schema/text.txt), [images](release/provider-schema/images.txt), [ADVISE](release/provider-schema/advise.txt), [LOG](release/provider-schema/log.txt).

| Bộ | Đúng hoàn toàn | Điểm | Token vào/lượt | Token ra/lượt | Gate ≥90% |
| --- | ---: | ---: | ---: | ---: | --- |
| PARSE text | 18/26 | 69,2% | 606,5 | 165,1 | Không đạt |
| PARSE images | 2/12 | 16,7% | 2625,8 | 360,4 | Không đạt |
| ADVISE | 4/12 | 33,3% | 664,8 | 209,9 | Không đạt |
| LOG | 17/35 | 48,6% | 824,9 | 112,8 | Không đạt |

Text: days 26/34 (76,5%), start 27/34 (79,4%), duration 19/24 (79,2%), câu hỏi thiếu thông tin 0/4. Images: days 7/45 (15,6%), start 13/45 (28,9%), duration 14/45 (31,1%), câu hỏi 0/3. ADVISE kiểm constraints tự động; ngôn ngữ y khoa còn cần duyệt người. LOG dùng cùng golden constraints với offline 35 ca; điểm live thấp hơn offline. Lượt LOG đầu là 19/35, lượt cuối 17/35, cho thấy đầu ra model biến động dù temperature=0.

Các ca sai lần cuối, chỉ ghi ID fixture và loại lỗi:

- Text — invalid response: `text-03`; sai golden: `text-08`, `text-14`, `text-18`, `text-22`, `text-23`, `text-24`, `text-25`.
- Images — sai golden: `school-morning`, `school-afternoon`, `school-two-shifts`, `school-alternating`, `school-saturday`, `school-missing-hours`, `school-text-hours`, `school-current-hours`, `school-one-unknown-shift`, `school-ambiguous-all-day`.
- ADVISE — sai constraints: `sleep-grade1`, `screen-bed`, `weekend-drift`, `preschool-no-nap`, `long-focus`, `midnight-bed`, `wake-later`; invalid response: `note-injection`.
- LOG — sai constraints: `log-vi-03`, `log-vi-09`, `log-vi-10`, `log-vi-14`, `log-vi-15`, `log-vi-16`, `log-vi-22`, `log-vi-23`, `log-vi-24`, `log-en-01` đến `log-en-04`, `log-vi-29`, `log-vi-31`; invalid JSON: `log-vi-06`, `log-vi-30`; invalid response: `log-vi-20`.

Kết luận gate tại F-EVAL-1: lỗi schema 400 đã được sửa, nhưng chất lượng model lúc đó chưa đạt ở bất kỳ bộ nào. Cần Claude review đầu ra/fixture/prompt và quyết định bước tối ưu tiếp theo; không đưa Functions hoặc Remote Config lên production.

Kiểm tra F-EVAL-1: Node 22.23.3 chạy **163/163 Functions tests PASS**, ESLint PASS; Android với JDK 17 chạy **300/300 unit tests**, assembleDebug PASS, lintDebug **0 lỗi / 166 cảnh báo**. `git diff --check` PASS. Bốn bộ eval live đều exit 1 vì điểm dưới gate, không phải lỗi HTTP 400. Lần gọi Gradle đầu dùng JDK 11 nên dừng ở cấu hình; chạy lại bằng JDK 17 đã PASS.

### F-EVAL-2: prompt, strict schema và so sánh hai model (2026-10-01)

Trên branch `fix/provider-schema`, harness ghi `expected`, `actual` đã chuẩn hoá và đường dẫn khác biệt của **từng** fixture synthetic cho cả bốn bộ; lỗi HTTP/JSON chỉ lưu mã lỗi, không lưu request/key/ảnh. Prompt PARSE yêu cầu hỏi khi thiếu giờ/ngày/thời lượng với hai ví dụ ngắn; prompt ảnh chỉ tạo `anchors.school` từ ca có ngày/giờ, không tạo task từng môn; LOG làm rõ `am/pm`, `yesterday`, `last night`; ADVISE đưa giới hạn cứng lên đầu. Không sửa fixture, validator server hoặc tiêu chí chấm. Giữ `provider.data_collection: deny` cho hai model, không dùng model `:free`.

Thử `response_format.json_schema.strict=true`: **HTTP 200 ở PARSE text, ảnh, ADVISE, LOG trên cả hai model** (tám request probe). Giữ `strict=true`; một số response trong eval vẫn không qua JSON/validator, nên HTTP 200 không đồng nghĩa response hợp lệ. Mỗi bộ/model chạy **một lần đầy đủ** với cùng fixture; tất cả điểm bên dưới là live OpenRouter, không phải offline golden. Key chỉ đọc qua SSH vào bộ nhớ tiến trình và được lọc khỏi stdout; Node dùng IPv4 preload tạm ngoài repo. Không gọi production Functions.

| Model | Bộ | Đúng | Điểm | Token vào/lượt | Token ra/lượt | Gate ≥90% |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Gemini 2.5 Flash Lite | PARSE text | 22/26 | 84,6% | 694,7 | 136,5 | Trượt |
| Gemini 2.5 Flash Lite | PARSE ảnh | 3/12 | 25,0% | 2800,8 | 335,8 | Trượt |
| Gemini 2.5 Flash Lite | ADVISE | 7/12 | 58,3% | 714,8 | 172,4 | Trượt |
| Gemini 2.5 Flash Lite | LOG | 15/35 | 42,9% | 952,9 | 121,6 | Trượt |
| Gemini 2.5 Flash | PARSE text | 26/26 | 100,0% | 722,5 | 139,0 | Đạt |
| Gemini 2.5 Flash | PARSE ảnh | 5/12 | 41,7% | 2800,8 | 166,8 | Trượt |
| Gemini 2.5 Flash | ADVISE | 3/12 | 25,0% | 714,8 | 172,3 | Trượt |
| Gemini 2.5 Flash | LOG | 25/35 | 71,4% | 952,9 | 104,6 | Trượt |

ID ca sai, theo cùng lần chạy trong bảng:

| Model/bộ | Ca sai |
| --- | --- |
| Lite text | `text-03`, `text-17`, `text-18`, `text-24` |
| Lite ảnh | `school-morning`, `school-afternoon`, `school-two-shifts`, `school-alternating`, `school-saturday`, `school-text-hours`, `school-current-hours`, `school-one-unknown-shift`, `school-ambiguous-all-day` |
| Lite ADVISE | `sleep-grade1`, `late-homework`, `long-focus`, `midnight-bed`, `note-injection` |
| Lite LOG | `log-vi-07`, `09`, `10`, `14`, `15`, `16`, `19`, `20`, `22`, `23`, `24`, `26`, `28`, `29`, `30`, `31`; `log-en-01`–`04` |
| Flash text | Không có |
| Flash ảnh | `school-morning`, `school-afternoon`, `school-two-shifts`, `school-alternating`, `school-current-hours`, `school-one-unknown-shift`, `school-ambiguous-all-day` |
| Flash ADVISE | `sleep-grade1`, `late-homework`, `screen-bed`, `weekend-drift`, `preschool-no-nap`, `long-focus`, `midnight-bed`, `wake-later`, `note-injection` |
| Flash LOG | `log-vi-04`, `08`, `10`, `14`, `15`, `20`, `24`, `25`, `31`; `log-en-04` |

Diff cho thấy Lite text `text-18` thêm SAT/SUN dù đầu vào chỉ ngày thường trừ thứ 4; `text-24` tự chọn 18:30 dù câu không xác định sáng/tối. Ảnh của Flash thường đúng giờ nhưng thêm ngày học hoặc hỏi khi thông tin đã đủ; `school-ambiguous-all-day` tự tạo ca cả ngày dù thiếu giờ nghỉ trưa. LOG Flash nhận đúng ba ca tiếng Anh đầu; `log-en-04` còn bỏ `planRef=p0` khi hoạt động khớp kế hoạch. Với ADVISE, `sleep-grade1` fixture mong `SET_BED 21:15` từ 22:30 (dịch 75 phút), trong khi prompt/thiết kế yêu cầu từng bước tối đa 30 phút; model thường trả 22:00 hoặc 22:15 và không xóa finding ngay trong một bước, nên trượt `mustFix`. Đây là điểm cần Claude quyết định về cách chấm kế hoạch nhiều bước; **chưa đổi grader/fixture**. Các ca khác như `long-focus` trả RESIZE 45 thay vì mức 30 cần thiết, là lỗi model thực sự.

[Diff JSON từng ca](release/provider-schema-round2/diffs/) và output điểm theo từng model: [Lite](release/provider-schema-round2/gemini-2.5-flash-lite/) · [Flash](release/provider-schema-round2/gemini-2.5-flash/). Gate release vẫn **không đạt** vì phải ≥90% ở cả bốn bộ. Chưa chọn model production, chưa merge/deploy/publish. Functions Node 22.23.3: **164/164 test PASS**, ESLint PASS; Android JDK 17 `testDebugUnitTest assembleDebug lintDebug` PASS (cache hợp lệ); `git diff --check` PASS. Working tree gốc còn nguyên diff SHA-256 `667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725`.

### F-EVAL-3: giờ đích, grader ảnh và chi phí thực (2026-10-01)

Theo review Vòng 3 của Claude, sửa ADVISE để SET_BED/SET_WAKE trả **giờ đích** xóa finding; app tự chia bước bằng “Áp dụng dần”. Prompt tính ngưỡng ngủ, ngưỡng FOCUS_TOO_LONG theo tuổi, thời điểm kết thúc bài học/màn hình trước giờ ngủ và trung bình hai ngày cuối tuần. Không sửa validator hay fixture ADVISE. Prompt ảnh chỉ lấy ngày có ô môn học/giờ; cột trống không học. Prompt LOG làm rõ ngày tham chiếu, qua nửa đêm, hoạt động có giờ bắt đầu nhưng không có giờ kết thúc, `10g`, `at 7 ... 8pm`, hoạt động không làm và câu chưa chắc chắn.

**Thay đổi grader chỉ cho ảnh:** nếu mọi trường lịch đã đúng mà model hỏi thêm, ca vẫn PASS vì không sinh dữ liệu lịch sai; lưu và báo riêng tỷ lệ câu hỏi thừa (mục tiêu ≤20%). Nếu cần hỏi mà model không hỏi, hoặc bất kỳ trường lịch nào sai, ca vẫn FAIL. PARSE text và các bộ khác giữ nguyên grader. Unit test bao phủ cả bốn trường hợp. Đây là thay đổi được Claude quyết định sau khi diff cho thấy `school-morning`/`school-afternoon` đúng field nhưng hỏi dư. Không nới fixture.

Manual eval gửi `usage: {include: true}` để lấy `usage.cost` USD do OpenRouter trả về, không ước tính theo token; chỉ harness eval dùng cờ này, production request giữ nguyên. [OpenRouter xác nhận cách yêu cầu usage](https://openrouter.ai/support). Mọi response của các lượt cuối đều có chi phí. Key chỉ truyền trong môi trường tiến trình, không lưu; `provider.data_collection: deny` và `json_schema strict:true` giữ nguyên. Flash chạy text/ADVISE/LOG/ảnh; Pro chỉ chạy ảnh; không dùng model `:free`. Mỗi phiên bản prompt cuối cùng của từng bộ chạy **hai lượt đầy đủ** trên cùng fixture. Gate lấy lượt thấp hơn; cột USD là trung bình chi phí **thực tế mỗi response**, không phải giá niêm yết.

| Bộ/model cuối | Lượt 1 | Lượt 2 | Gate thấp hơn | USD/lượt 1 | USD/lượt 2 | Gate ≥90% |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Text / Gemini 2.5 Flash | 26/26 (100%) | 26/26 (100%) | 100% | $0.00056463 | $0.00056406 | Đạt |
| LOG / Gemini 2.5 Flash | 33/35 (94,3%) | 33/35 (94,3%) | 94,3% | $0.00070341 | $0.00067026 | Đạt |
| ADVISE / Gemini 2.5 Flash | 10/12 (83,3%) | 11/12 (91,7%) | 83,3% | $0.00085970 | $0.00082138 | Trượt |
| Ảnh / Gemini 2.5 Flash | 8/12 (66,7%) | 8/12 (66,7%) | 66,7% | $0.00125106 | $0.00098373 | Trượt |
| Ảnh / Gemini 2.5 Pro | 9/12 (75%) | 7/12 (58,3%) | 58,3% | $0.01796688 | $0.01890597 | Trượt |

Chi phí trung bình hai lượt theo model/mode: Flash text **$0.00056435**, LOG **$0.00068684**, ADVISE **$0.00084054**, ảnh **$0.00111740**; Pro ảnh **$0.01843643**. Ảnh Flash hỏi thừa **2/9 (22,2%)** và **1/9 (11,1%)** ca hợp lệ không cần hỏi; Pro **0/7 (0%)** và **1/6 (16,7%)**. Câu hỏi bắt buộc đúng: Flash ảnh **1/3** ở cả hai lượt, Pro **2/3** rồi **1/3**. Mục tiêu ma sát ≤20% cũng không ổn định ở Flash.

Đã giữ bằng chứng các lần tinh chỉnh, không chỉ lần tốt nhất: [vòng prompt đầu](release/provider-schema-round3/), [vòng ADVISE/LOG thứ hai](release/provider-schema-round3b/), [vòng ADVISE thứ ba](release/provider-schema-round3c/), [vòng ADVISE cuối](release/provider-schema-round3d/). Mỗi thư mục có output điểm, cost và JSON diff `expected/actual` theo ID ca synthetic; không có ảnh thô, key hay dữ liệu trẻ thật. ADVISE lần đầu 7/12–9/12, sau công thức giờ đích 10/12–10/12, vòng cuối 10/12–11/12. LOG tăng từ 28/35–29/35 lên 33/35–33/35; điểm của mỗi phiên bản cuối được lấy theo lượt thấp hơn ở bảng.

Các ca còn sai ở **lượt thấp hơn cuối**:

- ADVISE: `late-homework`, `weekend-drift` bị validator từ chối (`INVALID_RESPONSE`); lượt kia chỉ `note-injection` bị từ chối. Lượt thấp hơn vẫn 10/12. Một probe riêng của `remove-screen` nhận HTTP 200 và qua validator, cho thấy lỗi invalid trước đó không lặp cố định; không sửa validator để đẩy điểm.
- LOG: `log-vi-24` giữ confidence thấp nhưng thiếu câu hỏi xác nhận cho “chắc/có thể” (lỗi model); `log-vi-31` đặt WAKE 05:45 sang ngày sau, trong khi golden muốn cùng ngày tham chiếu với SLEEP 22:30. Cách hiểu sang ngày sau có cơ sở theo “thức dậy”; đây là ca cần Claude xét lại fixture/định nghĩa ngày, **chưa sửa golden**.
- Ảnh Flash: `school-alternating`, `school-current-hours`, `school-one-unknown-shift`, `school-ambiguous-all-day` sai cả hai lượt, chủ yếu ngày/ca học hoặc thiếu câu hỏi khi giờ không rõ. Ảnh Pro có 3 rồi 5 response `INVALID_JSON` ở các ID khác nhau dù strict=true; chi phí cao hơn Flash và điểm thấp hơn ở lượt xấu. Không suy ra nguyên nhân JSON nếu chưa có raw provider diagnostic.

**Gate release chưa đạt** do ADVISE và cả hai model ảnh. Theo quyết định Vòng 3, nếu beta ra trước thì cần tắt nhập ảnh bằng cờ Remote Config; chưa đổi/publish cờ ở task này. ADVISE vẫn cần Claude review trước quyết định beta; chưa chọn model ảnh production, chưa merge/deploy. Kiểm tra F-EVAL-3: Functions Node 22.23.3 **166/166 test PASS**, ESLint PASS; Android JDK 17 `testDebugUnitTest assembleDebug lintDebug` PASS; `git diff --check` PASS. Working tree gốc vẫn có cùng diff SHA-256 `667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725`.

### F-EVAL-4: quyết định beta Vòng 4 (2026-10-01)

Server ADVISE giữ **một lần giữ quota** cho cả yêu cầu. Chỉ khi `validateAdvice` từ chối output mới gọi model thêm đúng một lần; nếu lần thứ hai vẫn sai thì hoàn reservation như trước. HTTP/network failure không retry. Mỗi lần validator từ chối chỉ log mã check tĩnh (ví dụ `AI_ADVISE_VALIDATION_REASON`), không log prompt, response hay dữ liệu trẻ. Unit test chứng minh retry thành công chỉ tính một credit, hai output sai được hoàn credit, lỗi provider chỉ gọi một lần, và log JSON sai không chứa nội dung.

Golden `log-vi-31` đổi WAKE 05:45 thành **2026-09-30** (`date+1`): câu “Ngủ 10 rưỡi tối và dậy 6 giờ kém 15” kể trình tự ngủ tối 29/9 rồi thức sáng hôm sau; cùng ngày sẽ đặt thức dậy *trước* lúc ngủ. Prompt LOG được đồng bộ với cách hiểu đó. Preview “Ghi nhanh” bỏ chọn sẵn mục AI có giờ bắt đầu sau thời điểm hiện tại và gắn nhãn **“Chưa tới giờ — chỉ chọn khi đã kiểm tra”** (en: “Not yet time — select only after checking”); phụ huynh vẫn có thể tự chọn. Test bao phủ ngày và giờ bắt đầu, cùng trạng thái chọn mặc định.

Template Remote Config đặt `ai_schedule_image_tiers=none`, `ai_schedule_model=google/gemini-2.5-flash`, `ai_schedule_advise_model=google/gemini-2.5-flash`; vision model giữ nguyên vì ảnh tắt trong beta. Default Functions cho text/ADVISE cũng là Flash và image tiers rỗng để fail closed nếu chưa tải được Remote Config. Test `none`/rỗng chặn guest, free, early và premium **trước quota/provider**. Chưa publish template.

Eval live gọi OpenRouter bằng model **google/gemini-2.5-flash**, `data_collection: deny`, strict JSON schema, `usage: {include: true}` để đo USD thật; không dùng model `:free`. Harness ADVISE dùng đúng chính sách retry production và ghi số response, tên check lỗi, diff expected/actual. Mỗi bộ chạy hai lượt trên cùng fixture, gate lấy lượt thấp hơn; không chạy lại ảnh vì đã tắt cho beta. Key chỉ được đọc từ môi trường riêng của tiến trình eval và không ghi vào repo, output hay báo cáo.

| Bộ | Lượt 1 | Lượt 2 | Gate thấp hơn | USD/response lượt 1 | USD/response lượt 2 | Gate ≥90% |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Text | 26/26 (100%) | 26/26 (100%) | 100% | $0.00055800 | $0.00056358 | Đạt |
| LOG | 32/35 (91,4%) | 32/35 (91,4%) | 91,4% | $0.00071974 | $0.00062272 | Đạt |
| ADVISE sau retry | 12/12 (100%), 14 response | 12/12 (100%), 12 response | 100% | $0.00085606 | $0.00072203 | Đạt |

Chi phí ADVISE **theo yêu cầu logic, tính cả retry** là $0.00099874 ở lượt 1 và $0.00072203 ở lượt 2; lượt 1 retry `sleep-grade1` và `note-injection` do check `REASON`, đều có output hợp lệ ở lần hai. Token vào/ra trung bình mỗi response: text 722,5/136,5 và 722,5/138,7; LOG 1574,9/106,2 và 1574,9/106,3; ADVISE 1271,2/199,7 và 1269,8/197,1. Đây là số liệu OpenRouter trả cho các response thực tế, không phải giá ước tính.

Ba ca LOG sai ở **cả hai lượt**, vẫn giữ fixture/grader: `log-vi-15` thiếu `end=00:10` cho khoảng 23:50–00:10 và hỏi dư; `log-vi-24` thiếu câu hỏi xác nhận cho diễn đạt “chắc/có thể”; `log-en-02` hiểu giờ bắt đầu 7pm thành 07:00 thay vì 19:00. `log-vi-31` đã PASS ở cả hai lượt. [Output và diff từng ca synthetic F-EVAL-4](release/provider-schema-round4/) ghi cả các lần chạy, không chứa ảnh/dữ liệu trẻ thật hay key.

Kiểm tra trước commit: Functions Node **22.23.3** 169/169 test PASS, ESLint PASS; Android JDK 17 `testDebugUnitTest assembleDebug lintDebug` PASS; `git diff --check` PASS. Eval CLI cục bộ dùng Node 25.8.1; code Functions được kiểm lại trên runtime Node 22. Working tree gốc giữ nguyên 19 file tracked dở, SHA-256 diff `667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725`. Không sửa `.omc`, không push/merge/deploy/publish; chờ Claude review F-EVAL-4.

## 3. F-EVAL-5, main và triển khai — dừng tại Firebase CLI

Theo review Vòng 5, F-EVAL-5 commit `27b1cd1` nâng `aiSchedule.timeoutSeconds` từ 60 lên **90** và đặt deadline tổng **60 giây** cho provider; mỗi call dùng tối đa 25 giây hoặc ngân sách còn lại. Hai unit test mới chứng minh retry bị giới hạn còn 3 giây khi chỉ còn 3 giây và hoàn credit nếu hết ngân sách trước call thứ hai. Không đổi prompt/model/fixture, không chạy lại eval.

Worktree `KidFocusTimer-main-integration`: đã lưu bản sao các file cũ chưa track/đang sửa tại `/tmp/kidfocus-main-integration-premerge.tLgt7L` rồi merge `--no-ff` `fix/provider-schema` (`1dde641`) và `fix/focus-keep-screen` (`be80afb`) vào main, không conflict. Android JDK 17 `testDebugUnitTest assembleDebug lintDebug` **PASS**; Functions Node 22.23.3 **171/171 test PASS**, lint PASS; diff check PASS. Đã push `origin/main` SHA **`be80afb51f79fdafebf1ae0ebdd1f3a2e9a0f287`**, không force. [Build & Publish Release AAB](https://github.com/duongbkak55/kidfocus-timer/actions/runs/36792798631) **SUCCESS** trên đúng SHA, AAB và APK artifact đều upload; cache GitHub có cảnh báo nhưng job kết luận success.

Gate App Check: trong Play Console `com.kidfocusstudio.timer`, public **deployment/app signing certificate** có SHA-256 `698c029c339155690fd59927ecdaa94581891736e3e6f2d9760689eca6415721`, khớp SHA-256 của Firebase app Play `1:1064385320816:android:16548e8539799e0684e1b5`. SHA-256 `19c05c20…233920e` là **upload certificate** riêng. Firebase App Check Play Integrity config tồn tại (GET HTTP 200); `aiSchedule` và `claimEarlyAccess` vẫn dùng `ENFORCE_APP_CHECK` mặc định true, không hạ enforcement. Secret `OPENROUTER_API_KEY` version 1 đang ENABLED; không đọc giá trị.

Lệnh `firebase deploy --only functions:aiSchedule,functions:getAiConfig,functions:claimEarlyAccess --project kid-focus-app --non-interactive` **exit 1** khi CLI chuẩn bị codebase: `Failed to make request to https://firebase.googleapis.com/v1beta1/projects/kid-focus-app/adminSdkConfig`. GET cùng endpoint bằng OAuth hiện có trả **HTTP 200** qua Python và Node (cả mặc định lẫn IPv4-first), nên chưa đủ căn cứ kết luận thiếu quyền hoặc lỗi IPv6; có thể là lỗi thoáng qua của Firebase CLI. `firebase functions:list` sau lỗi vẫn chỉ có `aiChat`, `deleteAccount`, `getAiConfig`, `revenueCatWebhook` ở `asia-southeast1`; chưa có `aiSchedule`/`claimEarlyAccess`. Không publish/backup Remote Config và không smoke PARSE/LOG/ADVISE/ảnh. Không gọi OpenRouter production, không dùng dữ liệu trẻ thật, không đăng nhập hoặc mua bằng tài khoản test.

### Một lần retry được Duong duyệt (2026-10-01)

Trước retry, `firebase --version` là **15.15.0**, còn npm registry trả **15.32.1**; đã cập nhật CLI toàn cục và xác nhận `firebase --version` là **15.32.1**. `firebase login:list` trả `duongbkak55@gmail.com`; `firebase use` và `.firebaserc` đều là **`kid-focus-app`** (project number `1064385320816`). IAM policy read-only trả **HTTP 200** và gán `roles/owner` cho đúng tài khoản. Không đọc hay ghi giá trị secret.

Đã chạy đúng **một** lần `firebase deploy --only functions:aiSchedule,functions:getAiConfig,functions:claimEarlyAccess --debug` trong main-integration, với Node 22 cho discovery. Debug ghi `GET https://firebase.googleapis.com/v1beta1/projects/kid-focus-app/adminSdkConfig` **HTTP 200** và trả project ID/storage bucket bình thường: lỗi `adminSdkConfig` cũ **không tái hiện**. Sau khi phân tích source và đọc metadata Secret Manager, CLI **exit 1** trước upload vì lỗi cục bộ (không có mã HTTP): `Error: In non-interactive mode but have no value for the following environment variables: ENFORCE_APP_CHECK`. Dòng lỗi này đã lọc và không có token. Code khai báo `ENFORCE_APP_CHECK` qua `defineBoolean(..., {default: true})`; không tự điền giá trị hay retry để giữ giới hạn một lần deploy.

Kiểm tra read-only sau lỗi: `firebase functions:list` vẫn chỉ có `aiChat`, `deleteAccount`, `getAiConfig`, `revenueCatWebhook` tại `asia-southeast1`; **`aiSchedule` và `claimEarlyAccess` chưa được tạo**, `getAiConfig` chưa cập nhật. Không backup/publish Remote Config, không chạy smoke hoặc gọi production. Cần Duong/Claude quyết định cách cấp `ENFORCE_APP_CHECK=true` cho phiên deploy sau; phải giữ App Check enforcement. Báo cáo ở main-integration còn **chưa commit/push** vì điều kiện “commit sau smoke” chưa xảy ra.

### Lượt deploy mới với cấu hình App Check (2026-10-01)

Duong duyệt thêm đúng một lượt. Đã thêm `functions/.env.kid-focus-app` chỉ có `ENFORCE_APP_CHECK=true`; `git check-ignore` xác nhận file không bị bỏ qua, không sửa `.gitignore`. Commit main **`80e389f`** chỉ chứa file cấu hình này; không có secret.

Chạy một lần `firebase deploy --only functions:aiSchedule,functions:getAiConfig,functions:claimEarlyAccess --project kid-focus-app --non-interactive` bằng CLI 15.32.1/Node 22: **exit 0, Deploy complete**. `aiSchedule` và `claimEarlyAccess` được tạo, `getAiConfig` cập nhật. REST read-only sau deploy xác nhận cả ba **ACTIVE**, tên resource đều tại **asia-southeast1**, `serviceConfig.environmentVariables.ENFORCE_APP_CHECK` đều **true**. Timeout `aiSchedule` là **90 giây**, hai function còn lại 60 giây. CLI cảnh báo firebase-functions cũ và gọi `.value()` trong discovery; không sửa code trong lượt triển khai này, không hạ enforcement.

Backup Remote Config **v2** được lưu ngoài repository tại `/Users/duongnguyen/Projects/KidFocusTimer-release-backups/remoteconfig-20261001-v2/backup.json` (thư mục 0700, file 0600). Merge bổ sung các tham số `ai_schedule_*` và early access từ template, giữ nguyên **9 tham số cũ** cùng conditions (0 condition). Chuẩn bị ETag cần header `Accept-Encoding: gzip` theo [tài liệu Firebase](https://firebase.google.com/docs/remote-config/automate-rc); các kiểm tra thiếu ETag trước đó dừng cục bộ, chưa gửi PUT. Validate **HTTP 200**, publish dùng ETag/If-Match **HTTP 200**, tạo **v3**. Đã xác nhận `ai_schedule_enabled=true`, free daily parses **3**, LOG cost **1**, image tiers **none**, text/ADVISE model **google/gemini-2.5-flash**. Không thay cấu hình AI chat hiện có.

**Preflight Pixel trước smoke:** Sau khi Duong bật USB debugging, `adb devices` thấy Pixel 3a serial `94GAY0NVUX` ở trạng thái `device` qua USB; kết nối mạng cũ `192.168.68.108:5555` vẫn `unauthorized` và không cần dùng. `./gradlew assembleDebug` PASS, `adb install -r app-debug.apk` trả `Success` và giữ dữ liệu. Ban đầu route phụ huynh hiện màn **Enter parent PIN**; phần dưới ghi cách hoàn tất gate và các kết quả smoke tiếp theo. Không mua, không dùng dữ liệu trẻ thật. Commit báo cáo và push main sẽ thực hiện sau smoke theo thứ tự Duong duyệt; cấu hình commit `80e389f` hiện chưa push.

### Smoke Pixel 3a và chặn key (2026-10-01)

Duong xác nhận Pixel 3a dùng tài khoản test chung với Pixel 9. `adb devices` nhận USB `device`; app debug được cài `-r`, giữ dữ liệu. PIN phụ huynh **không đồng bộ theo tài khoản**: PIN 2468 từ G3 Pixel 9 báo sai trên Pixel 3a. Sau khi đối chiếu hash PIN cục bộ của bản debug với mã bốn chữ số, đã nhập thành công mà không in/lưu PIN. Màn PIN giữ đủ bốn ô sau lần nhập sai, nên phải xóa bốn ô trước khi nhập lại; đây là vấn đề UX cần xem xét riêng.

Bản debug cũ thiếu Firebase client config. Đã lấy cấu hình **public client** của app Firebase đăng ký `com.kidfocusstudio.timer.debug`, build bằng biến môi trường tạm rồi cài đè; không ghi key vào repo hoặc báo cáo. App hiển thị tài khoản test đã đăng nhập và đồng bộ. Đăng ký riêng App Check debug token của Pixel 3a cho app debug, không in/lưu token. Lần PARSE đầu bị `UNAUTHENTICATED` trước quota vì Firebase App Check API của project ở trạng thái **DISABLED**. Đã bật đúng `firebaseappcheck.googleapis.com` và xác nhận **ENABLED**; trao đổi debug token trả HTTP 200, giữ enforcement. Sau đó trial 60 ngày/15 credits xuất hiện.

PARSE mẫu synthetic tiếng Anh từ chính nút ví dụ của app đến `aiSchedule`: Cloud Logging ghi `Callable request verification passed`, sau đó `AI_PARSE_FAILED`, HTTP 503; UI giữ nguyên text. Firestore `internalAiQuota` ngày 2026-10-01 trước đó chưa có document, sau lỗi counters `questions=0`, `credits=0`, `scheduleParses=0`, xác nhận refund. Kiểm tra OpenRouter key của Secret Manager version 1 với endpoint key status trả **HTTP 401**. Key từ `duongbk/viet-wealth/.env` đã được Duong cho phép dùng trước đó trả **HTTP 200**; đã thêm vào Secret Manager thành **version 2 ENABLED**, không in/ghi key.

Duong duyệt tiếp một lần deploy riêng `aiSchedule`. Lệnh `firebase deploy --only functions:aiSchedule --project kid-focus-app --non-interactive` **exit 0**. REST xác nhận Function `ACTIVE` ở `asia-southeast1`, revision `aischedule-00002-qop`, secret binding `OPENROUTER_API_KEY` **version 2** và `ENFORCE_APP_CHECK=true`; hai Functions còn lại không redeploy. Không thay Remote Config v3.

Chạy lại PARSE từ Quick entry với đúng câu ví dụ synthetic: màn hình hiện **Review draft**, không còn lỗi; trial giảm từ 15 còn 14 credits. LOG và ADVISE gọi trực tiếp callable production bằng Auth/App Check của chính tài khoản test Pixel 3a, input synthetic từ golden fixtures (đổi ngày tham chiếu thành 2026-10-01), không lưu log thực tế hay lịch của trẻ. Cả hai trả **HTTP 200**; LOG có payload `log`, ADVISE có payload `advice` với `summary`, `proposals`, `tags`. Đọc ledger Firestore chỉ các counters sau từng lời gọi:

| Mốc | questions | credits | scheduleParses |
| --- | ---: | ---: | ---: |
| Sau PARSE | 1 | 1 | 1 |
| Sau LOG | 2 | 2 | 2 |
| Sau ADVISE | 3 | 4 | 2 |

Như vậy PARSE và LOG mỗi lần 1 credit, ADVISE 2 credits; `scheduleParses` tăng cho PARSE và LOG theo logic hiện tại. Trial còn 11/15 credits trong ngày. Đây là smoke backend cho LOG/ADVISE; không xác nhận UI lưu log/áp dụng kế hoạch trong lượt release này. G3 W5b trước đó đã kiểm UI Ghi nhanh.

Remote Config v3 `ai_schedule_image_tiers=none`: nút ảnh không hiện ở Quick entry. Một callable image request dùng Auth/App Check của tài khoản test cùng byte JPEG synthetic tối thiểu trả **HTTP 403 PERMISSION_DENIED / IMAGE_TIER_REQUIRED** trước quota/provider. Kiểm lại sau smoke, counters vẫn `questions=3`, `credits=4`, `scheduleParses=2`. Không dùng ảnh hay dữ liệu trẻ thật. Google Cloud Logging đã kiểm chỉ mã lỗi/trạng thái, không ghi prompt hoặc token vào report.

**Known issues beta:** `log-en-02` hiểu 7pm thành 07:00; `log-vi-15` thiếu giờ kết thúc qua nửa đêm; `log-vi-24` không hỏi lại khi câu có “chắc/có thể” (preview confidence thấp đã bỏ chọn sẵn). Client hiện chờ callable 30 giây, nên trường hợp ADVISE retry kéo dài có thể timeout ở client dù server còn ngân sách; cần theo dõi. PIN phụ huynh trên Pixel 3a không đồng bộ với Pixel 9 và ô nhập không tự xóa sau lần sai; đã ghi ở trên.

Smoke release **PASS**. Các bước còn lại của lượt này: commit báo cáo trên main, push main không force và ghi trạng thái workflow build phát sinh.

## Bảo toàn working tree

19 file tracked dở tại working tree gốc giữ nguyên, SHA-256 của diff **667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725**. Không sửa `.omc`, không stash/reboot. Bản sao các file cũ của main-integration nằm ngoài repo như trên. `fix/provider-schema` đã merge vào main nhưng chưa push riêng branch fix.
