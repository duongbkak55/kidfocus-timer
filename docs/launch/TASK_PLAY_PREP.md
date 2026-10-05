# Task: Chuẩn bị Play (privacy policy, store listing, release notes, hosting)

Người giao: Claude · Người làm: ChatGPT/Codex · Ngày: 2026-10-03
Branch: `play-prep` từ main mới nhất (worktree riêng). Không push/deploy khi Duong chưa duyệt. Chỉ `git add` file của task này.
[GMAIL] = `kidfocusstudio@gmail.com` (Gmail dự án). Thay **mọi** chỗ có `duongbkak55.us@gmail.com` trong tài liệu công khai (privacy VI/EN, store listing, site/) bằng [GMAIL]; grep để chắc không còn sót.

## 1. Privacy policy (`docs/privacy-policy.html`, cả VI và EN)
- Cập nhật ngày; email liên hệ = [GMAIL]; thêm anchor `id="xoa-du-lieu"` cho mục xoá dữ liệu.
- Mục 3a: đổi "Nhập nhanh chỉ dành cho phụ huynh sau khóa PIN" → "Nhập nhanh lịch tuần dành cho phụ huynh (sau PIN)…" (giữ phần còn lại).
- Thêm **3c. Nhật ký hôm nay và "Con tự kể"** (VI + EN), nội dung tối thiểu:
  · Phụ huynh hoặc trẻ có thể kể lại việc đã làm (gõ hoặc nói). Trẻ dùng được không cần PIN, trong app và từ màn hình khoá
    (widget, ô cài đặt nhanh) khi phụ huynh bật; phụ huynh tắt được trong Cài đặt phụ huynh.
  · Màn hình khoá chỉ hiện ô nhập và kết quả của lần nhập đó; không hiện lịch, lịch sử hay thông tin khác của trẻ.
  · Chữ nhập, nhóm tuổi, ngày/giờ và lịch dự kiến rút gọn được gửi qua Firebase Functions/OpenRouter để hiểu nội dung — giống 3a:
    không gửi tên bé, không lưu/ghi log trên server, `data_collection: deny`. Dùng hạn mức của tài khoản phụ huynh.
  · Mục đã hiểu được lưu trên máy (đồng bộ nếu đăng nhập); phụ huynh xem, sửa, xoá được. Nhắc: không nói tên/trường của bé.
- Mục 4 (Trẻ em): bổ sung ngoại lệ "Con tự kể" (chỉ ghi nhật ký, phụ huynh bật/tắt) vào câu về tính năng sau PIN.
- Kiểm tra lại toàn văn: không còn câu nào mâu thuẫn với W5/W6/W7 (đọc kỹ, liệt kê các chỗ đã đổi trong report).

## 2. Hosting trang privacy (Firebase Hosting, project kid-focus-app)
- Dùng site riêng `kidfocus-studio` qua target `legal` trong `firebase.json` và `.firebaserc`; `public: "site"`, `cleanUrls: true`, header `Cache-Control: max-age=300`. Hosting mặc định đang phục vụ dashboard, không deploy lên đó.
- `site/privacy.html` = bản build từ `docs/privacy-policy.html` (script copy đơn giản, không sửa tay 2 nơi); `site/index.html` chuyển hướng tới `/privacy`.
- URL kỳ vọng: `https://kidfocus-studio.web.app/privacy` và `https://kidfocus-studio.web.app/terms`. Hai lệnh chờ Duong duyệt: `firebase hosting:sites:create kidfocus-studio --project kid-focus-app`, rồi `firebase deploy --only hosting:legal --project kid-focus-app`. **KHÔNG chạy** khi chưa có Duong duyệt; không deploy Hosting mặc định.
- Không deploy kèm functions/rules/remoteconfig.

## 3. Store listing (`store-listing/vi-VN`, `store-listing/en-US`)
- Short description (≤80 ký tự, đếm bằng script):
  VI: "Nói 1 câu là có lịch cho bé. Nhắc việc, đồng hồ tập trung, học qua trò chơi."
  EN: "Say it once, get your kid's weekly routine. Reminders, focus timer, games." (chỉnh nếu >80)
- Full description: giữ cấu trúc hiện tại, thêm vào mục DÀNH CHO PHỤ HUYNH (VI; EN tương đương):
  • Nhật ký hôm nay: ghi lại con thực sự làm gì và so với kế hoạch.
  • Con tự kể việc vừa làm bằng gõ hoặc giọng nói — kể cả từ màn hình khoá (widget hoặc ô cài đặt nhanh), phụ huynh bật/tắt được.
  Thêm vào ĐỒNG HỒ TẬP TRUNG: • Bé tự chọn thời gian 5–120 phút, thêm 5 phút khi cần (phụ huynh đặt giới hạn). • Màn hình luôn sáng khi đang đếm giờ.
  Không nhắc tính năng nhập bằng ảnh (đang tắt). ≤4000 ký tự.
- Title giữ nguyên.

## 4. Release notes (`store-listing/release-notes/beta-1.vi.txt`, `.en.txt`, ≤500 ký tự)
VI:
Bản dùng thử đầu tiên 🎉
• Nhập lịch tuần bằng gõ hoặc giọng nói, xem lại rồi Áp dụng
• Góp ý lịch tuần: giờ ngủ theo tuổi, lịch chồng chéo
• Nhật ký hôm nay, so với kế hoạch
• Con tự kể việc đã làm, cả từ màn hình khoá
• Đồng hồ tập trung: bé tự chọn thời gian, thêm 5 phút khi cần
Góp ý: nhắn Page KidFocus trên Facebook hoặc email ở mục Liên hệ
EN: bản dịch tương đương.

## 5. Khai báo Play (soạn sẵn trong report để Duong dán)
- Foreground service (specialUse, subtype "timer"): 1–2 câu giải thích VI/EN: đồng hồ tập trung/giải lao của trẻ phải chạy chính xác
  và hiện thông báo đang đếm giờ khi trẻ rời app; người dùng bắt đầu/dừng; dừng khi hết phiên. Nếu Play đòi video: dùng V3 (SCREENSHOT_TASK).
- Xác minh lại: manifest không còn USE_EXACT_ALARM; SCHEDULE_EXACT_ALARM có fallback (không cần khai báo).
- Data safety: làm 3 mục "ChatGPT xác minh" trong `docs/launch/DATA_SAFETY.md`, ghi kết quả.

## Gate
- Claude review diff (G2). Mở `site/privacy.html` local render đúng VI/EN.
- Report: `docs/launch/TASK_PLAY_PREP_REPORT.md` (commit, file đổi, số ký tự listing, kết quả xác minh Data safety, lệnh deploy chờ duyệt).
