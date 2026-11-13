# Wear OS 互換性（Android 15〜17 / Wear OS 5〜7）

**日本語** | [English](wearos-compatibility.en.md)

このドキュメントでは、Yozuku のモダンフォークで導入された Wear OS 互換性の改善について説明します。

## 対応バージョン
* Wear OS 5.1（Android 15）
* Wear OS 6（Android 16）
* Wear OS 6.1（Android 16 / API 36.1）
* Wear OS 7（Android 17 / API 37）- **完全対応**

## 後方互換性
Yozuku は古い Wear OS バージョン（API 25 まで）との完全な後方互換性を維持しています。
* **動的なチェック:** サーバーは、非公開のパッケージ API と権限 API を `Android17Compat` で解決します。実行中のプラットフォームが実際に公開しているシグネチャを順に試し、成功したものをキャッシュするため、複数デバイスに対応した権限メソッドは、それが存在する環境でのみ使われます。
* **安定した UI:** Compose Material 3 の UI は古い端末でも問題なく縮退して動作するため、高度な Monet の切り替え効果など最新のプラットフォーム機能がなくても、機能的に使える状態が保たれます。


## 実装した変更

### 1. ウォッチ端末へのインストール
`AndroidManifest.xml` に、`android.hardware.type.watch` のハードウェア機能フラグ（`required="false"`）を追加しました。
これにより、Android のパッケージマネージャーと Google Play が、この APK を Wear OS のスマートウォッチにインストール可能だと正しく判断できます。

### 2. ネイティブな Wear Compose Material 3 UI
Wear OS 向けのユーザーインターフェースは、公式の `androidx.wear.compose:compose-material3` ライブラリで全面的に書き直しました。
* **TransformingLazyColumn:** すべてのリスト（許可済みアプリ、ADB モジュール、設定）で、円形ディスプレイに自動で適応するウォッチ専用コンポーネントを使用しています。画面の端にスクロールするにつれて、要素が縮小・湾曲します。
* **Edge-to-Edge コンポーネント:** 円形の画面形状に沿った、Wear OS ネイティブの Material 3 ボタン、カード、スイッチを使用しています。
* **ダイナミックカラー（Monet）:** Wear OS 4 以降の端末でダイナミックカラー（Monet）に完全対応しており、ユーザーのウォッチフェイスやシステムテーマに自動で色を合わせます。
* **最適化されたレイアウト:** 小さく高密度な円形画面に合わせて、適切な余白とタップ領域でネイティブに表示されます。

### 3. ネイティブな Wear Compose Material 3 ダイアログ
従来のダイアログは、ネイティブの `androidx.wear.compose.material3.AlertDialog` に置き換えました。
* **見た目の一貫性:** ダイアログは真っ黒な背景で、見た目の一貫性を保っています。
* **スクロール対応:** ダイアログの内容はスクロールできるため、円形のディスプレイでも読みやすくなっています。
* **プラットフォーム標準の UI:** Wear OS ネイティブの Material 3 ボタンとレイアウトを使用しています。

### 4. WearOS での WebView
WearOS では、ADB モジュール（その WebUI を含む）を提供していません。モジュールの画面、WebView のホスト、エンジンはスマートフォンでは引き続き使えます。Wear 専用の WebView のフォールバックは含めていません。
