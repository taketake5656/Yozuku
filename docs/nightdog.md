# NightDog

**日本語** | [English](nightdog.en.md)

NightDog は、Yozuku のサーバープロセス（`shizuku_server`）に組み込まれたウォッチドッグです。メインスレッドの応答と、重要な Android システムサービスとの接続を監視します。異常が起きたらサーバーを終了させ、ネイティブのスターターデーモンがきれいに再起動できるようにします。

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

## 有効化と無効化

NightDog は、スマートフォン、Wear OS、Android TV のいずれでも、**設定 → 試験運用機能 → NightDog ウォッチドッグ** で切り替えます。

- `NightDogClient` は、Binder IPC（`BINDER_TRANSACTION_setNightDogEnabled` / `BINDER_TRANSACTION_getNightDogEnabled`）で `ShizukuService` とやりとりします。
- サーバー側では、`ShizukuConfigManager` が `nightdog_enabled` の変更をすぐに `/data/user_de/0/com.android.shell/shizuku.json` に書き込みます。
- サーバーが動いていないときにマネージャーで切り替えた場合、`NightDogClient` は設定をアプリ内に保存しておき、サーバーの binder を受け取った時点で自動的に同期します。

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

## 関連ファイル

- `nightdog/src/main/java/rikka/shizuku/nightdog/NightDog.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuService.kt`
- `server/src/main/java/rikka/shizuku/server/ShizukuConfigManager.kt`
- `server/src/main/java/rikka/shizuku/server/BinderSender.kt`
- `rust/starter/src/main.rs`
- `manager/src/main/java/moe/shizuku/manager/nightdog/NightDogClient.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/LabFeaturesActivity.kt`
- `manager/src/main/java/moe/shizuku/manager/settings/TvLabMenuScreen.kt`
