# FlashList → FlatList 架构迁移记录

**日期**: 2026-02-05  
**影响模块**: `app/chat/[id].tsx`（聊天详情页）

---

## 问题背景

聊天界面在滚动历史消息时出现明显的"回弹/跳变"现象，尤其在包含 Markdown 表格的长消息附近高频触发。

## 根因分析

经过系统性排查，确认问题源于 **@shopify/flash-list 的内部机制**：
- FlashList 的 Cell 回收/复用逻辑与复杂 Markdown 渲染存在冲突
- `overrideItemLayout` 和 `maintainVisibleContentPosition` 的交互导致布局计算不稳定
- 这是 FlashList 的已知上游问题，非本项目代码问题

## 解决方案

将 FlashList 替换为 React Native 原生 FlatList。

## 权衡分析

| 维度 | FlashList | FlatList |
|------|-----------|----------|
| **内存占用** | 低（Cell 回收） | 中（保留渲染过的 Cell） |
| **滚动稳定性** | 存在回弹 bug | 稳定 |- **适用场景** | 大量同质化列表 | 中等规模异构列表 |

**选择 FlatList 的理由**：
1. 聊天场景消息数量通常 <100 条，内存压力可接受
2. 文本内容内存占用极低（~1-2 KB/条）
3. 用户体验稳定性优先于理论性能

## 清理内容

| 位置 | 清理项 |
|------|--------|
| `app/chat/[id].tsx` | 移除 `layoutHeightsRef`、`onLayout` 高度缓存逻辑 |
| `app/chat/[id].tsx` | 移除 FlashList 专属 props |
| DB schema | 保留 `layout_height` 字段（向后兼容） |

## 相关代码

```tsx
// app/chat/[id].tsx - 弃用注释
/**
 * 🔑 架构决策：使用 FlatList 而非 FlashList
 * 原因：FlashList 在复杂 Markdown 内容场景下存在滚动回弹 bug
 */
const AnimatedFlatList = Animated.createAnimatedComponent(FlatList);
```
