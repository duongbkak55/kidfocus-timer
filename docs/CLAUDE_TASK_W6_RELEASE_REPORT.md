# W6 — báo cáo phát hành

Ngày 2026-10-02. Duong duyệt merge, push, triển khai riêng `aiSchedule` rồi smoke; G2 và G3 W6 đã PASS.

## Main và CI

- Worktree riêng `KidFocusTimer-main-integration` sạch trước merge, `origin/main` và main cùng ở `71ec458`. Merge `--no-ff` nhánh `feature/w6-focus-time` (`85f164c`) vào main tại `67e0089`, không conflict; push `origin/main` không force.
- Android JDK 17: `./gradlew testDebugUnitTest assembleDebug lintDebug` **PASS**. Functions Node 22: `npm --prefix functions test` **172/172 PASS**, `npm --prefix functions run lint` **PASS**. `git diff --check` **PASS**.
- [Build & Publish Release AAB](https://github.com/duongbkak55/kidfocus-timer/actions/runs/36974291783) trên đúng SHA `67e0089`: **SUCCESS** sau 9 phút 18 giây; AAB và APK artifact đều upload. Cảnh báo GitHub cache/setup-java không ảnh hưởng kết luận job.

## Backend trước phát hành app

- Sau CI xanh, chạy đúng `firebase deploy --only functions:aiSchedule --project kid-focus-app --non-interactive`: **Deploy complete**, chỉ cập nhật `aiSchedule(asia-southeast1)`.
- Kiểm tra metadata sau deploy: Function **ACTIVE**, runtime `nodejs22`, source hash `47f769122542de6fc2fbc233923ef08a6901ab26`, `OPENROUTER_API_KEY` bind **version 3**, `ENFORCE_APP_CHECK=true`, timeout 90 giây. Không đọc giá trị secret; không thay Remote Config hoặc deploy Function khác.

## Smoke tài khoản test

- Duong chuyển đích smoke sang **Pixel 3a**, kết nối ADB USB ổn định. Cài đè (`adb install -r`) APK debug từ main đã build với cấu hình Firebase **public client** lấy tạm từ Firebase CLI; giữ nguyên phiên đăng nhập tài khoản test trên máy. Không đưa cấu hình tạm, Auth/App Check token hoặc PIN vào repo, log hay báo cáo.
- `getAiConfig` với Auth và App Check token của phiên test trên Pixel 3a: **HTTP 200**, Smart Schedule bật, còn **15 credits** trước smoke.
- Một ADVISE duy nhất qua callable production `aiSchedule` với fixture **synthetic** `overlap`, đổi `t0` thành `TEST_PRACTICE`/“Luyện đề giả lập” và ngày tham chiếu thành 2026-10-02: **HTTP 200**, có `summary` và một đề xuất `MOVE`; còn **13 credits** (đúng giá 2 credits). Request gửi kèm Firebase Auth và App Check token của app trên Pixel 3a; không đọc hay ghi lịch/nhật ký thật của trẻ.
- Qua màn **Cú học / Study Owl** trên Pixel 3a, hỏi phép cộng giả lập `2 + 3`: nhận đáp án đúng **5**; bộ đếm câu hỏi giảm **14 → 13**. `aiChat` đang bật enforcement App Check, nên phản hồi thành công xác nhận đường gọi từ app qua App Check hoạt động.
- Không mua, không dùng dữ liệu trẻ thật. Pixel 9 từng hiện ba tài khoản không xác định nên không chọn tài khoản nào trên máy đó; ADB không dây của Pixel 9 sau đó ngắt. Theo chỉ định mới của Duong, toàn bộ smoke được thực hiện trên Pixel 3a có sẵn tài khoản test.

## Phạm vi và phần để sau

- Không phát hành app trong lượt này; workflow chỉ build/upload artifact. Không sửa `.omc` hoặc 19 file dở ở working tree gốc.
- P3 để sau: dọn map `focus_extension_*` trong DataStore cũ hơn 60 ngày.
