# NightDog ウォッチドッグとバックグラウンドでのモジュールの監視

**日本語** | [English](nightdog.en.md)

Yozuku は、特権が必要なプロセスの監視と、特権の要らないネットワークでの確認を、2 つのしくみに分けています。

1. **NightDog（`NightDog` と `NightDogClient`）** — 特権で動くサーバーのウォッチドッグ、システムサービスの復旧、モジュールの整合性の確認、`service.sh` の実行、特権が必要なアップデートの一括適用。
2. **`ModuleUpdateScheduler`（`JobScheduler` / `ModuleUpdateJobService`）** — OS が予約して実行する、ネットワークの条件付きのアップデートの確認（`JOB_ID = 4200`）。ADB や root の権限は不要で、`androidx.work` や `Room` にも依存しません。

## しくみ

### 1. サーバーのウォッチドッグ（`shizuku_server` の中の `NightDog`）

- **ハートビートの監視**: `ShizukuService` は、メインの `Looper` で `5 秒` ごとに `NightDog.beat()` を呼びます。専用のデーモンの `HandlerThread`（`nightdog`）が、`min(ポーリング間隔, タイムアウトまでの残り時間)` ごとに、`60 秒` のタイムアウトに対して新しさを確認します。止まっていた場合は、メインスレッドのスタックトレースを記録し、`Process.killProcess(Process.myPid())` を呼んで、`rust/starter` にサーバーを起動し直させます。
- **システムサービスの復旧**: `package`、`activity`、`user`、`appops`、（あれば）`permissionmgr` に `IBinder.linkToDeath` を登録し、補助として `pingBinder()` による定期的な確認も行います。失敗したサービスは、指数関数的に間隔を延ばしながら（`5 秒` → `60 秒`、最大 `10` 回）接続し直します。
- **復旧時のコールバック（`onServiceRecovered`）**: `ShizukuService` は、`Android17Compat` のキャッシュを無効にし、`BinderSender` のオブザーバーを登録し直し、許可の状態を照合し、有効な `UserAccessPolicy` の範囲で Binder を配り直します。致命的な障害は `ServerSmartLogCollector` に記録されます。

### 2. アプリ側の監視（`NightDogClient`）

`ShizukuApplication` で、全体の `OnBinderReceivedListener` と `OnBinderDeadListener` のコールバックを受け取ります。

- **Binder が切れたとき**: サーバーの再起動後に `service.sh` がもう一度実行されるように、`AdbModuleManager` のセッションごとのサービス実行の制御をリセットします。
- **Binder を受け取ったとき**:
  1. `BINDER_TRANSACTION_getNightDogStatus`（`10005`）で状態を取得します。
  2. `adb_modules` に残った古い `.installing` の一時フォルダを削除します。
  3. ポリシーが許せば、有効な `service.sh` を実行します（`runEnabledServicesIfAllowed`）。
  4. モジュールの状態を確認します（`inspectModuleHealth`: `module.prop`、`.sh` の実行権限、スクリプトのサイズ、`logs/service-last.log` の終了コード、WebUI の `index.html`）。
  5. 待っている自動アップデートを適用するか（`applyPendingAutoUpdatesIfReady`）、確認の頻度が `ON_START` なら `ModuleUpdateScheduler.onServerStarted()` を実行します。

> 注: 上流の英語の文書は 5 の関数を `applyPendingAutoUpdatesIfReady` と `ModuleUpdateScheduler.onServerStarted()` としていますが、r63 のコードにこの 2 つの関数はありません。実際の `NightDogClient` は、Binder を受け取ると `AdbSettings.preProvisionForAutostart()`（自動起動のための ADB の設定の準備）と `flushPendingPrivilegeUpdates()`（サーバーが動いていない間に適用したアップデートについて、保留していた `service.sh` の実行）を呼び、バックグラウンドでの確認が有効なら `runBackgroundCheck(onServerStart = true)`、確認の頻度が `ON_START` なら `ModuleUpdateScheduler.checkUpdates(onServerStart = true)` を呼びます。

### 3. アップデートの予約（`ModuleUpdateScheduler`）

- 設定が変わると、`JobInfo`（`JOB_ID = 4200`）を確認の頻度に合わせて設定し直します: `MANUAL`（既定）、`ON_START`、`EVERY_6_HOURS`、`DAILY`、`WEEKLY`。
- 「Wi-Fi のときだけ」が有効なら `NETWORK_TYPE_UNMETERED`、それ以外は `NETWORK_TYPE_ANY` を条件にします。手動で確認するときは、`ConnectivityManager` でも直接確かめます。
- モジュールごとのバージョンの固定（「バージョンを固定」）を守り、バックアップ・インストール・ロールバックの一括処理は、`NightDogClient.applyUpdates()` を通して `AdbModuleManager` に任せます。

## パラメーターと設定の場所

| パラメーター | 既定値 | 説明 |
| :--- | :--- | :--- |
| ハートビートの間隔 | `5 秒` | `ShizukuService` のメインスレッドのハートビートの間隔 |
| ハートビートのタイムアウト | `60 秒` | プロセスを終了するまでの、止まっているとみなす時間 |
| 補助の確認の間隔 | `60 秒` | `pingBinder()` による確認の最大の間隔 |
| 再接続の待ち時間 | `5 秒〜60 秒` | サービスへの再接続に失敗したときの、指数関数的に延びる待ち時間 |
| 再接続の最大回数 | `10` | この回数を超えると、スターターに起動し直させるためにサーバーを終了します |
| 安定稼働とみなす時間 | `60 秒` | スターターのクラッシュの回数をリセットする稼働時間 |
| アップデートの `JobScheduler` の ID | `4200` | `ModuleUpdateJobService` のジョブ ID |
| アップデートの確認の頻度の既定値 | `MANUAL` | `MANUAL`、`ON_START`、`6 時間`、`24 時間`、`168 時間` |

| 画面 | 内容 |
| :--- | :--- |
| **設定 → 試験運用機能**（`LabFeaturesActivity`、`TvLabMenuScreen`） | NightDog の有効・無効のスイッチと、現在の状態（`boundServices`、`totalRebinds`、`beatCount`、`uptimeMs`） |
| **設定 → モジュール → アップデートと確認の設定**（`UpdateSettingsScreen`、スマートフォンのみ） | 確認の頻度、自動アップデート、Wi-Fi のときだけ確認、通知、既定のインストール方法、カタログの表示、GitHub トークン |
| **ADB モジュール**（`ModulesActivity`、スマートフォンのみ） | 手動での確認、**すべて更新**、バックグラウンドでのアクションのスイッチ、モジュールごとの状態・アップデートのバッジ、バージョンの固定 |

## 主なソースファイル

- `nightdog/src/main/java/rikka/shizuku/nightdog/NightDog.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuService.kt`
- `rust/starter/src/main.rs`
- `manager/src/main/java/moe/shizuku/manager/nightdog/NightDogClient.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/ModuleUpdateScheduler.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/UpdateChecker.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/ModuleInstaller.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/UpdateSettingsScreen.kt`
