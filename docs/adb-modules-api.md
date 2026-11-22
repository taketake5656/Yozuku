# ADB モジュール API 仕様

**日本語** | [English](adb-modules-api.en.md)

ADB モジュールは、Yozuku のアプリ専用のストレージにインストールされ、動作中の `shizuku_server` のプロセス（ADB なら UID `2000`、root なら UID `0`）で実行される ZIP アーカイブです。この実行環境は、管理されたスクリプト／WebUI の実行環境であり、システムを書き換えない root 用のオーバーレイのファイルシステムではありません。

## アーカイブの構成

正しいモジュールのアーカイブは、ZIP のルートに `module.prop` を含みます。絶対パスや `..` を含むパスは、展開のときに拒否されます。

```text
module.zip
├── module.prop
├── banner.png               （任意: .png、.jpg、.jpeg、.webp）
├── nightzuku-guard.sh       （任意: フォーク対策のシェル用ガード）
├── action.sh                （任意: 手動で実行するアクションのスクリプト）
├── service.sh               （任意: バックグラウンド用のフック）
└── webui/                   （任意: webroot/、webui/、web/ のいずれか）
    ├── nightzuku-guard.js   （任意: フォーク対策の WebUI 用ガード）
    └── index.html
```

## `module.prop` の項目

| 項目 | 必須 | 制約・既定値 |
| :--- | :--- | :--- |
| `id` | はい | 正規表現 `[A-Za-z][A-Za-z0-9._-]{1,63}` |
| `name` | はい | 表示名 |
| `version` | はい | 表示用のバージョンの文字列 |
| `versionCode` | はい | アップデートのたびに大きくなる整数 |
| `author` | はい | 作者 |
| `description` | はい | 短い説明 |
| `banner` | いいえ | `.png`／`.jpg`／`.jpeg`／`.webp` への相対パス。`banner.*` は自動で見つけます |
| `webui` | いいえ | `index.html` があるフォルダ。`webroot`、`webui`、`web` は自動で見つけます |
| `action` | いいえ | 既定値: `action.sh` |
| `usesShellBridge` | いいえ | `true` にすると、WebUI に `window.Nightzuku` / `window.Shizuku` を公開します |
| `requireNightzuku` | いいえ | インストール時と実行時のガードの確認で、本物の Nightzuku のホストであることを求めます |
| `minNightzukuApi` | いいえ | `NIGHTZUKU_MODULES_API` の最小値（整数） |
| `minNightzukuVersion` | いいえ | Nightzuku のメジャーバージョン／バージョンの最小値 |
| `updateJson` | いいえ | アップデートの情報の HTTPS の URL |

`service.sh` は、モジュールのルートにあれば自動で見つけます。

## インストールの流れと安全のための制限

1. アーカイブを、バイト数の上限を設けながらキャッシュに読み込みます。
2. `module.prop` を読み取り、`minNightzukuApi` / `minNightzukuVersion` を確認します。
3. 正規化したパスがフォルダの中にあることを確かめながら（`ModuleRuntimeUtils.ensureInside`）、一時フォルダに展開します。
4. `.sh` のファイルに実行権限を付け、ホストの証明の情報 `.nightzuku_env` を作ります。
5. アップデートのときは、既存のフォルダを `<id>.backup` に移し、`.source.prop` と `disable` の状態を引き継ぎます。失敗したときは、まとめて元に戻します。

> 注: コード（`AdbModuleManager.kt`）では、一時フォルダは `.<id>.installing`、バックアップは `.<id>.backup` と、名前の先頭にドットが付きます。

| パラメーター | 上限 |
| :--- | :--- |
| ZIP のエントリーの最大数 | `2048` |
| 展開後の最大サイズ | `200 MB` |
| スクリプトの実行のタイムアウト | `120 秒`（終了コード `124`） |
| 保持する stdout / stderr | それぞれ最後の `64 KB` |
| WebUI の `download()` のファイルの上限 | `20 MB`（HTTPS のみ。`index.html` は上書きできない） |
| WebUI の `execWithOptions` の標準入力の上限 | `64 KB` |

## 実行時の環境

スクリプトは、標準入力から `sh -s <モジュール ID>` に渡され、作業フォルダ（cwd）は `$MODDIR` になります。これにより、`argv` の 128 KB の制限を避けつつ、`$0` を `<モジュール ID>` にしています。常に `$MODDIR` を使い、`/data/adb/modules` を前提にしないでください。

> 注: 実際には `sh -s <モジュール ID>` の形で起動するため、`$0` は `sh`、モジュール ID は `$1` になります（`AdbModuleManager.kt` のコメントにも「`$0 == "sh"`」とあります）。

| 変数 | 値 |
| :--- | :--- |
| `MODDIR` | `/data/user/0/kerneldroid.nightzuku/files/adb_modules/<id>`（注を参照） |
| `ASH_STANDALONE` | `1` |
| `SHIZUKU_MODULE_ID` | モジュールの `id` |
| `SHIZUKU_MODULE_MODE` | `safe` \| `custom` \| `full` |
| `SHIZUKU_MODULE_TRUSTED` | `0` \| `1` |
| `SHIZUKU_MODULE_BACKGROUND` | `0` \| `1` |
| `NIGHTZUKU_HOST` | `1` |
| `NIGHTZUKU_HOST_PKG` | `kerneldroid.nightzuku` |
| `NIGHTZUKU_HOST_VERSION` | アプリの `versionName` |
| `NIGHTZUKU_HOST_VERSION_CODE` | アプリの `versionCode` |
| `NIGHTZUKU_MODULES_API` | `2` |
| `NIGHTZUKU_BINDER_DESCRIPTOR` | `kerneldroid.nightzuku.INightService` |
| `NIGHTZUKU_PROVIDER_AUTHORITY` | `kerneldroid.nightzuku.nightzuku`（注を参照） |
| `NIGHTZUKU_SIGNER_SHA256` | アプリの署名証明書の SHA-256（16 進数） |
| `NIGHTZUKU_SESSION_TOKEN` | モジュールに結び付いた 64 文字の HMAC-SHA256 のトークン |

> 注（コードとの違い）:
>
> - **`MODDIR`**: r63 のコード（`ModuleRuntimeUtils.buildModuleEnvironment`、`syncModuleToShellRuntime`）では、`action.sh` / `service.sh` を実行する前に、モジュールのフォルダを tar でシェル用のフォルダ `/data/local/tmp/kerneldroid.nightzuku/adb_modules/<id>` に写し、`MODDIR` と作業フォルダもそこにします。ADB（UID `2000`、`u:r:shell:s0`）で動くサーバーが、SELinux の制限でアプリの専用フォルダを読めない問題を避けるためです。上流の文書の `/data/user/0/...` は、アプリ側にあるモジュール本体の場所です。
> - **`NIGHTZUKU_PROVIDER_AUTHORITY`**: 上流の英語の文書は `kerneldroid.nightzuku.shizuku` としていますが、コード（`ModuleRuntimeUtils.kt`）が設定する値は `kerneldroid.nightzuku.nightzuku` です（`NightProtocol.PROVIDER_SUFFIX` が `.nightzuku`）。このページはコードに合わせています。どちらのオーソリティも同じプロバイダ（`ShizukuManagerProvider`）を指します。

## 実行のポリシー

| フック | 実行のきっかけ | セーフモード | カスタム／フルアクセスモード | ログファイル |
| :--- | :--- | :--- | :--- | :--- |
| `action.sh` | ユーザーが手動で実行 | 許可 | 許可 | `logs/action-last.log` |
| `service.sh` | Binder のセッションごとに 1 回、およびアップデートの後 | ブロック | サービスの権限と「バックグラウンドアクション」が有効なとき（または完全信頼）に許可 | `logs/service-last.log` |

## フォーク対策のホストの証明（`nightzuku-guard`）

モジュールに `nightzuku-guard.sh` と `webui/nightzuku-guard.js` を入れておくと、`INightService` や完全な WebUI のブリッジを持たない、対応していないフォークでの実行を拒否できます。

1. **身元の確認**: `MODDIR`、`NIGHTZUKU_HOST_PKG`、`NIGHTZUKU_BINDER_DESCRIPTOR` を確かめます。
2. **署名付きの環境ファイル**: `NIGHTZUKU_SESSION_TOKEN` を `$MODDIR/.nightzuku_env` と比べます。
3. **IPC による問い合わせ（`verifyNightzukuHost`）**:
   - シェル: `content call --uri content://kerneldroid.nightzuku.nightzuku --method verifyNightzukuHost --arg "guard-probe"`（SELinux で `content call` が使えないときは、`.nightzuku_env` のトークンでの確認に切り替えます）。
   - WebUI: `window.Nightzuku.verifyHost(nonce)` で、`genuine=true` と 64 桁の 16 進数の HMAC のトークンを確かめます。

> 注: 上流の英語の文書は、シェルの例の URI を `content://kerneldroid.nightzuku.shizuku` としていますが、テスト用モジュールの `nightzuku-guard.sh` が問い合わせるのは `content://kerneldroid.nightzuku.nightzuku`（`NIGHTZUKU_REQUIRED_AUTHORITY`）です。このページはガードのコードに合わせています。
>
> ガードは読み込むだけでは確認を行いません。シェルではガードを読み込んだあとに `require_nightzuku_host` を、WebUI では `NightzukuGuard.enforce(...)` を呼び出してください（テスト用モジュールの `action.sh` と `webui/index.html` と同じ書き方です。使い方は [ADB モジュール ガイド](adb-modules-guide.md) を参照）。
>
> ガードには、期待するアプリの署名証明書の SHA-256 を指定して、`NIGHTZUKU_SIGNER_SHA256` と一致しなければ止める機能もあります（シェルは `require_nightzuku_host "<SHA-256>"`、WebUI は `expectedSignerSha256`）。
>
> **Yozuku での動作**: Yozuku はパッケージ ID（`kerneldroid.nightzuku`）とプロトコルを変えていないフォークなので、ガードの確認は通ります。ただし、モジュールの作者が本家の署名証明書の SHA-256 を指定している場合は、署名が違うため Yozuku では実行が止まります。

## 仮想 HTTPS の WebUI と JavaScript のブリッジ

各モジュールの WebUI は、`ModuleWebAssetServer` によって、モジュールごとに分けられた仮想の HTTPS のオリジンから配信されます。

```text
https://mod-<sha256_prefix>.appassets.androidplatform.net/
```

- **分離**: モジュールごとに別のサブドメインになるので、`localStorage`、`sessionStorage`、`IndexedDB`、`CacheStorage` がモジュールごとに分かれます。WebView から `file://` と `content://` を直接開くことはできません。
- **ES6 モジュールにそのまま対応**: `<script type="module">`、`import`/`export`（`.js`、`.mjs`、`.cjs`）、`fetch()`、Web Worker、`.wasm` が、完全信頼なしで使えます。
- **組み込みの ES6 ブリッジ**: `import { exec, getModuleInfo, verifyHost, download } from "/__nightzuku__/bridge.js";`（`/nightzuku.mjs` でも同じ）。

| ブリッジのメソッド | 説明 |
| :--- | :--- |
| `getModuleInfo()` | JSON の情報（`id`、`version`、`accessMode`、`trusted`、`hostPackage`、`wireDescriptor`、`moduleApiVersion`、`hostToken`）を返します |
| `verifyHost(nonce)` | `nightzuku-guard.js` が使う、署名付きのホストの証明を返します |
| `exec(command)` | 既定のタイムアウト `120 秒` でシェルのコマンドを実行します |
| `execWithOptions(command, optionsJson)` | `timeoutSeconds`、`cwd`、`stdin`（`64 KB` 以下）、`env` を指定して実行します |
| `download(url, relativeWebPath)` | HTTPS のファイル（`20 MB` 以下）を、モジュールの WebUI のフォルダにダウンロードします（`index.html` は上書きできません） |

> 注（コードとの違い）:
>
> - WebUI の配信では、`ModuleRuntimeUtils.isInside` で、正規化したパスが `webRoot` の中にあるかを確かめます。また、モジュールごとのオリジンのほかに、共通のホスト `appassets.androidplatform.net` を明示した URL も互換のために受け付けます。
> - 組み込みの `bridge.js` は `window.Nightzuku`（または `window.Shizuku`）を包んだものなので、ES6 モジュールの読み込み自体に完全信頼は不要でも、`exec` などのヘルパーを使うにはブリッジが公開されている必要があります。ブリッジが公開されていないと、`exec`・`verifyHost`・`download` などはエラーを投げます（`isBridgeAvailable()` で確かめられます）。コード上、ブリッジが公開されるのは、モジュールが有効で、アクセスのポリシーがブリッジを許可し、`usesShellBridge=true`（または完全信頼）で、さらに WebView のインターネットがオフ（または完全信頼）の場合です。
> - コードのブリッジには、表にないメソッド `readText`、`writeText`、`listFiles`、`getRuntimeInfo` もあります。

バックグラウンドでのアップデートの確認（`JobScheduler`）と、特権が必要な自動アップデートについては [NightDog のしくみ](nightdog.md) を参照してください。
