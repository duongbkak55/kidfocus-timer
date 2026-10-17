# Báo cáo Codex — web demo Luyện tư duy

Nhánh `feat/web-tuduy` (base `6825f26`). Đã làm trang `/tu-duy/` cho bé chơi thử 5 câu mỗi lượt và thêm lối vào từ `/beta/`. Chưa push, merge hoặc deploy; Claude tiếp tục G2/G3 và triển khai.

## Thực hiện

- `web/tu-duy/index.html` dùng đúng markup và các ID của UI trong app. Trang có hai nhóm tuổi, âm thanh WebAudio chỉ bật sau thao tác đầu tiên, giọng Việt cục bộ nếu trình duyệt cung cấp, nút tắt tiếng, hiệu ứng CSS, điểm/sao và lời mời đến `/beta/`. Nếu máy không có giọng Việt, tính năng đọc im lặng và trò chơi vẫn dùng được.
- Chỉ thêm hai tùy chọn tương thích ngược vào `shared/learning/www/tuduy/tuduy-ui.js`: `storageKey` và `sessionLength`. Web đặt `tuduy.web.v1` và 5 câu; app tiếp tục mặc định `tuduy.v1` và 8 câu. Không chỉnh engine, CSS hay bộ câu hỏi dùng chung.
- `web/build-tuduy.sh` sao chép nguyên vẹn tài nguyên đã phát hành vào `web/dist/tu-duy/`, cùng trang pháp lý cùng origin, favicon và OG 1200×630 dựng từ ảnh thật của trang. Bản `/beta/` lấy từ staging được thêm nút chơi thử.
- Không dùng tracker, cookie, tài khoản, quảng cáo, service worker, font/script bên thứ ba hay gửi dữ liệu trẻ ra ngoài. Tiến độ chỉ ở localStorage trên thiết bị. Trang demo dùng bản sao cùng origin của `site/privacy.html` và `site/terms.html`; footer `/beta/` trỏ đến địa chỉ pháp lý công khai. Đây là cách giữ trang demo không gọi URL ngoài và vẫn dùng cùng nội dung pháp lý.
- Cụm “không phải luyện thi” được giữ theo yêu cầu nội dung của task; ngoài cụm phủ định được yêu cầu này, trang không có từ ngữ định hướng thi cử bị cấm.

## Kiểm tra

| Kiểm tra | Kết quả |
| --- | --- |
| `TUDUY_N=10000 node --test shared/learning/tests/*.test.js` | 96/96 pass; không sửa 31 test gốc |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew testDebugUnitTest lintDebug assembleDebug` | BUILD SUCCESSFUL; 361/361 unit test Android, lint xanh |
| `npm --prefix web test` | 3/3 pass; so SHA-256 từng asset, audit nội dung/URL, Chromium chạy đủ 5 câu cho từng nhóm tuổi; không có JS error hoặc request khác origin |
| Kiểm tra bố cục tự động | Mọi dạng ở 360 và 412 px, cả hai nhóm tuổi: không tràn ngang; các nút/link đang hiện có vùng chạm tối thiểu 44 px |
| `git diff --check` | Pass |

`web/dist/tu-duy/` có 725.201 byte (43 file; `du` là 804 KiB do block size), dưới ngân sách 1,5 MB. Chạy build hai lần liên tiếp cho cùng danh sách file và SHA-256. Thư mục `web/dist/` và `web/node_modules/` được gitignore. Smoke headless dùng Brave Chromium trên Mac; có thể đặt `CHROMIUM_PATH` khi chạy ở máy khác.

## Ảnh chụp 390×844

- [Chọn dạng](web-tuduy/chooser-390x844.png)
- [Câu hỏi](web-tuduy/question-390x844.png)
- [Giải thích](web-tuduy/explanation-390x844.png)
- [Hoàn thành 5 câu](web-tuduy/done-390x844.png)

Ảnh lấy từ trang build chạy trong Chromium; không có ảnh trẻ em hay thông tin tài khoản. `docs/tasks/g3/` là dữ liệu có sẵn trong worktree, không thuộc commit này.
