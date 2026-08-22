# Round6 — Covit official integration, Library interaction, palette, layout, and Analyze

## Phạm vi và nguyên tắc

Round6 đã loại bỏ hoàn toàn việc gọi internal endpoint `/api/search` và `/api/info` của MusicHoarders. HTTP 401 được coi là chính sách chặn của site, không phải lỗi cần vượt qua. Không dùng User-Agent/cookie/header giả, proxy hoặc API internal. WebView chỉ dùng remote protocol chính thức với `remote.port=browser`, `postMessage`, và `@JavascriptInterface` duy nhất là `onPick(String)`.

Các thuật toán verdict, STFT, codec, cutoff, scan, backup và việc xử lý file nhạc gốc không bị thay đổi. Không thêm playback, lyrics crawling, destructive Room migration fallback, hoặc ghi tag vào audio gốc.

## 14 commit Round6

1. `259075c` — `fix(library): use one combinedClickable in YnAlbumCard`: bỏ `pointerInput/detectTapGestures` thủ công, dùng một `combinedClickable` có semantics role và log `NAVDEBUG`.
2. `870c48a` — `fix(library): remove outer grid and list pointer handlers`: bỏ các wrapper gesture ở grid/list để child card nhận tap trực tiếp.
3. `e096cce` — `refactor(cover): remove direct musichoarders internal api calls`: xóa `MusicHoardersApi`, test internal API và provider DI; thay màn form bằng placeholder chính thức.
4. `c1b14ab` — `feat(cover): add official remote browser webview bridge`: thêm `covit_bridge.html`, iframe full màn hình, query A1, `postMessage` handler và bridge một hàm.
5. `0605726` — `feat(cover): add custom tab fallback and gallery guidance`: thêm AndroidX Browser Custom Tab, URL fallback và hướng dẫn lưu ảnh rồi nhập từ thư viện.
6. `a65707a` — `feat(cover): open official webview from album menu`: menu Album Detail điều hướng trực tiếp tới route WebView với `launchSingleTop`.
7. `6272359` — `feat(cover): persist official picked cover and invalidate cache`: áp ảnh đã chọn vào app-private files, cập nhật source chính thức và evict Coil cache.
8. `8b12e6d` — `fix(albumdetail): prefer bright palette swatches and log colors`: Palette 24 màu, `clearFilters()`, ưu tiên vibrant/lightVibrant/muted trước fallback tối, log swatch và `CHOSEN`.
9. `f2247cc` — `fix(albumdetail): enforce readable gradient contrast`: tính màu chữ theo contrast ratio, thêm static scrim khi dưới 4.5:1 và log `CONTRAST`.
10. `4202945` — `fix(albumdetail): align static gradient hero height`: tăng hero token lên 420dp cho gradient tĩnh.
11. `8008c58` — `fix(insets): avoid duplicate Album Detail window padding`: neutralize inset thừa ở child Scaffold và log `INSET`.
12. `6c81bc3` — `fix(analyze): move choose file action into content flow`: bỏ FAB nổi, giữ folder icon trên TopAppBar và thêm nút inline cho kết quả.
13. `7477588` — `fix(analyze): standardize FlowRow chip spacing`: chuẩn hóa FlowRow bằng token spacing, cập nhật invariant obsolete và hoàn tất parity string cho cover flow.
14. **Báo cáo Round6 này** — `test: cover pick, Library navigation, palette, contrast, inset, and Analyze evidence`: tài liệu hóa toàn bộ output và các bằng chứng chưa thể thu thập trong sandbox.

> Ghi chú: commit 14 là commit báo cáo cuối; mã hash được ghi trong `git log` sau khi commit hoàn tất.

## Evidence H

### H1 — Không còn internal API

Output sau sửa:

```text
$ grep -rn "api/search\|api/info" app/src/main/java/
# không có output
```

### H2 — Video chọn và áp ảnh bìa

`CHƯA LÀM: sandbox không có điện thoại thật, emulator, WSA hoặc ADB để quay video. Luồng đã được triển khai: Album Detail → menu → WebView chính thức → message `cover/pick` → tải ảnh → app-private `filesDir/covers` → cập nhật album → quay lại detail.`

### H3 — So sánh AlbumCard với Rate item và nguyên nhân lỗi

Output source sau sửa của `YnAlbumCard`:

```text
fun YnAlbumCard(item: LibraryAlbum, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .semantics { role = Role.Button }
            .combinedClickable(
                onClick = {
                    android.util.Log.d("NAVDEBUG", "album click id=${item.album.id}")
                    onClick()
                },
                onLongClick = onLongClick,
            ),
```

Rate item vẫn dùng click trực tiếp:

```text
Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(YnDimens.radiusSm), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
```

Nguyên nhân trước sửa là `YnAlbumCard` dùng `Card(onClick = {})` khi có long-press, đồng thời đặt `pointerInput/detectTapGestures` ở modifier ngoài. Inner Card đã nuốt pointer event nên tap không truyền tới callback thủ công. Sau sửa, cả click và long-press nằm trong một `combinedClickable`, không còn `pointerInput` ở AlbumCard.

### H4 — Video Library grid mở Album Detail

`CHƯA LÀM: không có thiết bị thật/emulator/ADB để thao tác và quay video. Source đã nối callback thật từ `LibraryScreen` → `AlbumCard` → `onOpenAlbum` → route `album/{albumId}`, và log tap bằng tag `NAVDEBUG`.`

### H5 — Sáu swatch và swatch được chọn của 3 album

`CHƯA LÀM: không có dữ liệu album/cover live trong thiết bị để tạo logcat thật cho 3 album. Mã nguồn đã thêm log `PALETTE` gồm `vibrant`, `lightVibrant`, `darkVibrant`, `muted`, `darkMuted`, `dominant`, và `CHOSEN`; Palette chạy sau khi decode bitmap đầy đủ, resize nội bộ 128px và không dùng placeholder.`

### H6 — Contrast ratio của 3 album

`CHƯA LÀM: không có thiết bị và cover thật để đo tại đúng vị trí chữ của 3 album. Mã nguồn đã thêm log `CONTRAST` dạng `album=<id> bg=<hex> text=<hex> ratio=<number>`, dùng foreground có contrast cao hơn và static scrim 30% khi ratio dưới 4.5:1.`

### H7 — Khoảng cách TopAppBar trước/sau

`CHƯA LÀM: không có screenshot hoặc hệ tọa độ thiết bị để đo pixel/dp thực tế. Đã thêm `INSET` log ở Album Detail; child Scaffold dùng `contentWindowInsets = WindowInsets(0)` để không cộng lại inset. Hero gradient token hiện là 420dp.`

### H8 — Analyze empty/result screenshot

`CHƯA LÀM: không có thiết bị thật/emulator/ADB để chụp hai trạng thái. Source hiện bỏ FAB thủ công, giữ folder icon trên TopAppBar, hiển thị `Chọn tệp` trong empty state và `Phân tích bài khác` bằng OutlinedButton ở cuối danh sách; FlowRow không scroll ngang.`

## Output kiểm tra bắt buộc

### Diff commit 1

```diff
+import androidx.compose.foundation.combinedClickable
+import androidx.compose.foundation.ExperimentalFoundationApi
+import androidx.compose.ui.semantics.Role
+import androidx.compose.ui.semantics.role
+@OptIn(ExperimentalFoundationApi::class)
-    val gestureModifier = if (onLongClick == null) { ... pointerInput ... }
-    Card(onClick = if (onLongClick == null) onClick else ({}), modifier = gestureModifier, ...)
+    Card(
+        modifier = modifier
+            .semantics { role = Role.Button }
+            .combinedClickable(
+                onClick = { Log.d("NAVDEBUG", ...); onClick() },
+                onLongClick = onLongClick,
+            ),
+        ...
+    ) {
```

### Diff commit 2

```diff
-LazyVerticalGrid(... modifier = Modifier.weight(1f).pointerInput(Unit) { detectVerticalDragGestures(...) }, ...)
+LazyVerticalGrid(... modifier = Modifier.weight(1f), ...)
-LazyColumn(modifier = Modifier.weight(1f).pointerInput(Unit) { detectVerticalDragGestures(...) }, ...)
+LazyColumn(modifier = Modifier.weight(1f), ...)
```

### Card grep sau sửa

```text
app/src/main/java/com/youneko/rate/ui/musicbrainz/MusicBrainzSearchPanel.kt:187:    androidx.compose.material3.Card(onClick = { onClick(item) }, modifier = Modifier.fillMaxWidth()) {
app/src/main/java/com/youneko/rate/ui/components/YnComponents.kt:245:    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(YnDimens.radiusSm), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
```

`YnAlbumCard` không còn `Card(onClick=...)` mà dùng `combinedClickable`, tránh pattern lồng click.

### Inset grep sau sửa

```text
# grep -rn "statusBarsPadding\|systemBarsPadding" app/src/main/java/
# không có output
```

### Đoạn trước sửa, nguyên văn `sed -n '240,290p'`

```text
            modifier = Modifier.padding(horizontal = YnDimens.space4, vertical = YnDimens.space2),
        )
        when {
            onlineMode -> MusicBrainzSearchPanel(
                viewModel = onlineViewModel,
                onImported = onOpenAlbum,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            state.gridView -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount -> if (dragAmount > 0f) pulled += dragAmount; change.consume() },
                        onDragEnd = { if (pulled >= 120f) refresh() },
                        onDragCancel = { pulled = 0f },
                    )
                },
                contentPadding = PaddingValues(start = YnDimens.space4, end = YnDimens.space4, top = YnDimens.space2, bottom = YnDimens.navigationSafe),
                horizontalArrangement = Arrangement.spacedBy(YnDimens.space3),
                verticalArrangement = Arrangement.spacedBy(YnDimens.space3),
            ) {
                when {
                    state.error != null -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { YounekoErrorState(state.error ?: stringResource(R.string.error_generic), onRetry = viewModel::clearError, modifier = Modifier.fillMaxWidth()) }
                    state.albums.isEmpty() -> item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { EmptyLibrary(onAddAlbum, hasQuery = state.query.isNotBlank() || state.unfinishedOnly) }
                    refreshing -> items(6, key = { "skeleton-$it" }) { YnSkeleton(Modifier.padding(YnDimens.space2)) }
                    else -> items(state.albums, key = { stableAlbumKey(it.album.id) }) { item -> AlbumCard(item, onOpenAlbum, onAnalyzeTrack) }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount -> if (dragAmount > 0f) pulled += dragAmount; change.consume() },
                        onDragEnd = { if (pulled >= 120f) refresh() },
                        onDragCancel = { pulled = 0f },
                    )
                },
```

### Build

Laatste 5 dòng của lệnh:

```text
[Incubating] Problems report is available at: file:///home/ubuntu/youneko-rate-fix/build/reports/problems/problems-report.html

BUILD SUCCESSFUL in 3m 4s
74 actionable tasks: 34 executed, 40 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.0/userguide/configuration_cache_enabling.html
```

APK debug:

```text
/home/ubuntu/youneko-rate-fix/app/build/outputs/apk/debug/app-debug.apk
29779635 bytes
```

Logcat filter tags:

```text
NAVDEBUG
PALETTE
CONTRAST
INSET
```

## Trạng thái git và giới hạn bằng chứng

Full verification đã pass: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, và `compileDebugAndroidTestKotlin`. Unit tests pass sau khi cập nhật invariant cũ để phản ánh FlowRow bắt buộc. Do sandbox không có thiết bị, mọi bằng chứng video, screenshot, logcat thực của cover thật, contrast theo 3 album, và đo khoảng cách trước/sau đều được ghi rõ `CHƯA LÀM`, không suy đoán hoặc giả lập.
