# Báo cáo Codex — Luyện tư duy “Làm quen” (4–5 tuổi)

Nhánh `feat/web-app`, trên `f8834f9`. Chưa push, merge hoặc deploy. Chờ Claude G2/G3.

## Đã làm

- Thêm engine level 1 riêng cho 13 dạng được phép; `generate(type, 1, rng, {max:10})` từ chối loại khác. Level 2/3 giữ nguyên đường sinh bài.
- Mỗi câu có tối đa một ô trống, một đáp án xác định; số hiển thị trong 0–10, mọi phép cộng của level 1 có toán hạng và kết quả ≤5. Câu hỏi ngắn, dùng hình/chấm và lời giải đơn giản.
- Band 4–5 chỉ chơi level 1, mỗi lượt 5 câu. Bộ chọn ghi “Làm quen”; đáp án chạm tối thiểu 64px, “Khác loại” và “Vị trí” chạm trực tiếp vào hình. Không hiện dạng ngoài whitelist.
- Tiến bộ level 1 lưu ở `stats['l1:'+type]`, không nâng lên level 2. Review giữ nguyên item và `level`; chỉ hiện review của band đang chơi. Dạng `{stats, review}` và đường đồng bộ `learning/{profileId}_tuduy` không đổi.
- Learning Hub hiện Luyện tư duy cho 4–5; web `/tu-duy/?band=4-5` chọn sẵn nhóm đó, mặc định trang vẫn là 5–6; web app cho hồ sơ 4–5 mở đúng band. Không sửa Kotlin; APK debug đã chứa `assets/tuduy/tuduy-level1-core.js`.

## Kiểm tra

- `node --test shared/learning/tests/*.test.js`: **110/110** xanh, gồm 13 × 2.000 seed level 1, kiểm lại đáp án độc lập, biên số, độ dài câu hỏi và whitelist. 96 test cũ vẫn chạy.
- So với `f8834f9`, đầu ra level 2/3 **giống hệt JSON** ở 2.000 ca seed (mọi dạng, max 10/20 hợp lệ, extension bật).
- `npm --prefix web test`: **20/20** xanh. Chromium chơi đủ 5 câu của từng dạng ở 4–5; kiểm 320/360/412px không tràn ngang, đáp án ≥64px và không có request ra ngoài.
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest assembleDebug`: **376/376** unit test Android xanh; build debug thành công.
- `git diff --check`: xanh.

Theo mục **Decision** trong task, chỉ assertion visibility cũ ở `tuduy-content.test.js` được đổi từ “5–6 trở lên” sang “4–5 trở lên”; test vẫn giữ kiểm tra nhóm 2–3 không được thấy Luyện tư duy. Đây là đổi quy tắc sản phẩm có chủ đích, không xoá hay nới các kiểm tra khác.

## Ảnh từng dạng (trang fixture 390×844)

| Dạng | Ảnh |
| --- | --- |
| Đếm hình (`shapes`) | ![Đếm hình](tuduy-level1-screenshots/shapes.jpg) |
| So sánh (`compare`) | ![So sánh](tuduy-level1-screenshots/compare.jpg) |
| Sắp xếp (`order`) | ![Sắp xếp](tuduy-level1-screenshots/order.jpg) |
| Số liền kề (`neighbor`) | ![Số liền kề](tuduy-level1-screenshots/neighbor.jpg) |
| Quy luật dãy số (`sequence`) | ![Quy luật dãy số](tuduy-level1-screenshots/sequence.jpg) |
| Quy luật hình (`shapePattern`) | ![Quy luật hình](tuduy-level1-screenshots/shapePattern.jpg) |
| Khác loại (`oddOne`) | ![Khác loại](tuduy-level1-screenshots/oddOne.jpg) |
| Vị trí (`position`) | ![Vị trí](tuduy-level1-screenshots/position.jpg) |
| Trong và ngoài (`insideOut`) | ![Trong và ngoài](tuduy-level1-screenshots/insideOut.jpg) |
| Nối cùng kết quả (`match`) | ![Nối cùng kết quả](tuduy-level1-screenshots/match.jpg) |
| Đoàn tàu phép tính (`chain`) | ![Đoàn tàu phép tính](tuduy-level1-screenshots/chain.jpg) |
| Ô trống cân bằng (`balance`) | ![Ô trống cân bằng](tuduy-level1-screenshots/balance.jpg) |
| Sudoku nhí (`latin`) | ![Sudoku nhí](tuduy-level1-screenshots/latin.jpg) |

Ảnh dùng seed cố định 1729 trong `shared/learning/tests/tuduy-level1-screenshot.html`. G3 trên Pixel do Claude thực hiện sau review.
