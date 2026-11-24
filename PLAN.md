# Kế hoạch: mở GUI mà không tàng hình với người khác

## Mục tiêu

Thêm chế độ tùy chọn để người mở fullscreen GUI:

- Vẫn ẩn tay/người chơi đối với **chính client của họ** để không che GUI.
- Với người khác, vẫn hiển thị như một player bình thường và có thể bị tấn công/tương tác.
- Không ngồi lên armor stand.
- Con trỏ, hover và click vẫn khớp tọa độ.
- Không bị kick với thông báo `Flying is not enabled on this server`.
- Mặc định giữ nguyên hành vi hiện tại để tương thích ngược.

## Config đề xuất

Thêm vào `views` trong `src/main/resources/config.yml`:

```yaml
views:
  # true: giữ hành vi cũ, ẩn player với mọi người và dùng cơ chế view hiện tại.
  # false: chỉ ẩn player ở client của chính họ; những người khác vẫn thấy và có thể đánh họ.
  invisible: true

  # true: sát thương làm đóng GUI; false: GUI vẫn mở và sát thương vẫn được áp dụng.
  close-on-damage: true
```

`views.invisible` và `views.close-on-damage` là hai lựa chọn độc lập. Giá trị mặc định của
cả hai là `true`.

## Thiết kế đã chọn

Khi `views.invisible: false`, ép mọi fullscreen view dùng **client-pointer** (phương án 1 đã
được chọn), dù `views.client-pointer` đang tắt. Không dùng server-pointer hay mount player
lên armor stand trong chế độ này.

### Vì sao chọn client-pointer

- Với client-pointer, client tự đặt con trỏ từ hướng nhìn; server tính tọa độ từ rotation
  packets.
- `Views` đã có cơ chế ghép click với rotation packet kế tiếp và xử lý click khi chuột đứng yên.
- Hover được cập nhật từ cùng trạng thái pointer. Không cần thêm một pipeline click/hover khác.
- Server-pointer hiện gắn với camera entity và armor-stand vehicle. Bỏ mount nhưng giữ nguyên
  server-pointer dễ khiến camera, vị trí và tọa độ pointer không còn đồng bộ.

### Vị trí và teleport

- Không teleport player đến một vị trí xa hoặc đến armor stand.
- Dùng `clientEye(player, facing)` để tìm điểm flagged hợp lệ gần vị trí mắt hiện tại; `seat`
  được tính từ điểm mắt đó và eye height của player. Điểm flagged cần thiết để shader đặt
  chính xác ảnh và pointer.
- Nếu player đã ở đúng `seat`, tránh teleport thừa. Nếu lệch, chỉ teleport một lần đến điểm
  flagged gần nhất khi mở view; khi đóng thì trả về `returnTo` như luồng hiện tại.
- Tránh teleport lặp lại mỗi tick. Chỉ giữ logic sửa vị trí nếu player thực sự bị client/entity
  đẩy lệch, và giữ kiểm tra teleport ngoài ý muốn để đóng view thay vì kéo player ngược về.
- Overlay `keeps` có bước take riêng: kiểm tra và loại bỏ teleport thứ hai nếu player đã ở
  đúng seat, nhưng không làm thay đổi logic chụp/giữ world của overlay.
- Nếu không tìm được điểm an toàn gần vị trí mở GUI, không âm thầm fallback về server-pointer
  trong chế độ này. Hủy mở view có thông báo phù hợp, hoặc chọn fallback khác chỉ sau khi có
  quyết định thiết kế rõ ràng.

## Hạng mục implementation

### 1. Settings và config

- Thêm boolean `invisible` và `closeOnDamage` vào
  [`Settings.java`](src/main/java/ai/resourcepack/ui/framework/Settings.java).
- Đọc `views.invisible` và `views.close-on-damage`, mặc định cả hai là `true`.
- Thêm mô tả và giá trị mặc định vào `src/main/resources/config.yml`.

### 2. Chọn pointer và tạo entity trong `Views`

- Tại luồng mở view trong [`Views.java`](src/main/java/ai/resourcepack/ui/framework/world/Views.java),
  nếu `settings.invisible == false`, yêu cầu `clientEye` thành công và bật `clientPointer`
  cho view đó.
- Chế độ này không spawn `ArmorStand vehicle`, không mount player và không chuyển camera sang
  entity camera. Player dùng eye/camera của chính họ như client-pointer hiện tại.
- Giữ target `Interaction` và pointer display theo nhánh client-pointer; đảm bảo entity phụ
  cần thiết chỉ hiện cho người mở GUI.
- Không đổi đường xử lý hiện tại khi `invisible == true`.

### 3. Ẩn bản thân ở client của player, không ẩn toàn server

- Hành vi `invisible: true` giữ nguyên potion effect và `hidePlayer` với người khác.
- Hành vi `invisible: false` không áp dụng invisibility potion effect ở trạng thái server và
  không gọi `hidePlayer` cho các player khác. Giá trị mặc định là false
- Thay phần ẩn hình ảnh của player mở GUI bằng thay đổi **chỉ gửi đến client của player đó**
  qua packet/entity metadata hoặc packet effect phù hợp với các helper trong `Nms`/`Pipeline`.
- Không dùng `Player#hidePlayer` để ẩn chính player như giải pháp thay thế, vì có thể loại entity
  khỏi client thay vì chỉ ẩn first-person arm/model như yêu cầu.
- Khi đóng view, khôi phục metadata/effect của chính client về trạng thái trước view; không
  ghi đè invisibility effect hoặc metadata khác mà player/plugin đã có từ trước.
- Xác minh riêng first-person hand/arm và F5/third-person local rendering. Không coi việc
  người chơi khác còn nhìn thấy player là bằng chứng đủ rằng local client vẫn ẩn được tay.

### 4. Giữ vị trí và tránh fly-check

- Dùng cơ chế hiện có `hold()` / `release()` trong `Views`: held abilities qua `Pipeline`,
  movement attributes, gravity, jump/step và velocity handling.
- Không bỏ held abilities hoặc gravity handling khi bỏ armor-stand mount. Phần gravity và
  abilities hiện có là một phần bảo vệ fly-check; mount không phải yếu tố duy nhất giữ player
  tại chỗ.
- Chế độ mới phải gọi `hold(player)` trước khi client có thể di chuyển ở vị trí seat; trên mọi
  lối thoát phải gọi `release(player)` và khôi phục attribute/abilities.
- Kiểm tra hit/velocity khi `close-on-damage` false: sát thương phải vào health bình thường.
  Player có thể vẫn được neo tại chỗ trong lúc GUI mở do cơ chế hold; ghi nhận và kiểm thử rõ
  knockback/velocity để không nhầm “nhận damage” với “nhận knockback”.
- Không cancel sát thương PvP trong chế độ mới. Chỉ giữ xử lý riêng hiện có cho fall,
  suffocation và cramming nếu vẫn cần cho mọi chế độ.

### 5. Damage policy

- `views.close-on-damage: true`: giữ hành vi hiện tại; damage event đóng GUI, sau đó player
  nhận damage bình thường.
- `views.close-on-damage: false`: không đóng GUI do damage; damage vẫn áp dụng trong khi GUI
  tiếp tục mở.
- Damage không được dùng làm lý do ẩn player, mount lại player, hoặc teleport họ về một điểm
  khác ngoài cơ chế giữ vị trí đã chọn.
- Vẫn đóng/cleanup view khi chết, disconnect, đổi world hoặc teleport ngoài ý muốn theo các
  lifecycle handler hiện tại.

### 6. Click, hover và đồng bộ pointer

Không viết lại đường packet hiện tại. Giữ nhất quán các bước:

1. `Pipeline.watch` đăng ký listener mouse cho player.
2. `clicked()` ghi nhận click đến trước rotation.
3. `turned()` cập nhật cursor từ yaw/pitch và rotation kế tiếp xác nhận vị trí click.
4. `tickEnded()` xử lý click không có rotation mới khi chuột đứng yên.
5. `atClick()`/`placeClicks()` chạy click ở tọa độ đã chốt; `report()` cập nhật hover.

Không để click chỉ đọc `cursorX/cursorY` ban đầu, vì tâm canvas là trạng thái khởi tạo và sẽ
gây đúng lỗi “pointer di chuyển nhưng click luôn ở giữa”. Kiểm tra cả click khi pointer đang
di chuyển lẫn click khi pointer đứng yên.

## Test plan

### Unit tests

- Settings: kiểm tra default `true` và cấu hình `false` cho cả hai field.
- Damage policy: `close-on-damage=true` đóng view; `false` giữ view và không cancel damage.
- Client-pointer: chế độ `invisible:false` không chọn nhánh server-pointer/vehicle.
- Pointer input: click sau rotation dùng tọa độ ngoài tâm; click khi chuột đứng yên dùng tọa độ
  pointer hiện tại; hover theo rotation mới được cập nhật.
- Cleanup: close/death/quit/teleport khôi phục effect/metadata, attributes, abilities,
  visibility, camera và entity; không để player bị held sau khi đóng.

### Server integration/manual checks

Test trên Paper (và Folia nếu có rig phù hợp), có ít nhất hai player:

- Người mở GUI không thấy arm che GUI; kiểm tra first person, F5 front/back và reopen/close.
- Người quan sát vẫn thấy player đứng bình thường, không thấy player ngồi trên armor stand.
- Observer có thể đánh và tương tác với player; xác nhận health giảm.
- Với `close-on-damage: true`, bị đánh đóng GUI và damage vẫn áp dụng.
- Với `close-on-damage: false`, bị đánh không đóng GUI và damage vẫn áp dụng.
- Di chuyển pointer tới các góc và một control ở ngoài tâm; hover, click trái/phải và scroll
  trúng đúng vị trí.
- Click khi chuột không di chuyển vẫn trúng đúng control.
- Thử mở khi đứng trên đất, trong không khí hợp lệ, cạnh block/entity và dưới tác động knockback.
- Kiểm tra console/server logs trong thời gian đủ dài: không kick `Flying is not enabled`,
  không có teleport lặp theo tick, không có stuck abilities/attributes sau khi thoát GUI.
- Kiểm tra thêm hành vi khi client từ chối/mất resource pack và khi server đóng plugin.

## Baseline test đã biết trước khi implementation

Đã chạy `sh gradlew test` bằng JDK 21 trước khi bắt đầu code:

- 284 tests completed, 1 failed, 1 skipped.
- Failure có sẵn: `OverlayTest.hoverRepaintsOfAnOverlayMatchAFreshPicture`.
- Sai khác 4 pixel tại `(1199, 443)`, `label.slot r-epic`, assertion ở `OverlayTest.java:79`.
- Lần chạy đầu với JDK 25 không vào test do Gradle 8.10.2 báo
  `Unsupported class file major version 69`; JDK 21 chạy được suite.

Sau implementation chạy lại cùng test suite bằng JDK 21. Kết quả chấp nhận được là không có
failure mới ngoài baseline overlay nêu trên; điều tra riêng nếu baseline đó thay đổi hoặc xuất
hiện thêm lỗi.

## Tài liệu cần cập nhật

- `README.md`: giải thích hai chế độ và các tùy chọn damage.
- `NOTES.md`: ghi lại invariant mới về local-only visibility, forced client-pointer và held
  player lifecycle nếu implementation thêm cơ chế packet mới.
- Không cập nhật ảnh render baseline nếu thay đổi không tác động renderer/pages.

## Tiêu chí hoàn thành

- Mặc định (`invisible: true`, `close-on-damage: true`) giữ nguyên hành vi cũ.
- `invisible: false` cho phép người khác thấy/tấn công player, không mount armor stand, local
  GUI không bị arm che, click/hover không lệch và không có fly kick trong các kịch bản test.
- `close-on-damage` chọn đúng giữa đóng GUI và tiếp tục GUI; damage không bị vô hiệu hóa.
- Không có regression về cleanup, reconnect, death, teleport hoặc Folia thread ownership.
- Test suite sau thay đổi không có lỗi mới so với baseline.
