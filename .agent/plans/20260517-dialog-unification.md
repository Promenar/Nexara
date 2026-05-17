# 20260517-dialog-unification: 统一 Nexara 危险操作删除二次确认弹窗的实施计划

本项目正致力于打磨 Nexara 极具现代美感和高端设计语言（Glassmorphism）的 UI 视觉体验。针对用户反馈的“删除提供商”弹窗位置错位、覆盖状态栏的严重 Bug，以及“删除助手和会话的确认弹窗在中间，风格不统一”的问题，本计划提出一次彻底的、全局无死角的重构方案。

## 核心分析与诊断

1. **Bug 根源剖析**：
   - 现有的 `NexaraConfirmDialog` 是一个依据 Nexara 暗黑高阶玻璃拟态（Glassmorphism）规范设计的精致 Composable 容器。
   - 然而在实现或使用时，`NexaraConfirmDialog` 本身仅是一个普通的 `Surface` 布局。若调用方未用 `androidx.compose.ui.window.Dialog` 对其进行显式窗体包裹，它就会被作为普通的行内 Composable 绘制。
   - 在 `UserSettingsHomeScreen.kt`（删除提供商）、`SessionSettingsScreen.kt`（删除会话）、`RecycleBinPanel.kt`（永久删除/清空回收站）、`ProviderModelsScreen.kt`（删除所有模型）中，调用方均**漏掉了 `Dialog` 包裹**，导致弹窗被直接渲染在屏幕最顶层，造成宽度撑满并覆盖状态栏的错位 Bug。
   - 在 `ChatScreen.kt` 中，清空历史和删除会话弹窗因为套了 `Dialog` 因而得以在屏幕中央精美呈现。

2. **设计统一决策**：
   - 将所有不可逆、破坏性的危险操作二次确认（删除助手、删除会话、删除提供商、永久删除文件等）**全部统统完美统一为屏幕中央的 Glassmorphism 精美二次确认小弹窗**。
   - 理由：屏幕中央的二次确认 Dialog 可以强力夺取用户焦点，辅以系统级蒙层，拥有极佳的安全防误触体验，是此类交互最标准的黄金规范。而底部滑出（ModalBottomSheet）更适合选项选择或多量操作，单薄的确认提示在底部铺开会导致空间极度空旷且操作感过重。
   - 优雅重构方法：
     - **重构 `ConfirmDialog.kt`**：使其直接包裹 `NexaraConfirmDialog`，作为 `Dialog` 窗体版本的便捷入口。这样，诸如 `AgentEditScreen.kt`（删除助手）在内的所有调用旧版普通 `ConfirmDialog` 的页面都会**自动、免费升级**为拥有精致玻璃描边、优雅暗磨砂背景的高阶 `NexaraConfirmDialog`，一键实现全系统视觉统一！
     - **修复漏掉 `Dialog` 的所有调用点**：为 UserSettingsHomeScreen、SessionSettingsScreen、RecycleBinPanel、ProviderModelsScreen 中的 `NexaraConfirmDialog` 完美套上 `Dialog`。

---

## 拟修改文件

### [MODIFY] [ConfirmDialog.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/common/ConfirmDialog.kt)
重构 `ConfirmDialog`，使其在其内部 `Dialog` 中直接调用 `NexaraConfirmDialog` 进行绘制，保留旧版参数接口，提供 100% 完美的向下兼容。

### [MODIFY] [UserSettingsHomeScreen.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/hub/UserSettingsHomeScreen.kt)
修复 `showDeleteDialog != null` 时渲染的提供商删除弹窗，加上 `Dialog(onDismissRequest = { showDeleteDialog = null })` 完美包裹。

### [MODIFY] [SessionSettingsScreen.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/SessionSettingsScreen.kt)
修复 `showDeleteDialog` 时渲染的删除会话弹窗，加上 `Dialog(onDismissRequest = { showDeleteDialog = false })` 包裹。

### [MODIFY] [RecycleBinPanel.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/components/RecycleBinPanel.kt)
修复 `showPermanentDeleteConfirm` 与 `showEmptyConfirm` 时渲染的永久删除和清空回收站弹窗，分别套上 `Dialog` 包裹。

### [MODIFY] [ProviderModelsScreen.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/settings/ProviderModelsScreen.kt)
修复 `showDeleteAllDialog` 时渲染的删除所有模型弹窗，加上 `Dialog(onDismissRequest = { showDeleteAllDialog = false })` 包裹。

---

## 验证与验收方案

### 自动化编译与静态检查
- 在 `Cwd = k:\Nexara\native-ui` 目录下执行 `./gradlew compileDebugKotlin` 确保重构后代码 100% 无任何编译或签名兼容问题。

### 视觉与交互全路径 DIA 审计
- 检查“删除提供商”、“删除助手”、“删除会话（两个入口）”、“永久删除文件”、“清空回收站”、“删除所有模型”这 6 处确认弹窗，确保全部完美居中展示，带有暗磨砂浮层且绝对不覆盖手机顶端状态栏。

---

## 跨会话 DIA 交接与进度归档 (handover.md)
我们将在实施后同步更新文档注册表中的 `.agent/handover.md`、`docs/CHANGELOG.md` 与 `docs/ARCHITECTURE.md`，做到“代码变，文档同步变”的超一流开发要求！
