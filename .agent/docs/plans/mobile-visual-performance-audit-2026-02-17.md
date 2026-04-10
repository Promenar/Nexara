# Nexara 移动端全量视觉与性能审计报告

**审计日期**: 2026-02-17  
**审计范围**: `/home/lengz/Nexara/src` 全部移动端代码  
**审计维度**: 视觉设计、交互性能、运行性能、潜在BUG

**最后更新**: 2026-02-17 23:05

---

## 一、问题总览

| 严重程度 | 数量 | 类型 | 已修复 | 跳过 | 待处理 |
|----------|------|------|--------|------|--------|
| 🔴 **严重** | 8 | Worklet 变量访问违规 | 8 | 0 | 0 |
| 🟠 **中等** | 12 | 性能优化、列表渲染 | 4 | 5 | 3 |
| 🟡 **轻微** | 15 | 代码规范、样式一致性 | 3 | 1 | 11 |

---

## 二、严重问题 (P0) ✅ 全部完成

### 2.1 Worklet 变量访问违规

**问题描述**: `useAnimatedStyle` 中的 worklet 函数运行在 UI 线程，直接访问 JS 线程变量会导致动画不同步、主题切换时颜色不更新。

| 文件 | 组件 | 状态 | 修复版本 |
|------|------|------|----------|
| `src/components/ui/Switch.tsx` | Switch | ✅ 已修复 | v1.2.66 |
| `src/components/ui/AnimatedSearchBar.tsx` | AnimatedSearchBar | ✅ 已修复 | v1.2.66 |
| `src/components/ui/AnimatedInput.tsx` | AnimatedInput | ✅ 已修复 | v1.2.66 |
| `src/features/settings/screens/ProviderFormScreen.tsx` | ProviderFormScreen | ✅ 已修复 | v1.2.66 |
| `src/features/settings/screens/ProviderModelsScreen.tsx` | TypeButton | ✅ 已修复 | v1.2.64 |
| `src/features/settings/screens/ProviderModelsScreen.tsx` | CapabilityTag | ✅ 已修复 | v1.2.64 |
| `src/features/chat/components/ChatInput.tsx` | ChatInput | ✅ 已修复 | v1.2.65 |

**修复方案**: 在组件渲染时预先计算颜色值，再传入 worklet：

```typescript
// ❌ 错误
const animatedStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(progress.value, [0, 1], [
        isDark ? '#fff' : '#000',  // JS 线程变量
        colors[500],                // JS 线程变量
    ]),
}));

// ✅ 正确
const inactiveColor = isDark ? '#fff' : '#000';
const activeColor = colors[500];
const animatedStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(progress.value, [0, 1], [inactiveColor, activeColor]),
}));
```

---

## 三、中等问题 (P1)

### 3.1 列表渲染未使用虚拟化

| 文件 | 组件 | 状态 | 说明 |
|------|------|------|------|
| `src/features/chat/components/StreamingCardList.tsx` | StreamingCardList | ⏭️ 跳过 | 流式场景不适合 FlashList，会导致闪烁 |
| `src/components/skills/ToolExecutionTimeline.tsx` | TimelineItem 列表 | 📋 待处理 | - |
| `src/features/chat/components/RagReferences.tsx` | RagReferences | 📋 待处理 | - |

### 3.2 Slider 高频更新 ✅ 已修复

| 文件 | 状态 | 修复版本 |
|------|------|----------|
| `src/features/settings/components/GlobalRagConfigPanel.tsx` | ✅ 已修复 | v1.2.66 |
| `src/features/settings/components/AdvancedRetrievalPanel.tsx` | ✅ 已修复 | v1.2.66 |
| `src/features/settings/components/AgentRagConfigPanel.tsx` | ✅ 已修复 | v1.2.66 |

**修复方案**: ThemedSlider 添加 `useSlidingComplete` 可选参数，支持滑动完成后再更新 Store。

### 3.3 CollapsibleSection 双重渲染 ✅ 已修复

**文件**: `src/components/ui/CollapsibleSection.tsx`

**修复方案**: 添加 `measured` 状态，测量完成后不再渲染测量层，减少 50% 的 children 渲染开销。

**修复版本**: v1.2.68

### 3.4 Marquee 使用旧版 Animated API ✅ 已修复

**文件**: `src/components/ui/Marquee.tsx`

**修复方案**: 迁移到 Reanimated 的 `useSharedValue` + `withTiming` + `withRepeat`，添加 `cancelAnimation` 清理逻辑。

**修复版本**: v1.2.68

---

## 四、轻微问题 (P2)

### 4.1 Haptics 调用不一致 ✅ 已修复

**修复方案**: 移除 ContextManagementPanel 中多余的 `setTimeout` 包装，`lib/haptics` 已内置 10ms 延迟。

**修复版本**: v1.2.67

### 4.2 搜索输入无防抖 ✅ 已修复

**文件**: `src/features/settings/ModelPicker.tsx`

**修复方案**: 添加 150ms 防抖，分离 `searchQuery`（显示值）和 `debouncedQuery`（过滤值）。

**修复版本**: v1.2.67

### 4.3 内联样式过多 📋 待处理

| 文件 | 内联样式数量 | 状态 |
|------|-------------|------|
| ProviderModelsScreen.tsx | 30+ 处 | 📋 待处理 |
| RagReferences.tsx | 15+ 处 | 📋 待处理 |
| ExecutionModeSelector.tsx | 10+ 处 | 📋 待处理 |
| GlobalRagConfigPanel.tsx | 15+ 处 | 📋 待处理 |

### 4.4 CachedSvgUri 使用过时 API ⏭️ 跳过

**文件**: `src/components/ui/CachedSvgUri.tsx`

**原因**: 涉及 21 个文件，工作量过大，暂时跳过。

### 4.5 动画未显式清理 ✅ 已修复

**文件**: `src/features/chat/components/ChatBubble.tsx:286-301`

**修复方案**: 在 `useEffect` 的 cleanup 中调用 `cancelAnimation` 清理动画。

**修复版本**: v1.2.69

---

## 五、视觉设计一致性分析

### ✅ 设计规范良好的方面

| 设计元素 | 规范 | 状态 |
|----------|------|------|
| 圆角 | 统一使用 `rounded-lg/2xl/3xl` | ✅ |
| 毛玻璃 | 使用 `Glass` 常量统一管理 | ✅ |
| 动画曲线 | 使用 `animations.ts` 统一配置 | ✅ |
| 触感反馈 | 统一通过 `haptics.ts` 包装 | ✅ |
| 弹簧配置 | Button/Card 使用相同配置 | ✅ |

### ⚠️ 需要统一的方面

| 设计元素 | 问题 | 状态 |
|----------|------|------|
| 间距 | 部分组件使用 `p-3`，部分使用 `p-4`，缺乏统一 Token | 📋 待处理 |
| 阴影 | GlassBottomSheet 和 GlassAlert 的阴影参数略有不同 | 📋 待处理 |
| 边框颜色 | 部分使用 `border-indigo-500/10`，部分使用 `rgba()` 硬编码 | 📋 待处理 |

---

## 六、修复进度汇总

| 优先级 | 问题 | 状态 | 修复版本 |
|--------|------|------|----------|
| **P0** | Worklet 变量访问问题 (8处) | ✅ 已修复 | v1.2.64-66 |
| **P1** | Slider 高频更新 | ✅ 已修复 | v1.2.66 |
| **P1** | CollapsibleSection 双重渲染 | ✅ 已修复 | v1.2.68 |
| **P1** | Marquee API 迁移 | ✅ 已修复 | v1.2.68 |
| **P1** | StreamingCardList 虚拟化 | ⏭️ 跳过 | - |
| **P1** | ToolExecutionTimeline 虚拟化 | ⏭️ 跳过 | - |
| **P1** | RagReferences 虚拟化 | ⏭️ 跳过 | - |
| **P2** | Haptics 延迟统一 | ✅ 已修复 | v1.2.67 |
| **P2** | ModelPicker 搜索防抖 | ✅ 已修复 | v1.2.67 |
| **P2** | ChatBubble 动画清理 | ✅ 已修复 | v1.2.69 |
| **P2** | 内联样式优化 | 📋 待处理 | - |
| **P3** | CachedSvgUri API 迁移 | ⏭️ 跳过 | - |
| **P3** | ChatBubble 动画清理 | 📋 待处理 | - |

---

## 七、待处理问题清单

| # | 优先级 | 问题 | 文件 | 工作量 |
|---|--------|------|------|--------|
| 1 | P1 | ToolExecutionTimeline 虚拟化 | `src/components/skills/ToolExecutionTimeline.tsx` | ⏭️ 跳过（滚动逻辑复杂） |
| 2 | P1 | RagReferences 虚拟化 | `src/features/chat/components/RagReferences.tsx` | ⏭️ 跳过（列表短+动画） |
| 3 | P2 | ChatBubble 动画清理 | `src/features/chat/components/ChatBubble.tsx` | ✅ v1.2.69 |
| 4 | P2 | 内联样式优化 | 多个文件 | 📋 待处理 |
| 5 | P2 | 间距 Token 统一 | 全局 | 📋 待处理 |
| 6 | P2 | 阴影参数统一 | GlassBottomSheet, GlassAlert | 📋 待处理 |
| 7 | P2 | 边框颜色统一 | 多个文件 | 📋 待处理 |
