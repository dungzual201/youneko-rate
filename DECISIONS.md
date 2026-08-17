# Youneko Rate! — Technical Decisions

Tài liệu này ghi lại các quyết định kỹ thuật của dự án, lý do, trạng thái và giai đoạn liên quan. Mọi thay đổi quan trọng phải được bổ sung sau mỗi giai đoạn.

| Mã | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| D-0001 | Chọn Native Android với Kotlin + Jetpack Compose, Material 3. | Đây là stack bắt buộc trong SPEC và phù hợp với yêu cầu decode audio native, FFT, SAF và xử lý nền Android. | Đã quyết định |
| D-0002 | Thiết kế offline-first; chỉ dùng mạng cho metadata và cho phép tắt toàn bộ network. | Điểm, review, phân tích âm thanh và dữ liệu người dùng phải hoạt động trên máy; không analytics, tracker hay telemetry. | Đã quyết định |
| D-0003 | Không dùng `ffmpeg-kit`. | SPEC xác định thư viện đã bị retire từ 01/2025 và repo đã archived; dùng thư viện đã ngừng duy trì là rủi ro bảo trì và tương thích. | Đã quyết định |
| D-0004 | Bọc giải mã sau interface `AudioDecoder`, triển khai hai tầng: Media3/MediaExtractor/MediaCodec trước, FFmpeg prebuilt AAR còn được duy trì hoặc FFmpeg tự build + JNI cho định dạng mở rộng. | Tầng mặc định nhẹ cho các định dạng phổ biến; tầng mở rộng xử lý các định dạng Media3 không hỗ trợ. Interface giúp thay thế và kiểm thử độc lập. | Đã quyết định về kiến trúc; thư viện tầng 2 cần xác minh trước khi triển khai |
| D-0005 | Dùng JTransforms hoặc TarsosDSP cho FFT thuần Java; chỉ cân nhắc KissFFT/JNI nếu benchmark thực tế cho thấy cần thiết. | Tránh phụ thuộc native sớm, giảm độ phức tạp và vẫn đáp ứng FFT streaming trên background thread. | Đã quyết định về hướng; thư viện cuối cùng cần benchmark |
| D-0006 | Dùng SAF thay cho quyền lưu trữ legacy. | Phù hợp Android hiện đại, tôn trọng quyền riêng tư và yêu cầu chọn file/folder bằng URI có persistable permission. | Đã quyết định |
| D-0007 | Dùng UUID cho khóa chính và migration Room tường minh; không dùng `fallbackToDestructiveMigration`. | UUID giúp merge backup giữa thiết bị không xung đột; migration phá hủy sẽ làm mất dữ liệu người dùng, trái nguyên tắc dữ liệu là tối cao. | Đã quyết định |
| D-0008 | MusicBrainz là provider chính, luôn bật; Cover Art Archive đi kèm; provider phụ mặc định tắt. | MusicBrainz dùng dữ liệu CC0/public domain và phù hợp cache offline; provider phụ có điều kiện token, quota hoặc Terms of Use riêng. | Đã quyết định |
| D-0009 | Credits chỉ fetch lazy khi người dùng chủ động bấm; dùng fallback recording/work khi lookup release tổng hợp thất bại hoặc thiếu dữ liệu. | Giảm tải MusicBrainz và xử lý thực tế response lớn/giới hạn tổ hợp `inc`. | Đã quyết định |
| D-0010 | Mọi metadata online nullable và mọi lỗi API phải chuyển thành lỗi thân thiện, không crash và không fake dữ liệu. | API có thể thiếu field, timeout, 404 hoặc 503; dữ liệu giả bị cấm tuyệt đối trong SPEC. | Đã quyết định |
| D-0011 | Rate-limit MusicBrainz bằng token bucket capacity 5, refill 1 token/giây; User-Agent riêng đúng format; retry 503 với exponential backoff + jitter. | Đây là yêu cầu bắt buộc để tôn trọng giới hạn dịch vụ và tránh bị block. | Đã quyết định |
| D-0012 | Chưa chốt thư viện FFmpeg tầng 2, tag parser và FFT cho đến khi xác minh artifact/version còn hoạt động và benchmark trên thiết bị yếu. | Không được bịa dependency/version; cần bằng chứng build/runtime thực tế trước khi khóa lựa chọn. | Chờ xác minh |

## Quy tắc ghi quyết định

Mỗi quyết định mới phải nêu rõ vấn đề, phương án được chọn, lý do, tác động và giai đoạn áp dụng. Nếu thay thư viện so với SPEC, phải ghi tên thư viện thay thế, version đã kiểm chứng, lý do thay và bằng chứng build/test hoặc log lỗi của phương án cũ.
