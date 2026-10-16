# Luyện tư duy — Phase 2B: báo cáo Codex

Branch: `feat/learning-tuduy` trong worktree `KidFocusTimer-tuduy`. Không push, merge hay deploy. File giao việc được commit riêng tại `5961d4f`; 31 test cũ trong `tuduy-content.test.js` không bị sửa.

## Đã làm

- Thêm 9 dạng `latin`, `magic`, `signs`, `triCenter`, `sumSplit`, `addTable`, `numberLine`, `ring`, `match`. Engine thuần JS; các đáp án được suy lại từ dữ kiện hiển thị và được đối chiếu bằng bộ giải độc lập trong test. Sudoku kiểm duy nhất cả bảng; dấu thử hết (2^k) tổ hợp; ô vuông và cặp tổng kiểm mọi cách điền/ghép; nối cặp thử mọi hoán vị.
- Mở rộng mức 3: câu đố xếp hàng có tên, Venn ba vòng, chọn phép tính bằng số đích, chấm chạy quanh bốn ô, ma trận hình và số chấm, cặp xúc xắc tổng lớn nhất, tháp bốn tầng Lớp 1, đoàn tàu 6–7 phép cộng/trừ. Các biến thể mở rộng chỉ bật khi UI truyền `phase2b: true`, nên hợp đồng và test Phase 1–2 vẫn giữ nguyên.
- Màn chọn bài chia bốn nhóm: Số & phép tính, Quy luật, Hình & vị trí, Câu đố ô số. Trộn các dạng nay lấy ngẫu nhiên từ toàn bộ danh sách hợp lệ. UI có phím dấu +/−, bàn 1..n cho Sudoku, nối cặp bằng chạm trái→phải, ếch nhảy từng vạch, vòng số, bảng cộng và bảng 5×5 co theo chiều rộng.
- JSON mẫu đầy đủ cho từng dạng mới và từng biến thể mở rộng: [TUDUY_PHASE2B_EXAMPLES.json](TUDUY_PHASE2B_EXAMPLES.json). Ví dụ trong file: `4 ○ 1 ○ 3 = 8` giải bằng `+`, `+`; vòng số đi `3 → 1 → 2 → 4 → 1 → 0 → 3`; nối cặp mẫu là `1–1, 2–3, 3–2`.

## Kiểm tra

| Lệnh | Kết quả |
|---|---|
| `TUDUY_N=10000 node --test shared/learning/tests/*.test.js` | 87/87 pass; riêng Phase 2B kiểm độc lập 340.000 câu dạng mới và 150.000 câu biến thể |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest lintDebug assembleDebug` | Build thành công; 361 Android unit test |
| `node --check` trên engine và UI; `git diff --check` | Pass |

Test hình học kiểm bảng Latin 5×5, vòng số, tia số, tam giác, ma trận hình/chấm và khung 360/412 px. [Fixture 390×844](../../shared/learning/tests/tuduy-phase2b-screenshot.html) có chế độ câu hỏi và `?type=latin&level=3&view=explain` để chụp phần giải thích bằng UI thật.

## Quyết định khi spec xung đột

- Bảng `magic` 3×3 có 9 ô nên không thể đồng thời có 3–4 ô cho sẵn và chỉ 2–4 ô trống. Dùng 6 ô cho sẵn, 3 ô trống, mỗi hàng/cột đủ dữ kiện và toàn bảng chỉ có một cách điền.
- Biến thể “mỗi cột được 10 bằng các số 1–4” không thể tuân thủ đồng thời mức 5–6 yêu cầu tổng ≤9 và mức Lớp 1 cấm nhớ qua 10. Giữ biến thể này trên giấy; các ô vuông trong app dùng tổng 6/9 hoặc tối đa 15 khi không nhớ.
- `addTable` ghi “Lớp 1 only” nhưng cũng nhắc bản 5–6. Theo danh sách band, chỉ hiện cho Lớp 1 trở lên; 5–6 vẫn có nhiều bài cộng khác.
- Task nói nhóm “20 dạng”, nhưng 6 dạng Phase 1 + 11 dạng Phase 2 + 9 dạng Phase 2B = 26. Chia cả 26 vào bốn nhóm, không ẩn bớt bài đã có.

## Giữ ngoài app

Cộng/trừ nhớ qua 10; phép nhân/chia, gấp đôi/chia đôi và đổi tỉ lệ; mèo đuổi chuột, phân số hình tròn, gấp giấy đục lỗ, đếm khối lập phương, que diêm, ghép mảnh/tô chồng, tìm bóng, nhận biết tay trái. Toán có lời văn vẫn ở các bài Learning Hub hiện có.

## Ảnh và G3

Pixel 9 đã nhận một APK debug ở lượt làm việc này, trước lần chỉnh UI cuối. Chủ máy chọn hoàn tất code trước khi dùng Pixel để chụp. Chưa có ảnh câu hỏi/giải thích 390×844 cho 9 dạng và chưa kiểm tra thao tác UI cuối cùng trên máy; không tạo ảnh thay thế. Khi Pixel rảnh: cài lại APK mới nhất, mở từng dạng, chụp màn hỏi và màn giải thích vào `docs/tasks/g3/phase2b/`, đặc biệt Latin 5×5 và vòng số; sau đó cập nhật mục này. `docs/tasks/g3/` đang là dữ liệu chưa theo dõi có sẵn từ trước và không được stage trong commit Phase 2B.
