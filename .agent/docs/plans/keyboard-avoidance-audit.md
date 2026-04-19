# 键盘避让机制全面审计报告

> **审计日期**: 2026-04-20  
> **审计范围**: Nexara 项目中所有包含 `TextInput` 的组件  
> **审计状态**: 已完成修复

---

## 一、全局基础设施

| 项目 | 状态 | 说明 |
|------|:----:|------|
| `KeyboardProvider` 包裹 | ✅ | `app/_layout.tsx` 全局包裹，提供键盘事件分发 |
| `react-native-keyboard-controller` | ✅ | v1.18.5，仅用于 `KeyboardStickyView`（聊天输入框） |
| Android `windowSoftInputMode` | ✅ | `adjustResize`，原生整体 UI 上移 |
| iOS `KeyboardAvoidingView` | ✅ | `behavior="padding"` 标准模式 |

### 统一避让策略

**聊天输入框（不变）**: `KeyboardStickyView` — 键盘粘性跟随  
**其他所有位置（统一）**: RN `KeyboardAvoidingView` + `behavior={Platform.OS === 'ios' ? 'padding' : undefined}`  
  - iOS: `padding` 行为，视图自动收缩为键盘让出空间  
  - Android: `behavior=undefined`，依赖原生 `adjustResize` 整体 UI 上移

---

## 二、已修复的组件

### Fix 1: `BackupSettings.tsx` — KAV 来源统一

| 项目 | 修改前 | 修改后 |
|------|--------|--------|
| Import | `react-native-keyboard-controller` | `react-native` (合并到主导入) |
| behavior | `'padding' : 'padding'` (两平台都是 padding) | `'padding' : undefined` (Android 依赖原生) |
| keyboardVerticalOffset | `Platform.OS === 'ios' ? 100 : 0` | 移除 |

### Fix 2: `KGNodeEditModal.tsx` — Modal 内新增 KAV

- **问题**: Modal 内知识图谱节点编辑表单无键盘避让
- **修复**: 在 `<Modal>` 内部添加 `KeyboardAvoidingView` 包裹

### Fix 3: `KGEdgeEditModal.tsx` — Modal 内新增 KAV

- **问题**: Modal 内知识图谱边编辑表单无键盘避让
- **修复**: 在 `<Modal>` 内部添加 `KeyboardAvoidingView` 包裹

### Fix 4: `WorkspacePathIndicator.tsx` — Modal 内新增 KAV

- **问题**: Modal 内工作区路径创建输入框无键盘避让
- **修复**: 在 `<Modal>` 内部添加 `KeyboardAvoidingView` 包裹

---

## 三、全量组件审计清单

### ✅ 已有正确避让 — 无需修改

| # | 文件 | 避让方式 | 避让层级 |
|---|------|----------|----------|
| 1 | `app/chat/[id].tsx` (主输入框) | `KeyboardStickyView` | 页面级 |
| 2 | `app/chat/[id].tsx` (标题编辑Modal) | RN `KeyboardAvoidingView` | Modal级 |
| 3 | `app/chat/[id]/settings.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 4 | `app/chat/super_assistant/settings.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 5 | `app/chat/agent/edit/[agentId].tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 6 | `app/settings/search.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 7 | `app/settings/rag-config.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 8 | `app/(tabs)/rag.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 9 | `app/rag/editor.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 10 | `src/features/settings/screens/ProviderModelsScreen.tsx` | RN `KeyboardAvoidingView` | Modal级 |
| 11 | `src/features/settings/screens/ProviderFormScreen.tsx` | RN `KeyboardAvoidingView` | Modal级 |
| 12 | `src/features/settings/screens/RagAdvancedSettings.tsx` | RN `KeyboardAvoidingView` | 页面级 |
| 13 | `src/features/settings/BackupSettings.tsx` | RN `KeyboardAvoidingView` | **已修复** |
| 14 | `src/components/rag/TagManagerSheet.tsx` | RN `KeyboardAvoidingView` | Modal级 |

### ✅ 已修复 — Modal 内新增 KAV

| # | 文件 | 修复内容 |
|---|------|----------|
| 15 | `src/components/rag/KGNodeEditModal.tsx` | 新增 `KeyboardAvoidingView` 包裹 |
| 16 | `src/components/rag/KGEdgeEditModal.tsx` | 新增 `KeyboardAvoidingView` 包裹 |
| 17 | `src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx` | 新增 `KeyboardAvoidingView` 包裹 |

### ⏭️ 跳过 — 子组件，依赖父级避让

以下组件是嵌入在已有 KAV 的父页面或 BottomSheet 中的子组件，父级避让机制会自动生效：

| # | 文件 | 输入类型 | 父级避让来源 |
|---|------|----------|-------------|
| 18 | `src/components/ui/SettingsInput.tsx` | 通用设置输入 | 父页面 KAV |
| 19 | `src/components/ui/AnimatedInput.tsx` | 动画输入 | 父页面 KAV |
| 20 | `src/components/ui/AnimatedSearchBar.tsx` | 搜索栏 | 父页面 KAV |
| 21 | `src/components/ui/ColorPickerPanel.tsx` | 颜色HEX输入 | 父页面 KAV |
| 22 | `src/components/ui/FloatingTextEditorModal.tsx` | 长文本编辑 | `useKeyboardHandler` |
| 23 | `src/components/ui/FloatingCodeEditorModal.tsx` | 代码编辑 | `useKeyboardHandler` |
| 24 | `src/features/settings/components/ParsedInput.tsx` | 解析输入 | 父页面 KAV |
| 25 | `src/features/settings/ModelPicker.tsx` | 模型搜索 | 父 BottomSheet |
| 26 | `src/features/chat/components/ChatInput.tsx` | 聊天输入框 | 父级 `KeyboardStickyView` |
| 27 | `src/features/chat/components/SessionSettingsSheet/ModelSelectorPanel.tsx` | 模型搜索 | 父 BottomSheet |
| 28 | `src/components/chat/InferenceSettings.tsx` | 推理参数输入 | 父页面 KAV |
| 29 | `src/components/settings/SkillsSettingsPanel.tsx` | MCP服务器配置 | 父页面 KAV |
| 30 | `src/components/rag/ArtifactLibrary.tsx` | Artifact搜索 | 父页面 KAV |

### ⏭️ 跳过 — 聊天流内联组件，adjustResize 自动处理

| # | 文件 | 输入类型 | 原因 |
|---|------|----------|------|
| 31 | `src/features/chat/components/ApprovalCard.tsx` | 人工干预输入 | 在 FlatList 消息流中，adjustResize 处理 |
| 32 | `src/components/skills/ToolExecutionTimeline.tsx` | 工具干预输入 | 在 FlatList 消息流中，adjustResize 处理 |
| 33 | `src/features/chat/components/WorkspaceSheet/ArtifactFilterBar.tsx` | 搜索过滤 | 在 BottomSheet 顶部，不易被遮挡 |
| 34 | `src/features/chat/components/WorkspaceSheet/index.tsx` | 内容编辑 | 在 BottomSheet 内，adjustResize 处理 |

### ⏭️ 跳过 — 搜索类输入，位于页面顶部

| # | 文件 | 输入类型 | 原因 |
|---|------|----------|------|
| 35 | `app/(tabs)/chat.tsx` | 会话搜索 | 顶部 AnimatedSearchBar，键盘不遮挡 |
| 36 | `app/chat/agent/[agentId].tsx` | Agent搜索 | 顶部搜索栏，键盘不遮挡 |

---

## 四、跨平台兼容性分析

### Android (adjustResize)

- **配置**: `android:windowSoftInputMode="adjustResize"`
- **全屏页面**: `adjustResize` 会缩小窗口根视图，所有内容自动上移
- **Modal 内**: React Native 的 `Modal` 组件创建独立的 Window，`adjustResize` 对其不生效。因此所有 Modal 内的 TextInput **必须**有独立的 `KeyboardAvoidingView`
- **BottomSheet 内**: `GlassBottomSheet` 基于 RN `Modal` 实现，同理需要独立避让

### iOS (KeyboardAvoidingView)

- **behavior**: 统一使用 `"padding"`
- **效果**: 视图底部自动增加 padding，为键盘让出空间
- **Modal 内**: 同样需要独立 `KeyboardAvoidingView`

---

## 五、修改文件清单

```
修改文件:
  src/features/settings/BackupSettings.tsx          — KAV 统一为 RN 版 + 标准行为
  src/components/rag/KGNodeEditModal.tsx             — Modal 内新增 KAV
  src/components/rag/KGEdgeEditModal.tsx             — Modal 内新增 KAV
  src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx — Modal 内新增 KAV

未修改 (已有正确避让):
  app/chat/[id].tsx                                   — KeyboardStickyView (聊天主输入框，保持不变)
  app/chat/[id]/settings.tsx                          — RN KAV
  app/chat/super_assistant/settings.tsx               — RN KAV
  app/chat/agent/edit/[agentId].tsx                   — RN KAV
  app/settings/search.tsx                             — RN KAV
  app/settings/rag-config.tsx                         — RN KAV
  app/(tabs)/rag.tsx                                  — RN KAV
  app/rag/editor.tsx                                  — RN KAV
  src/features/settings/screens/ProviderModelsScreen.tsx — RN KAV
  src/features/settings/screens/ProviderFormScreen.tsx   — RN KAV
  src/features/settings/screens/RagAdvancedSettings.tsx  — RN KAV
  src/components/rag/TagManagerSheet.tsx               — RN KAV
```
