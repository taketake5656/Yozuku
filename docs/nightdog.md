# NightDog とバックグラウンドでのモジュールの確認

**日本語** | [English](nightdog.en.md)

NightDog は、Yozuku のサーバープロセス（`shizuku_server`）に組み込まれたウォッチドッグです。あわせて、Yozuku マネージャーでのバックグラウンドの処理（モジュールの状態の確認、`service.sh` の実行、アップデートの確認）もまとめて受け持ちます。

## 仕組み

1. **メインスレッドのハートビート監視**:
   - 有効にすると、`ShizukuService` がメインルーパースレッドで 5 秒ごとにハートビート（`NightDog.beat()`）を送ります。
   - `NightDog.start()` は開始時にすぐ基準のタイムスタンプを記録します。そのため、最初の定期ハートビートより前に固まった場合も検出できます。
   - 専用のデーモン `HandlerThread`（`nightdog`）が、設定されたタイムアウト（`60s`）に対してハートビートの新しさを確認します。次の確認は `min(pollInterval, remainingTimeout)` 後に予約するので、停止はタイムアウトの時点ですぐに検出されます（最大でタイムアウトの `2x` 待つことはありません）。
   - 停止したサーバープロセスを終了させる（`Process.killProcess(Process.myPid())`）前に、デバッグに役立つよう、メインルーパースレッドのスタックトレースを取得してログに出します。

2. **システムサービスの停止と復旧の監視**:
   - 4 つの重要なシステムサービス（`package`、`activity`、`user`、`appops`）と、任意で `permissionmgr` に `IBinder.linkToDeath` を登録します。
   - 各ヘルスチェックでは、予備として `pingBinder()` による確認も行います。再接続の前には、古くなった死亡通知の登録をきちんと解除します。
   - サービスが停止したり応答しなくなったりすると、サービスごとに保留中の再接続を 1 つにまとめ、指数バックオフ（`5s`、`10s`、`20s`、`40s`、`60s`、最大 10 回）で再試行します。新しい binder が生きていること（`pingBinder()`）を確かめてから受け入れます。
   - サービスが復旧すると（`NightDog.Listener.onServiceRecovered`）、`ShizukuService` は次のことを行います。
     - キャッシュしていた `Android17Compat` のプロキシ（`package` / `permissionmgr`）を破棄する
     - `activity` が再起動した場合は、`BinderSender` のプロセス・UID のオブザーバーを登録し直す
     - binder をマネージャーとクライアントアプリに送り直す
   - 10 回試しても重要なサービスが復旧しない場合は、サーバープロセスを終了させます。スターターデーモンがサーバーを起動し直し、システムサービスの準備ができるのをきちんと待てるようにするためです。

3. **バックグラウンドでのモジュールの確認（`NightDogClient`）**:
   - `NightDogClient` は、`ShizukuApplication` で `OnBinderReceivedListener` と `OnBinderDeadListener` をアプリ全体に登録します。
     - `shizuku_server` の binder が停止すると、`AdbModuleManager` の service の実行済みの記録をリセットします。そのため、サーバーが復旧すると `service.sh` が自動でもう一度実行されます。
     - サーバーの binder を受け取ったとき（起動時、手動での開始、NightDog による復旧）と、バックグラウンドでの確認が有効な間は 15 分ごとに、`runBackgroundCheck()` を実行します。
       1. `BINDER_TRANSACTION_getNightDogStatus`（`10005`）で、サーバーのウォッチドッグの最新の状態（接続中のシステムサービス、再接続の合計回数、ハートビートの回数、稼働時間）を取得します。
       2. `adb_modules` に残った古い `.installing` の作業ディレクトリを削除します。
       3. バックグラウンドアクション（`allowBackgroundActions`）が許可されていれば、有効なモジュールの `service.sh` を実行します（`AdbModuleManager.runEnabledServicesIfAllowed`）。
       4. インストール済みのすべてのモジュールの整合性を調べます（`AdbModuleManager.inspectModuleHealth`）。対象は `module.prop`、スクリプトの実行権限、`action.sh` と `service.sh` のサイズ、`logs/service-last.log` に記録された 0 以外の終了コード、WebUI の `index.html` です。
       5. 設定した間隔（`DAILY` / `WEEKLY`）または手動の操作で、`UpdateChecker` を使ってモジュールのアップデートを確認します。

## 画面での操作

NightDog とバックグラウンドでのモジュールの確認は、スマートフォン、Android TV、Wear OS のいずれでも操作できます。

- **設定 → 試験運用機能**（`LabFeaturesActivity`、`TvLabMenuScreen`）:
  - **NightDog ウォッチドッグ** の切り替え（`BINDER_TRANSACTION_setNightDogEnabled` / `BINDER_TRANSACTION_getNightDogEnabled`）
  - **バックグラウンドでのモジュールの確認（NightDog）** の切り替え（`ModuleSettings.isBackgroundCheckEnabled()`）
  - **バックグラウンドアクションを許可**（`service.sh`）の切り替え（`ModuleSettings.allowBackgroundActions()`）
  - 最新の状態の表示と、手動で確認するボタン（**モジュールとサーバーを今すぐ確認**）
- **設定 → モジュール → アップデートと確認の設定**（`SettingsActivity`、`UpdateSettingsScreen`、`TvSettingsScreen`、`WearSettingsScreen`）:
  - バックグラウンドアクション、バックグラウンドでの確認、アップデートを確認する間隔（`手動`、`毎日`、`毎週`）、既定のインストール方法（`ソースからビルド` か `リリース ZIP をダウンロード`）、GitHub のトークンをすべて設定できます。
- **ADB モジュールの画面**（`ModulesActivity`、`TvModulesScreen`、`WearModulesScreen`）:
  - 上部に NightDog によるモジュールの確認のバナーがあり、すぐに確認するボタンとバックグラウンドアクションのスイッチがあります。
  - モジュールごとに、状態のバッジ（`正常` / スクリプトの終了コードの問題）、バックグラウンドの許可のバッジ、アップデートのバッジ（`アップデート: vX.Y`）が表示され、ワンタップの **アップデート** ボタン（`ModuleInstaller.updateModule()`）があります。

## スターターの復旧動作

`shizuku_server` が起動したとき、または異常終了したとき・NightDog に終了させられたときの動作です。

- ネイティブの `starter`（`rust/starter/src/main.rs`）は `SIGPIPE` と `SIGHUP` を無視します（`SIG_IGN`）。そのため、起動に使った ADB や root のシェルを閉じても、バックグラウンドの監視プロセスが終了したり、パイプの切断で落ちたりしません。起動する `shizuku_server` の子プロセスでは、`execvp` の前に `SIG_DFL` に戻します。
- 新しい監視プロセスを起動する前に、`starter` は古い `shizuku_server` と、残っている以前の `starter` 監視プロセス（`/proc/<pid>/exe` で判定）を終了させます。そのため、監視プロセスが重複して競合することはありません。
- `starter` は切り離したバックグラウンドの監視プロセス（`setsid`、標準入出力は `/dev/null`）を fork し、最初の `shizuku_server` の PID をパイプで受け渡します。前面の starter プロセスはすぐに `info: shizuku_server pid is <pid>` と `info: shizuku_starter exit with 0` を出力して正常終了するので、`StarterActivity` やワイヤレス ADB のセッションを止めることはありません。
- 切り離した監視プロセスは、`waitpid` で `shizuku_server` を待ちます（`EINTR` のときは再試行）。起動や終了の記録を Android の logcat（`Shizuku` タグ）に出し、異常終了したときは 5 秒待ってから `shizuku_server` を起動し直します。
- 短時間にクラッシュを繰り返す場合、連続での再起動は 5 回までです（`max_restarts = 5`）。
- `shizuku_server` が 60 秒以上安定して動くと（`STABLE_UPTIME_RESET_SECS`）、連続クラッシュの回数は `0` に戻ります。そのため、何日も動かし続けても、再試行の上限を使い切らずに NightDog がサーバーを復旧できます。
- 正常終了（`0`）と、マネージャーがアンインストールされたことによる終了（`50` / `MANAGER_APP_NOT_FOUND`）の場合、starter は再起動せずに終了します。

## 設定値

| パラメーター | 既定値 | 説明 |
|-----------|---------|-------------|
| ハートビートの間隔 | 5 秒 | メインスレッドがハートビートを送る間隔 |
| ハートビートのタイムアウト | 60 秒 | ハートビートがないまま許容する最大時間。超えるとプロセスを終了させる |
| ポーリング間隔 | 60 秒 | 予備の `pingBinder()` による確認の最大間隔 |
| 最初の再接続までの待ち時間 | 2 秒 | サービスが停止してから最初に再接続を試すまでの時間 |
| バックオフの範囲 | 5〜60 秒 | 再接続に失敗したときの、次の試行までの指数バックオフの待ち時間 |
| 再接続の最大回数 | 10 回 | この回数を超えると、スターターに復旧させるためサーバーを終了させる |
| 安定稼働でのリセット | 60 秒 | スターターの連続クラッシュ回数をリセットする、サーバーの稼働時間のしきい値 |
| バックグラウンドでのモジュールの確認の間隔 | 15 分 | `NightDogClient` が、モジュールの状態と期限の来たアップデートを定期的に確認する間隔 |

## 関連ファイル

- `nightdog/src/main/java/rikka/shizuku/nightdog/NightDog.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuService.kt`
- `server/src/main/java/rikka/shizuku/server/ServerConstants.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuConfigManager.kt`
- `server/src/main/java/rikka/shizuku/server/BinderSender.kt`
- `rust/starter/src/main.rs`
- `manager/src/main/java/moe/shizuku/manager/nightdog/NightDogClient.kt`
- `manager/src/main/java/moe/shizuku/manager/module/AdbModuleManager.kt`
- `manager/src/main/java/moe/shizuku/manager/module/ModuleSettings.kt`
- `manager/src/main/java/moe/shizuku/manager/module/ModulesActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/UpdateSettingsScreen.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/LabFeaturesActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/TvLabMenuScreen.kt`

関連: [NightDog によるバックグラウンドの処理とモジュールのアップデート](nightdog-updates.md)
