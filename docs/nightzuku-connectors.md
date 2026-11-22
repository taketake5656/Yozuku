# Yozuku コネクタ API

**日本語** | [English](nightzuku-connectors.en.md)

サードパーティのローカル権限昇格（「アクティベーター」）アプリが、`shizuku_server` の起動に必要なシェルのコマンドを問い合わせるための、実験的なローカルの IPC のインターフェース（`ShizukuConnectorProvider`）です。

## 安全のための制限

既定では無効です。プロバイダがデータを返すようにするには、ユーザーが **設定 → 試験運用機能** で **Yozuku コネクタ** を明示的に有効にし、警告のダイアログで同意する必要があります。

## ContentProvider の仕様

| 項目 | 値 |
| :--- | :--- |
| URI | `content://kerneldroid.nightzuku.connector` |
| 戻り値 | 有効なときは `Cursor`（1 行 1 列）。無効なときは `null` または空のカーソル |
| 列の名前 | `command`（`String`。`shizuku_server` を起動するための完全なシェルのコマンド） |

## クライアントの例

### Kotlin（Android）

```kotlin
val uri = Uri.parse("content://kerneldroid.nightzuku.connector")
contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex("command")
        if (idx != -1) {
            val command = cursor.getString(idx)
            // 昇格した権限でコマンドを実行する
        }
    }
}
```

### シェル

```sh
OUTPUT=$(content query --uri content://kerneldroid.nightzuku.connector)
case "$OUTPUT" in
  *command=*)
    CMD=$(printf '%s\n' "$OUTPUT" | sed -n 's/.*command=//p')
    eval "$CMD"
    ;;
  *)
    echo "Yozuku Connectors disabled or Yozuku not installed." >&2
    exit 1
    ;;
esac
```
