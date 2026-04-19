---
name: keyboard-avoiding-audit
overview: 对项目中所有 33 个包含 TextInput 的组件进行全面键盘避让机制审计，识别缺失/不当的避让实现，并制定统一修复方案。
todos:
  - id: audit-report
    content: 生成完整的键盘避让审计报告文档，保存至 .agent/docs/plans/keyboard-avoidance-audit.md
    status: completed
  - id: verify-modal-components
    content: 使用 [subagent:code-explorer] 深入验证所有 P0/P1 级 Modal 内组件的父容器层级和实际键盘行为
    status: completed
    dependencies:
      - audit-report
  - id: fix-strategy
    content: 基于审计结果，编写分阶段修复方案并追加到审计报告
    status: completed
    dependencies:
      - verify-modal-components
---

## 产品概述

对 Nexara 项目中所有包含 TextInput 输入组件的页面和组件，进行系统性的键盘避让机制审计，产出完整的审计报告文档。

## 核心内容

- 全量扫描项目中 33+ 个包含 TextInput 的文件，逐一记录其键盘避让实现状态
- 审计 3 种键盘避让策略的跨平台兼容性（KeyboardStickyView、RN KeyboardAvoidingView、useKeyboardHandler）
- 对已确认正常工作的聊天输入框实现记录为基准参考
- 对无键盘避让的 21+ 个组件标记风险等级和问题描述
- 针对 Android（adjustResize）和 iOS（padding/height 行为差异）分别给出兼容性分析
- 输出改进方案和修复优先级排序，确保所有输入框在键盘弹起时不被遮挡
- 审计报告保存至 `.agent/docs/plans/` 目录

## 技术栈

- **框架**: React Native + Expo (Expo Router)
- **样式**: NativeWind (Tailwind CSS)
- **键盘控制库**: `react-native-keyboard-controller@1.18.5`
- **动画库**: `react-native-reanimated`
- **Android 配置**: `windowSoftInputMode="adjustResize"`

## 审计方法

### 全局基础设施验证

- `app/_layout.tsx` 中 `KeyboardProvider` 包裹层级正确，为全应用提供键盘事件基础设施
- `AndroidManifest.xml` 配置 `adjustResize`，Android 侧窗口会为键盘腾出空间
- 但 `KeyboardProvider` 仅提供事件分发，**不提供自动避让行为**，需各屏幕独立实现

### 三种避让策略分析

| 策略 | 来源 | 使用场景 | iOS 行为 | Android 行为 | 评估 |
| --- | --- | --- | --- | --- | --- |
| `KeyboardStickyView` | keyboard-controller | 聊天主输入框 | 跟随键盘粘性定位 | 跟随键盘粘性定位 | 最佳方案 |
| RN `KeyboardAvoidingView` | react-native | 设置页/表单页 | `behavior="padding"` 有效 | `behavior=undefined` 依赖 adjustResize | 基本可用，Android 无额外处理 |
| `useKeyboardHandler` | keyboard-controller | 浮动编辑器 Modal | 手动计算偏移 | 手动计算偏移 | Modal 场景适用 |


### 核心问题模式

**问题 1: Android 上 `behavior=undefined` 的隐患**
所有使用 RN `KeyboardAvoidingView` 的页面均采用 `Platform.OS === 'ios' ? 'padding' : undefined` 模式。在 Android 上 behavior 为 undefined 时，完全依赖 `adjustResize`。这在某些 Android 设备（特别是全面屏手势导航）上可能不生效，导致输入框被键盘遮挡。

**问题 2: Modal 内的 TextInput 完全无避让**
大量组件在 `Modal` 或 `GlassBottomSheet` 内使用 TextInput，但 Modal 不受 `adjustResize` 影响，需要独立的避让处理。以下组件存在此问题：

- `KGNodeEditModal.tsx` — 知识图谱节点编辑
- `KGEdgeEditModal.tsx` — 知识图谱边编辑
- `ArtifactLibrary.tsx` — Artifact 搜索
- `SkillsSettingsPanel.tsx` — MCP 服务器配置
- `ColorPickerPanel.tsx` — 颜色值输入
- `ApprovalCard.tsx` — 人工干预指令输入
- `ToolExecutionTimeline.tsx` — 工具执行干预输入
- `WorkspaceSheet/index.tsx` — 工作区内容编辑
- `WorkspacePathIndicator.tsx` — 工作区路径创建
- `WorkspaceSheet/ArtifactFilterBar.tsx` — Artifact 过滤搜索
- `ModelSelectorPanel.tsx` — 模型搜索
- `ModelPicker.tsx` — 模型选择搜索
- `ParsedInput.tsx` — 解析输入
- `TagManagerSheet.tsx` — 标签管理（虽有 KAV 但在 Modal 内需验证）

**问题 3: 全屏页面搜索输入无避让**

- `app/(tabs)/chat.tsx` — 会话列表搜索
- `app/(tabs)/settings.tsx` — 设置页（无 KAV）
- `app/chat/agent/[agentId].tsx` — Agent 会话列表搜索

### 风险分级

| 等级 | 定义 | 数量 | 典型组件 |
| --- | --- | --- | --- |
| P0-严重 | Modal 内长文本输入，键盘遮挡后用户无法看到输入内容 | ~6 | FloatingTextEditorModal, WorkspaceSheet 编辑, KGNodeEditModal |
| P1-高 | Modal 内短文本输入，可能被遮挡但影响较小 | ~8 | ColorPickerPanel, ApprovalCard, TagManagerSheet, SkillsSettingsPanel |
| P2-中 | 全屏页面有 KAV 但 Android behavior 为 undefined | ~11 | 所有设置页、表单页 |
| P3-低 | 搜索框，通常位于页面顶部不易被遮挡 | ~5 | AnimatedSearchBar, ArtifactFilterBar, ModelSelectorPanel |


## 修复策略

### 统一方案: 使用 keyboard-controller 的 KeyboardAvoidingView

项目已安装 `react-native-keyboard-controller@1.18.5`，该库的 `KeyboardAvoidingView` 比 RN 原生版本更精确（使用原生键盘事件而非 JS 事件）。建议：

1. **Modal/Sheet 内 TextInput**: 在 Modal 内容外层包裹 `keyboard-controller` 的 `KeyboardAvoidingView`（参照 `BackupSettings.tsx` 的做法）
2. **全屏页面**: 将现有 RN `KeyboardAvoidingView` 统一替换为 `keyboard-controller` 版本，并移除 `Platform.OS` 条件判断
3. **底部固定输入**: 使用 `KeyboardStickyView`（参照聊天输入框的最佳实践）

### 实施优先级

1. 先统一 import 来源（RN → keyboard-controller）
2. 再补齐 Modal 内缺失的避让
3. 最后处理搜索框等低优先级场景

## 目录结构

```
.agent/docs/pl/
└── keyboard-avoidance-audit.md    # [NEW] 键盘避让全面审计报告
```

## SubAgent

- **code-explorer**: 用于深入审查特定组件的键盘避让实现细节，验证 Modal 层级关系和父容器结构，确保审计报告中每个条目的准确性