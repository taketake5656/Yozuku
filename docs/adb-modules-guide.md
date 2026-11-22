# ADB モジュール作成ガイド

**日本語** | [English](adb-modules-guide.en.md)

Yozuku の ADB モジュールを作ってテストするための、実践的な手順です。正式な仕様については [ADB モジュール API 仕様](adb-modules-api.md) を参照してください。

## 範囲と制約

- 実行の権限は、動作中の `shizuku_server` の UID を引き継ぎます（ADB なら `2000`、root なら `0`）。
- Magisk / KernelSU のようなマウントのオーバーレイ、`/data/adb/modules` との互換性、常駐デーモンの監視機能はありません。
- 状態は必ず `$MODDIR` からの相対パスで保存してください。

## 最小限のモジュールのひな形

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
description=Short technical description
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

echo "module=$SHIZUKU_MODULE_ID mode=$SHIZUKU_MODULE_MODE"
id
```

ZIP にまとめて、「ADB モジュール」画面（スマートフォンの UI）からインストールします。

```sh
cd my-module && zip -r ../my-module.zip .
```

> 注: 上流の英語の文書の `action.sh` には `require_nightzuku_host` の呼び出しがありませんが、ガードは読み込むだけでは確認を行わないため、テスト用モジュールの `action.sh` に合わせて補っています。

## フォーク対策のガードの組み込み

1. `test-modules/adb-test-module/nightzuku-guard.sh` をモジュールのルートにコピーし、`action.sh` と `service.sh` の先頭で読み込んで、`require_nightzuku_host` を呼び出します。
2. `test-modules/adb-test-module/webui/nightzuku-guard.js` を `webui/` にコピーし、`webui/index.html` で最初に `<script src="nightzuku-guard.js"></script>` を読み込んで、`NightzukuGuard.enforce(...)` を呼び出します。
   ```html
   <script src="nightzuku-guard.js"></script>
   <script>
     NightzukuGuard.enforce({ minApiVersion: 2 });
   </script>
   ```
3. `module.prop` に `requireNightzuku=true` と `minNightzukuApi=1` を書きます。

> 注: 上流の英語の文書には `require_nightzuku_host` と `NightzukuGuard.enforce(...)` の呼び出しが書かれていませんが、テスト用モジュールのコードに合わせて補っています。
>
> **Yozuku での動作**: Yozuku はパッケージ ID とプロトコルを変えていないので、ガードの確認は通ります。モジュールの作者が本家の署名証明書の SHA-256 を指定した場合だけ、署名が違うため実行が止まります（[API 仕様](adb-modules-api.md) の「フォーク対策のホストの証明」）。

## フックの選び方とポリシー

| 目的 | フック | ポリシーによる制限 |
| :--- | :--- | :--- |
| ユーザーが必要なときに実行するコマンド | `action.sh` | すべてのモードで許可 |
| セッションごとの初期化 | `service.sh` | セーフモードではブロック。フル／カスタム＋サービスの権限と、バックグラウンドアクションが必要（または完全信頼） |
| 対話型の設定画面 | `webui/index.html` | モジュールごとに分けられた `https://mod-<hash>.appassets.androidplatform.net/` で配信。シェルのブリッジを使うには `usesShellBridge=true` を指定 |

**完全信頼** は、モジュールごとの例外の設定です（モジュールのカードを長押し）。全体のポリシーのモードによる制限を受けなくなりますが、基本の安全のための制限（エントリー `2048` 個、展開後 `200 MB`、タイムアウト `120 秒`／終了コード `124`、ログはそれぞれ `64 KB` まで保持）はそのまま適用されます。

## デバッグと参考のテスト用モジュール

- 実行のログは `$MODDIR/logs/action-last.log` と `$MODDIR/logs/service-last.log` に保存されます。
- `test-modules/adb-test-module.zip` は、インストール、アクション、サービス、WebUI の ES6 ブリッジ、バナーの表示、`nightzuku-guard` による証明をひととおり確認できる、完全なテスト用モジュールです。

> 注: r63 のコードでは、スクリプトの実行前にモジュールのフォルダがシェル用のフォルダ（`/data/local/tmp/kerneldroid.nightzuku/adb_modules/<id>`）に写され、`$MODDIR` もそこを指します（[API 仕様](adb-modules-api.md) の「実行時の環境」）。実行後、変更はアプリ側のモジュールのフォルダに戻され（`syncBackFromShellRuntime`）、最終実行のログ（`logs/action-last.log`、`logs/service-last.log`）はアプリ側のモジュールのフォルダに保存されます。
