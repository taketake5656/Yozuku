# NightDog background work and module updates

[日本語](nightdog-updates.md) | **English**

Upstream added this document as `docs/NIGHTDOG.md` (in Russian). Windows file names are case-insensitive, so that name collides with `nightdog.md`; this fork renames it and translates it. Setting values and defaults follow the code (`ModuleSettings.kt`).

## Why NightDog instead of WorkManager

Background module monitoring and updates are built on, and extend, **NightDog** (`NightDog` inside `shizuku_server` plus `NightDogClient` in the manager process) rather than `androidx.work:work-runtime`:

1. **Tied directly to the `shizuku_server` lifecycle**: installing and updating modules needs a live privileged Binder connection (`uid 0` / `uid 2000`). `WorkManager` is scheduled by the OS even while the server is stopped, and is heavily throttled by OEM restrictions (`App Standby Buckets`, Xiaomi/MIUI, Samsung/One UI, Huawei, Realme).
2. **No extra overhead**: no need to ship `androidx.work`, `Room`, or background `JobService`s that create extra SQLite databases.
3. **Immediate trigger on server start**: as soon as the user or auto-start brings the server up, `NightDogClient` receives `onServiceStatusChanged(running = true)` and, with the **On server start** frequency, checks and updates right away.

## Configurable, optional module updates

All update settings are stored in `ModuleSettings` and can be changed on the **Update & Check Settings** screen (`UpdateSettingsScreen`) and directly on module cards (Phone / Android TV / Wear OS):

- **Background check frequency (`UpdateFrequency`)**:
  - `Manual` (`manual`, default): no background checks.
  - `On server start` (`on_start`): checks once when `shizuku_server` starts, and on the manual button.
  - `Every 6 hours` (`6h`), `Daily` (`daily`), `Weekly` (`weekly`).
- **Auto-update modules (`adb_modules_auto_update`, default off)**:
  - When enabled, `NightDogClient` downloads and installs found updates automatically and re-runs `service.sh` for enabled modules.
- **Wi-Fi / unmetered network only (`adb_modules_update_wifi_only`, default on)**:
  - Checks `ConnectivityManager` (`NET_CAPABILITY_NOT_METERED` / `TRANSPORT_WIFI` / `TRANSPORT_ETHERNET`) before making background GitHub API requests.
- **Update notifications (`adb_modules_update_notifications`, default on)**:
  - Posts a notification on the `nightzuku_module_updates` channel when updates are found or installed in the background; tapping it opens `ModulesActivity`.
- **Freezing a version (ignore updates per module)**:
  - Any module can be frozen at its current version with **Freeze version** (shows an **Updates frozen** chip). Frozen modules are skipped by background auto-update and by **Update all**.
- **Batch update and the interactive update dialog**:
  - **Update all (N)** updates every module with an available update in one pass, with a progress indicator.
  - Tapping an update badge opens a dialog with the version transition (`vCurrent → vLatest`), the changelog (from GitHub Releases or the commit), the install mode (`Default` / release ZIP / sources), and the freeze button.
- **Atomic backup and auto-rollback (`AdbModuleManager`)**:
  - Before replacing a module directory, a `.<id>.backup` copy is made and `.source.prop` and the `disable` marker are carried over. On any failure the working version is restored automatically.
