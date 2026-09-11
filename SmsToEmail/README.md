# 短信转发邮箱 (SmsToEmail)

一个 Android 原生应用，**接收安卓短信后自动通过 SMTP 转发到指定邮箱**。

整个链路在设备本地完成：
- 短信内容不经过任何第三方服务器
- 邮箱密码用 Android Keystore 派生密钥加密存储
- 支持白名单 / 关键字 / 去重，避免骚扰

---

## 1. 项目结构

```
SmsToEmail/
├── build.gradle.kts                # 顶层
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts            # 模块依赖
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/smstoemail/
        │   ├── SmsApp.kt           # Application 类 (通知 Channel, 异常兜底)
        │   ├── ConfigManager.kt    # EncryptedSharedPreferences 配置
        │   ├── EmailService.kt     # SMTP 发邮件 (JavaMail for Android)
        │   ├── SmsReceiver.kt      # 短信接收广播 + 去重
        │   ├── Notifier.kt         # 成功/失败通知
        │   ├── BootReceiver.kt     # 开机自启
        │   ├── KeepAliveService.kt # 前台保活服务
        │   └── MainActivity.kt     # 配置界面
        └── res/
            ├── layout/activity_main.xml
            ├── values/{strings,colors,themes}.xml
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap*/ic_launcher{,_round}.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

---

## 2. 编译 & 运行

### 2.1 准备

- **Android Studio Koala (2024.1.1)** 或更新版本
- **JDK 17**（与 `compileOptions` 一致）
- **Android SDK Platform 34** + Build-Tools 34.0.0
- 一台真机（模拟器通常无法接收真实短信；可以用模拟器测试「测试发送」按钮）

### 2.2 编译 Debug APK

```bash
cd SmsToEmail
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或者直接在 Android Studio 中按 ▶ 运行。

### 2.3 安装到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 首次启动时，应用会要求短信权限 + 通知权限，请全部允许。

---

## 3. 配置邮箱（关键步骤）

### 3.1 各邮箱 SMTP 配置速查

| 邮箱 | SMTP 主机 | 端口 | 加密 | 备注 |
|------|-----------|------|------|------|
| **QQ** | `smtp.qq.com` | **465** | SSL | 授权码在「设置→账户→POP3/SMTP/IMAP」生成，**不是登录密码** |
| **163** | `smtp.163.com` | **465** 或 25 | SSL | 同样需「客户端授权密码」 |
| **Gmail** | `smtp.gmail.com` | **465** | SSL | 需开启两步验证 + 「应用专用密码」 |
| **Outlook** | `smmtp.office365.com` | **587** | STARTTLS | 关闭 SSL，复选框不勾 |
| **企业邮箱** | 自查 IT 文档 | 自定 | 一般 SSL | — |

### 3.2 在 App 中填入

打开应用，按界面输入：

1. **SMTP 主机**：见上表
2. **端口**：465（SSL）或 587（STARTTLS）
3. **SSL 复选框**：勾选 = SSL，取消 = STARTTLS
4. **发件邮箱**：你的 QQ 邮箱等
5. **授权码 / 密码**：上面生成的专用授权码，**不是登录密码**
6. **收件邮箱**：可以填自己另一个邮箱，也可以填同邮箱（自己发给自己）

点击 **「测试发送」**，收到测试邮件即配置成功。

---

## 4. 后台保活 / 自启动设置（重要）

国产 ROM（MIUI / ColorOS / OriginOS / EMUI / HarmonyOS）默认禁止应用后台拉起，
**必须手动开启自启动**，否则杀进程后将收不到短信广播。

| 厂商 | 开启路径 |
|------|----------|
| 小米 MIUI | 设置 → 应用 → 应用管理 → 短信转发邮箱 → 自启动 = 开 + 电池优化 = 不限制 |
| 华为 EMUI/HarmonyOS | 设置 → 电池 → 应用启动管理 → 短信转发邮箱 → 改为"允许自启动/允许关联启动/允许后台活动"三项 |
| OPPO ColorOS | 设置 → 电池 → 更多电池设置 → 睡眠待机优化 → 找到应用 → 不允许后台冻结 |
| VIVO OriginOS | 设置 → 电池 → 后台高耗电 → 允许应用后台高耗电 |
| 一加 OxygenOS | 设置 → 电池 → 电池优化 → 不优化（应用列表） |
| 三星 OneUI | 设置 → 应用程序 → 短信转发邮箱 → 电池 → 后使用限制 = 不优化 |
| 谷歌 Pixel/Android 原生 | 通常无需特殊设置 |

> 强烈建议在「设置 → 应用 → 默认应用 → 短信应用」中将本应用设为默认短信应用，可彻底解决杀进程问题（设置后请在 App 中允许相关权限）。

---

## 5. 高级功能

### 5.1 白名单（只转发指定号码）

`白名单号码` 一栏填入以逗号分隔的手机号片段，例如：

```
10086, 95588, 银行
```

只有短信发送方号码**包含**这些子串时才会转发。留空表示全部转发。

### 5.2 关键字过滤（正则）

`关键字正则` 一栏填入 Java 正则语法，例如：

```
验证码|码|code|verify
```

只转发内容中匹配到关键字的短信。留空表示全部转发。

### 5.3 去重窗口

同号码同内容在 N 秒内只转发一次（默认 60 秒），防止广播重发导致邮件重复。

### 5.4 保活开关

开启后会启动一个 Foreground Service 常驻通知栏，**会显著增加耗电**。
只在「设置 → 应用」保活不稳定的机型上启用。

---

## 6. 邮件内容样例

```
发件人: 10086
时间: 2026-09-11 16:30:45
机型: Xiaomi 23049PCD8C
Android: 14 (SDK 34)
────────────────────────────────────────
【中国移动】您本月流量已使用 12.4GB，剩余 17.6GB...
```

邮件主题：`[SMS] 10086 @ 2026-09-11 16:30:45`

---

## 7. 常见问题

**Q：测试发送成功，但实际收不到短信转发？**
A：99% 是被系统杀进程。在「设置 → 应用」允许自启动 + 关闭电池优化。

**Q：QQ 邮箱一直提示 535 错误？**
A：用的是登录密码，不是授权码。QQ 邮箱第三方登录必须用授权码。

**Q：Gmail 报 "Username and Password not accepted"？**
A：需要 (1) 开启两步验证 (2) 生成「应用专用密码」。

**Q：Android 13+ 收不到？**
A：在「设置 → 应用 → 默认应用 → 短信应用」中将本应用设为默认。

**Q：能否支持彩信 / iMessage？**
A：本项目只处理 SMS。多媒体消息（MMS）需要 `WAP_PUSH_RECEIVED` 额外处理。

**Q：能否发到 Telegram / 微信 / Webhook？**
A：可扩展。`EmailService.send()` 替换为调用 Telegram Bot API 等。

---

## 8. 安全声明

- 邮件授权码加密存储于 Android Keystore 派生的 AES-256-GCM 密钥下，root 设备才有理论风险。
- 短信内容仅在设备本地解析、立即通过 SMTP 发送，**不会上传到任何中间节点**。
- 强烈建议使用专用的「转发邮箱」而不是主力邮箱，便于隔离。

---

## 9. 进一步扩展建议

- 加入 **Webhook 推送**（SmsForwarder 风格：钉钉 / 飞书 / Telegram / Bark）
- 改为 **支持 SIM 卡识别**，区分"主卡收到的短信" vs"副卡收到的短信"
- 改为 **可配置正文模板**（用户自定义邮件正文格式）
- 改为 **多目标推送**（同时发给邮件 + Webhook + 多个邮箱）

这些功能都能在本代码基础上扩展——核心 `SmsReceiver.onReceive()` 里替换或追加分发逻辑即可。