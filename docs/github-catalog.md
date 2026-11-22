# GitHub モジュールカタログ

**日本語** | [English](github-catalog.en.md)

GitHub Topics（`topic:adb-modules`）を使って、Yozuku の ADB モジュールをオンラインで探してインストールする、分散型のしくみです。

## 検索とインストールの流れ

1. `GET /search/repositories?q=topic:adb-modules` で検索します（ユーザーのトークン（PAT）で認証することもできます）。
2. `ModuleValidator` が、リポジトリのルートとサブフォルダに正しい `module.prop` があるかを調べます。
3. 検索の結果は、ローカルの `DiscoveryCache` に `6 時間` 保存します。
4. `ModuleInstaller` が、選んだモジュールを `AdbModuleManager.install()` でインストールします。

## 利用の上限

| 制限 | 値 |
| :--- | :--- |
| GitHub のアカウントあたりのモジュールの最大数 | すべてのリポジトリの合計で `4`（公式のオーナー `kerneldroid` は対象外） |
| 認証なしの GitHub API の上限 | `1 時間あたり 60 回` |
| 認証あり（PAT）の GitHub API の上限 | `1 時間あたり 5,000 回` |
| 条件付きのリクエスト | 使える場合は ETag / `304 Not Modified` を使います（`RateLimitTracker`） |
| ローカルのキャッシュの有効期間 | `6 時間` |

## インストール方法

| 方法 | しくみ |
| :--- | :--- |
| **ソースから** | 判定した既定のブランチについて GitHub Contents API に問い合わせ、`SourceZipBuilder` が `module.prop`、`*.sh`、`banner.*`、`webui/**` だけを含む ZIP を作ります |
| **リリース ZIP から** | GitHub Releases API に問い合わせ、モジュール ID のパターンでアセットを選び、そのままインストールします |

## トークンの安全性

- `TokenStore` で、AndroidKeyStore（`AES/GCM/NoPadding`）により暗号化して保存します。以前の暗号化されていないトークンは、起動時に移行します。
- `ghp_` / `github_pat_` で始まり、`30` 文字以上であるかを検証します。
- logcat には書き出しません。
- `api.github.com` と `github.com` にだけ送ります（サードパーティの `updateJson` の URL には送りません）。

## プラットフォームごとの対応

| プラットフォーム | カタログの画面 | アップデートとトークンの設定 | 試験運用機能 |
| :--- | :--- | :--- | :--- |
| スマートフォン / タブレット | `CatalogScreen.kt` | `UpdateSettingsScreen.kt` | `LabFeaturesActivity.kt` |
| Android TV | 対象外 | 対象外 | `TvLabMenuScreen.kt` |
| Wear OS | 対象外 | 対象外 | `LabFeaturesActivity.kt`（Wear 用のレイアウト） |

## ソースの構成

| フォルダ | 主なファイル |
| :--- | :--- |
| `manager/.../module/discovery/` | `ModuleDiscoveryManager.kt`、`ModuleValidator.kt`、`DiscoveredModule.kt`、`GitHubModels.kt`、`DiscoveryCache.kt`、`RateLimitTracker.kt` |
| `manager/.../module/update/` | `ModuleInstaller.kt`、`ModuleUpdateScheduler.kt`、`SourceZipBuilder.kt`、`UpdateChecker.kt`、`UpdateResult.kt`、`UpdateSettingsScreen.kt`、`GitHubReleaseModels.kt` |
| `manager/.../module/catalog/` | `CatalogScreen.kt`、`TokenStore.kt` |
| `manager/.../settings/` | `LabFeaturesActivity.kt`、`TvLabMenuScreen.kt` |
