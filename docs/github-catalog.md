# GitHub カタログ

**日本語** | [English](github-catalog.en.md)

Yozuku の ADB モジュールをオンラインで探すための仕組みです。

## 仕組み

モジュールは GitHub Topics の検索（`topic:adb-modules`）で見つけます。モジュールの作者は、自分のリポジトリにこのトピックを付けます。中央の登録所は必要ありません。

**流れ:**
1. 初回起動時に、ユーザーが GitHub の PAT（パーソナルアクセストークン）を入力します
2. アプリが GitHub を検索します: `GET /search/repositories?q=topic:adb-modules`
3. 各リポジトリを検証します（ルートまたはサブディレクトリに `module.prop` があるかを確認）
4. モジュールをローカルにキャッシュします（有効期間 6 時間）
5. ユーザーがカタログを閲覧し、インストール方法（ソースまたはリリース ZIP）を選択します

## モジュール数の制限

- 1 ユーザーあたり最大 4 モジュール（そのユーザーの全リポジトリの合計）
- 公式のオーナー（`kerneldroid`）は制限の対象外です
- 制限を超えたユーザーのリポジトリは、検索時にスキップされます

## インストール方法

**ソースから:**
- GitHub Contents API からファイルを取得します
- 既定のブランチを動的に判定します（ハードコードしません）
- 必要なファイルだけで ZIP を作成します: module.prop、*.sh、banner.*、webui/**（全ファイル）
- 既存の `AdbModuleManager.install()` でインストールします

**リリース ZIP から:**
- GitHub Releases からアセットをダウンロードします
- モジュール ID のパターンでアセットを照合します
- そのままインストールします

## セキュリティ

- トークンは AndroidKeyStore（`AES/GCM/NoPadding`）で暗号化して保存されます。以前の暗号化されていないトークンは、起動時に移行されます
- トークンは `ghp_`／`github_pat_` で始まり 30 文字以上かを検証します
- トークンは logcat に記録されません
- トークンは `api.github.com`／`github.com` のドメインにのみ送信されます（updateJson の URL には漏れません）

## 対応プラットフォーム

| プラットフォーム | カタログ UI | トークン設定 | 試験運用機能メニュー |
|----------|-----------|----------------|----------|
| スマートフォン | CatalogScreen.kt | UpdateSettingsScreen.kt | SettingsActivity から |
| Android TV | なし（モジュールの画面は削除） | なし | TvLabMenuScreen.kt |
| WearOS | なし（モジュールの画面は削除） | なし | WearSettingsScreen から |

## API の利用

- 認証なし: 60 リクエスト／時（制限あり）
- PAT あり: 5,000 リクエスト／時
- すべての API 呼び出しでレート制限を追跡します
- 可能な場合は ETag による条件付きリクエストを使用します（304 はカウントされません）

## ファイル構成

```
module/discovery/
├── ModuleDiscoveryManager.kt   # 全体の制御: 検索 → 検証 → キャッシュ
├── ModuleValidator.kt          # module.prop の検証とサブディレクトリの走査
├── DiscoveredModule.kt         # 見つかったモジュールのデータクラス
├── GitHubModels.kt             # GitHub API のレスポンスモデル
├── DiscoveryCache.kt           # SharedPreferences のキャッシュ（有効期間 6 時間）
└── RateLimitTracker.kt         # GitHub のレート制限の追跡

module/update/
├── ModuleInstaller.kt          # インストール処理（ソース／リリース）
├── SourceZipBuilder.kt         # ソースファイルから ZIP を作成
├── UpdateChecker.kt            # モジュールのアップデート確認
├── UpdateResult.kt             # アップデート確認結果のモデル
├── UpdateSettingsScreen.kt     # スマートフォン向けのアップデート設定
└── GitHubReleaseModels.kt      # GitHub Release API のモデル

module/catalog/
├── CatalogScreen.kt            # スマートフォン向けカタログ UI
└── TokenStore.kt               # トークンの保存

settings/
└── TvLabMenuScreen.kt          # TV 向け試験運用機能メニュー
```

> 上流の英語の文書（r62）の一覧には `TvCatalogScreen.kt` が残っていますが、このファイルはコードから削除されているため、このページでは外しています。
