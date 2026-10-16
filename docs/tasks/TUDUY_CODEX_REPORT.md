# Báo cáo Codex — Luyện tư duy / nhóm 5–6 tuổi

Nhánh `feat/learning-tuduy`, worktree `KidFocusTimer-tuduy`. Chưa push, merge, deploy hoặc tải bản lên Play.

## Thay đổi

- Thêm `AgeBand` làm nguồn chung cho ID, thứ tự, nhãn vi/en và ngưỡng lịch. Picker hồ sơ theo thứ tự 2–3, 4–5, 5–6, Lớp 1–3.
- Giữ `5-6` qua hồ sơ, Learning ViewModel, kết quả học tập Room/Firestore, cloud sync và payload PARSE/LOG/ADVISE. Band không hợp lệ tiếp tục fallback `4-5` ở các đường đọc; thao tác lưu hồ sơ từ chối giá trị lạ.
- Ngưỡng ngủ tối thiểu 600 phút và Focus tối đa 20 phút của 5–6 lấy cùng mức 4–5; đây là lựa chọn ít thay đổi theo task. Các quy tắc còn lại dựa vào nhóm và dữ liệu lịch thực tế, không cần nhánh riêng cho 5–6.
- Functions cũng phải nhận `5-6`: ba validator và prompt/ngưỡng ADVISE đã cập nhật. **Chưa deploy Functions**; trước khi phát hành band mới cần deploy `aiSchedule`, nếu không PARSE/LOG/ADVISE trên bản production sẽ từ chối band này.
- CI dùng Node 20 chạy `node --test shared/learning/tests/*.test.js` trước khi build release. Test thất bại làm job thất bại.
- Thêm **30 icon WebP 128×128 trong suốt**, tổng **189.004 byte** (<300 KB), tại `shared/learning/www/tuduy/icons/`; chỉ chuẩn bị tài nguyên, chưa thay emoji hoặc sửa mã `tuduy/*` hiện có. Tạo bằng imagegen tích hợp, mỗi mẫu một lệnh; prompt chung: một đồ vật/con vật duy nhất, nét tròn dễ thương, viền rõ, màu tươi, nền trong suốt, không chữ/số/logo/nhân vật thương hiệu. Tên từng mẫu đúng tên file (apple, pear, banana, grapes, strawberry, orange, carrot, cupcake, mooncake, donut, rabbit, tiger, bear, cat, dog, panda, turtle, chick, frog, ant, train, car, rocket, flower, star, cloud, balloon, block, apple-basket, scale).

## Kiểm tra

- Android: `./gradlew testDebugUnitTest lintDebug assembleDebug` — **361 test, 0 lỗi; lint/build xanh**.
- Nội dung web: `node --test shared/learning/tests/*.test.js` — **29/29 qua**.
- Functions offline: `npm --prefix functions test` — **174/174 qua**; `npm --prefix functions run lint` — qua. Không gọi OpenRouter/production.
- `git diff --check` — qua.
- Bộ icon: 30/30 file có kích thước 128×128, kênh alpha thật; đã kiểm ảnh liên hệ bằng mắt, không thấy chữ/số/logo.

## Máy thật và ảnh

- APK debug đã build: `app/build/outputs/apk/debug/app-debug.apk`.
- Pixel 9 chưa có trong ADB. Thiết bị duy nhất phát hiện qua mDNS là **BRAVIA 4K UR3** (`192.168.68.108:5555`), nên chưa cài APK hoặc chạy kịch bản lên thiết bị đó.
- Ảnh picker, màn chọn trò, 6 dạng bài (câu hỏi + giải thích), màn kết quả và kiểm tra thống kê phụ huynh: **chờ Pixel 9**. Chưa ghi nhận G3 là đạt.

## Câu hỏi còn mở

- Cần địa chỉ ADB wireless của Pixel 9 hoặc cắm USB và mở khóa máy để chạy G3, chụp ảnh đúng kịch bản. Không nhập PIN điện thoại.
