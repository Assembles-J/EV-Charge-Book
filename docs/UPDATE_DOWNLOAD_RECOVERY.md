# Update download recovery

The Android updater treats DownloadManager as the source of truth for an update APK once download starts.

Expected behavior:

1. Persist the DownloadManager id and immutable update metadata immediately after enqueue.
2. If the app process is recreated while the task is pending/running/paused, resume observing the same task instead of enqueueing another download.
3. If the task completed while the app was closed, recover its Uri, re-verify the manifest SHA-256, and surface the install action immediately.
4. Keep the completed task persisted until the app has actually moved to that version. This allows closing the app from the install-ready dialog and still recovering it on the next launch.
5. Missing, failed, corrupt, or already-installed tasks are discarded and normal update discovery resumes.

This recovery is forward-compatible from the first build containing the persisted DownloadManager metadata. Downloads created by older builds did not persist their DownloadManager id and therefore cannot be reconstructed reliably after process death.
