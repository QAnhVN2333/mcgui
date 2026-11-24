# Handoff: vấn đề view overlay/blur và kick do flying

Ngày tổng hợp: 2026-11-24

Tài liệu này ghi lại trạng thái điều tra để tiếp tục ở session mới. Các nguyên nhân bên dưới là giả thuyết cho đến khi được tái hiện và đo đạc; chưa có thay đổi nào trong working tree được xác nhận là đã giải quyết lỗi.

## 1. Overlay/blur mở nhưng không hiện ổn định

### Đã quan sát

- Với `views.invisible: false` và `views.client-pointer: true`, fullscreen hoạt động; overlay và blur thường không hiện ngay khi mở lệnh. Có trường hợp hiếm tự hiện sau đó.
- Với overlay/blur, nhấn `W/A/S/D` làm client/server đưa player về vị trí giữ (`seat`); sau lần chỉnh vị trí đó, giao diện được báo là hiện ổn định.
- Trace ngày 2026-11-24:
  - `market-blur` (overlay): bản đồ đã được gửi (`all written 136`), client xác nhận (`client confirmed 149`).
  - `market` (fullscreen): bản đồ đã được gửi (`all written 72`), client xác nhận (`client confirmed 81`).
  - `market-overlay` cũng báo `client confirmed`.
- Trace xác nhận tiến trình gửi/acknowledge, nhưng **không chứng minh shader đã vẽ đúng panel hoặc blur**.

### Hướng điều tra / giả thuyết

- Trong [`Views.java`](src/main/java/ai/resourcepack/ui/framework/world/Views.java), overlay chỉ vào nhánh `keeps` nếu thỏa các điều kiện tại `open`: client pointer được dùng, kích thước map phù hợp, client chạy overlay chain được hỗ trợ và `overlayEye(player)` tìm được vị trí hợp lệ.
- Nếu không vào `keeps`, view có thể đi qua nhánh overlay live. Với `views.invisible: false`, `requireClientPointer` là true và `clientEye(..., true)` có thể được dùng. Hàm này cho phép sàn chồng tối đa 0,2 block; ngược lại `overlayEye` kiểm tra hộp va chạm không bị chồng vào block nào. Vì vậy phỏng đoán player bị client đẩy khỏi vị trí shader nhận diện có thể chỉ đúng với một số nhánh, chưa thể áp dụng chung.
- Giả thuyết phù hợp với triệu chứng: client chỉ nhận diện overlay/pointer/blur khi tọa độ hoặc trạng thái camera khớp với “flagged spot”; lần server sửa vị trí sau phím di chuyển có thể làm shader nhận diện lại. Cần xác minh bằng tọa độ và trạng thái nhánh lúc mở, không chỉ dựa vào trace map.

### Việc cần làm tiếp

1. Tái hiện cùng client/server và cùng page, ghi rõ view là `kept-overlay` hay `live-overlay`, `clientPointer`, `revealed`, vị trí feet/eye và `onGround`.
2. Thêm/ bật log quanh các chuyển trạng thái `take` → `keep` hoặc `reveal`, cùng các lần `keepStill` gửi teleport; hiện log mới chỉ ghi khi phá block dưới chân.
3. So sánh một lần mở không nhấn phím với một lần nhấn `W/A/S/D`; kiểm tra có teleport/correction nào và tọa độ nào thay đổi ngay trước lúc panel xuất hiện.
4. Chỉ cân nhắc sửa việc đặt vị trí hoặc gửi block giả sau khi xác định được client nhận trạng thái gì và nhánh overlay nào đang chạy. Chưa có bằng chứng đủ để triển khai packet block giả.

## 2. Phá block dưới chân khi đang mở view có thể dẫn tới kick flying

### Đã quan sát

- Nếu một người khác phá block mà player đang mở view đứng lên, player có thể bị giữ lơ lửng; đóng view rồi bị kick với `Flying is not enabled on this server`.
- Cũng cần kiểm tra trường hợp chính player đang mở view là người phá block dưới chân.
- Lỗi kick được báo là cơ chế kiểm tra bay mặc định của Minecraft, không phải kick từ plugin bên thứ ba.

### Hướng điều tra / giả thuyết

- `hold()` trong [`Views.java`](src/main/java/ai/resourcepack/ui/framework/world/Views.java) đặt trọng lực bằng 0 và giữ player tại seat. Khi đóng view, luồng `close` trả player về `returnTo` đã lưu lúc mở rồi khôi phục trạng thái/attributes. Nếu block nền đã bị phá, vị trí trả về có thể không còn được đỡ; việc khôi phục trọng lực/di chuyển và đồng bộ vị trí client-server có thể dẫn tới kick. Đây là giả thuyết, cần xác nhận vị trí trước/sau close và thứ tự teleport/release.
- Phỏng đoán trước đó là phần thân dưới chồng 0,2 block xuống sàn khiến client tự đẩy player lên. Chỉ một số nhánh có thể cho phép overlap: `clientEye(..., true)`. Nhánh overlay `keeps` dùng `overlayEye` và không cho phép hộp va chạm chồng vào block. Cần log `keeps` và tọa độ thực tế trước khi quy lỗi cho client collision.

### Giới hạn của log đang có

- Thay đổi chưa commit hiện thêm `[view-underfoot]` trong `onBlockBreak`, bật bằng `debug: true` ở root config.
- Log chỉ phát nếu **người phá block cũng đang có view**, block bị phá đúng block được tính dưới chân họ, và event không bị hủy. Do đó nó **không bắt được** trường hợp người khác phá block dưới chân của viewer.
- Ghi chú hiện tại gồm loại block, vị trí, loại view, `client-pointer`, `revealed`, vị trí player và `on-ground`. Chưa có test/build chạy sau thay đổi này.
- Muốn bắt đúng trường hợp người khác phá nền, cần xác định viewer nào đang đứng/được giữ trên block đó khi `BlockBreakEvent` xảy ra; tránh chỉ kiểm tra UUID của người gây event.

### Việc cần làm tiếp

1. Tái hiện hai trường hợp riêng: viewer tự phá block và người khác phá block của viewer.
2. Ghi vị trí server/client (nếu có thể đo), `seat`, `returnTo`, block hỗ trợ, trạng thái `onGround`, thời điểm teleport/release và log kick.
3. So sánh việc đóng view khi block vẫn còn với khi block đã bị phá; xác minh có teleport về `returnTo` thất bại/được chấp nhận nhưng không có nền hay không.
4. Nếu cần can thiệp close, phải đảm bảo player không bị treo và không bị đưa vào vị trí không an toàn; tránh sửa bằng cách đổi game mode hoặc tắt kiểm tra flying nếu chưa xác định root cause.

## Thay đổi đang có trong working tree

Các thay đổi này chưa commit và chưa được xác nhận bằng build/test:

- [`Settings.java`](src/main/java/ai/resourcepack/ui/framework/Settings.java): thêm `debug`, đọc từ config `debug` (mặc định `false`).
- [`config.yml`](src/main/resources/config.yml): thêm mô tả và `debug: false`.
- [`Views.java`](src/main/java/ai/resourcepack/ui/framework/world/Views.java): thêm listener log `[view-underfoot]`.
- [`SettingsTest.java`](src/test/java/ai/resourcepack/ui/framework/SettingsTest.java): kiểm tra mặc định và bật `debug`.
- `git diff --check` đã chạy và không báo lỗi whitespace.

Trạng thái lúc tổng hợp: bốn file trên đang modified; không thấy `issues.md` có sẵn. Giữ nguyên các thay đổi đang có khi tiếp tục.

## Kiểm tra build đã biết từ phiên trước

Ở lần build gần nhất được ghi nhận, JAR được tạo và SHA-256 của engine nhúng, engine JAR và `script-engine.properties` khớp. Tuy nhiên `clean build` vẫn thất bại ở test có sẵn `OverlayTest.hoverRepaintsOfAnOverlayMatchAFreshPicture` do lệch 4 pixel. Đây là lỗi baseline được báo từ trước, chưa chứng minh có liên quan đến hai vấn đề view trong handoff này.

## Lệnh kiểm tra gợi ý cho phiên tiếp

1. Chạy test tập trung cho thay đổi config: `sh gradlew test --tests ai.resourcepack.ui.framework.SettingsTest`.
2. Chạy `sh gradlew build`; nếu vẫn dừng ở `OverlayTest.hoverRepaintsOfAnOverlayMatchAFreshPicture`, ghi nhận riêng như baseline đã biết.
3. Kiểm tra diff và trạng thái working tree trước khi sửa để không ghi đè các thay đổi đang có.
