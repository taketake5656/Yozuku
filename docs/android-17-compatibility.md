# Android 15 以降の非公開 API の変更と Android17Compat レイヤー

**日本語** | [English](android-17-compatibility.en.md)

このドキュメントでは、プラットフォームの非公開のパッケージ API と権限 API で実際に何が変わったか、それをどう確かめたか、そして `Android17Compat` がそれにどう対処しているかを記録します。以下のシグネチャはすべて、記憶ではなくプラットフォームから読み取ったものです。

## 確認の方法

- コンパイル済みの Android 17 フレームワーク `org.robolectric:android-all:17-robolectric-15733970`。AOSP からビルドされたもので、SDK の公開 `android.jar` には含まれない非公開の `android.permission.IPermissionManager$Stub` と `android.content.pm.IPackageManager$Stub` が入っています。この 2 つのインターフェースに `javap` を使うと、正確なディスクリプタが得られます。
- `platform/frameworks/base` の `android15-release`、`android16-release`、`android17-release` ブランチにある AIDL のソース。
- `dev.rikka.hidden:compat:4.4.0` 本体。このヘルパーライブラリがどのシグネチャに対してコンパイルされたかを確認するためです。

## 変わったこと

`IPermissionManager`（15、16、17 で共通）:

```
int  checkPermission(String packageName, String permissionName, String persistentDeviceId, int userId)
void grantRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId)
void revokeRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId, String reason)
int  checkUidPermission(int uid, String permissionName, int deviceId)
```

重要な点が 2 つあり、どちらも間違えやすいものです。

1. **パッケージ名が先で、権限名が後です。** 逆に渡しても、目立つ失敗にはなりません。どちらの引数も `String` なので呼び出しは成功し、`PERMISSION_DENIED` が返ります。その結果、付与されている権限が拒否と報告され、プログラムの誤りではなく権限の問題のように見えてしまいます。
2. **ここでのデバイス ID は `String` で、このデバイスを表す値は `"default:0"` です。** これは `android.permission.PermissionManager` の `getPersistentDeviceId(0)` が返す値で、`"default"` ではありません。一方で、*同じ*インターフェースのほかのメソッド（`shouldShowRequestPermissionRationale`、`isPermissionRevokedByPolicy`、`getPermissionRequestState`）は今も `int deviceId` を受け取ります。そのため、インターフェース全体に通用する決め打ちの形はありません。

`IPackageManager`:

```
PackageInfo     getPackageInfo(String, long, int)         変更なし
ApplicationInfo getApplicationInfo(String, long, int)     変更なし
                getInstalledPackages(long, int)           17 で ParceledListSlice -> PackageInfoList
                getInstalledApplications(long, int)       変更なし
```

`getInstalledPackages` は**戻り値の型**が変わりました。JVM のメソッドディスクリプタには戻り値の型が含まれるため、古いディスクリプタでは解決できなくなり、呼び出しは `NoSuchMethodError` で失敗します。`getPackageInfo` と `getApplicationInfo` は変わって*いない*ことに注意してください。

この変更は Android 17 に限ったものではありません。`android15-release` の時点で、すでに 4 引数の権限メソッドのシグネチャになっています。

## Android17Compat がしていること

- 非公開のインターフェースを `ServiceManager` と `Class.forName` で解決し、プラットフォームが実際に公開しているメソッドに対して、引数の形の候補を順に試します。
- 引数の型が**完全に**一致することを条件にし、すでに試した候補と同じ形の候補は試しません。同じ引数の型に当てはまる 2 つの引数リストは、別の候補にはなりません。リフレクションでは区別できないため、それを別の候補として扱うと、誤った順序が黙って通ってしまいます。
- `IllegalArgumentException` は「この形はこのメソッドに合わない」と解釈して次の候補を試します。`InvocationTargetException` はプラットフォームが応答した結果と解釈し、推測の誤りとして再試行はしません。
- 解決したメソッドを、うまくいった形と一緒にキャッシュします。そのため推測は 1 回で済みます。どの候補も合わなかった場合は、応答に見える拒否を返すのではなく、サーバーのログで報告します。
- `grantRuntimePermission` と `revokeRuntimePermission` は、プラットフォームに届いたかどうかを返します。そのため、権限の付与が知らないうちに失われることはありません。

## 既知の制限

- 対応しているのは 15、16、17 の形です。15 より前の形も、引数の数で区別できる場合は古い候補として残していますが、対象ではありません。
- このレイヤーは Android 17 の SDK でコンパイルできることを確認し、プラットフォームのソースと照らし合わせてレビューしています。実機での動作はまだ確認していません。
