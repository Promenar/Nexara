---
description: 重建双工作流 (D:\NF\R)
---

// turbo
1. 创建 release-production 分支 (如果不存在)
```powershell
git branch release-production 2>$null
```

2. 添加 Worktree 到 R 目录
```powershell
git worktree add -B release-production D:\NF\R release-production
```

3. 安装依赖 (Legacy Peer Deps)
```powershell
cd D:\NF\R
npm install --legacy-peer-deps
```

4. 注入签名配置 (物理补丁)
```powershell
# 这里由 Agent 根据已知的签名信息在 android/app/build.gradle 中注入 signingConfigs.release
```

5. 验证环境
```powershell
cd D:\NF\R\android
./gradlew help
```
