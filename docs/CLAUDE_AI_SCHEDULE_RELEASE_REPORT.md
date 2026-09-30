# Smart Weekly Schedule W1–W5 — merge, eval và triển khai

Ngày: 2026-09-29. Dev: Codex. Duong duyệt thực hiện ba bước theo thứ tự, dừng nếu bước thất bại.

**Trạng thái mới nhất 2026-09-30:** F-EVAL-1 đã loại lỗi HTTP 400 do provider schema, nhưng eval live cả bốn bộ chưa đạt 90%; không merge/deploy/publish. Xem kết quả mới ở cuối mục 2; các số liệu trước đó là lịch sử chẩn đoán.

## 1. Merge, kiểm và push — PASS

Dùng worktree `KidFocusTimer-main-integration`, không checkout/reset/stash working tree gốc. Fetch origin và merge `--no-ff` branch `feature/smart-schedule-w5` (`99775f6`) vào main: **d775692555e796c4842700b17174f800f8b1a1d1**, không conflict.

- Gradle `testDebugUnitTest assembleDebug lintDebug --max-workers=2`: PASS, **300/300 test / 46 suite**, lint **0 lỗi / 166 cảnh báo**, assemble PASS; build **1m 27s**. Kết quả cache được Gradle tái sử dụng đúng inputs trên worktree main.
- `npm --prefix functions test`: **158/158 PASS**. Kiểm lại bằng **Node 22.23.3** và Functions lint: PASS; runtime tải vào thư mục tạm từ bản phân phối chính thức, đã đối chiếu SHA-256, không đổi dependency/lockfile repo.
- `git diff --check`, merge commit check: PASS. Push origin **main + feature/smart-schedule-w5**, không force.
- [Build & Publish Release AAB — SUCCESS](https://github.com/duongbkak55/kidfocus-timer/actions/runs/36586298761), đúng SHA main; đủ `kidfocus-release-aab` và `kidfocus-release-apk`. Chỉ build/upload artifact, chưa deploy Firebase.

[Số liệu kiểm](release/smart-schedule-w5/01-main-merge-checks.json), [workflow](release/smart-schedule-w5/02-workflow-result.json), [artifacts](release/smart-schedule-w5/02-workflow-artifacts.json).

## 2. Remote Config và live eval — DỪNG: provider chưa nhận request lịch

Template cục bộ: `ai_schedule_free_daily_parses=3` (đã là 3), thêm `ai_schedule_log_cost=1`. Chưa publish. Models text/image/ADVISE/LOG giữ **google/gemini-2.5-flash-lite**, đúng template sẽ triển khai. Key lấy từ biến môi trường có sẵn do Duong cấp, chỉ truyền cho process eval, không ghi vào repo/log/report.

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

Kết luận gate: lỗi schema 400 đã được sửa, nhưng chất lượng model hiện chưa đạt ở bất kỳ bộ nào. Cần Claude review đầu ra/fixture/prompt và quyết định bước tối ưu tiếp theo; không đưa Functions hoặc Remote Config lên production.

Kiểm tra F-EVAL-1: Node 22.23.3 chạy **163/163 Functions tests PASS**, ESLint PASS; Android với JDK 17 chạy **300/300 unit tests**, assembleDebug PASS, lintDebug **0 lỗi / 166 cảnh báo**. `git diff --check` PASS. Bốn bộ eval live đều exit 1 vì điểm dưới gate, không phải lỗi HTTP 400. Lần gọi Gradle đầu dùng JDK 11 nên dừng ở cấu hình; chạy lại bằng JDK 17 đã PASS.

## 3. Triển khai và smoke — KHÔNG THỰC HIỆN do bước 2 thất bại

Chưa kiểm SHA-256 App Check/chưa deploy Functions/chưa publish Remote Config/chưa smoke PARSE hoặc LOG. CLI login inventory có một tài khoản đã đăng nhập; chưa kiểm quyền project/deploy. Không thay đổi cloud resources.

Nếu được giao tiếp sau khi eval đạt: kiểm App Check SHA-256 đúng bản ký, giữ enforcement; deploy riêng aiSchedule/getAiConfig/claimEarlyAccess tại asia-southeast1; backup/merge/publish Remote Config giữ các key/conditions khác; smoke 1 PARSE + 1 LOG bằng tài khoản test, không mua. Firebase CLI chưa đăng nhập/thiếu quyền thì dừng hỏi Duong.

## Bảo toàn working tree

19 file tracked dở tại working tree gốc giữ nguyên, SHA-256 của diff **667b37fae661a56e01d326cdaa98c9494e0535303ef36c9222e82cabb1c04725**. Không sửa `.omc`, không reset/stash/reboot. Worktree main-integration vẫn giữ thay đổi template/báo cáo cục bộ; bản sao và F-EVAL-1 nằm riêng trên `fix/provider-schema`. Không push branch này, không merge/deploy/publish.
