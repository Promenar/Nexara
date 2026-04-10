# 性能监控体系 (APM)

**状态**: ⚪ Draft
**优先级**: Low

## 目标
建立量化的性能监控体系，及时发现线上的掉帧与崩溃。

## 初步规划
1. **Tracing**: 集成 Sentry Performance Monitoring。
2. **Metrics**: 关键指标上报 (TTV, FCP, Memory Usage)。
3. **Alerting**: 建立 Crash Rate 报警阈值。

## 待调研
- Sentry SDK 对 React Native New Architecture (Fabric) 的支持。
- 隐私合规性 (数据脱敏)。
