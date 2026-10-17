# Web app / sync v2 — báo cáo Step A và B

Nhánh `feat/web-app`. Đã merge `feat/web-tuduy` sau khi sửa lỗi hoạt hình cũ. Step A và Step B đã triển khai. Chưa push hoặc deploy. Không sửa `shared/sync/`.

## Phần app đã làm

- Port `shared/sync/sync-v2.js` thành `SyncV2Model.kt`: chuẩn hoá trường, ID, ngày lặp, `toDoc`/`fromDoc`, migration v1, LWW theo thời gian rồi `deviceId`, và ưu tiên completion `ON_TIME`/sớm hơn. Gradle copy fixture gốc từ `shared/sync/fixtures/` vào test resources khi chạy test; không có bản fixture fork trong repo.
- `CloudSyncManager` dùng listeners cho `profiles`, `tasks`, `routines`, `sessions` (cửa sổ 400 ngày), `completions`, và `sync/settings`. Mỗi mục được ghi qua Firestore transaction đọc phiên bản hiện có rồi chọn winner; không còn upload `backups/current` hoặc `deleteAll`/nạp lại toàn bộ Room. `dayLogs` tiếp tục dùng đồng bộ từng tài liệu đã có.
- Room v8 thêm duy nhất bảng `sync_records`, khoá `(ownerUid, collection, docId)`, giữ payload/phiên bản/tombstone/cờ dirty qua lần tắt app và mất mạng. Quan sát các DAO để tạo thay đổi từng mục; xóa lưu tombstone với đầy đủ entity fields. Lúc khởi động, so lại các mục đã theo dõi trước khi tải cloud để không mất thay đổi Room vừa thực hiện. Các mục local mới được đưa lên sau khi tải cloud ban đầu.
- Task, routine và session mới dùng ID ngẫu nhiên 52-bit tường minh; ID cũ giữ nguyên. Session history cục bộ giữ 400 ngày để phù hợp truy vấn v2. Thay đổi task/routine từ cloud chỉ huỷ/lên lịch lại báo thức của mục đó. Cập nhật routine dùng `UPDATE` khi đã tồn tại để không kích hoạt FK cascade xoá completion.
- Migration đọc `sync/meta` bằng transaction, chuyển `backups/current` schema 1 sang tài liệu từng mục theo batch tối đa 450 ghi rồi ghi meta schema 2. Nếu app cũ tiếp tục ghi snapshot mới, màn Đồng bộ hiện cảnh báo và có nút nhập lại; từng mục nhập lại vẫn qua `pickWinner`, không ghi đè mù.

## Kiểm tra

| Lệnh / kiểm tra | Kết quả |
| --- | --- |
| `./gradlew testDebugUnitTest lintDebug assembleDebug` | BUILD SUCCESSFUL; 368/368 Android test, 0 failure/error; lint và APK debug xanh |
| `node --test shared/learning/tests/*.test.js shared/sync/test/*.test.js` | 100/100 pass (96 Learning Hub + 4 sync reference) |
| `npm --prefix web test` | 4/4 pass, gồm test animation race 100 tổ hợp |
| `git diff --check` | Pass |
| Room `Migration7To8LocalTest` | Hàng session v7 còn nguyên; bảng/cấu trúc sync record được Room validate |
| `SyncRecordQueueTest`, `SyncV2DeviceMergeTest` | Tombstone dirty tồn tại sau khi mở lại DB; hai thiết bị giả lập sửa khác mục, cùng mục, và phát lại hàng đợi xóa offline |

Các fixture Android mới xác nhận đúng 9 tài liệu migration và toàn bộ case conflict trong fixture JS; 31 test Tu Duy gốc giữ nguyên.

## Ghi chú review

- Chưa chạy Firestore emulator hoặc đăng nhập hai máy thật. Test xung đột hiện dùng fake giao dịch xác định; G2/G3 cần kiểm tra migration tài khoản beta, đồng bộ offline hai thiết bị, listener khôi phục sau mất mạng, cảnh báo app cũ và lịch báo thức trên máy.
- Màn app chỉ thêm cảnh báo và nút nhập lại trên trang Đồng bộ.
- `docs/tasks/g3/` là dữ liệu không theo dõi có sẵn trong worktree, không đưa vào commit.

## Step B — web app W1

- Thêm `web/app/` dùng Vite, TypeScript, Preact, Firebase modular Auth và Firestore; không CDN hoặc analytics. `npm --prefix web run build` tạo `web/dist/app/`; Hosting target `demo` rewrite `/app/**` tới `app/index.html`.
- Đăng nhập email/mật khẩu hoặc Google (điện thoại dùng redirect), đặt lại mật khẩu, đăng xuất. Bộ nhớ đệm Firestore chỉ bật nếu phụ huynh chọn “Đây là máy của tôi”; màn đăng nhập nhắc đăng xuất khi dùng máy chung.
- Hồ sơ bé lấy từ `profiles`; màn Hôm nay nối task/routine theo giờ, đánh dấu routine đúng giờ hoặc trễ bằng completion key tự nhiên. Lịch tuần chung cho mọi tuần cho thêm, sửa, tắt và xóa task/routine với giới hạn giống app.
- Focus gắn với task, cho chọn thời lượng phiên trong tab, giữ màn hình sáng bằng Wake Lock nếu trình duyệt hỗ trợ, phát âm khi xong, chỉ xin quyền Notification khi phụ huynh bấm. Kết thúc hoặc dừng sớm ghi `sessions` với `taskId`, `taskTitle`, `completed`. Màn Lịch sử hiển thị 7 cột theo tuần và danh sách focus/routine. Có trạng thái đồng bộ, nút thử lại và cảnh báo app cũ/nhập lại v1.
- Mọi ghi core item đi qua `shared/sync/sync-v2.js` `toDoc` và giao dịch `pickWinner`; không có bản fork của reference. `npm audit` báo 0 lỗ hổng sau khi ép phiên bản grpc đã sửa lỗi. Cấu hình public `web/app/firebase-config.json` do Claude cung cấp **chưa có**; giao diện báo rõ nếu thiếu. Test dùng file cấu hình demo tạo trong `web/dist/` (bị gitignore), tuyệt đối không dùng project production.

| Kiểm tra Step B | Kết quả |
| --- | --- |
| `npm --prefix web test` | 7/7 pass, gồm 3 unit mới cho task/routine/completion/session/tombstone và bài test Chrome animation |
| `node --test shared/learning/tests/*.test.js shared/sync/test/*.test.js` | 100/100 pass |
| `npm --prefix web audit --audit-level=high` | 0 vulnerabilities |
| Smoke Brave + Firebase Auth/Firestore emulator | PASS: đăng nhập, rule chặn UID khác, tạo task/routine, đánh dấu hoàn thành, chạy Focus 1 phút, thấy lịch sử; không có page error |
| Layout ở 360 và 412 px | Không tràn ngang, mọi button đang hiện ≥44 px |
| `git diff --check` | Pass |

Ảnh test 390×844 với tài khoản emulator và hồ sơ giả **Bé Na**: [Hôm nay](web-app-screenshots/w1-hom-nay-390x844.png), [Lịch](web-app-screenshots/w1-lich-390x844.png), [Tập trung](web-app-screenshots/w1-tap-trung-390x844.png), [Đã hoàn thành](web-app-screenshots/w1-da-hoan-thanh-390x844.png).

### Vướng mắc cần quyết định trước Step C

`WEB_APP_CODEX_TASK.md` yêu cầu test **mọi** đường ghi, gồm `settings`, `dayLog` và `learning`, phải qua `toDoc`/`pickWinner`. Nhưng reference `shared/sync/sync-v2.js` chỉ định nghĩa 5 collection `tasks`, `routines`, `completions`, `sessions`, `profiles`; `toDoc('settings' | 'dayLogs' | 'learning', …)` ném lỗi. Đồng thời file này Claude-owned, cấm thay đổi hành vi. Đề xuất: giữ reference nguyên vẹn và dùng adapter riêng theo format app hiện có cho ba loại W2, với test giao dịch/winner tương ứng. Chờ xác nhận contract trước khi làm Step C; không tự thay reference.
