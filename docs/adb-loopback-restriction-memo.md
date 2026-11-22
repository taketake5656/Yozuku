# 今後のためのメモ: 今後の Android の QPR で、デバイス内からの ADB のループバック（`127.0.0.1`）接続が制限される可能性について

**日本語** | [English](adb-loopback-restriction-memo.en.md)

> **状況:** 上流（Google）で議論が続いている段階です。2026 年 11 月の時点で、AOSP の方針として決まったものでも、出荷されたコミットでもありません。Android 17/18 の QPR を調べるときの技術的な参考として残しておくメモです。

## 1. 背景: CVE-2026-0073 と IssueTracker #526109803

2026 年 5 月、Google は **CVE-2026-0073**（CVSS 8.8、深刻度「高」。Android のセキュリティパッチレベル `2026-05-01` / `2026-05-05` で修正）を公表しました。`packages/modules/adb/daemon/auth.cpp` の `adbd_tls_verify_cert()` の論理的な誤りで、`EVP_PKEY_cmp(...)` が 0 以外の値（負のエラーコードを含む）を返すと、鍵が一致したものとして扱われていました（[RunZero の分析](https://www.runzero.com/blog/android-debug-bridge/)、[Smarttech247 の勧告](https://www.smarttech247.com/threat-intel-reports/android-cve-2026-0073-wireless-adb-auth-flaw)）。`adbd` のワイヤレス用の TLS サーバーは `INADDR_ANY` で待ち受けるため、修正前の Android 14〜16 QPR2 でワイヤレスデバッグを有効にしているデバイスは、同じネットワークにいる相手から、操作なしで相互 TLS 認証を回避される状態にありました。

これを受けて、ある開発者が **[Google IssueTracker #526109803](https://issuetracker.google.com/issues/526109803)**（2026 年 6 月 21 日）で、`adbd` を `INADDR_ANY` ではなく特定のネットワークインターフェースで待ち受けられるようにする選択肢を求めました。このスレッドで、Android の ADB の中心的なメンテナーが、さらに踏み込んで **`adbd` が Wi-Fi のインターフェース（`wlan0`）だけで待ち受けるように制限する**ことを提案しました。デバイス内のアプリが localhost（`127.0.0.1`）で `adbd` に接続して権限を昇格する使い方がされてきた、というのが理由です（[Developers Digest](https://www.developersdigest.tech/blog/android-restrict-on-device-adb-hn-analysis)、[Android Authority](https://www.androidauthority.com/google-android-on-device-adb-changes-discussion-3691371/)、[Android GadgetHacks](https://android.gadgethacks.com/news/android-on-device-adb-changes-how-google-may-restrict-loopback-access/)）。

## 2. 実際に導入された場合の Yozuku への具体的な影響

root を使わないデバイス内での起動（`BootCompleteReceiver` / `AdbBootStartJobService` / `HomeAdbDiscoveryDialog`）は、Yozuku のアプリの中で動く `AdbClient` が、同じデバイスの `adbd` に接続し直すことで成り立っています。

将来の Android の QPR で、`adbd`（`packages/modules/adb/daemon/adb_wifi.cpp` の `TlsServer`）がループバックや同じデバイスからのソケット接続を拒むように変わった場合（たとえば `wlan0` への `SO_BINDTODEVICE` と、送信元のインターフェース・接続相手での絞り込みの組み合わせ）:

1. **root を使わないデバイス内からのワイヤレス ADB での起動は、まったくできなくなります** — ワイヤレスデバッグ（ADB Wi-Fi 2.0）が有効で、`AdbMdns` がポートを見つけても、スマートフォンから自分自身への `AdbClient.connect()` は `ECONNREFUSED`、`EPERM`、`ETIMEDOUT` で失敗します。
2. `adbd` が、インターフェースを選べる仕組みではなく `wlan0` に固定する形になると、**VPN や有線 LAN を使う ADB の使い方もできなくなります**。
3. **上流の結論として現実的な 2 つの可能性:**
   - **可能性 A（開発者向けのオプトインのスイッチ）:** ループバックの ADB は既定で無効になるが、「開発者向けオプション」の永続的なスイッチで再び有効にできる。Yozuku は、そのスイッチを有効にするよう案内する画面を加えるだけで済みます。
   - **可能性 B（エミュレーター以外のビルドでの完全な削除）:** `user` ビルドでは、ループバック／デバイス内からの ADB が完全になくなる。root なしでこれに代わる正規のアプリ向けの API はありません。

## 3. 今後の AOSP / QPR のリリースで確認すること

Android 17/18 の新しい QPR や Canary のイメージを調べるときは、まず次のファイルを確認します。

- `packages/modules/adb/daemon/adb_wifi.cpp`（`TlsServer`、`network_inaddr_any_server`、`SO_BINDTODEVICE` / `wlan0` への固定や、ローカルのインターフェースのアドレスを拒む接続相手の IP の絞り込みがないか）
- `packages/modules/adb/daemon/main.cpp`（`setup_mdns`、`service.adb.listen_addrs`、インターフェースの絞り込み）
- `frameworks/base/services/core/java/com/android/server/adb/AdbDebuggingManager.java`（「開発者向けオプション」の新しいスイッチや、ポリシーのフラグ）
- `system/sepolicy/private/untrusted_app*.te`、または `netd` / eBPF のソケットのフィルタで、ローカルのアプリの UID から `adbd` の一時的な TLS のポートへの接続が止められていないか

## 4. Yozuku にすでにある、制限後も使える正規の構成

**可能性 B**（`user` ビルドでデバイス内のループバックの ADB が完全になくなる）の場合でも、`wlan0` や USB（`usb-ffs`）を使った外部からの ADB は Android の開発に欠かせないので残ります。Yozuku には、ループバックが禁止されても使える、root を使わない正規の方法が 4 つ、すでに含まれているか対応しています。

1. **LAN 上の外部の機器 / OpenWrt / PC からの mDNS による自動起動（`tools/nightzuku-lan-starter`）:**
   同じ Wi-Fi ネットワークにある家庭用ルーター（OpenWrt）、Raspberry Pi、PC で動かします。起動後に Android 17 の ADB Wi-Fi 2.0 が `_adb-tls-connect._tcp` を通知するとすぐに、スマートフォンに **`wlan0` 経由で**（`127.0.0.1` ではなく）接続し、`libshizuku.so` を起動します。ループバックの禁止、Doze、`ACCESS_LOCAL_NETWORK` の影響をまったく受けません。
2. **オフラインでも動く Device Owner との組み合わせ（`DeviceOwnerBridge` / `NightzukuDeviceAdminReceiver`）:**
   `adb shell dpm set-device-owner` で一度だけ設定します。電源を入れ直しても、ADB や Wi-Fi なしで完全にオフラインのまま、パッケージのインストール・非表示・停止、実行時の権限の付与、データの消去、委任されたスコープを扱えます。
3. **オフラインのハードウェアによる USB-C の ADB ホスト（ESP32-S3 / 別の Android デバイス）:**
   `/data/misc/adb/adb_keys` に事前に許可した RSA 鍵と、永続的な `adb_enabled=1`（`DeviceOwnerBridge.ensureUsbDebuggingEnabled()` でも維持できます）を使い、USB Bulk の `usb-ffs` で接続します。再起動後に 2 秒ほどつなぐだけで、Wi-Fi もループバックも使わずに `libshizuku.so` を起動します。
4. **root（`su`）/ PC につないだ状態（`adb shell`）:**
   これまでどおりの基本の方法です。

> 注: 3 の USB-C の ADB ホスト（ESP32-S3 など）の機器やファームウェアは、このリポジトリには含まれていません（2026-11 時点で確認）。リポジトリにあるのは 1 の `tools/nightzuku-lan-starter` と、2 の `DeviceOwnerBridge`（`ensureUsbDebuggingEnabled()` を含む）です。

## 5. 参考資料

- [Google IssueTracker #526109803 — Feature Request: Allow adbd to bind to a specific network interface/IP](https://issuetracker.google.com/issues/526109803)
- [RunZero — Android ADB vulnerability CVE-2026-0073](https://www.runzero.com/blog/android-debug-bridge/)
- [Developers Digest — Android May Soon Restrict On-Device ADB](https://www.developersdigest.tech/blog/android-restrict-on-device-adb-hn-analysis)
- [Android Authority — Google considers potential on-device ADB changes](https://www.androidauthority.com/google-android-on-device-adb-changes-discussion-3691371/)
- [Android GadgetHacks — How Google May Restrict Loopback Access](https://android.gadgethacks.com/news/android-on-device-adb-changes-how-google-may-restrict-loopback-access/)
- [Android Developers Blog — Wireless Debugging ADB Wi-Fi 2.0](https://android-developers.googleblog.com/2026/09/wireless-debugging-adb-wifi-2.html)
