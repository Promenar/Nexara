# Android Release Build Protocol (Nexara)

为了确保开发环境与发行环境的物理隔离，并保护签名密钥安全，特制定以下协议：

## 1. 物理隔离 (Physical Isolation)
*   **主工作区 (Root - `main`)**: 仅用于开发和测试包编译。严禁在此包含正式签名配置。
*   **发行工作区 (Worktree - `release-production`)**: 专用发行环境。位于 `worktrees/release`。所有的正式发布包 (`Signed-Release`) 必须在此目录下编译。**注意：此分支仅作为本地编译工厂，严禁推送到远程仓库。**

## 2. 签名与 Git 策略
*   **自动化签名 (Persistent Config Plugin)**: 通过 `plugins/withAndroidSigning.js` 实现。在 `npx expo prebuild` 时自动注入 `secure_env` 路径下的 `promenar.keystore` 签名逻辑，无需手动编辑 `build.gradle`。
*   **Root (main)**: 包含所有功能代码及 Config Plugins。
*   **Worktree (release-production)**: 本地专用的编译工厂，严禁推送到远程仓库（GitHub 仅保留 `main` 分支作为唯一事实源）。
*   **环境隔离**: 签名信息受 `secure_env/secure.properties` 保护，Git 已忽略。

## 3. 操作指令 (Standard Operating Procedures)

### 编译开发包 (Dev Build)
在 **Root** 目录执行：
```bash
./gradlew assembleDebug
```

### 编译发行包 (Release Build - Standard SOP)
在 **Worktree** 目录执行：
1. `git merge main` (确保同步最新代码)
2. `rm -rf android/.cxx android/.gradle android/build android/app/build` (物理清理防止符号冲突)
3. `npm install`
4. `npx expo prebuild --platform android --clean`
5. `cd android && ./gradlew assembleRelease`

## 4. 依赖管理
*   ** gradle.properties**: 两边独立维护代理及编译参数。
*   ** 权限恢复**: 每次清理 node_modules 后，需确保 `linux64-bin/hermesc` 具备执行权限 (`chmod +x`)。
