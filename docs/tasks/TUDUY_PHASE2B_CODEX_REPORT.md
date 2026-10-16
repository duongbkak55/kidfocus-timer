# Luyện tư duy — Phase 2B: báo cáo Codex

Branch: `feat/learning-tuduy` trong worktree `KidFocusTimer-tuduy`. Không push, merge hay deploy. File giao việc được commit riêng tại `5961d4f`; 31 test cũ trong `tuduy-content.test.js` không bị sửa.

## Đã làm

- Thêm 9 dạng `latin`, `magic`, `signs`, `triCenter`, `sumSplit`, `addTable`, `numberLine`, `ring`, `match`. Engine thuần JS; các đáp án được suy lại từ dữ kiện hiển thị và được đối chiếu bằng bộ giải độc lập trong test. Sudoku kiểm duy nhất cả bảng; dấu thử hết (2^k) tổ hợp; ô vuông và cặp tổng kiểm mọi cách điền/ghép; nối cặp thử mọi hoán vị.
- Mở rộng mức 3: câu đố xếp hàng có tên, Venn ba vòng, chọn phép tính bằng số đích, chấm chạy quanh bốn ô, ma trận hình và số chấm, cặp xúc xắc tổng lớn nhất, tháp bốn tầng Lớp 1, đoàn tàu 6–7 phép cộng/trừ. Các biến thể mở rộng chỉ bật khi UI truyền `phase2b: true`, nên hợp đồng và test Phase 1–2 vẫn giữ nguyên.
- Màn chọn bài chia bốn nhóm: Số & phép tính, Quy luật, Hình & vị trí, Câu đố ô số. Trộn các dạng nay lấy ngẫu nhiên từ toàn bộ danh sách hợp lệ. UI có phím dấu +/−, bàn 1..n cho Sudoku, nối cặp bằng chạm trái→phải, ếch nhảy từng vạch, vòng số, bảng cộng và bảng 5×5 co theo chiều rộng.
- JSON mẫu đầy đủ cho từng dạng mới và từng biến thể mở rộng: [TUDUY_PHASE2B_EXAMPLES.json](TUDUY_PHASE2B_EXAMPLES.json). Ví dụ trong file: `4 ○ 1 ○ 3 = 8` giải bằng `+`, `+`; vòng số đi `3 → 1 → 2 → 4 → 1 → 0 → 3`; nối cặp mẫu là `1–1, 2–3, 3–2`.

## Sửa theo review G2 Phase 2B

- `sumSplit`: chọn đáp án chỉ khi đúng duy nhất trong mọi cách ghép bốn số, kể cả tổng vượt giới hạn của bài; năm số đều từ 1 đến 10. Cách ghép được trình bày cho trẻ vẫn phải là phép cộng hợp lệ ở band tương ứng.
- `signs`: duyệt mọi chuỗi dấu có kết quả trung gian không âm, kể cả chuỗi vượt phạm vi bài; chỉ phát câu có một đáp án. Chuỗi đáp án phải thỏa luật tính ở band đó.
- Tia số chấp nhận đổi chỗ hai số hạng của phép cộng; phép trừ giữ đúng thứ tự. Bộ so sánh đáp án được tách thành hàm thuần để test.
- Ba engine dùng chung luật cộng/trừ mới: các phép trong phạm vi 10 được giữ, trên 10 tránh nhớ/mượn. `triSum` có tổng 9 hoặc 10; dãy hàng xóm cho phép 9 → 10.
- Hình Venn và bài trong/ngoài được dịch vị trí trong biên an toàn 12 px; ô vuông kì diệu có thêm các bộ ba số; vòng số mức 3 hiện hai số cho sẵn; bài so sánh mức 2 có chênh lệch 0–3.

Test mới duyệt vét cạn các đáp án của `sumSplit` và `signs`, kiểm phép cộng đổi chỗ trên tia số, kiểm luật mới ở cả ba engine, kiểm ≥30 hình khác nhau trên 1.000 câu cho từng dạng Venn/trong-ngoài/ô vuông và khoảng cách tối thiểu 12 px. 31 test cũ không thay đổi.

## Sửa sau G3 Pixel 9

- Bốn emoji của `insideOut` được đặt ở bốn vùng có khoảng trắng tối thiểu 10 px giữa **cả hộp hình** và viền tròn/vuông; chữ SVG được căn giữa theo chiều dọc. Test 4.000 câu kiểm cả bán kính emoji, nét viền và mép khung.
- Câu đố xếp hàng nói rõ “Đầu hàng ở bên trái” trong prompt được đọc bằng TTS; hình có lá cờ đánh dấu đầu hàng.
- Nhãn tổng được bỏ khỏi mặt tam giác `triSum`; tổng vẫn hiện trong câu hỏi. Tia số 0–20 hiện tối đa 10 nhãn quanh bước nhảy, cỡ chữ 14 và khoảng cách mỗi vạch tối thiểu 24 px ngay cả ở khung 320 px; hoạt ảnh ếch dùng cùng thang mới theo chiều rộng SVG thực tế.
- Bỏ số thứ tự trước phép tính trong `expressionCondition`, lời giải nêu phép tính thay cho “ô số”; hai cột `match` không lặp nguyên phép tính; cặp xúc xắc hiển thị mặt chấm SVG tương ứng 1–6.

Test bố cục mới kiểm `insideOut`, `triSum`, `numberLine` và test thêm dấu đầu hàng, phép ghép khác nhau, mặt xúc xắc. Claude cần cài APK từ commit mới và chạy lại G3 trên Pixel 9; ảnh G3 trước đó vẫn là ảnh của `fd227ce`.

## Kiểm tra

| Lệnh | Kết quả |
|---|---|
| `TUDUY_N=10000 node --test shared/learning/tests/*.test.js` | 96/96 pass; riêng Phase 2B kiểm độc lập 340.000 câu dạng mới và 150.000 câu biến thể |
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

Claude đã kiểm tra G3 vòng đầu trên Pixel 9 bằng APK của `fd227ce`, ghi kết quả tại [TUDUY_PHASE2B_G3_REPORT.md](TUDUY_PHASE2B_G3_REPORT.md). Các lỗi nhìn thấy trên máy đã được sửa ở mục trên; cần chạy lại G3 với APK mới. `docs/tasks/g3/` là dữ liệu chưa theo dõi có sẵn và không được stage trong commit sửa G3.
