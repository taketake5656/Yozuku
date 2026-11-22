# Wear OS 互換性の仕様

**日本語** | [English](wearos-compatibility.en.md)

`:manager` と `:server` での Wear OS への対応を、技術的な観点からまとめたものです。

## 対応するプラットフォーム

| Wear OS のバージョン | Android のベース | API レベル | 状態 |
| :--- | :--- | :--- | :--- |
| 旧来の Wear OS | Android 7.1〜14 | API 25〜34 | `Android17Compat` の動的な呼び分けで後方互換を維持 |
| Wear OS 5.1 | Android 15 | API 35 | 対応 |
| Wear OS 6 / 6.1 | Android 16 | API 36 / 36.1 | 対応 |
| Wear OS 7 | Android 17 | API 37 | 対応（エミュレーターで確認済み） |

## 実装の詳細

| 項目 | 実装 |
| :--- | :--- |
| **マニフェストの宣言** | `<uses-feature android:name="android.hardware.type.watch" android:required="false" />` により、ウォッチの端末に APK を単体でインストールできます |
| **UI のフレームワーク** | ネイティブの `androidx.wear.compose:compose-material3`（`WearShizukuTheme`、`TransformingLazyColumn`、`SurfaceTransformation`、`WearAlertDialog`）。`EnvironmentUtils.isWatch(context)` で切り替えます |
| **画面** | `WearHomeScreen.kt`、`WearApplicationManagementScreen.kt`、`WearSettingsScreen.kt`、`WearStarterScreen.kt`、`LabFeaturesActivity.kt`（Wear 用の分岐） |
| **テーマ** | API 31 以降はダイナミックカラー（`WearDynamicColorScheme`）。OLED 向けに真っ黒（`#000000`）の背景も選べます |
| **ADB モジュールと WebView** | Wear OS では意図的に提供していません。モジュールの管理と WebView の表示はスマートフォンのみです |
