<p align="center">
  <img src="manager/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="Yozuku icon" />
</p>

# Yozuku

**日本語** | [English](README.en.md)

**Yozuku** は、kerneldroid による **Shizuku** のモダンフォーク **Nightzuku** を、日本語化したものです。「Night（夜）」を日本語の「夜（Yo）」に置き換えて、Yozuku という表示名にしています。アプリが昇格した権限（root / ADB）でシステム API を直接利用するための、堅牢で高性能なインターフェースを提供します。

このプロジェクトは、Android 16/17 をターゲットとした安定性の確保など最新の Android プラットフォームの動向に追従しており、Jetpack Compose による刷新された Material 3 Expressive UI と、ADB ベースの ZIP モジュール実行機能を備えています。

> [!NOTE]
> このリポジトリ（[taketake5656/Yozuku](https://github.com/taketake5656/Yozuku)）は [kerneldroid/Nightzuku](https://github.com/kerneldroid/Nightzuku) のフォークで、アプリの日本語化を行っています。アプリの表示名は Yozuku ですが、パッケージ ID は Nightzuku と同じ `kerneldroid.nightzuku` のままです。署名が異なるため、kerneldroid 版の Nightzuku から上書きでインストールすることはできません。先にアンインストールしてください。

> [!IMPORTANT]
> **移行時の対応が必要です:** パッケージ ID の変更（`moe.shizuku.privileged.api` → `kerneldroid.nightzuku`）に伴い、Yozuku をインストールする前に、旧公式 Shizuku Manager アプリを端末から**必ずアンインストール**してください。アンインストールしないと競合が発生します。

アップストリームプロジェクト: <https://github.com/RikkaApps/Shizuku>

## フォークでの追加機能

- Material 3 Expressive コンポーネント、モーション、スイッチ、角丸アイコンを採用した Jetpack Compose 製の管理 UI。
- このフォークにおける、最新のプレビュー SDK／ビルドツールを使った Android 16/17 ターゲット対応。
- ZIP モジュールをインストール・管理するための「ADB モジュール」画面。
- モジュール機能: `module.prop`、バナー、有効／無効スイッチ、`action.sh`、ポリシーで制御される `service.sh`、ローカル WebUI、削除、パスチェック、サイズ制限、出力制限、最終実行ログ。
- モジュールのポリシー設定: セーフモード、フルアクセス、バックグラウンドアクションの制御。
- デバッグ用テストモジュール（`test-modules/adb-test-module.zip`）。

## ドキュメント

- [ADB モジュール ガイド](docs/adb-modules-guide.md)
- [ADB モジュール API リファレンス](docs/adb-modules-api.md)
- [Yozuku コネクタ API](docs/nightzuku-connectors.md)
- [Android 17 互換性](docs/android-17-compatibility.md)
- [Wear OS 互換性](docs/wearos-compatibility.md)
- [Wear OS ペア設定ガイド](docs/wearos-pairing.md)
- [Android TV サポート](docs/android-tv-support.md)
- [NightDog ウォッチドッグ](docs/nightdog.md)
- [GitHub カタログ](docs/github-catalog.md)

## 背景

root が必要なアプリを開発する場合、一般的な方法は `su` シェルでコマンドを実行することです。しかしこの方法は遅く、テキスト処理に頼るため信頼性に欠け、利用できるコマンドにも制限されます。ADB を使う場合でも、特権操作には root が必要になることがよくあります。

Yozuku は、アプリが昇格した権限でシステム API を直接利用できるようにすることで、高性能な代替手段を提供します。

## Yozuku の仕組み

Android では、アプリとシステムサーバー間のプロセス間通信（IPC）に `binder` が使われます。システムサーバーは、クライアントの UID/PID を確認して権限を判定します。

Yozuku は、ユーザーが root または ADB で Yozuku サーバープロセスを起動できるよう案内します。許可されたアプリが起動すると、そのアプリは Yozuku サーバーへの binder を受け取ります。

Yozuku はプロキシとして動作し、アプリからのリクエストを受け取ってシステムサーバーへ転送します。これにより、アプリはサーバーの昇格した権限（root または ADB）でシステム API を利用でき、システム API を直接使うのとほぼ同じ感覚で扱えます。

## スクリーンショット

<details>
  <summary>📸 クリックしてスクリーンショットを表示</summary>
  <br/>

  ### スマートフォン UI
  <table>
    <tr>
      <td align="center"><img src="screenshots/phone/main.png" width="300" /><br/><b>メイン画面</b></td>
      <td align="center"><img src="screenshots/phone/apps.png" width="300" /><br/><b>許可済みアプリ</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/phone/modules.png" width="300" /><br/><b>ADB モジュール</b></td>
      <td align="center"><img src="screenshots/phone/module-webui.png" width="300" /><br/><b>モジュール WebUI</b></td>
    </tr>
    <tr>
      <td colspan="2" align="center"><img src="screenshots/phone/settings.png" width="300" /><br/><b>設定</b></td>
    </tr>
  </table>

  ### Wear OS UI（ネイティブ Material 3）
  <table>
    <tr>
      <td align="center"><img src="screenshots/wearos/wearos_main_scaled.png" width="200" /><br/><b>メイン画面</b></td>
      <td align="center"><img src="screenshots/wearos/wearos_apps.png" width="200" /><br/><b>許可済みアプリ</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/wearos/wearos_modules_scaled.png" width="200" /><br/><b>ADB モジュール</b></td>
      <td align="center"><img src="screenshots/wearos/wearos_settings_scaled.png" width="200" /><br/><b>設定</b></td>
    </tr>
    <tr>
      <td colspan="2" align="center"><img src="screenshots/wearos/wearos_dialog.png" width="200" /><br/><b>テーマダイアログ</b></td>
    </tr>
  </table>

  ### Android TV UI（ネイティブ Material 3）
  <table>
    <tr>
      <td colspan="2" align="center"><img src="screenshots/tv/main.png" width="500" /><br/><b>メイン画面</b></td>
    </tr>
    <tr>
      <td align="center"><img src="screenshots/tv/modules.png" width="400" /><br/><b>ADB モジュール</b></td>
      <td align="center"><img src="screenshots/tv/settings.png" width="400" /><br/><b>設定</b></td>
    </tr>
  </table>
</details>

## 開発者向けガイド

### API とサンプル

公式の API とサンプルは次の場所で公開されています: <https://github.com/RikkaApps/Shizuku-API>

### 技術的な詳細

1. **ADB の権限**: ADB の権限はシステムのバージョンによって異なります。利用可能な権限は [Shell の AndroidManifest](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/packages/Shell/AndroidManifest.xml) で確認してください。サーバーの権限を確認するには `ShizukuService#getUid` または `ShizukuService#checkPermission` を使用します。

2. **非公開 API の制限**: Android 9 以降、非公開 API（hidden API）の利用は制限されています。必要に応じて [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) などのツールを使用してください。

3. **Android 8.0 と ADB**: API 26 では、ADB に `registerUidObserver` を使用する権限がありません。アプリのプロセスが Activity から起動されていない場合、binder の受け渡しを発生させるために透明な Activity を起動する必要があるかもしれません。

4. **`transactRemote` の直接利用**: 非公開 API のシグネチャは Android のバージョンによって変わります。ほとんどのケースは `ShizukuBinderWrapper` で対応できますが、トランザクションを直接呼び出す場合は、対象プラットフォームの AIDL 定義と照らし合わせて慎重に確認する必要があります。

## Yozuku の開発

### ビルド

- `git clone --recurse-submodules` でクローンします
- ネイティブ部分は Rust で書かれているため、Android SDK・NDK に加えて次のものが必要です
  - [rustup](https://rustup.rs/)（stable の Rust）。Windows では Visual Studio Build Tools の「C++ によるデスクトップ開発」も必要です
  - Android 向けターゲット: `rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android`
  - [cargo-ndk](https://github.com/bbqsrc/cargo-ndk): `cargo install cargo-ndk --locked`
- Gradle でビルドします: `./gradlew :manager:assembleDebug`

`:manager:assembleDebug` タスクはデバッグ可能なサーバーを生成します。デバッグ時に最新のサーバーコードを使うには、Android Studio で「Always install with package manager」にチェックが入っていることを確認してください。

### リリース版のビルド（GitHub Actions）

`.github/workflows/release.yml` で、署名済みのリリース版をビルドできます。署名にはリポジトリの Secrets（`KEYSTORE`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`、`KEYSTORE_ALIAS_PASSWORD`）を使用します。Secrets が未設定の場合はビルドが失敗します。

- **GitHub Release として公開する**: `v` で始まるタグを push します。ビルドした APK が添付されたリリースが自動で作成されます。

  ```sh
  git tag v13.7.1-ja1
  git push origin v13.7.1-ja1
  ```

- **ビルドだけ行う**: GitHub の Actions タブで「Release」ワークフローを選び、「Run workflow」から手動で実行します。APK は実行結果の Artifacts からダウンロードできます。デバッグ版だけが必要な場合は「App」ワークフローを使います。

## ライセンス

すべてのコードは Apache 2.0 ライセンスです。

- **アイコンの使用**: `manager/src/main/res/mipmap*/ic_launcher*.png` を Yozuku の表示以外の目的で使用することはできません。
- **名称と ID**: 派生物において、`Shizuku` をアプリ名として使用したり、`moe.shizuku.privileged.api` をアプリケーション ID として使用したりすることはできません。現在のパッケージ ID は `kerneldroid.nightzuku` です。


## クレジット

[**Razgame**](https://github.com/RazGame/Shizuku) — [アプリ一覧の修正](https://github.com/kerneldroid/Nightzuku/commit/6ea7e74984f860398760f5111a15083ea004c842)
