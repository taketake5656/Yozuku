# Yozuku ADB モジュール API

**日本語** | [English](adb-modules-api.en.md)

ADB モジュールは、Yozuku アプリのプライベートストレージにインストールされ、現在動作中の Yozuku サーバーを通じて実行される ZIP パッケージです。Yozuku が ADB で起動されている場合、モジュールのスクリプトは ADB シェルの権限で実行されます。Yozuku が root で起動されている場合は、root 権限で実行されます。

これは root のオーバーレイシステムではありません。アクション、WebUI、サービスフック、そして制御された ADB/root シェルアクセスのための、Yozuku を基盤としたモジュールランナーです。

## パッケージ形式

モジュールは、ZIP のルートに `module.prop` を含む `.zip` ファイルです。

```text
module.zip
├── module.prop
├── banner.png
├── nightzuku-guard.sh       （任意: フォーク対策のシェル用ガード）
├── action.sh
├── service.sh
└── webui/
    ├── nightzuku-guard.js   （任意: フォーク対策の WebUI 用ガード）
    └── index.html
```

すべてのパスは相対パスである必要があります。絶対パスや `..` によるトラバーサルは、インストール時に拒否されます。

## module.prop

必須フィールド:

```properties
id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short description
```

任意フィールド:

```properties
banner=banner.png
webui=webui
action=action.sh
usesShellBridge=true
requireNightzuku=true
minNightzukuApi=1
minNightzukuVersion=13
updateJson=https://example.com/update.json
```

ルール:

- `id` は `[A-Za-z][A-Za-z0-9._-]{1,63}` に一致する必要があります。
- `banner` には `.png`、`.jpg`、`.jpeg`、`.webp` を指定できます。
- `banner` を省略した場合、Yozuku は `banner.png`、`banner.jpg`、`banner.jpeg`、`banner.webp` の順に確認します。
- `webui` を省略した場合、Yozuku は `webroot`、`webui`、`web` の順に確認します。
- WebUI は `<webui>/index.html` が存在する場合にのみ利用できます。
- `action` の既定値は `action.sh` です。
- `service.sh` は自動的に検出されます。
- `requireNightzuku`、`minNightzukuApi`、`minNightzukuVersion` は、インストール時とガードによる実行時の確認で、本物の Nightzuku のホストであることを求める条件です。
  - `minNightzukuApi` はモジュール API のバージョン（現在は `2`）と比べます。
  - `minNightzukuVersion` はアプリの `versionCode` と比べます。
  - この 2 つの条件を満たさないモジュールはインストールできません。
  - `requireNightzuku` は目印です。アプリのコードはこの値を読んでいません。

## インストールとアップデートの動作

インストールの流れ:

1. ユーザーが Android のファイル選択画面でモジュールの ZIP を選ぶか、GitHub のモジュールカタログからインストールします。
2. Yozuku が ZIP をキャッシュにコピーします。読み込む量には上限（`200 MB`）があります。
3. Yozuku が `module.prop` を検証し、`minNightzukuApi` と `minNightzukuVersion` を確認します。
4. Yozuku がステージング用ディレクトリに展開し、安全でない相対パスや、正規化するとモジュールの外を指すパスを拒否します。
5. Yozuku が `.sh` ファイルに実行権限を付け、ホストの証明のための `.nightzuku_env` を作ります。
6. 既存のモジュールをアップデートするときは、`<id>.backup` ディレクトリにバックアップを作り、`.source.prop` と `disable` の状態を引き継ぎます。インストールに失敗したときは自動で元に戻します。

安全上の制限:

- ZIP の最大エントリ数: `2048`。
- 展開後の最大サイズ: `200 MB`。
- メモリ／ログに保持されるスクリプト出力: ストリームごとに最後の `64 KB`。
- スクリプトのタイムアウト: `120 秒`。

## 実行環境

スクリプトは Yozuku サーバーのプロセス生成機能を通じて実行されます（`sh /path/to/module/action.sh` または `sh /path/to/module/service.sh`）。作業ディレクトリはモジュールのディレクトリです。

スクリプトを実行するたびに渡される環境変数:

```sh
MODDIR=/data/user/0/kerneldroid.nightzuku/files/adb_modules/<id>
ASH_STANDALONE=1
SHIZUKU_MODULE_ID=<id>
SHIZUKU_MODULE_MODE=safe|custom|full
SHIZUKU_MODULE_TRUSTED=0|1
SHIZUKU_MODULE_BACKGROUND=0|1
NIGHTZUKU_HOST=1
NIGHTZUKU_HOST_PKG=kerneldroid.nightzuku
NIGHTZUKU_HOST_VERSION=<アプリの versionName>
NIGHTZUKU_HOST_VERSION_CODE=<アプリの versionCode>
NIGHTZUKU_MODULES_API=2
NIGHTZUKU_BINDER_DESCRIPTOR=kerneldroid.nightzuku.INightService
NIGHTZUKU_PROVIDER_AUTHORITY=kerneldroid.nightzuku.nightzuku
NIGHTZUKU_SIGNER_SHA256=<アプリの署名証明書の SHA-256>
NIGHTZUKU_SESSION_TOKEN=<モジュール ID に結び付いた 64 文字の HMAC-SHA256 トークン>
```

モジュール内のファイルには、すべて `MODDIR` を使用してください。`/data/adb/modules` のような root 向けのパスを前提にしないでください。

## フォーク対策のホストの証明（`nightzuku-guard.sh` と `nightzuku-guard.js`）

サードパーティのフォーク（「Shizuku Next」など）には、Nightzuku のモジュールの画面だけをまねて、独自のプロトコル `kerneldroid.nightzuku.INightService`（デュアルワイヤー）、`MODDIR` の分離、WebUI のシェルブリッジをきちんと実装していないものがあります。モジュールの作者は、`nightzuku-guard.sh` と `webui/nightzuku-guard.js` をモジュールに入れておくと、そうした不完全なフォークでの実行を止め、本家の Nightzuku（`https://github.com/kerneldroid/Nightzuku`）を案内するエラーメッセージを表示できます。

> **Yozuku での動作**: Yozuku はパッケージ ID（`kerneldroid.nightzuku`）とプロトコルを変えていないフォークなので、ガードの確認は通ります。ただし、モジュールの作者が本家の署名証明書の SHA-256 を指定している場合（下記の「署名の固定」）は、署名が違うため Yozuku では実行が止まります。

### 何段階かの確認のしくみ

1. **パスとパッケージの確認**: `MODDIR` が `kerneldroid.nightzuku` の中にあること、`NIGHTZUKU_HOST_PKG=kerneldroid.nightzuku`、`NIGHTZUKU_BINDER_DESCRIPTOR=kerneldroid.nightzuku.INightService` であることを確かめます。
2. **署名付きの環境ファイル（`.nightzuku_env`）**: スクリプトを実行する前に、`AdbModuleManager` が `$MODDIR/.nightzuku_env` に、モジュールに結び付いた `NIGHTZUKU_SESSION_TOKEN`（HMAC-SHA256）を書き込みます。
3. **Binder による問い合わせ（`verifyNightzukuHost`）**:
   - **シェル（`nightzuku-guard.sh`）**: 次を呼び出します。
     ```sh
     content call --uri content://kerneldroid.nightzuku.nightzuku \
       --method verifyNightzukuHost \
       --arg "guard-probe"
     ```
     `ShizukuManagerProvider` が HMAC-SHA256 のトークンを計算し、`genuine=true`、`hostPackage=kerneldroid.nightzuku`、`binderDescriptor=kerneldroid.nightzuku.INightService`、`modulesApiVersion`、`attestationToken` を返します。SELinux で `content call` が使えないときは、`NIGHTZUKU_SESSION_TOKEN` と `.nightzuku_env` に書かれたトークンを比べる方法に切り替えます。
   - **WebUI（`nightzuku-guard.js`）**: `window.Nightzuku.verifyHost(nonce)`（`window.Shizuku.verifyHost(nonce)` でも可）を呼び出し、返ってきたホストの情報と 64 桁の 16 進数のトークンを確かめます。対応していないフォークの中で読み込まれたときは、画面全体をふさぐ警告を表示します。
4. **署名の固定（任意）**: 期待するアプリの署名証明書の SHA-256 を指定すると、`NIGHTZUKU_SIGNER_SHA256` と一致しない場合に止まります（シェルは `require_nightzuku_host "<SHA-256>"`、WebUI は `expectedSignerSha256` を指定）。

### `action.sh` と `service.sh` での使い方

ガードを読み込んだあと、`require_nightzuku_host` を呼び出します（読み込むだけでは確認は行われません。テスト用モジュールの `action.sh` と同じ書き方です）。

```sh
#!/system/bin/sh
MODDIR="${MODDIR:-${0%/*}}"
[ -f "$MODDIR/nightzuku-guard.sh" ] && . "$MODDIR/nightzuku-guard.sh"
require_nightzuku_host
```

### `webui/index.html` での使い方

ガードを最初に読み込み、`NightzukuGuard.enforce(...)` を呼び出します（テスト用モジュールの `webui/index.html` と同じ書き方です）。

```html
<script src="nightzuku-guard.js"></script>
<script>
  NightzukuGuard.enforce({ minApiVersion: 2 });
</script>
```

> 上流の英語の文書には `require_nightzuku_host` と `NightzukuGuard.enforce(...)` の呼び出しが書かれていませんが、このページではテスト用モジュールのコードに合わせて補っています。

## アクション

`action.sh` は、モジュールのカードからユーザーが手動で実行するアクションです。

アクションの結果:
- stdout／stderr はダイアログに表示されます。
- 最後の出力はモジュールディレクトリ内の `logs/action-last.log` に書き込まれます。
- タイムアウト（120 秒）時は終了コード `124` を返します。

## サービス

`service.sh` はバックグラウンド用のフックです。

実行ポリシー:
- **セーフモード**: ブロックされます。
- **カスタム／フルアクセスモード**: サービスの権限と「バックグラウンドアクションを許可」が有効な場合（またはモジュールが完全信頼の場合）に許可されます。
- サービススクリプトは Yozuku の binder セッションごとに 1 回実行されます（モジュールのアップデート後やサーバーの再起動後にも自動で実行し直されます）。
- 最後の出力は `logs/service-last.log` に書き込まれます。
- タイムアウト（120 秒）時は終了コード `124` を返します。

## WebUI と JavaScript のブリッジ

WebUI は `webui/index.html` から読み込まれます。`module.prop` に `usesShellBridge=true` があり、ポリシーで許可されている場合、`window.Nightzuku` と `window.Shizuku` の両方が公開されます。

### ブリッジのメソッド

- `window.Nightzuku.getModuleInfo()` — `id`、`name`、`version`、`versionCode`、`author`、`description`、`enabled`、`trusted`、`accessMode`、`hostPackage`、`hostVersionCode`、`wireDescriptor`、`moduleApiVersion`、`hostToken` を含む JSON を返します。
- `window.Nightzuku.verifyHost(nonce)` — `nightzuku-guard.js` が使う、暗号によるホストの証明です。
- `window.Nightzuku.exec(command)` — 既定のタイムアウト（`120 秒`）でシェルのコマンドを実行します。
- `window.Nightzuku.execWithOptions(command, optionsJson)` — `timeoutSeconds`、`cwd`、`stdin`（最大 64 KB）、`env` を指定して実行します。
- `window.Nightzuku.download(url, relativeWebPath)` — HTTPS のファイル（最大 20 MB）をモジュールの WebUI のディレクトリにダウンロードします（`index.html` は上書きできません）。

## バックグラウンドでのアップデート

定期的なアップデートの確認は Android の `JobScheduler`（`ModuleUpdateScheduler` と `ModuleUpdateJobService`）で予約され、特権が必要な自動アップデートと `service.sh` の再実行は `NightDogClient` が受け持ちます。詳しくは [NightDog とバックグラウンドでのモジュールのアップデートのしくみ](nightdog.md) を参照してください。
