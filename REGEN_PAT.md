# 重新生成带 workflow 权限的 PAT

GitHub 默认禁止普通 PAT 修改 `.github/workflows/*.yml`，必须勾选 `workflow` scope。

## 操作步骤

1. 打开 https://github.com/settings/tokens/new
2. 填写：
   - **Note**: `WorkBuddy编译-v2`（便于区分）
   - **Expiration**: 7 days（一次性）
   - **Scopes**（这次勾四个）:
     - ☑ **`repo`** （完整仓库权限）
     - ☑ **`workflow`** （**关键！允许推送 GitHub Actions 工作流文件**）
3. 点 **Generate token**
4. 复制 `ghp_xxx...` 开头的字符串

## 拿到后告诉我

新 PAT 复制给我，我继续 push + 触发编译。

---

## 为什么 GitHub 要这个限制

GitHub Actions 可以执行任意代码（`run:` 块）。如果允许普通 PAT 修改 workflow，攻击者拿到 token 后就能注入恶意脚本在你的仓库里跑（挖矿、窃取 secrets 等）。

`workflow` scope 需要额外授权，意味着你"明确允许"自己修改 Actions 配置。

---

## 替代方案（如果不想再走 token）

如果不想再生成 PAT，也可以让我**改用不同的 workflow 文件名**——GitHub 只限制 `.github/workflows/*.yml` 和 `.github/workflows/*.yaml` 这两个路径。

我可以在项目里改名为 `build.yaml`（同样是 GitHub 识别的格式）避开这个限制。但这是 hack 方案，正常用户应该用带 workflow 权限的 PAT。

走哪条路？
- **A**: 你去生成新 PAT 发我（推荐）
- **B**: 我改 workflow 文件名为 `build.yaml` 绕过