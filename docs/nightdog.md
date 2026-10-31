# NightDog とバックグラウンドでのモジュールのアップデートのしくみ

**日本語** | [English](nightdog.en.md)

Yozuku は、バックグラウンドでの監視を 2 つの部品に分けています。特権のあるプロセスの監視が、特権のいらないネットワークの確認で手間を取られないようにするためです。

1. **NightDog（`NightDog` と `NightDogClient`）**: 特権のあるサーバーのウォッチドッグです。インストール済みのモジュールの整合性の確認、`service.sh` の管理、特権が必要なアップデートのインストールも受け持ちます。
2. **`ModuleUpdateScheduler`（`JobScheduler` / `ModuleUpdateJobService`）**: ADB モジュールのアップデートを、バックグラウンドで確認します。OS が実行の時期を決め、ネットワークの条件も守ります。

## `NightDogClient` の中で確認せず、`JobScheduler` と NightDog に分けた理由

- **特権の分離**: GitHub Releases や `module.prop` を HTTPS で確認するのに、ADB や root の特権はいりません。一方で、ネットワークの状態（従量制でない Wi-Fi かモバイル通信か、Doze 中にまとめて実行されるか）には左右されます。
  - そこで `ModuleUpdateScheduler` は、定期的な確認を Android の `JobScheduler`（`ModuleUpdateJobService`、ジョブ ID `4200`）に任せています。
  - `JobScheduler` は `NETWORK_TYPE_UNMETERED` / `NETWORK_TYPE_ANY` の条件を守ります。アプリのプロセスが終了しても予約は残ります。
  - 外部のライブラリ（`androidx.work` や `Room`）もいりません。
- **特権が必要な処理の受け渡し**: アップデートが見つかり、「モジュールを自動でアップデート」が有効なときは、`ModuleUpdateScheduler` が確認済みの対象を `NightDogClient.applyUpdates()` に渡します。
  - `shizuku_server` が動いているとき（`Shizuku.pingBinder()`）は、`NightDogClient` が各モジュールをバックアップしてインストールし、`service.sh` を root / ADB の権限で実行し直します。
  - バックグラウンドのジョブの実行中にサーバーが止まっているときは、保留中のアップデートとして記録しておきます。`OnBinderReceivedListener` が呼ばれたら、すぐに自動で適用します。

## しくみ

### 1. サーバーのウォッチドッグ（`shizuku_server` 内の `NightDog`）

- **メインスレッドのハートビート監視**:
  - 有効にすると、`ShizukuService` がメインルーパースレッドで 5 秒ごとに `NightDog.beat()` を送ります。
  - `NightDog.start()` は開始時にすぐ基準のタイムスタンプを記録します。そのため、最初の定期ハートビートより前に固まった場合も検出できます。
  - 専用のデーモン `HandlerThread`（`nightdog`）が、設定されたタイムアウト（`60s`）に対してハートビートの新しさを確認します。
    - 次の確認は `min(pollInterval, remainingTimeout)` 後に予約します。
    - そのため、停止はタイムアウトのちょうどその時点で検出されます（最大でタイムアウトの `2x` 待つことはありません）。
  - 止まったサーバープロセスを終了させる（`Process.killProcess(Process.myPid())`）前に、メインルーパースレッドのスタックトレースをログに出します。
- **システムサービスの停止と復旧の監視**:
  - 4 つの重要なシステムサービス（`package`、`activity`、`user`、`appops`）と、任意で `permissionmgr` に `IBinder.linkToDeath` を登録します。
  - 予備として `pingBinder()` による確認も行います。再接続の前には、古くなった死亡通知の登録を解除します。
  - 停止したサービスへの再接続は、指数バックオフ（`5s`、`10s`、`20s`、`40s`、`60s`、最大 10 回）で再試行します。新しい binder が生きていること（`pingBinder()`）を確かめてから受け入れます。
  - サービスが復旧すると（`NightDog.Listener.onServiceRecovered`）、`ShizukuService` は次のことを行います。
    - キャッシュしていた `Android17Compat` のプロキシを破棄する
    - `BinderSender` の UID・プロセスのオブザーバーを登録し直す
    - binder をマネージャーとクライアントアプリに送り直す
  - 10 回試しても重要なサービスが復旧しない場合は、`shizuku_server` を終了させます。ネイティブの `starter` の監視プロセスが、サーバーをきれいに起動し直します。

### 2. 特権のあるモジュールの管理（`NightDogClient`）

- `ShizukuApplication` で、`OnBinderReceivedListener` と `OnBinderDeadListener` をアプリ全体に登録します。
  - **binder が停止したとき**: `AdbModuleManager` の service の実行済みの記録をリセットします。そのため、サーバーが起動し直すと `service.sh` が自動でもう一度実行されます。
  - **binder を受け取ったとき**:
    1. `BINDER_TRANSACTION_getNightDogStatus`（`10005`）で、サーバーのウォッチドッグの状態（接続中のシステムサービス、再接続の合計回数、ハートビートの回数、稼働時間）を取得します。
    2. `adb_modules` に残った古い `.installing` の作業ディレクトリを削除します。
    3. バックグラウンドアクション（`allowBackgroundActions`）が許可されていれば、有効なモジュールの `service.sh` を実行します（`AdbModuleManager.runEnabledServicesIfAllowed`）。
    4. インストール済みのモジュールの整合性を調べます（`AdbModuleManager.inspectModuleHealth`）。対象は次のとおりです。
       - `module.prop`
       - `.sh` の実行権限
       - `action.sh` と `service.sh` のサイズ
       - `logs/service-last.log` に記録された 0 以外の終了コード
       - WebUI の `index.html`
    5. 保留中の自動アップデートを適用します（`applyPendingAutoUpdatesIfReady`）。アップデートを確認する頻度が `ON_START` なら、`ModuleUpdateScheduler` でアップデートを確認します。
- アプリのプロセスが動いている間は、バックグラウンドでの確認が有効なら、15 分ごとにも手順 2〜4 の確認を行います（アップデートの確認は含みません）。

### 3. バックグラウンドでのモジュールのアップデートの確認（`ModuleUpdateScheduler` と `ModuleUpdateJobService`）

- アップデートの設定が変わるたびに、OS の `JobInfo` の予約を合わせ直します（`ModuleUpdateScheduler.syncSchedule()`）。
  - **頻度**: 次の 5 つから選びます。
    - `MANUAL`（手動。既定）
    - `ON_START`（`shizuku_server` の起動時）
    - `SIX_HOURS`（6 時間ごと）
    - `DAILY`（毎日）
    - `WEEKLY`（毎週）

    `JobScheduler` に予約するのは `SIX_HOURS` / `DAILY` / `WEEKLY` だけです。
  - **ネットワークの条件**: 「Wi-Fi / 従量制でないネットワークのみ」が有効なら `JobInfo.NETWORK_TYPE_UNMETERED`、無効なら `JobInfo.NETWORK_TYPE_ANY` を指定します。すぐに実行する場合や手動の場合は、`ConnectivityManager` でも直接確かめます。
- **モジュールごとのバージョンの固定（「バージョンを固定」）**: 固定したモジュールは、アップデートの確認とまとめてのアップデートの対象から外れます。
- **バックアップと自動での巻き戻し（`AdbModuleManager`）**: アップデートで既存のモジュールのディレクトリを置き換える前に、前のバージョンを `<id>.backup` に移します。
  - `.source.prop` と `disable` の状態は引き継ぎます。
  - 展開や検証に失敗したときは、自動で元に戻します。

## 画面での操作

- **設定 → 試験運用機能**（`LabFeaturesActivity`、`TvLabMenuScreen`）:
  - **NightDog サーバーのウォッチドッグ** の切り替えと、サーバーの最新の状態（`boundServices`、`rebindCount`、`heartbeatCount`、`uptimeSeconds`）の表示だけになりました。
  - モジュールの設定と重なっていたスイッチは、試験運用機能の画面から外されました。
- **設定 → モジュール → アップデートと確認の設定**（`SettingsActivity`、`UpdateSettingsScreen`、`TvSettingsScreen`、`WearSettingsScreen`）では、次の項目をまとめて設定します。
  - バックグラウンドでのモジュールの確認
  - アップデートを確認する頻度（`手動`、`サーバーの起動時`、`6 時間ごと`、`毎日`、`毎週`）
  - 自動アップデート
  - Wi-Fi のみの条件
  - アップデートの通知
  - 既定のインストール方法（`ソース` か `リリース ZIP`）
  - モジュールカタログの切り替え
  - GitHub のトークン
- **ADB モジュールの画面**（`ModulesActivity`、`TvModulesScreen`、`WearModulesScreen`）:
  - 上部の状態のバナーには、次のものがあります。
    - 手動で確認するボタン
    - **すべてアップデート（N 件）** の進み具合のバー
    - バックグラウンドアクションのスイッチ
  - モジュールごとに次のものがあります。
    - 状態のバッジ
    - アップデートのバッジ（`アップデート: vX.Y`）
    - 変更履歴とインストール方法のダイアログ
    - バージョンの固定（「バージョン固定中」）

## 設定値

| パラメーター | 既定値 | 説明 |
|-----------|---------|-------------|
| ハートビートの間隔 | 5 秒 | `shizuku_server` のメインスレッドがハートビートを送る間隔 |
| ハートビートのタイムアウト | 60 秒 | ハートビートがないまま許容する最大時間。超えるとサーバーを終了させる |
| ポーリング間隔 | 60 秒 | 予備の `pingBinder()` による確認の最大間隔 |
| 再接続のバックオフ | 5〜60 秒 | サービスへの再接続に失敗したときの、次の試行までの指数バックオフの待ち時間 |
| 再接続の最大回数 | 10 回 | この回数を超えると、スターターに復旧させるためサーバーを終了させる |
| 安定稼働でのリセット | 60 秒 | スターターの連続クラッシュ回数をリセットする、サーバーの稼働時間のしきい値 |
| アップデートの確認の予約 | `JobScheduler` | ネットワークの条件付きで OS が管理する定期ジョブ（`JOB_ID_MODULE_UPDATE_CHECK = 4200`） |
| アップデートを確認する頻度 | `MANUAL`（手動） | `ON_START`、6 時間、24 時間、168 時間に変更可能 |

> 上流の英語の文書では、頻度の既定値が `DAILY`、ジョブ ID が `40921`、手動の値が `NEVER` と書かれています。このページの値は、コード（`ModuleSettings.kt`、`ModuleUpdateScheduler.kt`）に合わせています。

## 関連ファイル

- `nightdog/src/main/java/rikka/shizuku/nightdog/NightDog.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuService.kt`
- `rust/starter/src/main.rs`
- `manager/src/main/java/moe/shizuku/manager/nightdog/NightDogClient.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/ModuleUpdateScheduler.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/UpdateChecker.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/ModuleInstaller.kt`
- `manager/src/main/java/moe/shizuku/manager/module/update/UpdateSettingsScreen.kt`
- `manager/src/main/java/moe/shizuku/manager/module/ModuleRuntimeUtils.kt`
