# 键盘避让机制全面审计报告

> **审计日期**: 2026-04-20  
> **修订日期**: 2026-04-20（方案 A 实施后更新）  
> **审计范围**: Nexara 项目中所有包含 `TextInput` 的组件  
> **审计状态**: 已完成修复

---

## 一、全局基础设施

| 项目 | 状态 | 说明 |
|------|:----:|------|
| `KeyboardProvider` 包裹 | ✅ | `app/_layout.tsx` 全局包裹，提供键盘事件分发 |
| `react-native-keyboard-controller` | ✅ | v1.18.5，全局使用 |
| `edgeToEdgeEnabled` | ✅ | Android edge-to-edge 模式已启用 |
| Android `windowSoftInputMode` | ✅ | `adjustResize`（在 Manifest 中声明） |
| `softwareKeyboardLayoutMode` | ✅ | `resize`（app.json 中声明） |

### 统一避让策略

**聊天输入框（不变）**: `KeyboardStickyView` — 键盘粘性跟随  
**其他所有位置（方案 A 统一）**: `react-native-keyboard-controller` 的 `KeyboardAvoidingView` + `behavior="padding"`  
  - 该组件内部使用 `useKeyboardAnimation()` 获取共享变量，自动适配 Android edge-to-edge 和 iOS  
  - 不再依赖 RN 原生 `KeyboardAvoidingView`（在 edge-to-edge 模式下 Android 端完全无效）

### 根因分析（2026-04-20 实机测试发现）

**问题**: 设置页面模型管理界面的输入框在键盘弹起时不会自动避让。

**根因**: `app.json` 启用了 `edgeToEdgeEnabled: true`，导致 `react-native-keyboard-controller` 的 `KeyboardProvider` 
自动检测到 edge-to-edge 模式并接管了 Android 的 IME WindowInsets 处理。这会屏蔽系统原生 `adjustResize` 的行为，
而 RN 原生 `KeyboardAvoidingView` 在 Android 端依赖 `adjustResize` 才能工作，因此完全失效。

**解决方案**: 将所有 RN 原生 `KeyboardAvoidingView` 替换为 `react-native-keyboard-controller` 提供的同名组件。
该组件内部通过共享变量（`useKeyboardAnimation()`）获取键盘高度，不依赖系统 `adjustResize`，
在 edge-to-edge 模式下也能正确工作。

---

## 二、已修复的组件

### 阶段一修复（初始审计）：为 Modal 内缺失避让的组件新增 KAV

| 文件 | 修复内容 |
|------|----------|
| `src/components/rag/KGNodeEditModal.tsx` | Modal 内新增 `KeyboardAvoidingView` 包裹 |
| `src/components/rag/KGEdgeEditModal.tsx` | Modal 内新增 `KeyboardAvoidingView` 包裹 |
| `src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx` | Modal 内新增 `KeyboardAvoidingView` 包裹 |

### 阶段二修复（实机测试后发现根因）：统一 KAV 来源

**根因**: `edgeToEdgeEnabled: true` 导致 `KeyboardProvider` 接管了 Android IME WindowInsets，
屏蔽了系统 `adjustResize`，使 RN 原生 `KeyboardAvoidingView` 在 Android 上完全无效。

**修复**: 将所有 16 个文件的 `KeyboardAvoidingView` 从 RN 原生版替换为 `react-native-keyboard-controller` 版，
并统一 `behavior="padding"`。

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `app/settings/rag-config.tsx` | KAV 来源替换 + behavior 统一 |
| 2 | `app/settings/search.tsx` | KAV 来源替换 + behavior 统一 |
| 3 | `app/(tabs)/rag.tsx` | KAV 来源替换 + behavior 统一 |
| 4 | `app/chat/[id]/settings.tsx` | KAV 来源替换 + behavior 统一 |
| 5 | `app/chat/super_assistant/settings.tsx` | KAV 来源替换 + behavior 统一 |
| 6 | `app/chat/agent/edit/[agentId].tsx` | KAV 来源替换 + behavior 统一 |
| 7 | `app/rag/editor.tsx` | KAV 来源替换 + behavior 统一 |
| 8 | `app/chat/[id].tsx` | behavior 统一（已是 keyboard-controller 版） |
| 9 | `src/features/settings/screens/ProviderModelsScreen.tsx` | KAV 来源替换 + behavior 统一 |
| 10 | `src/features/settings/screens/ProviderFormScreen.tsx` | KAV 来源替换 + behavior 统一 |
| 11 | `src/features/settings/screens/RagAdvancedSettings.tsx` | KAV 来源替换 + behavior 统一 |
| 12 | `src/features/settings/BackupSettings.tsx` | KAV 来源替换 + behavior 统一 |
| 13 | `src/components/rag/TagManagerSheet.tsx` | KAV 来源替换 + behavior 统一 |
| 14 | `src/components/rag/KGNodeEditModal.tsx` | KAV 来源替换 + behavior 统一 |
| 15 | `src/components/rag/KGEdgeEditModal.tsx` | KAV 来源替换 + behavior 统一 |
| 16 | `src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx` | KAV 来源替换 + behavior 统一 |

---

## 三、全量组件审计清单

### ✅ 所有使用 KeyboardAvoidingView 的页面（已统一为 keyboard-controller 版）

| # | 文件 | 避让方式 | 层级 |
|---|------|----------|------|
| 1 | `app/chat/[id].tsx` (主输入框) | `KeyboardStickyView` | 页面级 |
| 2 | `app/chat/[id].tsx` (标题编辑Modal) | KAV (keyboard-controller) | Modal级 |
| 3 | `app/chat/[id]/settings.tsx` | KAV (keyboard-controller) | 页面级 |
| 4 | `app/chat/super_assistant/settings.tsx` | KAV (keyboard-controller) | 页面级 |
| 5 | `app/chat/agent/edit/[agentId].tsx` | KAV (keyboard-controller) | 页面级 |
| 6 | `app/settings/search.tsx` | KAV (keyboard-controller) | 页面级 |
| 7 | `app/settings/rag-config.tsx` | KAV (keyboard-controller) | 页面级 |
| 8 | `app/(tabs)/rag.tsx` | KAV (keyboard-controller) | Modal级 |
| 9 | `app/rag/editor.tsx` | KAV (keyboard-controller) | 页面级 |
| 10 | `src/features/settings/screens/ProviderModelsScreen.tsx` | KAV (keyboard-controller) | 页面级 |
| 11 | `src/features/settings/screens/ProviderFormScreen.tsx` | KAV (keyboard-controller) | 页面级 |
| 12 | `src/features/settings/screens/RagAdvancedSettings.tsx` | KAV (keyboard-controller) | 页面级 |
| 13 | `src/features/settings/BackupSettings.tsx` | KAV (keyboard-controller) | BottomSheet级 |
| 14 | `src/components/rag/TagManagerSheet.tsx` | KAV (keyboard-controller) | Modal级 |
| 15 | `src/components/rag/KGNodeEditModal.tsx` | KAV (keyboard-controller) | Modal级 |
| 16 | `src/components/rag/KGEdgeEditModal.tsx` | KAV (keyboard-controller) | Modal级 |
| 17 | `src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx` | KAV (keyboard-controller) | Modal级 |

### ⏭️ 跳过 — 子组件，依赖父级避让

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

### ⏭️ 跳过 — 聊天流内联组件 / 页面顶部搜索

| # | 文件 | 输入类型 | 原因 |
|---|------|----------|------|
| 31 | `src/features/chat/components/ApprovalCard.tsx` | 人工干预输入 | FlatList 消息流中 |
| 32 | `src/components/skills/ToolExecutionTimeline.tsx` | 工具干预输入 | FlatList 消息流中 |
| 33 | `src/features/chat/components/WorkspaceSheet/ArtifactFilterBar.tsx` | 搜索过滤 | BottomSheet 顶部 |
| 34 | `src/features/chat/components/WorkspaceSheet/index.tsx` | 内容编辑 | BottomSheet 内 |
| 35 | `app/(tabs)/chat.tsx` | 会话搜索 | 页面顶部 |
| 36 | `app/chat/agent/[agentId].tsx` | Agent搜索 | 页面顶部 |

---

## 四、跨平台兼容性分析

### Android (edge-to-edge + keyboard-controller)

- **配置**: `edgeToEdgeEnabled: true` + `windowSoftInputMode="adjustResize"` + `softwareKeyboardLayoutMode="resize"`
- **键盘避让**: 由 `KeyboardProvider` 通过 `WindowInsetsAnimationCompat` API 接管 IME inset 动画
- **KAV 行为**: `keyboard-controller` 版 `KeyboardAvoidingView` 使用共享变量 `useKeyboardAnimation()` 获取键盘高度，自动计算 `paddingBottom`，与 edge-to-edge 完美兼容
- **Modal**: React Native `Modal` 创建独立 Window，`keyboard-controller` 的 `ModalAttachedWatcher` 会自动将 `SOFT_INPUT_ADJUST_NOTHING` 应用到 Modal 的 Window，KAV 仍然通过共享变量工作

### iOS (keyboard-controller)

- **KAV 行为**: `keyboard-controller` 版 `KeyboardAvoidingView` 通过键盘事件共享变量自动计算 `paddingBottom`
- **behavior="padding"**: 全平台统一，无需 Platform 判断

---

## 五、修改文件清单（方案 A 最终版）

```
阶段一修改（3个文件）:
  src/components/rag/KGNodeEditModal.tsx             — Modal 内新增 KAV
  src/components/rag/KGEdgeEditModal.tsx             — Modal 内新增 KAV
  src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx — Modal 内新增 KAV

阶段二修改（16个文件）:
  app/settings/rag-config.tsx                        — KAV 来源替换 + behavior="padding"
  app/settings/search.tsx                            — KAV 来源替换 + behavior="padding"
  app/(tabs)/rag.tsx                                 — KAV 来源替换 + behavior="padding"
  app/chat/[id]/settings.tsx                         — KAV 来源替换 + behavior="padding"
  app/chat/super_assistant/settings.tsx              — KAV 来源替换 + behavior="padding"
  app/chat/agent/edit/[agentId].tsx                  — KAV 来源替换 + behavior="padding"
  app/rag/editor.tsx                                 — KAV 来源替换 + behavior="padding"
  app/chat/[id].tsx                                  — behavior="padding"（已是 kc 版）
  src/features/settings/screens/ProviderModelsScreen.tsx — KAV 来源替换 + behavior="padding"
  src/features/settings/screens/ProviderFormScreen.tsx   — KAV 来源替换 + behavior="padding"
  src/features/settings/screens/RagAdvancedSettings.tsx  — KAV 来源替换 + behavior="padding"
  src/features/settings/BackupSettings.tsx           — KAV 来源替换 + behavior="padding"
  src/components/rag/TagManagerSheet.tsx             — KAV 来源替换 + behavior="padding"
  src/components/rag/KGNodeEditModal.tsx             — KAV 来源替换 + behavior="padding"
  src/components/rag/KGEdgeEditModal.tsx             — KAV 来源替换 + behavior="padding"
  src/features/chat/components/WorkspaceSheet/WorkspacePathIndicator.tsx — KAV 来源替换 + behavior="padding"
```
