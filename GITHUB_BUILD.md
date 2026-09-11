# GitHub Actions 云端编译 — 操作手册

**适用人群**：不想装 Android Studio，希望代码上传 GitHub 后，自动在云端编译 APK 下载到本地。

---

## 整体流程

```
[你在 GitHub 上创建空仓库]
            ↓
[把代码 push 到这个仓库]
            ↓
[GitHub Actions 自动编译 (5–10 分钟)]
            ↓
[下载 APK → 传到手机 → 装]
```

---

## 第 1 步：创建 GitHub 仓库

1. 打开 https://github.com （你已经登录了)
2. 右上角 **+** → **New repository**
3. 填写：
   - **Repository name**: `SmsToEmail` (或任何你喜欢的名字)
   - **Description**: 短信转发邮箱
   - 选择 **Public** (公开仓库有 2000 分钟/月的免费 Actions 时长，私有仓库只有 2000 分钟，编译一次大约 5–8 分钟)
   - **不要**勾选 "Add a README file"
   - **不要**勾选 "Add .gitignore"
4. 点 **Create repository**
5. 记下仓库地址（类似 `https://github.com/你的用户名/SmsToEmail.git`）

---

## 第 2 步：把代码传到仓库

下面给你 3 种方法，**方法 A 最推荐（最简单）**：

### 方法 A：用 GitHub Desktop（最推荐，无需命令行）

1. 下载安装 **GitHub Desktop**: https://desktop.github.com/
2. 安装后用 GitHub 账号登录
3. **File → Clone repository → URL** → 粘贴刚才的仓库地址 → 选本地路径（比如 `D:\projects\SmsToEmail`）→ Clone
4. 复制我们 `SmsToEmail.zip` 里的所有文件到这个本地路径
   - 重要：复制时**直接把 SmsToEmail 文件夹里面的内容**复制到 GitHub Desktop 克隆的目录
   - 不要让里面再有 SmsToEmail 这层目录
   - 最终结构应该是：
     ```
     D:\projects\SmsToEmail\
     ├── .github\
     ├── app\
     ├── build.gradle.kts
     ├── settings.gradle.kts
     ├── gradle.properties
     ├── README.md
     ├── INSTALL_GUIDE.md
     └── ...
     ```
5. 回到 GitHub Desktop，左边会列出所有改动
6. 左下角输入框填：`init: 短信转发邮箱 Android 应用`
7. 点 **Commit to main**
8. 顶部点 **Push origin**

### 方法 B：用命令行（你已经装了 Git）

打开 PowerShell 或 Git Bash，粘贴以下命令（**替换 `你的用户名`**）：

```bash
# 1. 解压 zip 到一个临时目录
Expand-Archive -Path "C:\Users\56238\WorkBuddy\游戏\SmsToEmail.zip" -DestinationPath "D:\projects"

# 2. 进入项目目录 (注意: 是 SmsToEmail 子目录)
cd D:\projects\SmsToEmail

# 3. 初始化 Git 仓库
git init
git branch -M main
git config user.name "你的用户名"
git config user.email "你的邮箱@example.com"

# 4. 提交代码
git add .
git commit -m "init: 短信转发邮箱 Android 应用"

# 5. 关联远程仓库 (替换成第 1 步记下的地址)
git remote add origin https://github.com/你的用户名/SmsToEmail.git

# 6. 推送
git push -u origin main
```

如果弹出登录窗口，正常输入 GitHub 凭据即可。如果提示 `git: command not found`，请用方法 A。

### 方法 C：用 GitHub 网页直接上传（文件少时可行）

GitHub 网页上传**不支持保留子文件夹结构**，但因为我们的项目几乎所有 Kotlin 代码都在一个文件夹下，可以这样：

1. 在 GitHub 仓库页面，点 **uploading an existing file**
2. 把 `app/src/main/java/com/smstoemail/*.kt` 8 个文件全部拖进浏览器
3. 但是 `.github/workflows/build.yml` 必须放到正确位置——网页上传做不到

**所以方法 C 不推荐**。请用方法 A 或 B。

---

## 第 3 步：等待 GitHub Actions 自动编译

1. 代码 push 成功后，访问你的仓库页面：
   ```
   https://github.com/你的用户名/SmsToEmail
   ```
2. 顶部菜单点 **Actions**
3. 会看到一条 **Build Android APK** 任务正在运行（黄色圆圈表示在跑）
4. 等 5–10 分钟（首次会下载 Gradle 依赖，慢一些；之后会快很多）
5. 看到绿色 ✅ = 编译成功
6. 如果红色 ❌ = 编译失败，点进去看错误日志，截图给我

---

## 第 4 步：下载 APK

1. 编译成功后点进那条 Actions 任务
2. 滚到页面底部 **Artifacts** 区域
3. 点 **app-debug** 下载（是个 zip 包）
4. 下载完解压，得到 `app-debug.apk` 文件

---

## 第 5 步：传到手机安装

### 方式 1：QQ / 微信（推荐）

1. 把 `app-debug.apk` 拖到 QQ 「我的手机」或「文件传输助手」
2. 手机端 QQ 点击下载 → 点击安装
3. 第一次安装会被系统拦截，去：
   - 设置 → 应用 → 特殊权限 → 安装未知应用 → 允许 QQ

### 方式 2：数据线

1. 用 USB 数据线连接电脑和手机
2. 把 `app-debug.apk` 拷到手机 `Download/` 文件夹
3. 手机文件管理器 → Download → 点击 apk → 安装

---

## 第 6 步：配置 App

按 `INSTALL_GUIDE.md` 的最后部分操作：

1. 打开「短信转发邮箱」App
2. 授予短信 + 通知权限
3. 配置邮箱（QQ/163/Gmail）— **必须用授权码**
4. 点「测试发送」，收到测试邮件 = 配置成功
5. 开「启用」开关
6. 去系统设置里**开启自启动 + 关闭电池优化**

---

## 后续：怎么重新编译

代码改了以后：

- **方法 A**：GitHub Desktop 会自动检测文件变化，**Commit + Push** 即可触发
- **方法 B**：本地修改文件后：
  ```bash
  cd D:\projects\SmsToEmail
  git add .
  git commit -m "fix: 修了点东西"
  git push
  ```
- 或者不 push 代码，直接在 GitHub 网页 Actions 页面点 **Run workflow** 手动触发（用现有代码重编译）

---

## 常见问题

**Q：Actions 报错 `Could not resolve com.sun.mail:android-mail`？**
A：JitPack 网络不稳。我们已经在 `settings.gradle.kts` 加了 `google()` 和 `mavenCentral()`，但 `com.sun.mail:android-mail` 在 mavenCentral 上是有的。重试一次即可。

**Q：Actions 报错 `SDK location not found`？**
A：通常是 Gradle 没识别 Android SDK。Workflow 已用 `gradle/actions/setup-gradle@v4` 自动下载 Android SDK 组件，应该不会出这错。如果出了，把日志截图发我。

**Q：编译成功后 `app-debug.apk` 不能装？**
A：检查手机是否开启了「安装未知应用」。详见 INSTALL_GUIDE.md 第 4.1 节。

**Q：编译一次要多久？**
A：首次 8–10 分钟（要下载 SDK + Gradle + 依赖）；后续 3–5 分钟。

**Q：仓库要不要设为私有？**
A：建议设私有，**代码里包含 EncryptedSharedPreferences 的密钥派生逻辑**（虽然密钥本身是设备本地的）。私有仓库 2000 分钟/月免费额度，足够用了。

**Q：Actions 免费额度用完怎么办？**
A：编译一次 5 分钟，2000 分钟够你编译 400 次。私有仓库可能也够。

---

## 出错怎么办？

把 Actions 任务的错误日志（红色 ❌ 那条 → 进去看）截图发我，我帮你改。常见错误我都见过，基本是依赖下载失败或 Gradle 版本不匹配。