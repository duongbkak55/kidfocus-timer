# Web app / sync v2 — báo cáo Step A

Nhánh `feat/web-app`. Theo yêu cầu của Duong, đã merge `feat/web-tuduy` vào nhánh này sau khi sửa lỗi hoạt hình cũ. **Chỉ Step A được triển khai; chưa bắt đầu giao diện web Step B/C, chưa push hoặc deploy.** Không sửa `shared/sync/`.

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
- Ảnh 390×844 của các màn web là deliverable Step B/C, nên chưa có trong Step A. Màn app chỉ thêm cảnh báo và nút nhập lại trên trang Đồng bộ.
- `docs/tasks/g3/` là dữ liệu không theo dõi có sẵn trong worktree, không đưa vào commit.
