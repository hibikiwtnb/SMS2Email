# SMS2Email

一個為低端 Android 手機設計的極簡 SMS 即時郵件轉寄工具。APK 約 25 KB，不使用 Gradle、AndroidX、Compose、WebView、廣告、統計或第三方執行階段依賴。

## 功能

- 收到新 SMS 後立即透過 SMTP 轉寄
- 支援 SSL/TLS、STARTTLS 與未加密 SMTP
- 預設提供 QQ 郵箱設定：`smtp.qq.com`、連接埠 `465`、SSL/TLS
- 支援 iCloud、Gmail、Outlook、QQ、163 及自建 SMTP
- SMTP 使用者名稱與寄件地址可分開設定
- 收件地址留空時寄給自己
- SMTP 密碼或授權碼由 Android Keystore 加密保存
- 寄送失敗時將短信保存在本機 SQLite 佇列，待網路恢復後由 JobScheduler 重試
- 開機後自動恢復待寄工作
- 不讀取歷史短信
- 平時不輪詢、不維持常駐程序；收到短信時才短暫啟動

## 系統需求

- Android 6.0 或以上
- 可接收 SMS 的 Android 手機
- 可用的 SMTP 帳號

本專案主要針對 Android 7.1 實機設計及驗證。不同廠商的省電策略可能影響背景執行；若轉寄不穩定，請允許 App 自動啟動、背景執行，並將其排除在電池最佳化之外。

## 權限

| 權限 | 用途 |
| --- | --- |
| `RECEIVE_SMS` | 接收新 SMS 的系統廣播及正文 |
| `INTERNET` | 連接 SMTP 伺服器 |
| `ACCESS_NETWORK_STATE` | 讓背景工作等待可用網路 |
| `RECEIVE_BOOT_COMPLETED` | 重新開機後恢復待寄佇列 |

本 App 不申請 `READ_SMS`，因此無法查詢收件匣或歷史短信。新 SMS 的寄件人、正文及時間直接包含在 `SMS_RECEIVED` 廣播中。

## SMTP 設定範例

### QQ 郵箱

- 伺服器：`smtp.qq.com`
- 連接埠：`465`
- 加密：`SSL/TLS`
- 密碼：QQ 郵箱 SMTP 授權碼

### iCloud Mail

- 伺服器：`smtp.mail.me.com`
- 連接埠：`587`
- 加密：`STARTTLS`
- 使用者名稱：完整 iCloud 郵箱地址
- 密碼：Apple 帳號的 App 專用密碼

部分郵件服務可能把自動轉寄的驗證碼郵件分類為垃圾郵件。請將誤判郵件標記為非垃圾郵件，或建立以 `[短信轉寄]` 為條件的收件規則。

## 建置

此專案使用直接的 Android SDK 命令列工具建置，以避免 Gradle 帶來的額外下載與工程複雜度。

### 前置需求

- Windows PowerShell 7
- JDK 17 或以上，且 `javac` 位於 `PATH`
- Android SDK Platform
- Android SDK Build Tools

設定 `ANDROID_SDK_ROOT` 或將 SDK 安裝到下列其中一個常見位置：

- `C:\Workspace\tools\android-sdk`
- `%LOCALAPPDATA%\Android\Sdk`

在 PowerShell 中執行：

```powershell
.\build.ps1
```

腳本會自動選擇最新的已安裝 Android Platform 與 Build Tools，並在首次建置時產生僅供本機使用的除錯簽名金鑰。

輸出 APK：

```text
build\sms-relay-debug.apk
```

## 安裝

```powershell
adb install -r .\build\sms-relay-debug.apk
```

首次啟動後：

1. 輸入 SMTP 設定。
2. 按下「發送測試郵件」確認連線。
3. 啟用自動轉寄。
4. 授予 SMS 權限。

## 隱私與安全

- 沒有廣告、分析、追蹤或自有雲端服務
- 短信只會傳送到使用者設定的 SMTP 伺服器及收件地址
- SMTP 密碼或授權碼以 Android Keystore 金鑰加密
- 未寄出的短信只保存在 App 私有 SQLite 資料庫
- App 資料會在解除安裝時由 Android 一併刪除

請只在自己擁有或獲授權管理的手機與短信帳號上使用本軟體。短信可能包含一次性驗證碼及其他敏感資料，請妥善保護收件郵箱和 SMTP 授權碼。

## 專案結構

```text
SMS2Email/
├── AndroidManifest.xml
├── build.ps1
├── res/
│   ├── drawable/
│   └── values/
└── src/com/codex/smsrelay/
```

