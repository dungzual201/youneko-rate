# Batch cover search status

**CHƯA LÀM.** The single-album MusicHoarders search, streaming result display, validated download, app-private persistence, palette invalidation, Coil invalidation, and 30-second undo path are implemented. Automatic missing-cover search is intentionally disabled because the remaining WorkManager design must enforce at most three concurrent requests, 200 ms spacing between batches, a minimum 1000×1000 auto-apply threshold, progress, pause, cancellation, and explicit user confirmation without uncontrolled bulk downloads.

The Settings screen exposes the disabled state rather than presenting a control that appears to work. No background worker or automatic batch request is started by the application.
