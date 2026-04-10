# 多线程架构探索归档 (2026-01-31)

## 项目背景
探索将 StreamParser 迁移到后台 Worklet，实现 UI 渲染绕过 JS 主线程。

## 技术栈
- `react-native-worklets-core` v1.3.3
- `react-native-reanimated` v4.1.1

## 探索过程

### Phase 1: 基础设施搭建 ✅
- 创建 `WorkletService.ts` 封装 API
- 配置 babel.config.js

### Phase 2: Parser 迁移 ⚠️
- 创建 `StreamParserWorklet.ts`
- 发现 RegExp 在 Worklet 中触发 Babel polyfill 崩溃
- 改用纯字符串解析

### Phase 3: UI 绑定 ❌
- 发现 `console.error` 在 Worklet 中触发 Metro HMR 崩溃
- 发现 Reanimated 的 `runOnUI` 无法从 worklets-core 调用
- **关键发现**: 两个库的 SharedValue 不共享上下文

## 核心结论

| 测试 | 结果 |
|------|------|
| 直接更新 SharedValue | ❌ UI 无响应 |
| 桥接 JS 更新 SharedValue | ✅ UI 正常 |

**根本原因**: `react-native-worklets-core` 与 `react-native-reanimated` 是独立的 Worklet 运行时，SharedValue 不互通。

## 最终决策
放弃多线程改造，保持单线程架构。理由：
1. 本项目 JS 线程负载本就不高
2. 远程 LLM 调用为异步 I/O，解析负载极低
3. 本地模型在原生线程执行

## 保留组件
- `WorkletService.ts` - 保留以备后用
- `react-native-worklets-core` - 保留依赖

## 经验教训
1. Worklet 中避免使用 `console.error`
2. 不同 Worklet 库之间可能存在隔离
3. 架构改造前应先做可行性 PoC
