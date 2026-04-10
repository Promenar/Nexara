# 全局动画与交互升级 (v2)

**状态**: ✅ 已完成 (Verified)
**验证时间**: 2026-02-03
**核心策略**: 全面接管系统原生动画 (System Default / Fade)

## 实施结果
已移除所有自定义 JS 驱动的动画 (Reanimated)，全面回归 `expo-router` 的原生配置：
1. **Tab 切换**: 使用 `animation: 'fade'`，消除位移晕动感。
2. **Stack 导航**: 使用 `animation: 'default'` (iOS Cover / Android Default)，确保手势返回的物理跟手性。
3. **Hero/Shared Element**: 经评估，当前原生过渡已满足 90% 需求，暂不引入额外复杂度。

## 目标
实现类原生的转场体验，消除 React Native 默认导航的生硬感。
