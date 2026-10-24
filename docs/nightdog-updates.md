# NightDog によるバックグラウンドの処理とモジュールのアップデート

**日本語** | [English](nightdog-updates.en.md)

上流では `docs/NIGHTDOG.md`（ロシア語）として追加された文書です。Windows ではファイル名の大文字と小文字を区別しないため [nightdog.md](nightdog.md) と同じ名前になってしまうので、このフォークではファイル名を変えて訳しています。設定の値と既定値は、コード（`ModuleSettings.kt`）に合わせています。

## WorkManager ではなく NightDog を使う理由

バックグラウンドでのモジュールの監視とアップデートには、`androidx.work:work-runtime` ではなく **NightDog**（`shizuku_server` 側の `NightDog` と、マネージャーのプロセスの `NightDogClient`）を選び、機能を広げています。

1. **`shizuku_server` の動作状況と直接結びついている**: モジュールのインストールとアップデートには、特権のある Binder の接続（`uid 0` / `uid 2000`）が生きている必要があります。`WorkManager` はサーバーが止まっていても OS の都合で動き出し、メーカー独自の制限（`App Standby Buckets`、Xiaomi/MIUI、Samsung/One UI、Huawei、Realme）を強く受けます。
2. **余分な負荷がない**: `androidx.work` や `Room`、余分な SQLite のデータベースを作るバックグラウンドの `JobService` を APK に入れる必要がありません。
3. **サーバーの起動時にすぐ動く**: ユーザーの操作や自動起動でサーバーが動き出すと、`NightDogClient` が `onServiceStatusChanged(running = true)` を受け取ります。更新頻度が「サーバーの起動時」なら、その場で確認とアップデートを行います。

## モジュールのアップデートの設定

アップデートの設定はすべて `ModuleSettings` に保存されます。設定は **アップデートと確認の設定**（`UpdateSettingsScreen`）の画面と、モジュールのカード（スマートフォン、Android TV、Wear OS）で変更できます。

- **バックグラウンドで確認する頻度（`UpdateFrequency`）**:
  - `手動`（`manual`、既定）: バックグラウンドでは確認しません。
  - `サーバーの起動時`（`on_start`）: `shizuku_server` の起動時に 1 回と、ボタンを押したときに確認します。
  - `6 時間ごと`（`6h`）、`毎日`（`daily`）、`毎週`（`weekly`）。
- **モジュールを自動でアップデート（`adb_modules_auto_update`、既定はオフ）**:
  - オンにすると、`NightDogClient` が見つかったアップデートを自動でダウンロードしてインストールし、有効なモジュールの `service.sh` を実行し直します。
- **Wi-Fi / 従量制でないネットワークのみ（`adb_modules_update_wifi_only`、既定はオン）**:
  - バックグラウンドで GitHub API にアクセスする前に、`ConnectivityManager` でネットワークを確認します（`NET_CAPABILITY_NOT_METERED` / `TRANSPORT_WIFI` / `TRANSPORT_ETHERNET`）。
- **アップデートの通知（`adb_modules_update_notifications`、既定はオン）**:
  - アップデートが見つかったときや、バックグラウンドで自動アップデートしたときに、`nightzuku_module_updates` チャンネルで通知します。通知をタップすると `ModulesActivity` が開きます。
- **バージョンの固定（モジュールごとにアップデートを止める）**:
  - 「バージョンを固定」ボタンで、モジュールを今のバージョンに固定できます（「バージョン固定中」のチップが表示されます）。固定したモジュールは、バックグラウンドでの自動アップデートと「すべてアップデート」の対象から外れます。
- **まとめてのアップデートとアップデートのダイアログ**:
  - **すべてアップデート（N 件）** ボタンで、アップデートのあるモジュールを進み具合を表示しながらまとめてアップデートします。
  - アップデートのバッジをタップするとダイアログが開き、バージョンの変化（`vCurrent → vLatest`）、変更履歴（GitHub Releases またはコミットから取得）、インストール方法の選択（`既定` / リリース ZIP / ソース）、固定のボタンが表示されます。
- **バックアップと自動での巻き戻し（`AdbModuleManager`）**:
  - モジュールのディレクトリを置き換える前に `.<id>.backup` としてバックアップし、`.source.prop` と `disable` の目印を引き継ぎます。途中で失敗したときは、動いていた版に自動で戻します。
