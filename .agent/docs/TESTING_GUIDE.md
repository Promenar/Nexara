# Nexara 测试指引

> **最后更新**: 2026-04-23  
> **维护者**: Agent Team  
> **当前状态**: 19 套件 / 412 测试 / 0 失败

---

## 目录

1. [快速开始](#1-快速开始)
2. [项目测试架构](#2-项目测试架构)
3. [运行测试](#3-运行测试)
4. [编写新测试](#4-编写新测试)
5. [测试模式参考](#5-测试模式参考)
6. [Mock 体系](#6-mock-体系)
7. [基准测试](#7-基准测试)
8. [Agent 测试 CLI](#8-agent-测试-cli)
9. [已知问题与注意事项](#9-已知问题与注意事项)
10. [测试覆盖路线图](#10-测试覆盖路线图)
11. [更新此文档](#11-更新此文档)

---

## 1. 快速开始

### 运行所有测试

```bash
npx jest --no-cache
```

### 运行特定模块测试

```bash
# LLM 模块
npx jest src/lib/llm/__tests__/

# Store 模块
npx jest src/store/__tests__/

# RAG 模块
npx jest src/lib/rag/__tests__/

# Native 模块
npx jest src/native/

# 基准测试
npx jest src/__tests__/benchmarks/
```

### 运行单个文件

```bash
npx jest src/lib/llm/__tests__/stream-parser.test.ts
```

### Agent CLI（诊断/修复/基准/视觉）

```bash
npm run test:agent -- run          # 运行测试套件
npm run test:agent -- diagnose     # 诊断失败
npm run test:agent -- fix          # 自动修复
npm run test:agent -- benchmark    # 基准测试
npm run test:agent -- visual       # 视觉回归
```

---

## 2. 项目测试架构

### 2.1 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Jest | ^30.2.0 | 测试框架 |
| ts-jest | ^29.4.6 | TypeScript 编译 |
| module-alias | ^2.3.4 | Mock 模块路径别名 |
| tsx | ^4.21.0 | Agent CLI 运行时 |

> **注意**: 本项目使用 Jest（非 Vitest），preset 为 `react-native`。

### 2.2 配置文件

| 文件 | 功能 |
|------|------|
| `jest.config.js` | Jest 主配置 |
| `scripts/test-setup.ts` | 全局 setup，注册 23 个 module-alias mock |
| `scripts/jest-teardown.js` | 全局 teardown，清理 `__DEV__` |
| `scripts/tsconfig-test.json` | 测试专用 tsconfig，含路径映射 |
| `scripts/test-utils.ts` | 测试工具：配置加载、环境 polyfill、Provider 选择 |

### 2.3 jest.config.js 关键配置

```js
{
  preset: 'react-native',
  testEnvironment: 'node',
  testMatch: [
    '**/__tests__/**/*.?([mc])[jt]s?(x)',
    '**/?(*.)+(spec|test|bench).?([mc])[jt]s?(x)',
  ],
  setupFiles: ['<rootDir>/scripts/test-setup.ts'],
  testPathIgnorePatterns: [
    '/node_modules/', '/android/', '/ios/',
    '/web-client/', '/worktree/',
    '\\.benchmark\\.ts$',   // 排除 .benchmark.ts（不是 .bench.ts）
  ],
  testTimeout: 10000,
  moduleNameMapper: {
    '@probelabs/maid': '<rootDir>/scripts/mocks/probelabs-maid.ts',
    'ai-text-sanitizer': '<rootDir>/scripts/mocks/ai-text-sanitizer.ts',
  },
}
```

**要点**:
- `.bench.ts` 文件会被 Jest 正常运行
- `.benchmark.ts` 文件被排除（如 `vector-store.benchmark.ts`）
- `worktree/` 已在忽略列表中

### 2.4 目录结构

```
src/
├── __tests__/benchmarks/          # 全局基准测试
│   ├── stream-parsing.bench.ts
│   ├── artifact-parsing.bench.ts
│   └── text-processing.bench.ts
├── lib/
│   ├── __tests__/                 # lib 层测试
│   │   └── artifact-parser.test.ts
│   ├── llm/__tests__/             # LLM 解析测试
│   │   ├── stream-parser.test.ts
│   │   ├── error-normalizer.test.ts
│   │   ├── artifact-parser.test.ts
│   │   ├── model-utils.test.ts
│   │   └── thinking-detector.test.ts
│   ├── rag/__tests__/             # RAG 模块测试
│   │   ├── text-splitter.test.ts
│   │   ├── keyword-search.test.ts
│   │   ├── reranker.test.ts
│   │   ├── embedding.test.ts
│   │   └── vector-store.benchmark.ts  # 需 RN 环境，Jest 已排除
│   └── mcp/transports/__tests__/
│       └── sse-transport.test.ts
├── store/__tests__/               # Zustand Store 测试
│   ├── settings-store.test.ts
│   └── mcp-store.test.ts
└── native/
    ├── Sanitizer/__tests__/sanitizer.test.ts
    ├── TextSplitter/__tests__/text-splitter.test.ts
    └── TokenCounter/__tests__/token-counter.test.ts
```

---

## 3. 运行测试

### 3.1 标准命令

```bash
# 全量运行
npx jest

# 带覆盖率（注意：RN preset 下部分覆盖率可能不准确）
npx jest --coverage

# 监听模式
npx jest --watch

# 详细输出
npx jest --verbose

# 仅运行失败的测试
npx jest --onlyFailures

# 并行/串行
npx jest --maxWorkers=4     # 限制并发
npx jest --runInBand        # 串行执行（调试用）
```

### 3.2 调试单个测试

```bash
# 运行单个文件并打印 console
npx jest src/lib/llm/__tests__/stream-parser.test.ts --verbose --no-cache

# 进入 Node 调试器
node --inspect-brk node_modules/.bin/jest --runInBand src/lib/llm/__tests__/stream-parser.test.ts
```

### 3.3 测试超时

默认超时 10 秒（`jest.config.js: testTimeout: 10000`）。单个测试可覆盖：

```ts
it('长时间测试', () => {
  // ...
}, 30000); // 30 秒
```

---

## 4. 编写新测试

### 4.1 文件命名与放置

| 测试类型 | 文件名模式 | 放置位置 |
|---------|-----------|---------|
| 单元测试 | `*.test.ts` | `被测模块/__tests__/` |
| 基准测试 | `*.bench.ts` | `src/__tests__/benchmarks/` 或 `被测模块/__tests__/` |
| RN 专用基准 | `*.benchmark.ts` | `被测模块/__tests__/`（会被 Jest 排除） |

### 4.2 测试文件模板

#### 纯 JS 模块（无外部依赖）

```ts
/**
 * XXX 单元测试
 * 测试说明
 */

import { SomeClass } from '../some-module';

describe('SomeClass', () => {
  let instance: SomeClass;

  beforeEach(() => {
    instance = new SomeClass();
  });

  describe('功能组 A', () => {
    it('应正确处理 XXX', () => {
      const result = instance.doSomething('input');
      expect(result).toBe('expected');
    });

    it('应处理边界情况', () => {
      const result = instance.doSomething('');
      expect(result).toBe('');
    });
  });

  describe('边界情况', () => {
    it('应处理 null 输入', () => {
      expect(() => instance.doSomething(null)).toThrow();
    });

    it('应处理超长输入', () => {
      const longText = 'a'.repeat(10000);
      const result = instance.doSomething(longText);
      expect(result).toBeDefined();
    });

    it('应处理 Unicode', () => {
      const result = instance.doSomething('中文 🎉 émojis');
      expect(result).toBeDefined();
    });
  });
});
```

#### Zustand Store 测试

```ts
import { useSomeStore } from '../some-store';

// 1. Mock AsyncStorage
jest.mock('@react-native-async-storage/async-storage', () =>
  require('../../../scripts/mocks/async-storage')
);

// 2. Mock immer（关键！必须同时支持双参数和 curried 签名）
jest.mock('immer', () => ({
  produce: (...args: any[]) => {
    if (args.length === 2) {
      const [base, recipe] = args;
      recipe(base);
      return base;
    }
    const [recipe] = args;
    return (base: any) => {
      recipe(base);
      return base;
    };
  },
}));

describe('useSomeStore', () => {
  beforeEach(() => {
    // 重置 store 状态
    useSomeStore.setState({ items: [] });
  });

  it('addItem 应添加项目', () => {
    useSomeStore.getState().addItem({ id: '1', name: 'Test' });
    expect(useSomeStore.getState().items).toHaveLength(1);
  });

  it('removeItem 应删除项目', () => {
    useSomeStore.getState().addItem({ id: '1', name: 'Test' });
    useSomeStore.getState().removeItem('1');
    expect(useSomeStore.getState().items).toHaveLength(0);
  });
});
```

#### 需要外部依赖 Mock 的测试

```ts
// 在文件顶部声明 mock，必须在 import 之前
jest.mock('../../types/skills', () => ({
  ToolCall: {},
}));

jest.mock('../../lib/llm/response-normalizer', () => ({
  ProviderType: {},
}));

import { SomeParser } from '../some-parser';

describe('SomeParser', () => {
  // ...
});
```

### 4.3 编写测试的检查清单

编写新测试时，确保：

- [ ] 文件放在 `被测模块/__tests__/` 目录下
- [ ] 文件名为 `*.test.ts`
- [ ] 如果依赖 RN 模块，确认对应 mock 已在 `scripts/test-setup.ts` 注册
- [ ] 如果依赖新的外部包，需在 `jest.config.js` 的 `moduleNameMapper` 或 `scripts/test-setup.ts` 的 `addAliases` 中添加 mock
- [ ] Store 测试必须 mock `immer`（双签名模式）
- [ ] Store 测试必须 mock `@react-native-async-storage/async-storage`
- [ ] 每个 `describe` 有明确的语义分组
- [ ] 包含边界情况：空输入、null/undefined、超长文本、Unicode
- [ ] 运行 `npx jest <文件>` 确认通过

---

## 5. 测试模式参考

### 5.1 现有测试文件分类

| 类型 | 代表文件 | 特点 |
|------|---------|------|
| 纯逻辑测试 | `stream-parser.test.ts` | 无需 mock，直接实例化测试 |
| Store 测试 | `mcp-store.test.ts` | mock immer + AsyncStorage，测试 CRUD |
| 配置验证测试 | `settings-store.test.ts` | 本地定义 mock 对象，验证默认值和正则 |
| Native 桥接测试 | `sanitizer.test.ts` | mock 原生模块（TurboModule），验证调用和返回值 |
| 基准测试 | `stream-parsing.bench.ts` | `performance.now()` + 时间阈值断言 |

### 5.2 已有测试统计

| 模块 | 测试文件数 | 测试用例数 |
|------|-----------|-----------|
| `src/lib/llm/` | 5 | ~120 |
| `src/lib/rag/` | 4 | ~70 |
| `src/store/` | 2 | ~23 |
| `src/native/` | 3 | ~45 |
| `src/lib/` | 1 | ~16 |
| `src/lib/mcp/` | 1 | ~10 |
| `src/__tests__/benchmarks/` | 3 | ~12 |
| **合计** | **19** | **~412** |

---

## 6. Mock 体系

### 6.1 概览

项目通过 `module-alias` 在 `scripts/test-setup.ts` 中注册 23 个 mock，覆盖所有 React Native / Expo 原生模块。

### 6.2 Mock 文件清单 (`scripts/mocks/`)

| Mock 文件 | 替代模块 |
|-----------|---------|
| `async-storage.ts` | `@react-native-async-storage/async-storage` |
| `op-sqlite.ts` | `@op-engineering/op-sqlite` |
| `expo-file-system.ts` | `expo-file-system/legacy` |
| `expo-haptics.ts` | `expo-haptics` |
| `expo-clipboard.ts` | `expo-clipboard` |
| `expo-image.ts` | `expo-image` |
| `expo-router.ts` | `expo-router` |
| `expo-keep-awake.ts` | `expo-keep-awake` |
| `expo-sharing.ts` | `expo-sharing` |
| `expo-image-picker.ts` | `expo-image-picker` |
| `react-native-reanimated.ts` | `react-native-reanimated` |
| `react-native-gesture-handler.ts` | `react-native-gesture-handler` |
| `react-native-keyboard-controller.ts` | `react-native-keyboard-controller` |
| `react-native-view-shot.ts` | `react-native-view-shot` |
| `react-native-svg.ts` | `react-native-svg` |
| `react-native-screens.ts` | `react-native-screens` |
| `react-native-safe-area-context.ts` | `react-native-safe-area-context` |
| `react-native-webview.ts` | `react-native-webview` |
| `react-native-sse.ts` | `react-native-sse` |
| `@shopify-flash-list.ts` | `@shopify/flash-list` |
| `llama-rn.ts` | `llama.rn` |
| `probelabs-maid.ts` | `@probelabs/maid` |
| `ai-text-sanitizer.ts` | `ai-text-sanitizer` |

### 6.3 添加新 Mock

当引入新的 RN/Expo 依赖时，需要：

1. 在 `scripts/mocks/` 创建 mock 文件
2. 在 `scripts/test-setup.ts` 的 `addAliases({})` 中注册
3. 如果是 ESM-only 包，还需在 `jest.config.js` 的 `moduleNameMapper` 中添加

**Mock 模板**:

```ts
// scripts/mocks/some-module.ts
export const SomeModule = {
  methodA: jest.fn(() => Promise.resolve()),
  methodB: jest.fn(() => 'mock-value'),
};

export default SomeModule;
```

### 6.4 在测试文件中覆盖 Mock

如果某个测试需要特定 mock 行为，在文件顶部用 `jest.mock()` 覆盖：

```ts
// 覆盖全局 mock
jest.mock('@react-native-async-storage/async-storage', () =>
  require('../../../scripts/mocks/async-storage')
);

// 自定义 mock
jest.mock('some-module', () => ({
  methodA: jest.fn(() => 'custom-value'),
}));
```

---

## 7. 基准测试

### 7.1 模式

基准测试使用 Jest `it()` + `performance.now()` + 时间阈值断言：

```ts
it('应在 200ms 内处理 N 个输入', () => {
  const start = performance.now();
  // ... 执行操作 ...
  const duration = performance.now() - start;
  console.log(`操作名称: ${duration.toFixed(2)}ms`);
  expect(duration).toBeLessThan(200);
});
```

### 7.2 现有基准测试

| 文件 | 测试项 | 阈值 |
|------|-------|------|
| `stream-parsing.bench.ts` | 1000 SSE chunk 解析 | 200ms |
| | 5000 短 chunk 解析 | 500ms |
| | 代码块流 x50 | 100ms |
| `artifact-parsing.bench.ts` | 500 ECharts JSON | 100ms |
| | 500 代码块标记 | 200ms |
| | 500 元数据提取 | 100ms |
| | 200 不规范 JSON | 150ms |
| `text-processing.bench.ts` | 100KB 文本切分 | 50ms |
| | 10000 元素 JSON 解析 | 100ms |
| | 1000 次正则匹配 | 50ms |
| | 10000 次 trim+replace | 50ms |
| | 10000 字符串拼接 | 200ms |

### 7.3 编写新基准测试

在 `src/__tests__/benchmarks/` 下创建 `*.bench.ts` 文件。如果需要 mock 源码依赖，在文件顶部声明 `jest.mock()`。

**注意**: 时间阈值应根据 CI 环境适当放宽。本地调试时阈值可能非常宽松。

---

## 8. Agent 测试 CLI

### 8.1 概览

`scripts/agent-test/` 下有一套完整的自定义测试 CLI（~5800 行），提供测试运行、诊断、自动修复、基准对比、视觉回归功能。

### 8.2 架构

```
scripts/agent-test/
├── cli.ts                        # CLI 入口 (502 行)
├── config.ts                     # 配置加载
├── diagnostician/
│   ├── error-classifier.ts       # 错误分类 (10+ 模式)
│   ├── fix-strategies.ts         # 修复策略库
│   └── stack-parser.ts           # 堆栈解析
├── fix/
│   ├── safe-modifier.ts          # 安全代码修改器
│   └── rollback-manager.ts       # 回滚管理器
├── runner/
│   ├── benchmark-runner.ts       # 7 种基准配置
│   └── jest-runner.ts            # Jest 执行器
├── parser/
│   └── jest-parser.ts            # Jest JSON 报告解析
├── visual/
│   ├── screenshot-manager.ts     # 模拟器截图
│   ├── baseline-manager.ts       # 基线管理
│   └── diff-engine.ts            # 像素级对比
├── types/                        # 类型定义
└── utils/                        # 工具函数
```

### 8.3 使用方式

```bash
# 运行测试
npm run test:agent -- run

# 诊断失败测试
npm run test:agent -- diagnose

# 自动修复
npm run test:agent -- fix

# 基准测试
npm run test:agent -- benchmark

# 视觉回归
npm run test:agent -- visual
```

---

## 9. 已知问题与注意事项

### 9.1 已知问题

| 问题 | 状态 | 说明 |
|------|------|------|
| `vector-store.benchmark.ts` 编译错误 | 已排除 | 直接 import op-sqlite 原生模块，需 RN 环境 |
| `settings-store.test.ts` 较浅 | 低优先级 | 主要测试配置验证逻辑，未直接导入 Store |

### 9.2 关键注意事项

1. **immer mock 必须支持双签名**: Zustand + immer 需要 `produce(base, recipe)` 和 `produce(recipe)(base)` 两种调用方式
2. **不要用 `JSON.parse/stringify` 克隆含函数的 state**: immer mock 中直接 mutation 原始对象即可
3. **`jest.mock()` 必须在 `import` 之前**: Jest hoists `jest.mock()` 调用，但最好保持代码中也在顶部
4. **`.benchmark.ts` 和 `.bench.ts` 的区别**: 前者被 Jest 排除（需 RN 环境），后者被 Jest 正常运行
5. **`worktree/` 下的测试不应运行**: 已在 `testPathIgnorePatterns` 中排除
6. **新增 ESM 依赖需同步更新 mock**: 在 `scripts/test-setup.ts` 和 `jest.config.js` 中都要添加

---

## 10. 测试覆盖路线图

### 当前覆盖

- [x] LLM 流式解析 (StreamParser)
- [x] LLM 错误标准化 (ErrorNormalizer)
- [x] LLM Artifact 解析
- [x] LLM 模型工具 (ModelUtils)
- [x] LLM 思维检测 (ThinkingDetector)
- [x] RAG 文本切分 / 关键词搜索 / 重排 / Embedding
- [x] MCP SSE 传输
- [x] Store: settings / mcp
- [x] Native: Sanitizer / TextSplitter / TokenCounter
- [x] 性能基准 (3 文件 12 项)

### 待补充（按优先级）

| 优先级 | 模块 | 测试文件 | 说明 |
|--------|------|---------|------|
| **P0** | Store | `chat-store.test.ts` | 核心聊天功能，最重要 |
| **P0** | Store | `rag-store.test.ts` | RAG 状态管理 |
| **P1** | LLM Provider | `providers/*.test.ts` | Mock fetch，验证请求/响应 |
| **P1** | MCP | `client.test.ts` / `protocol.test.ts` | MCP 客户端逻辑 |
| **P2** | UI 组件 | `components/**/*.test.tsx` | 需引入 `@testing-library/react-native` |
| **P3** | 集成 | `e2e/*.test.ts` | E2E 测试 |
| **P3** | CI/CD | GitHub Actions / EAS | 自动化流水线 |

---

## 11. 更新此文档

### 何时更新

- 新增测试文件时
- 新增/修改 Mock 时
- jest.config.js 配置变更时
- 新增测试依赖时
- 修复重大测试问题时

### Agent 更新指引

当代码变更涉及测试体系时，Agent 应：

1. **新增测试文件**: 在 §2.4 目录结构和 §5.2 统计表中添加条目
2. **新增 Mock**: 在 §6.2 清单中添加条目
3. **配置变更**: 更新 §2.3 配置说明
4. **修复问题**: 更新 §9.1 已知问题表格
5. **路线图进展**: 更新 §10 覆盖路线图

### 文档格式规范

- 使用相对路径引用文件（从项目根目录开始）
- 表格使用 Markdown 标准格式
- 代码块标注语言类型
- 保持中文撰写

---

> **相关文档**:
> - [Agent 测试框架设计 v1](plans/agent-test-framework-v1.md) — 完整系统设计（2580 行）
> - [建设进度报告](plans/agent-test-framework-progress.md) — Phase 1-4 进度
> - [Phase 1 方案](plans/phase1-logic-tests.md) — 逻辑测试详细方案
> - [Phase 2 方案](plans/phase2-benchmark.md) — 基准测试详细方案
> - [Phase 3 方案](plans/phase3-visual.md) — 视觉测试详细方案
> - [Phase 4 方案](plans/phase4-diagnosis.md) — 诊断修复详细方案
