# 聊天会话管理功能重构

## 目标
修复聊天界面右上角菜单的功能缺失、位置偏移以及国际化不完整问题。实现“清除历史”、“重命名”和“删除会话”的核心逻辑。

## 变更内容

### 1. 核心业务逻辑 (ChatViewModel & MessageManager)
- **MessageManager**: 新增 `clearMessages(sessionId)` 方法，用于清空特定会话的所有消息记录。
- **ChatViewModel**:
    - 实现 `clearHistory()`: 调用 `messageManager.clearMessages`。
    - 实现 `renameSession(newName)`: 调用 `sessionManager.updateSessionTitle`。
    - 实现 `deleteSession()`: 调用 `sessionManager.deleteSession`。

### 2. UI 结构调整 (ChatScreen.kt)
- **ChatTopBar**: 
    - 将 `DropdownMenu` 从外部 Box 移入 `TopAppBar` 的 `actions` 内部，确保其相对于 `MoreVert` 图标正确锚定。
    - 统一使用 `DropdownMenuItem` 组件，并配以标准的 `History`、`Edit`、`Delete` 图标。
- **对话框交互**:
    - 为“清除历史”添加二次确认对话框，防止误删。
    - 为“删除会话”添加二次确认对话框，并在完成后执行 `onNavigateBack`。
    - 实现 `RenameDialog` 组件，提供优雅的毛玻璃样式输入框进行重命名。

### 3. 国际化 (Resources)
- 在 `strings.xml` 和 `values-zh-rCN/strings.xml` 中补全所有菜单项、对话框标题及提示文案。
- 修复了中文资源中部分字符乱码的问题。

## 验证计划
- [ ] 验证右上角菜单点击后位置是否紧贴图标。
- [ ] 验证“清除历史”是否能清空当前屏幕消息并同步数据库。
- [ ] 验证“重命名”是否能即时更新 TopBar 标题。
- [ ] 验证“删除会话”是否能删除会话并成功返回。
