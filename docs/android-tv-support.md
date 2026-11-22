# Android TV のアーキテクチャ

**日本語** | [English](android-tv-support.en.md)

`:manager` の Android TV 用 UI の技術的な仕様です。

## UI の構成と端末の種類による切り替え

`EnvironmentUtils.isTV(context)` によって、各 Activity は、スマートフォンのレイアウトを拡大するのではなく、`androidx.tv.material3`（`TvShizukuTheme`）で作った TV 専用のコンポーザブルに切り替わります。

| 画面 | スマートフォンの実装 | Android TV の実装 |
| :--- | :--- | :--- |
| ホーム画面 | `PhoneHomeScreen`（`HomeCards.kt`） | `TVHomeScreen`（`TVHomeScreen.kt`） |
| 許可済みアプリ | `ApplicationManagementActivity` | `TvApplicationManagementScreen.kt` |
| 設定 | `SettingsActivity`（カテゴリに分かれた画面） | `TvSettingsScreen.kt`（十字キーで操作する 2 ペインのレイアウト） |
| 試験運用機能 | `LabFeaturesActivity` | `TvLabMenuScreen.kt` |
| ADB のペア設定と起動 | `PairingGuideScreen`、`StarterActivity` | `TvAdbPairingTutorialScreen.kt`、`TvStarterScreen.kt` |
| シェルの使い方 | `ShellGuideScreen.kt` | `TvShellTutorialScreen.kt` |
| ADB モジュールとカタログ | `ModulesActivity`、`CatalogScreen` | 対象外（スマートフォンのみ） |

## テーマと操作

| 機能 | 仕様 |
| :--- | :--- |
| **ダイナミックカラー（Monet）** | Android 12 以降（API 31 以降）で `dynamicDarkColorScheme` / `dynamicLightColorScheme` を使います。設定で切り替えられます |
| **黒のナイトテーマ** | OLED や暗い部屋での視聴向けに、背景のサーフェスを真っ黒（`#000000`）にします |
| **十字キーでの操作** | `TvSurface` のコントロール間で明示的にフォーカスを移動し、コンテナの強調表示と、標準の「戻る」キーの動作に対応します |
| **ダイアログ** | `TvMaterialTheme` のテーマを適用した中央寄せの `AlertDialog`。内容は十字キーでスクロールできます |

## パッケージ ID と確認

- パッケージ ID: `kerneldroid.nightzuku`
- エミュレーターでの確認: Android TV 16（`screenshots/tv/`）
