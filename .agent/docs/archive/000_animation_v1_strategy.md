# 全局页面切换动画升级方案 (Final Strategy)

> **状态**: ✅ 已实施 (v1.1.59)
> **最后更新**: 2026-01-25
> **核心策略**: 回归原生默认 (Native Default) + 物理引擎接管

## 决策背景
原计划通过自定义 `<CustomTabsLayout>` 实现类原生的左右滑动 (Horizontal Slide) 切换。
但在实施后发现：
1.  **性能损耗**: JS 驱动的 Reanimated 动画在此时导致显著掉帧。
2.  **体验割裂**: 强行平移导致“晕动感”，不如系统默认的 Fade/Cover 轻快。
3.  **复杂性**: 自定义 Layout 破坏了 Expo Router 的原生导航栈优化。

## 最终实施方案

### 1. Tab 切换 (Top Level)
- **策略**: **Fade (淡入淡出)**
- **文件**: `app/(tabs)/_layout.tsx`
- **配置**:
  ```typescript
  animation: 'fade',
  lazy: false, // 预加载防止闪烁
  ```
- **收益**: 消除位移感，实现瞬时响应的沉浸式切换。

### 2. Stack 导航 (Drill Down)
- **策略**: **Native Default (Cover)**
- **文件**: `app/_layout.tsx`
- **配置**:
  ```typescript
  animation: 'default', // iOS: Cover, Android: System Default
  // animationDuration: REMOVED (Let OS physics handle it)
  fullScreenGestureEnabled: true, // iOS 全屏手势
  gestureDirection: 'horizontal'
  ```
- **收益**:
  - 移除固定时长 (250ms) 后，动画速度由手势速度和系统物理引擎决定，极其跟手。
  - `default` 模式下，iOS 呈现标准的“右侧覆盖”效果，而非整个页面的平移。

## 验证结果
- ✅ Tab 切换无掉帧，无视觉抖动。
- ✅ 二级页面手势返回丝滑，支持全屏拖拽。
- ✅ 设备发热显著降低 (相比自定义 Layout 方案)。
