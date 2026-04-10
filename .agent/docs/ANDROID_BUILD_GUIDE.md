# Android 构建指南与常见陷阱

## 核心问题：Windows 路径长度限制

### 问题表现
```
ninja: error: Stat(reactnativekeyboardcontroller_autolinked_build/CMakeFiles/...)
Filename longer than 260 characters
```

### 根本原因
Windows 系统默认限制文件路径为 260 字符。React Native + Expo 项目的深层 `node_modules` 结构（尤其是原生模块如 `react-native-keyboard-controller`、`react-native-reanimated` 等）容易触发此限制。

### ❌ 无效的解决方案

以下方案**已验证无效**，请勿尝试：

1. **虚拟驱动器映射 (subst)**
   ```powershell
   subst X: g:\Dev\NeuralFlow
   ```
   - 失败原因：Expo autolinking 在驱动器根目录无法找到 `package.json`
   - 错误提示：`Couldn't find "package.json" up from path "X:\"`

2. **Junction 目录链接**
   ```powershell
   mklink /J C:\Nx g:\Dev\NeuralFlow
   ```
   - 失败原因：Metro bundler 仍解析到原始 G: 路径
   - 错误提示：`this and base files have different roots: g:\Dev\NeuralFlow\node_modules\...`

3. **混合构建策略**（JS Bundle 在原路径，Native Build 在短路径）
   - 失败原因：`packageReleaseResources` 任务无法跳过，依赖图破损
   - 错误提示：`Querying the mapped value ... has completed is not supported`

4. **修改 Gradle buildDir**
   ```groovy
   buildDir = "C:/tmp/builds/${rootProject.name}/${project.name}"
   ```
   - 失败原因：破坏 Expo autolinking 的文件查找逻辑
   - 错误提示：`autolinking.json does not exist`

### ✅ 有效解决方案

**完全迁移项目到短路径**：

```powershell
# 1. 清理目标目录（如果存在）
if (Test-Path C:\Nx) { Remove-Item -Recurse -Force C:\Nx }

# 2. 复制项目文件（排除 node_modules 和构建产物）
robocopy G:\Dev\NeuralFlow C:\Nx /E /XD node_modules android\build .git .expo

# 3. 安装依赖
cd C:\Nx
npm install

# 4. 生成原生代码
npx expo prebuild --platform android --clean

# 5. 编译 Release APK
cd android
.\gradlew.bat assembleRelease
```

**路径长度对比**：
- ❌ 原路径: `G:\Dev\NeuralFlow\node_modules\...` (29+ 字符基础)
- ✅ 新路径: `C:\Nx\node_modules\...` (18 字符基础，节省 11 字符)

## 其他常见问题

### 1. Metro/Gradle 缓存污染

**症状**：修改代码后仍报旧错误，或出现 "different roots" 错误

**解决方案**：
```powershell
# 清理 Metro 缓存
rm -r .metro

# 清理 Gradle 缓存 (物理全量清理)
Remove-Item -Recurse -Force android/.cxx, android/.gradle, android/build, android/app/build
# 严禁仅依赖 gradlew clean

# 清理 node_modules（彻底方案）
rm -r node_modules
npm install
```

### 2. Expo Autolinking 失败

**症状**：`Process 'command 'cmd'' finished with non-zero exit value 1` in settings.gradle

**检查点**：
- 确保 `package.json` 存在于项目根目录
- 确保不在虚拟驱动器根目录（如 `X:\`）
- 运行 `npx expo-modules-autolinking resolve --platform android` 手动测试

### 3. TypeScript 类型错误

**症状**：编译前 IDE 报红，提示缺少属性

**原则**：
- ⚠️ **编译前必须修复所有 TypeScript 错误**
- Gradle 编译不会检查 TypeScript，但运行时会崩溃
- 使用 `tsc --noEmit` 验证类型完整性

## 最佳实践

### 开发环境设置

1. **使用短路径**
   - 推荐: `C:\Projects\ProjectName`
   - 避免: `C:\Users\Username\Documents\Development\LongProjectNameHere`

2. **定期清理**
   ```powershell
   # 每次大版本更新前
   rm -r node_modules android .expo
   npm install
   npx expo prebuild --clean
   ```

3. **验证路径长度**
   ```powershell
   # 检查最长路径（PowerShell）
   Get-ChildItem -Recurse | 
     Select-Object FullName, @{Name="Length";Expression={$_.FullName.Length}} | 
     Sort-Object Length -Descending | 
     Select-Object -First 10
   ```

### CI/CD 配置

- 使用 `C:\a\` 或 `D:\a\` 等短路径作为工作目录
- 在构建前验证路径长度不超过 200 字符
- 缓存 `node_modules` 时确保路径一致性

## 快速检查清单

构建失败时按此顺序排查：

- [ ] 检查项目路径是否超过 50 字符
- [ ] 运行 `tsc --noEmit` 验证 TypeScript 错误
- [ ] 清理所有缓存（`.metro`, `android\.gradle`, `node_modules`）
- [ ] 确保 `package.json` 在正确位置
- [ ] 如果在虚拟驱动器/Junction，切换到物理路径
- [ ] 最后手段：完全迁移到 `C:\Nx` 或 `C:\Build`

## 参考资料

- [Windows 路径长度限制文档](https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation)
- [Expo Prebuild 文档](https://docs.expo.dev/workflow/prebuild/)
- [React Native Android 构建指南](https://reactnative.dev/docs/signed-apk-android)
