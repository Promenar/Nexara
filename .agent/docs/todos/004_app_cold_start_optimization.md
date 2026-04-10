# 应用冷启动优化

**状态**: ⚪ Draft
**优先级**: High

## 目标
将应用的首次内容绘制 (FCP) 时间从目前的 ~1.5s 降低至 <800ms。

## 初步规划
1. **Profiling**: 使用 Hermes Profiler 分析 `App.tsx` 的挂载耗时。
2. **Hydration**: 优化 Zustand `persist` 中间件的同步阻塞行为。
3. **Lazy Load**: 延迟加载非首屏必要的 SDK (如 MCP Client, RAG System)。

## 待调研
- FlashList 的首屏渲染性能。
- 字体文件加载优化 (Preload)。
