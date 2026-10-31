# ADB モジュール ガイド

**日本語** | [English](adb-modules-guide.en.md)

このガイドはモジュールの作者とテスター向けです。API の正確な仕様については
[ADB モジュール API リファレンス](adb-modules-api.md) を参照してください。

## このシステムについて

ADB モジュールは、Yozuku のプライベートストレージにインストールされ、
動作中の Yozuku サーバーを通じて実行される ZIP パッケージです。

- ADB で起動した Yozuku: スクリプトは ADB シェルの権限で実行されます。
- root で起動した Yozuku: スクリプトは root 権限で実行されます。
- セーフモード: 手動のアクションのみ実行できます。
- フルアクセス: より強力なモジュールの動作を許可します。
- バックグラウンドアクション: `service.sh` を実行するには有効にする必要があります。

これは見た目だけのスタブではなく、実際に動作するモジュールランナーです。ZIP のインストール、
メタデータの解析、モジュールファイルの保存、Yozuku を通じたシェルスクリプトの実行、
ローカル WebUI の表示、有効状態の管理、`JobScheduler` によるアップデートの確認、最終実行ログの書き込みを行います。

Magisk/KSU のようなシステムレスオーバーレイの実装ではありません。マウントフックはなく、
`/data/adb/modules` との互換性も保証しません。また、常駐デーモンの監視機能もありません。

## 最小構成のモジュール

次の構成を作成します:

```text
my-module/
├── module.prop
├── nightzuku-guard.sh
├── action.sh
└── webui/
    ├── nightzuku-guard.js
    └── index.html
```

`module.prop`:

```properties
id=my-module
name=My Module
version=1.0
versionCode=1
author=Author
description=Short module description
usesShellBridge=true
requireNightzuku=true
minNightzukuApi=1
minNightzukuVersion=13
```

`action.sh`:

```sh
#!/system/bin/sh
MODDIR="${MODDIR:-${0%/*}}"
[ -f "$MODDIR/nightzuku-guard.sh" ] && . "$MODDIR/nightzuku-guard.sh"
require_nightzuku_host

echo "module=$SHIZUKU_MODULE_ID"
echo "mode=$SHIZUKU_MODULE_MODE"
id
```

パッケージ化します:

```sh
cd my-module
zip -r ../my-module.zip .
```

「ADB モジュール」画面から `my-module.zip` をインストールします。

## 不完全なフォークからモジュールを守る（`nightzuku-guard`）

非公式のフォークが Nightzuku のモジュール管理の画面だけをまねて、独自の実行環境 `kerneldroid.nightzuku.INightService` や WebUI のブリッジをきちんと実装していないと、モジュールが何も言わずに失敗したり、思わぬ動きをしたりします。そのフォークの利用者から、モジュールの作者に誤ったバグ報告が届く原因になります。

対応していないフォークでの実行を止めるには、次のようにします。

1. `test-modules/adb-test-module/nightzuku-guard.sh` をモジュールのルートにコピーし、`action.sh` と `service.sh` の先頭で読み込んで `require_nightzuku_host` を呼び出します。
   ```sh
   MODDIR="${MODDIR:-${0%/*}}"
   [ -f "$MODDIR/nightzuku-guard.sh" ] && . "$MODDIR/nightzuku-guard.sh"
   require_nightzuku_host
   ```
2. `test-modules/adb-test-module/webui/nightzuku-guard.js` を `webui/` フォルダにコピーし、`webui/index.html` で最初に読み込んで `NightzukuGuard.enforce(...)` を呼び出します。
   ```html
   <script src="nightzuku-guard.js"></script>
   <script>
     NightzukuGuard.enforce({ minApiVersion: 2 });
   </script>
   ```
3. `module.prop` に `requireNightzuku=true` と `minNightzukuApi=1` を書きます。

本物の Nightzuku の中で実行されると、`nightzuku-guard` は数ミリ秒で次の 3 つを確かめます。
- `kerneldroid.nightzuku` のパッケージのパス
- `.nightzuku_env` の HMAC トークン
- ContentProvider（`verifyNightzukuHost`）または JavaScript のブリッジへの問い合わせ

非公式のフォークでは、すぐに実行を止めます。そして、使っているアプリが対応していないフォークであること、エラーをモジュールの作者に報告しないでほしいことを、わかりやすく表示します。

> 上流の英語の文書には `require_nightzuku_host` と `NightzukuGuard.enforce(...)` の呼び出しが書かれていませんが、ガードは読み込むだけでは確認を行わないため、テスト用モジュールのコードに合わせて補っています。
>
> **Yozuku での動作**: Yozuku はパッケージ ID とプロトコルを変えていないので、ガードの確認は通ります。モジュールの作者が本家の署名証明書の SHA-256 を指定した場合だけ、署名が違うため実行が止まります（[API リファレンス](adb-modules-api.md) の「署名の固定」）。

## 任意のファイル

バナー:

```text
banner.png
```

WebUI:

```text
webui/index.html
```

バックグラウンド／サービスフック:

```text
service.sh
```

独自のパスは `module.prop` で指定できます:

```properties
banner=assets/banner.webp
webui=webui
usesShellBridge=true
action=scripts/action.sh
```

`window.Nightzuku` や `window.Shizuku` を必要とする WebUI ページでは、`usesShellBridge=true` の指定が必須です。

## スクリプトの実行環境

スクリプトはモジュールのディレクトリで実行されます。次の変数を使用できます:

```sh
MODDIR=/data/user/0/kerneldroid.nightzuku/files/adb_modules/<id>
ASH_STANDALONE=1
SHIZUKU_MODULE_ID=<id>
SHIZUKU_MODULE_MODE=safe|custom|full
SHIZUKU_MODULE_TRUSTED=0|1
SHIZUKU_MODULE_BACKGROUND=0|1
NIGHTZUKU_HOST=1
NIGHTZUKU_HOST_PKG=kerneldroid.nightzuku
NIGHTZUKU_MODULES_API=2
NIGHTZUKU_BINDER_DESCRIPTOR=kerneldroid.nightzuku.INightService
NIGHTZUKU_SESSION_TOKEN=<64 文字の HMAC トークン>
```

Magisk/KSU のパスをハードコードしないでください。`$MODDIR` を使用してください。

## アクションとサービス

ユーザーが操作して実行するコマンドには `action.sh` を使用します。

バックグラウンドでのセットアップには `service.sh` を使用します。次の条件をすべて満たすと実行されます:
- モジュールが有効になっている。
- アクセスモードがフルアクセス、またはサービスを有効にしたカスタムである（またはモジュールが完全信頼になっている）。
- 設定でバックグラウンドアクションが有効になっている。
- Yozuku の binder が利用可能である。

`NightDogClient` は、binder セッションごとに 1 回と、自動アップデートの後に、有効なサービスを自動実行します。

## 完全信頼

完全信頼（Full Trust）はモジュールごとの特別扱いの設定です。モジュールのカードを長押しして切り替えます。信頼されたモジュールは、基本的な安全上の制限（タイムアウト、出力の上限）は守りつつ、全体のポリシーによる制限を受けずに動作します。

## 安全上の制限

- ZIP 内のパストラバーサルは拒否されます。
- 最大エントリ数: `2048`。
- 展開後の最大サイズ: `200 MB`。
- スクリプトのタイムアウト: `120 秒`。
- 保持される出力: stdout／stderr それぞれ最後の `64 KB`。

タイムアウト時の終了コードは `124` です。

## ログ

ログはインストールされたモジュールのディレクトリ内に書き込まれます:

```text
logs/action-last.log
logs/service-last.log
```

各ログには、モジュール ID、スクリプト名、終了コード、アクセスモード、stdout、stderr が含まれます。

## テスト用モジュール

リポジトリには次のファイルが含まれています:

```text
test-modules/adb-test-module.zip
```

インストール、有効化／無効化、アクション、サービス、WebUI、フォーク対策のホストの証明（`nightzuku-guard.sh` と `nightzuku-guard.js`）、バナー表示の動作確認に使用してください。
