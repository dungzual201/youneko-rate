# Youneko Rate! — Seasonal mascot WIP status

**Updated:** 2026-08-24

## Bảo toàn thành quả

Chỉ thị mới nhất yêu cầu bảo toàn 8 preview đã tạo. Khi kiểm tra workspace thực tế, thư mục nguồn có **12 PNG mascot raster initial**: Xuân 3, Hạ 3, Thu 3 và Đông 3. Để không làm mất thành quả, tất cả 12 ảnh hiện có đã được sao chép vào thư mục này với tên chuẩn `spring-v1.png` … `winter-v3.png`. Bốn ảnh vượt ngoài số lượng 8 được nêu trong chỉ thị không được dùng để mở rộng phạm vi; chúng chỉ được giữ lại như WIP hiện hữu.

| Mùa | Đã lưu | Tên file |
|---|---:|---|
| Xuân | 3 | `spring/spring-v1.png`, `spring/spring-v2.png`, `spring/spring-v3.png` |
| Hạ | 3 | `summer/summer-v1.png`, `summer/summer-v2.png`, `summer/summer-v3.png` |
| Thu | 3 | `autumn/autumn-v1.png`, `autumn/autumn-v2.png`, `autumn/autumn-v3.png` |
| Đông | 3 | `winter/winter-v1.png`, `winter/winter-v2.png`, `winter/winter-v3.png` |

## Thiếu và trạng thái

Không thiếu file nào trong số các PNG đã tồn tại ở workspace. Tuy nhiên đây là các ảnh GPT raster initial, chưa phải asset chính thức: chúng vẫn có thể có nền/biên hoặc chi tiết lệch reference và **chưa được duyệt**. Batch image-editing v2 chưa chạy được vì quota tạo ảnh đã hết; thư mục `variation-v2/` chưa có ảnh hoàn chỉnh.

Paw-mark seasonal **chưa làm**. SVG master **chưa áp màu/geometry mới**. README và `README.vi.md` đã được cập nhật trong bốn commit tài liệu của chỉ thị này, chỉ dùng asset đã có. App, PDF, Google Drive và vector trong app **chưa sửa**.

## Chỉ thị đã chốt cho bước tiếp theo

Các ảnh GPT chỉ dùng để chốt nhanh màu và kiểu phụ kiện; sau khi được duyệt mới áp màu vào SVG master bằng thay đổi fill và vẽ phụ kiện vector. Không dùng ảnh GPT làm Android Vector Drawable.

Mascot phải giữ dáng loaf, lưng cong, đáy phẳng, hai chân trước nhỏ, viền tím than đều `#3E2C4E`, hai mắt oval đen xa nhau, mũi tam giác, miệng omega, ba ria mỗi bên nằm trong mặt, ba vệt tabby ngắn tách rời trên trán, đuôi dày cuộn sát sườn phải với khoang đậm–nhạt, và không có bóng/gradient/texture/3D. Theo chỉ thị mới nhất, **bỏ má hồng** trong các ảnh tạo lại tiếp theo; các PNG hiện có được giữ nguyên để bảo toàn lịch sử và không được coi là bản final.

Phụ kiện đã chốt: Xuân là hoa nhỏ ở mép tai trái; Hạ là nón lá mini hình chóp có vành, không dùng kính; Thu là lá phong nhỏ 5 thuỳ có cuống, không dùng ngôi sao; Đông là khăn quàng nhỏ màu tím `#6750A4`. Mỗi mascot chỉ có một phụ kiện, đặt trên cùng, không cắt viền, không che mắt–mũi–miệng và không quá 20% diện tích đầu.

## Bước tiếp theo

Chờ quota tạo ảnh reset. Khi được phép tiếp tục, tạo lại/hoàn thiện các preview mascot theo đúng chỉ thị mới nhất, trước hết xử lý đủ các biến thể cần duyệt; chưa làm paw-mark và chưa áp bất kỳ thay đổi nào vào SVG, app, PDF hoặc Google Drive cho tới khi người dùng duyệt.
