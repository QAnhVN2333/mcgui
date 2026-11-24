# Báo Cáo Phân Tích Nguyên Nhân & Giải Pháp Kỹ Thuật (Solution)

Tài liệu này phân tích chi tiết nguyên nhân gốc rễ (**Root Cause**) và đề xuất giải pháp (**Solution**) cho toàn bộ các vấn đề được nêu trong [`issues.md`](file:///D:/Code/PluginJava/MCUI/mcgui/issues.md).

> **Trạng thái kiểm chứng:** Phần sai lệch giữa `nearSeat` và tọa độ shader đã được xác nhận trực tiếp từ mã nguồn và xử lý bằng teleport căn chỉnh vô điều kiện khi mở view có client-pointer. Các phân tích về vanilla flight check và client tự đẩy người chơi ở vấn đề 2–3 vẫn là giả thuyết; không áp dụng các patch `allowFlight` hoặc fake block bên dưới cho tới khi tái hiện và xác nhận luồng thực tế.

---

## MỤC LỤC
1. [Vấn đề 1 & 4: Overlay/Blur không hiển thị ổn định trên mặt phẳng (chỉ hiện khi có block cạnh bên hoặc bấm WASD)](#1-vấn-đề-1--4-overlayblur-không-hiển-thị-ổn-định-trên-mặt-phẳng)
2. [Vấn đề 2: Phá block dưới chân khi đang mở view dẫn tới kick "Flying is not enabled on this server"](#2-vấn-đề-2-phá-block-dưới-chân-dẫn-tới-kick-flying)
3. [Vấn đề 3: Người chơi bị setback liên tục gây nhấp nháy GUI ở Fullscreen Mode](#3-vấn-đề-3-người-chơi-bị-setback-liên-tục-gây-nhấp-nháy-gui-fullscreen)
4. [Hướng Dẫn Triển Khai Mã Nguồn (Implementation Guide)](#4-hướng-dẫn-triển-khai-mã-nguồn)

---

## 1. Vấn đề 1 & 4: Overlay/Blur không hiển thị ổn định trên mặt phẳng

### Hiện tượng & Các manh mối quan sát được
- Khi người chơi đứng trên mặt phẳng (không có block cao hơn xung quanh trong bán kính 1 block): Dùng lệnh mở Overlay/Blur thì GUI **không xuất hiện** (hoặc rất hiếm khi tự hiện).
- Nếu có **$\ge 1$ block nằm sát cạnh người chơi**: GUI luôn xuất hiện bình thường ngay lần đầu.
- Khi đang bị lỗi không hiện, nếu người chơi **nhấn các phím di chuyển `W/A/S/D`**: Sau khi server điều chỉnh lại vị trí, GUI lập tức xuất hiện ổn định.
- Trace mạng xác nhận: Các packet map và signature đã gửi thành công (`all written`), client đã phản hồi (`client confirmed`), nhưng **màn hình client vẫn là thế giới bình thường, không có panel hay blur**.

---

### Phân tích nguyên nhân gốc rễ (Root Cause)

Nguyên nhân xuất phát từ sự **khập khiễng tai hại giữa dung sai vị trí của Server (`SEAT_TOLERANCE`) và dung sai vị trí của GLSL Shader**:

#### 1. Thuật toán tìm điểm đặt mắt trong `overlayEye(player)`:
Trong [`Views.java`](file:///D:/Code/PluginJava/MCUI/mcgui/src/main/java/ai/resourcepack/ui/framework/world/Views.java#L782-L802):
- Để shader nhận diện được Overlay, tọa độ camera của người chơi trên cả 3 trục (X, Y, Z) bắt buộc phải là phân số:
  $$\text{Tọa độ} = \frac{k}{16} + \frac{3}{128} \quad (\text{bước nhảy } \text{FLAG\_STEP} = 0.0625)$$
- Hàm `flagged(v)` tìm 2 giá trị cờ gần nhất (một giá trị bên dưới và một giá trị bên trên). Khoảng cách từ bất kỳ tọa độ thực tế nào đến điểm cờ gần nhất **luôn luôn $\le \frac{\text{FLAG\_STEP}}{2} = \frac{1}{32} = 0.03125$ block**.
- Hàm `overlayEye()` duyệt qua 8 tổ hợp tọa độ $(x, y, z)$ xung quanh người chơi và chọn ra điểm `best` có khoảng cách Euclid nhỏ nhất không bị va chạm với block.

#### 2. Lỗi logic bỏ qua Teleport trong `take(p, v)`:
Trong [`Views.java` dòng 1082](file:///D:/Code/PluginJava/MCUI/mcgui/src/main/java/ai/resourcepack/ui/framework/world/Views.java#L1082-L1086):
```java
boolean teleport = !nearSeat(p.getLocation(), v.seat);
v.awaitingTeleport = teleport;
hold(p);
p.setGliding(false);
if (teleport) nms.teleportKeepingRotation(p, v.seat.getX(), v.seat.getY(), v.seat.getZ());
```
Hàm `nearSeat` kiểm tra:
```java
private static boolean nearSeat(Location at, Location seat) {
    return at.getWorld() == seat.getWorld()
            && Math.abs(at.getX() - seat.getX()) <= SEAT_TOLERANCE
            && Math.abs(at.getY() - seat.getY()) <= SEAT_TOLERANCE
            && Math.abs(at.getZ() - seat.getZ()) <= SEAT_TOLERANCE;
}
```
Với `SEAT_TOLERANCE = 1.0 / 32.0` ($0.03125$).

> [!CRITICAL]
> **Điểm mâu thuẫn cốt tử:**
> - `overlayEye` được thiết kế để tìm điểm cờ gần nhất, nên trên một mặt phẳng không có chướng ngại vật, khoảng cách từ người chơi đến `seat` trên cả 3 trục **luôn luôn $\le 1/32$ block**!
> - Do đó, `nearSeat(p.getLocation(), v.seat)` **luôn trả về `TRUE`**!
> - Biến `teleport` trở thành **`FALSE`**!
> - **Server HOÀN TOÀN KHÔNG GỬI GÓI TIN TELEPORT** đến client khi mở view! Người chơi vẫn đứng ở tọa độ thực tế ban đầu (ví dụ lệch $0.01$ hay $0.02$ block so với `v.seat`).

#### 3. Phía GLSL Shader phản ứng thế nào?
Trong [`Shaders.java` dòng 357](file:///D:/Code/PluginJava/MCUI/mcgui/src/main/java/ai/resourcepack/ui/framework/pack/Shaders.java#L357):
```glsl
float mcb_flag(float v) { return abs(fract(v * 16.0) - 0.375) < 0.002 ? 1.0 : 0.0; }
```
- Dung sai cho phép của shader đối với tọa độ $v$ là:
  $$\text{Dung sai Shader} = \frac{0.002}{16.0} = 0.000125 \text{ block!}$$
- Trong khi đó, `SEAT_TOLERANCE` của Server là $0.03125$ block (gấp **250 lần** mức dung sai mà shader cho phép!).
- Vì Server không gửi teleport, camera của client lệch khoảng $0.01 \sim 0.02$ block so với cờ. Shader tính toán `mcb_flag(f.x)` hoặc `mcb_flag(f.y)` ra `0.0`.
- Hàm `mcb_flag_axis()` trả về `-1` (Không nhận diện Seated/Overlay). Toàn bộ chuỗi post-effect và shader kéo giãn bản đồ **bị vô hiệu hóa hoàn toàn**. Client render thế giới như bình thường, GUI không xuất hiện!

#### 4. Giải mã 2 hiện tượng bí ẩn:
- **Tại sao có block bên cạnh thì GUI lại hiện bình thường?**
  Khi có block bên cạnh, các điểm cờ gần nhất bị va chạm với block đó, ép `overlayEye` phải chọn một điểm cờ xa hơn ở phía ngược lại (khoảng cách $> 1/32$). Lúc này `nearSeat` trả về `false`, Server **có gửi** packet teleport $\rightarrow$ Client được đặt đúng tọa độ chuẩn của shader $\rightarrow$ GUI hiện ngay!
- **Tại sao nhấn phím `W/A/S/D` thì GUI hiện?**
  Khi nhấn `W/A/S/D`, client gửi packet di chuyển, làm vị trí lệch thêm chút ít vượt qua ngưỡng `SEAT_TOLERANCE`. Trong tick kế tiếp, hàm `keepStill(p, v)` phát hiện `!nearSeat(at, v.seat)` và thực hiện:
  ```java
  nms.teleportKeepingRotation(p, v.seat.getX(), v.seat.getY(), v.seat.getZ());
  ```
  Lệnh teleport này cưỡng chế đưa camera client về đúng tọa độ `v.seat` (chuẩn đến $0.000001$). Shader lập tức nhận diện được cờ và bật GUI lên!

---

### Giải pháp đề xuất (Solution cho Vấn đề 1 & 4)

Trong hàm `take(Player p, ViewImpl v)` của `Views.java`:
**KHÔNG ĐƯỢC DÙNG `nearSeat` để quyết định có teleport lúc mở view hay không!** Khi mở view, Server **luôn luôn bắt buộc phải gửi packet teleport** để căn chỉnh camera client vào đúng tọa độ của shader với độ chính xác tuyệt đối.

Hàm `nearSeat` chỉ dùng trong `keepStill()` ở các tick tiếp theo để tránh spam teleport khi người chơi đang đứng yên tại chỗ.

```java
// SỬA TẠI Views.java -> take():
private void take(Player p, ViewImpl v) {
    v.stage = 1;
    v.keepBy = Platform.tick() + KEEP_TIMEOUT;
    v.settleBy = Platform.tick() + SETTLE_TIMEOUT;
    
    // SỬA: Luôn luôn teleport khi mở view để đảm bảo camera client khớp chính xác với cờ của shader
    boolean teleport = true; // Không dùng !nearSeat(p.getLocation(), v.seat) ở đây
    v.awaitingTeleport = teleport;
    hold(p);
    p.setGliding(false);
    nms.teleportKeepingRotation(p, v.seat.getX(), v.seat.getY(), v.seat.getZ());
    
    signature(p, v);
    v.wall.showSignature(p);
    p.showEntity(plugin, v.glow);
    ...
```

---

## 2. Vấn đề 2: Phá block dưới chân dẫn tới kick "Flying is not enabled on this server"

### Hiện tượng & Các manh mối quan sát được
- Khi một người chơi đang mở view (fullscreen hoặc overlay), nếu người khác phá block dưới chân họ (hoặc chính viewer phá):
  - Người chơi bị giữ lơ lửng trong không trung.
  - Khi đóng view, người chơi ngay lập tức bị server kick với lý do: `Flying is not enabled on this server`.
- Lỗi này xuất phát từ cơ chế chống bay mặc định của Minecraft Vanilla (`ServerGamePacketListenerImpl`), không phải từ plugin anti-cheat bên thứ ba.

---

### Phân tích nguyên nhân gốc rễ (Root Cause)

#### 1. Cơ chế kiểm tra bay của Minecraft Vanilla (`ServerGamePacketListenerImpl`):
Trong mã nguồn Minecraft Server:
```java
if (!this.server.isFlightAllowed() && !this.player.getAbilities().mayfly) {
    // Nếu người chơi ở trên không (onGround == false) và không rơi tự do (deltaY >= -0.03125):
    if (d7 >= -0.03125D && !this.player.isInWater() && !this.player.isInLava()) {
        if (this.aboveGroundTickCount++ > this.getMaximumFlyingTicks(this.player)) {
            this.disconnect(Component.translatable("multiplayer.disconnect.flying"));
            return;
        }
    } else {
        this.aboveGroundTickCount = 0;
    }
}
```
Và hàm tính ngưỡng thời gian bay:
```java
private int getMaximumFlyingTicks(Entity entity) {
    double d0 = entity.getGravity();
    if (d0 < 0.00001D) {
        return Integer.MAX_VALUE;
    } else {
        double d1 = 0.08D / d0;
        return (int)Math.ceil(80.0D * Math.max(d1, 1.0D));
    }
}
```

#### 2. Plugin đã làm gì và kẽ hở xuất hiện ở đâu?
- Khi mở view, plugin gọi `hold(player)`:
  - Gửi packet giả lập cho Client rằng player đang bay (`abilities.flying = true`, `flying_speed = 0`).
  - Trên Server: Gamemode đặt thành `ADVENTURE`. Gán `AttributeModifier` đưa `Attribute.GRAVITY` về `0`.
  - **LỖ HỔNG LỚN:** Trên Server, `player.getAllowFlight()` và `abilities.mayfly` **VẪN LÀ `FALSE`**! Plugin chưa từng bật allow-flight ở phía server.
- **Khi block dưới chân bị phá:**
  - Hộp va chạm của người chơi không còn tiếp xúc với block nào $\rightarrow$ `onGround = false`.
  - Do `GRAVITY = 0`, người chơi không bị rơi $\rightarrow$ `deltaY = 0 >= -0.03125`.
  - Server kích hoạt nhánh đếm: `this.aboveGroundTickCount++` mỗi tick!
  - Trong lúc view đang mở: Vì `entity.getGravity() < 0.00001`, hàm `getMaximumFlyingTicks()` trả về `Integer.MAX_VALUE`. Do đó người chơi không bị kick *trong lúc đang xem view*.
  - **TUY NHIÊN:** Biến `aboveGroundTickCount` **vẫn âm thầm tích lũy liên tục** qua từng tick (ví dụ: mở view 5 giây = 100 ticks, mở 10 giây = 200 ticks)!

#### 3. Giây phút đóng view (`Views.close`):
1. Hàm `restore(player, ...)` đổi Gamemode về `SURVIVAL`.
2. Hàm `release(player)` gỡ bỏ `AttributeModifier` trọng lực:
   - `Attribute.GRAVITY` lập tức khôi phục về giá trị mặc định ($0.08$).
   - Ngưỡng kiểm tra bay `getMaximumFlyingTicks()` **tụt ngay lập tức từ `Integer.MAX_VALUE` xuống 80 ticks (4 giây)**!
3. Player được teleport về `v.returnTo` (vị trí cũ vốn cũng đã bị mất block sàn).
4. `Platform.teleport()` **KHÔNG HỀ RESET BIẾN `aboveGroundTickCount`** trong NMS!
5. Ở tick tiếp theo, client gửi packet di chuyển `ServerboundMovePlayerPacket`:
   - `aboveGroundTickCount` hiện tại là $200$.
   - Ngưỡng cho phép hiện tại là $80$.
   - Điều kiện `200 > 80` thỏa mãn ngay lập tức $\rightarrow$ **KICK NGAY: `Flying is not enabled on this server`!**

---

### Giải pháp đề xuất (Solution cho Vấn đề 2)

> Đây là giả thuyết cần kiểm chứng trên phiên bản server mục tiêu. Việc bật `allowFlight` phải lưu/khôi phục đúng trạng thái ban đầu qua mọi đường đóng view và khôi phục sau crash; không nên áp dụng đoạn mã minh họa như một patch hoàn chỉnh trước khi xác nhận cơ chế đếm flying.

#### Giải pháp 1 (Chuẩn Bukkit API nhất & Toàn diện nhất - Khuyên dùng):
Bật cờ bay hợp lệ trên Server bằng Bukkit API trong suốt thời gian người chơi ở trong view:
1. Trong `ViewImpl`: Lưu lại trạng thái bay ban đầu:
   ```java
   final boolean previousAllowFlight = player.getAllowFlight();
   ```
2. Trong `Views.hold(Player player)`:
   ```java
   player.setAllowFlight(true);
   ```
   Khi `player.getAllowFlight() == true` (tương ứng `abilities.mayfly = true` trên server), bộ kiểm tra bay của Minecraft Vanilla sẽ **hoàn toàn bỏ qua kiểm tra**, biến `aboveGroundTickCount` luôn được giữ ở mức `0`!
3. Trong `Views.release(Player player)`:
   Khôi phục lại cờ bay ban đầu:
   ```java
   player.setAllowFlight(v.previousAllowFlight);
   ```
4. Xử lý an toàn khi đóng view nếu block dưới chân đã bị phá:
   Trước khi teleport về `v.returnTo`, kiểm tra nếu block tại chân `v.returnTo` là `AIR` và người chơi sẽ rơi:
   - Vì `aboveGroundTickCount` trong suốt view bằng `0`, người chơi rơi tự do tự nhiên dưới tác động của trọng lực (`deltaY < -0.03125`), server sẽ reset tick đếm và không bao giờ kick!

#### Giải pháp 2 (Bổ trợ - Bảo vệ block dưới chân khi đang mở view, có thể implement tuy nhiên không nên mặc định bật vì người chơi có thể lạm dụng để ngăn người chơi khác đập block):
Mở rộng Listener `BlockBreakEvent` trong `Views.java`:
- Thay vì chỉ kiểm tra nếu người phá block là chính viewer (như code hiện tại), hãy kiểm tra nếu block bị phá nằm ngay dưới chân (`seat` hoặc `returnTo`) của **bất kỳ viewer nào đang mở view**:
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent e) {
    Block broken = e.getBlock();
    for (ViewImpl v : views.values()) {
        Player p = Bukkit.getPlayer(v.player);
        if (p == null || !p.isOnline()) continue;
        
        Block underSeat = v.seat != null ? v.seat.getBlock().getRelative(BlockFace.DOWN) : null;
        Block underReturn = v.returnTo != null ? v.returnTo.getBlock().getRelative(BlockFace.DOWN) : null;
        
        if (broken.equals(underSeat) || broken.equals(underReturn)) {
            e.setCancelled(true); // Ngăn không cho phá block điểm tựa của người đang xem menu
            return;
        }
    }
}
```

---

## 3. Vấn đề 3: Người chơi bị setback liên tục gây nhấp nháy GUI (Fullscreen)

### Hiện tượng & Các manh mối quan sát được
- Chỉ xảy ra ở chế độ **Fullscreen** (với client-placed pointer).
- Khi người chơi đứng ở vị trí mà 1 bên cạnh có khoảng trống/bậc thang/vực (`0xx / 0px / 0xx`):
  - Client liên tục tự ý đẩy người chơi trượt về phía ô trống `0`.
  - Server phát hiện người chơi lệch khỏi `v.seat`, gửi packet teleport setback giật người chơi trở lại.
  - Vòng lặp đẩy $\rightarrow$ giật lùi xảy ra liên tục mỗi tick, khiến vị trí camera rung lắc, shader bật/tắt liên tục làm GUI nhấp nháy điên cuồng.
- Ở chế độ Overlay thì không bị lỗi này.

---

### Phân tích nguyên nhân gốc rễ (Root Cause)

#### 1. Sự khác biệt giữa `clientEye` (Fullscreen) và `overlayEye` (Overlay):
- Trong `overlayEye` (Overlay): Kiểm tra va chạm nghiêm ngặt `Platform.collides(world, box)`. Nếu chân người chơi chạm vào bất kỳ block sàn nào, điểm đó bị loại bỏ. Người chơi trong Overlay luôn lơ lửng cao hơn sàn $0.028$ block, **hoàn toàn không va chạm block sàn**.
- Trong `clientEye` (Fullscreen) ([`Views.java` L745-L751](file:///D:/Code/PluginJava/MCUI/mcgui/src/main/java/ai/resourcepack/ui/framework/world/Views.java#L745-L751)):
  ```java
  double eyeY = y + 0.5, feet = eyeY - eyeHeight;
  double toleratedFloor = allowFloorOverlap ? Math.min(FLOOR_OVERLAP, height / 2) : 0;
  ```
  Với `FLOOR_OVERLAP = 0.2` block!
  Vì `eyeY` bị ép nằm trên lưới `y + 0.5` để khớp với shader (`mcb_grid(f.y)`), trong khi chiều cao mắt người chơi là $1.62$, nên:
  $$\text{feet} = (y + 0.5) - 1.62 = y - 1.12$$
  Nếu khối block sàn nằm từ $y-1$ đến $y$, thì chân người chơi tại $y-1.12$ **bị chìm sâu $0.12$ block vào bên trong khối block sàn**!

#### 2. Cơ chế thoát ngạt của Minecraft Client (`LocalPlayer.moveTowardsClosestSpace`):
Khi chân người chơi bị lún vào trong một khối block đặc:
- Minecraft Client chạy logic `moveTowardsClosestSpace(x, z)` mỗi tick:
  Nó kiểm tra xem người chơi có đang bị ngạt/kẹt trong block hay không (`suffocatesAt`).
- Nếu đứng giữa phòng kín xung quanh đều là block (`xxx / xpx / xxx`): Cả 4 hướng đều bị chặn, client không tìm được khoảng trống nên không đẩy.
- **Nếu có một hướng là ô trống (`0xx / 0px / 0xx`)**:
  Client coi hướng đó là "lối thoát hiểm" và lập tức gán vận tốc đẩy người chơi trôi về hướng ô trống đó!
- Ngay khi người chơi bị đẩy đi, Server chạy `keepStill()`:
  ```java
  if (!nearSeat(at, v.seat)) {
      nms.teleportKeepingRotation(p, v.seat.getX(), v.seat.getY(), v.seat.getZ());
  }
  ```
  Server giật người chơi trở lại `v.seat`.
- Trở lại `v.seat`, chân lại bị lún trong sàn $\rightarrow$ Client lại đẩy $\rightarrow$ Server lại giật lại $\rightarrow$ **Vòng lặp vĩnh cửu gây nhấp nháy GUI**.

---

### Giải pháp đề xuất (Solution cho Vấn đề 3)

Đây là giả thuyết trong `issues.md`, chưa được xác nhận bằng tái hiện hoặc trace client:

#### Phương án cần kiểm chứng: Gửi Fake Block `AIR` dưới chân người chơi ở phía Client
Khi người chơi mở Fullscreen view, Server gửi packet giả lập biến khối block mà chân người chơi đang lún vào thành `AIR` (chỉ trên màn hình của chính người chơi đó).
- **Nếu giả thuyết client đang xử lý va chạm với block sàn là đúng, cách này có thể giúp vì:**
  - Trong mắt client, khối block dưới chân là `AIR` $\rightarrow$ `suffocatesAt(pos)` trả về `false`.
  - Client không thấy mình bị ngạt/lún trong block $\rightarrow$ `moveTowardsClosestSpace` **hoàn toàn không được kích hoạt**!
  - Không có bất kỳ lực đẩy nào sinh ra từ client!
  - Người chơi không bị rơi vì đã có `abilities.flying = true` và `GRAVITY = 0`.
- **Khi đóng view (`release`):**
  Server gửi lại block thực tế của thế giới cho client để khôi phục nguyên trạng.

---

## 4. Hướng Dẫn Triển Khai Mã Nguồn (Implementation Guide)

Dưới đây là các đoạn mã patch cụ thể có thể áp dụng trực tiếp vào [`Views.java`](file:///D:/Code/PluginJava/MCUI/mcgui/src/main/java/ai/resourcepack/ui/framework/world/Views.java):

### Bước 1: Sửa lỗi hiển thị Overlay/Blur (Vấn đề 1 & 4)
Tại hàm `take(Player p, ViewImpl v)` trong `Views.java`:
```java
// SỬA: Thay thế dòng kiểm tra nearSeat bằng việc luôn teleport khi mở overlay
// Cũ: boolean teleport = !nearSeat(p.getLocation(), v.seat);
// Mới:
boolean teleport = true;
v.awaitingTeleport = true;
hold(p);
p.setGliding(false);
nms.teleportKeepingRotation(p, v.seat.getX(), v.seat.getY(), v.seat.getZ());
```

Ở `reveal()` cũng cần căn chỉnh vô điều kiện trước frame đầu tiên cho mọi view dùng client-pointer (`Platform.teleport(p, v.seat)`). `nearSeat` vẫn phù hợp cho `keepStill()` sau khi đã căn chỉnh ban đầu; dung sai đó không thay thế độ chính xác mà shader yêu cầu.

---

### Bước 2: Hướng xử lý đang chờ xác minh cho lỗi kick Flying (Vấn đề 2)

Không áp dụng đoạn mã dưới đây như patch trực tiếp: cần xác nhận counter/check của đúng server version và thiết kế lưu/khôi phục `allowFlight` an toàn cả khi đóng view lẫn khi plugin/server dừng bất thường.

Trong class `ViewImpl` (trong `Views.java`), thêm lưu trữ `previousAllowFlight`:
```java
// Trong ViewImpl:
final boolean previousAllowFlight;

// Trong constructor ViewImpl:
this.previousAllowFlight = player.getAllowFlight();
```

Trong hàm `hold(Player player)` của `Views.java`:
```java
private void hold(Player player) {
    for (Attribute a : HELD) {
        ...
    }
    // THÊM: Cho phép bay trên server để tắt vanilla flight check
    player.setAllowFlight(true);
    pipeline.hold(player, true);
    nms.resendMovement(player);
}
```

Trong hàm `release(Player player)` của `Views.java`:
```java
private void release(Player player) {
    removeModifiers(player);
    pipeline.hold(player, false);
    
    // THÊM: Khôi phục lại trạng thái allow flight ban đầu
    ViewImpl v = views.get(player.getUniqueId());
    if (v != null) {
        player.setAllowFlight(v.previousAllowFlight);
    } else {
        // Fallback an toàn nếu view đã bị remove
        String mode = player.getPersistentDataContainer().get(seatKey, PersistentDataType.STRING);
        if (mode != null && (mode.equals("CREATIVE") || mode.equals("SPECTATOR"))) {
            player.setAllowFlight(true);
        } else {
            player.setAllowFlight(false);
        }
    }
    
    if (player.isOnline()) nms.resendMovement(player);
}
```

Và thêm listener bảo vệ block dưới chân người xem menu:
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent e) {
    Block broken = e.getBlock();
    for (ViewImpl v : views.values()) {
        if (v.seat != null && broken.equals(v.seat.getBlock().getRelative(BlockFace.DOWN))) {
            e.setCancelled(true);
            return;
        }
        if (v.returnTo != null && broken.equals(v.returnTo.getBlock().getRelative(BlockFace.DOWN))) {
            e.setCancelled(true);
            return;
        }
    }
}
```

---

### Bước 3: Hướng xử lý đang chờ tái hiện cho lỗi client đẩy gây nhấp nháy (Vấn đề 3)

Chưa gửi fake block cho client trước khi xác nhận tọa độ block gây va chạm và thời điểm client/server setback. Fake block có thể làm sai hình ảnh hoặc trạng thái block nếu thế giới thay đổi trong lúc view đang mở.
Trong `ViewImpl`, thêm danh sách các khối block giả đã gửi:
```java
final List<Block> fakeAirBlocks = new ArrayList<>();
```

Khi mở view client-pointer (`reveal` hoặc `open`):
```java
// Gửi fake block AIR cho các block va chạm với chân player
if (v.clientPointer && v.seat != null) {
    Location feetLoc = v.seat.clone();
    Block footBlock = feetLoc.getBlock();
    Block belowFoot = footBlock.getRelative(BlockFace.DOWN);
    
    for (Block b : new Block[]{footBlock, belowFoot}) {
        if (b.getType().isSolid()) {
            p.sendBlockChange(b.getLocation(), Material.AIR.createBlockData());
            v.fakeAirBlocks.add(b);
        }
    }
}
```

Khi đóng view (`close`):
```java
// Khôi phục lại các block thực tế từ world
for (Block b : v.fakeAirBlocks) {
    if (player.isOnline()) {
        player.sendBlockChange(b.getLocation(), b.getBlockData());
    }
}
v.fakeAirBlocks.clear();
```

---

## 5. Tổng kết

| Vấn đề | Nguyên nhân gốc rễ | Giải pháp then chốt |
| :--- | :--- | :--- |
| **1. Overlay/Blur không hiện trên mặt phẳng** | Đã xác nhận `nearSeat` có thể coi tọa độ flagged là “đủ gần” dù shader cần tọa độ chính xác. Chưa xác nhận đây là nguyên nhân duy nhất của mọi lần overlay không hiện. | Đã sửa: teleport căn chỉnh vô điều kiện ở `take()` và `reveal()` cho client-pointer. Kiểm chứng lại trên server/client thật. |
| **2. Kick flying khi mất block sàn** | Chưa xác nhận counter/nhánh flight-check của đúng server version hoặc thứ tự teleport/release có gây kick hay không. | Chưa thay đổi hành vi; trước hết thu thập bằng chứng theo checklist dưới đây. |
| **3. Client đẩy trượt gây nhấp nháy GUI** | Chưa xác nhận client đang đẩy do overlap sàn hay server đang setback vì nguyên nhân khác. | Chưa gửi fake block; trước hết xác nhận tọa độ, layout block và nhịp teleport/correction. |

---

## 6. Checklist kiểm chứng và bằng chứng cần gửi

Mục tiêu là xác định **triệu chứng**, **nhánh view thực sự chạy**, và **thứ tự sự kiện** trước khi chọn patch. Hãy gửi kết quả theo từng vấn đề; không cần gửi toàn bộ thư mục server.

### Thông tin chung (gửi một lần)

- Phiên bản Minecraft server đầy đủ và loại server (Spigot/Paper/Folia, kèm build); phiên bản Java.
- Phiên bản client, có dùng ViaVersion/proxy hay mod/shader pack ngoài resource pack của MCGUI không.
- Phiên bản/build JAR MCGUI đang chạy; xác nhận client đã tải và chấp nhận đúng resource pack.
- Tên page và cấu hình liên quan: `views.invisible`, `views.client-pointer`, `debug`, cùng overlay/blur có bật không.
- Nếu có thể, một video ngắn có timestamp cho từng lần tái hiện. Che IP/token; tên người chơi có thể thay bằng `viewer` và `breaker`.
- Bật `debug: true` trong config rồi restart server trước khi thu log. Bật trace view bằng `/mcgui debug <player> trace on`; gửi các dòng từ lúc mở view tới lúc lỗi/đóng view. `client confirmed` chỉ xác nhận trao đổi packet, không chứng minh shader đã vẽ đúng.

### Vấn đề 1: Overlay/Blur không hiện lần đầu

Thử lần lượt tại cùng một vị trí và cùng một page:

1. Nền phẳng, không có block cao hơn sát cạnh; mở view và chờ khoảng 5 giây, không nhấn phím.
2. Đóng/mở lại ở cùng vị trí, lần này nhấn `W` một lần sau khi view mở.
3. Lặp lại với một block sát cạnh người chơi; ghi riêng overlay không blur và overlay có blur nếu cả hai được dùng.

Gửi cho mỗi lần: có panel/blur hay không và thời điểm xuất hiện; chế độ là `kept-overlay`, `live-overlay` hay fullscreen nếu đã biết; dòng OpenTrace từ `all written` đến `client confirmed`; có nhấn phím/teleport nào ngay trước khi hình xuất hiện; video hoặc ảnh trước/sau. Nếu có cách đọc được, kèm tọa độ feet/eye ngay khi mở và sau correction. So sánh bản build trước và sau thay đổi teleport vô điều kiện nếu còn bản trước.

**Bằng chứng xác nhận hướng sửa:** sau thay đổi, tọa độ client được căn đúng seat ngay lần đầu và panel/blur xuất hiện ổn định mà không cần WASD. Nếu map đã xác nhận nhưng shader vẫn không vẽ dù tọa độ đúng, cần tiếp tục điều tra signature, dialect/resource pack hoặc nhánh overlay; không kết luận từ trace packet đơn thuần.

### Vấn đề 2: Kick `Flying is not enabled on this server` khi mất block dưới chân

Chỉ thử trên server test hoặc khu vực an toàn, dùng block có thể đặt lại. Thực hiện riêng từng ca:

1. Viewer đang mở view tự phá block hỗ trợ.
2. Người chơi khác phá block hỗ trợ của viewer.
3. Đối chứng: đóng cùng loại view khi block hỗ trợ vẫn còn.

Gửi: ca nào tái hiện; loại view và `client-pointer`; vị trí block bị phá; có log `[view-underfoot]` nào xuất hiện (gửi nguyên dòng, có thể ẩn tên); thời điểm block bị phá, đóng view, teleport về vị trí cũ và kick; thông báo kick nguyên văn; video nếu được. Ghi rõ người chơi bị kick có rơi xuống sau khi đóng không, và có plugin bay/anti-cheat hoặc plugin thay đổi game mode/flight không.

**Bằng chứng cần để chọn giải pháp:** xác nhận viewer nào đang đứng/được giữ trên block đó; `returnTo` có còn chỗ đỡ không; teleport return thành công hay thất bại; người chơi có bắt đầu rơi trước khi kick không. Chỉ khi xác nhận flight-check vanilla là nguyên nhân mới cân nhắc bật `allowFlight` tạm thời; giải pháp phải khôi phục chính xác quyền bay ban đầu, kể cả sau crash/rejoin. Không hủy `BlockBreakEvent` mặc định vì sẽ cản người chơi phá block của người khác.

### Vấn đề 3: Fullscreen nhấp nháy/setback

Thử fullscreen với client-pointer tại hai layout an toàn: (A) mặt phẳng kín quanh người chơi; (B) có một hướng cạnh bên là khoảng trống/bậc thấp. Giữ nguyên vị trí, page và cấu hình; quay video 5–10 giây khi không nhấn phím, sau đó thử riêng một lần có nhấn WASD.

Gửi: sơ đồ block 3×3 quanh người chơi ở tầng chân và tầng ngay dưới (đánh dấu `P` là người chơi, `#` là block đặc, `.` là trống); loại block dưới chân; phiên bản client; tọa độ vị trí/seat nếu có; video thể hiện nhấp nháy; log debug các dòng `moved off the seat`/`putting ... back` hoặc teleport lặp lại và thời điểm tương ứng. Cho biết lỗi có còn nếu nền là block đầy đủ, không có khoảng trống cạnh bên hay không.

**Bằng chứng cần để chọn giải pháp:** xác nhận client tự lệch trước rồi server teleport về (chu kỳ lặp), và vị trí seat thật sự chồng vào collision shape nào. Nếu không thấy server correction lặp, cần kiểm tra pipeline shader/panel trước khi sửa collision. Chỉ cân nhắc fake block sau khi biết chính xác block/khối nào gây đẩy và có cách khôi phục theo trạng thái thế giới mới nhất khi block thay đổi, view đóng, người chơi thoát hoặc server dừng.

### Mẫu phản hồi

```text
Server/build + Java:
Client/version + proxy/mod/shader pack:
MCGUI build + resource pack accepted:
Page/config:

Issue 1:
- Các bước và kết quả từng lần:
- OpenTrace/debug log:
- Video/ảnh và tọa độ (nếu có):

Issue 2:
- Ca tự phá / người khác phá / đối chứng:
- [view-underfoot] log + thời điểm đóng/kick:
- Thông báo kick nguyên văn:

Issue 3:
- Sơ đồ block 3x3 + block dưới chân:
- Số lần correction/setback và debug log:
- Video/tọa độ (nếu có):
```
