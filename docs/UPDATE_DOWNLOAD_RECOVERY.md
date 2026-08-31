# Update download recovery

The Android updater treats DownloadManager as the source of truth for an update APK once download starts.

Expected behavior:

1. Persist the DownloadManager id and immutable update metadata immediately after enqueue.
2. If the app process is recreated while the task is pending/running/paused, resume observing the same task instead of enqueueing another download.
3. If the task completed while the app was closed, recover its Uri, re-verify the manifest SHA-256, and surface the install action immediately.
4. Install-ready state is durable: if Activity/Compose state is recreated while the verified APK still exists, restore the install action again rather than suppressing it with in-memory prompt deduplication.
5. Keep the completed task and current APK persisted until the app has actually moved to that version.
6. Once the installed app version reaches or exceeds the downloaded version, remove the tracked DownloadManager task and its APK.
7. Clean stale updater-owned `ev-charge-book-*.apk` files from the app's private external Downloads directory on startup/retry while preserving the currently active package.
8. Missing, failed, corrupt, or unrecoverable tasks are discarded, their updater-owned APK leftovers are cleaned, and normal update discovery resumes.
9. Cleanup is intentionally scoped to updater-owned filenames inside the app-specific directory; unrelated files and the system Downloads directory are not touched.

This recovery is forward-compatible from the first build containing the persisted DownloadManager metadata. Downloads created by older builds did not persist their DownloadManager id and therefore cannot be reconstructed reliably after process death. Their app-private updater APK leftovers are treated as stale and cleaned automatically.
