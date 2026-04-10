# 统一 LLM 输出清洗器 (`ContentSanitizer`) — 分阶段实施方案

> **版本**: v1.0 | **创建**: 2026-04-06 | **执行端**: Flash  
> **目标**: 将分散在多个文件中的 LLM 输出清洗逻辑，重构为模块化、可扩展的统一管线架构

---

## 一、现状审计：分散的清洗逻辑清单

当前共有 **5 处** 独立的清洗/预处理逻辑，分布在 4 个文件中：

| # | 文件 | 函数/逻辑 | 职责 |
|---|------|----------|------|
| 1 | `src/lib/markdown/markdown-utils.ts` | `preprocessMarkdown()` | LaTeX 转换、结构间距、表格修复、pangu 间距、智能换行 |
| 2 | `src/features/chat/components/ChatBubble.tsx:950` | `blockMathRegex` 替换 | 块级 `$$...$$` 转为 ` ```latex ``` ` 围栏 |
| 3 | `src/features/chat/utils/markdown-utils.ts` | `extractImagesFromMarkdown()` | 从 Markdown 提取 `![](url)` 图片 |
| 4 | `src/components/chat/EChartsRenderer.tsx:55-78` | `stripJsonComments()` + 宽松解析 | ECharts JSON 注释移除、尾逗号、属性名引号补全 |
| 5 | `src/features/chat/hooks/useMarkdownRules.tsx:87` | 行内数学 `$...$` 分割 | 行内 LaTeX 检测与分割渲染 |

> [!WARNING]
> 第 2 项（ChatBubble 中的 blockMathRegex）和第 5 项（useMarkdownRules 中的行内数学分割）是渲染层逻辑，不应迁入清洗器。清洗器只负责**文本级别**的结构修复，不涉及 React 组件渲染。

**实际需迁入清洗器的逻辑**: 第 1、3、4 项。

---

## 二、最终库选型

体积不敏感（138MB 包体），核心约束为**运行效率**（流式输出场景每 token 触发一次）。

| 库名 | 版本 | 用途 | 体积 | 运行效率 | 决策 |
|------|------|------|------|---------|------|
| `jsonrepair` | ^3.x | JSON/ECharts 畸形修复 | ~15KB | ✅ 纯函数，μs 级 | ✅ 引入 |
| `@probelabs/maid` | latest | Mermaid 语法验证+修复 | ~40KB | ✅ 纯 JS 解析器 | ✅ 引入 |
| `ai-text-sanitizer` | latest | LLM 水印/引用残留清理 | ~5KB | ✅ 零依赖纯函数 | ✅ 引入 |
| `remark` + `remark-gfm` | — | Markdown AST 修复 | ~120KB | ❌ AST 解析太重 | ❌ 不引入 |
| `ascfix` | — | ASCII 表格对齐 | N/A | — (Rust 工具) | ❌ 不适用 |

---

## 三、目标架构

### 3.1 管线设计

```
ContentSanitizer.sanitize(rawText, options?)
│
├─ Phase 0: AI 文本清洗 (ai-text-sanitizer)
│   └─ 移除零宽字符、引用残留 (oaicite)、规范化标点
│
├─ Phase 1: 保护块提取
│   └─ 提取 ```...``` 和 `...` → protectedBlocks[]
│
├─ Phase 2: LaTeX 规范化
│   └─ \[...\] → $$...$$, \(...\) → $...$
│
├─ Phase 3: Markdown 结构修复
│   ├─ heading-fixer     标题间距 + 畸形标题修复
│   ├─ hr-fixer          水平分隔线间距（仅独立行）
│   ├─ list-fixer        有序/无序列表间距
│   ├─ table-fixer       GFM 表格结构修复（状态机）
│   └─ pangu-spacing     中西文混排自动间距
│
├─ Phase 4: 富文本代码块修复（在 protectedBlocks 上操作）
│   ├─ json-repairer     jsonrepair 包装（echarts/json 围栏）
│   ├─ mermaid-fixer     @probelabs/maid validate+fix
│   └─ svg-validator     SVG 基础安全校验
│
├─ Phase 5: 中文智能换行
│   └─ line-breaker      长文本句末标点后插入换行
│
├─ Phase 6: 图片提取（可选）
│   └─ image-extractor   提取 ![alt](url) 并返回图片列表
│
└─ Phase 7: 保护块恢复 + 返回结果
```

### 3.2 文件结构

```
src/lib/sanitizer/
├── index.ts                    # ContentSanitizer 类 — 管线入口
├── types.ts                    # SanitizerPlugin 接口 + SanitizerResult 类型
├── plugins/
│   ├── ai-text-cleaner.ts      # Phase 0: ai-text-sanitizer 包装
│   ├── latex-normalizer.ts     # Phase 2: LaTeX 分隔符转换
│   ├── heading-fixer.ts        # Phase 3a: 标题间距修复
│   ├── hr-fixer.ts             # Phase 3b: HR 分隔线（锚定行首行尾）
│   ├── list-fixer.ts           # Phase 3c: 列表间距修复
│   ├── table-fixer.ts          # Phase 3d: GFM 表格结构修复（状态机）
│   ├── pangu-spacing.ts        # Phase 3e: 中西文混排
│   ├── json-repairer.ts        # Phase 4a: jsonrepair 包装
│   ├── mermaid-fixer.ts        # Phase 4b: @probelabs/maid 包装
│   ├── svg-validator.ts        # Phase 4c: SVG 安全校验
│   ├── line-breaker.ts         # Phase 5: 中文智能换行
│   └── image-extractor.ts      # Phase 6: 图片提取
└── __tests__/
    ├── content-sanitizer.test.ts
    ├── table-fixer.test.ts
    ├── json-repairer.test.ts
    └── mermaid-fixer.test.ts
```

### 3.3 核心接口

```typescript
// src/lib/sanitizer/types.ts

export interface SanitizerPlugin {
  /** 插件名（用于日志和调试） */
  name: string;
  /** 执行阶段 */
  phase: 'pre-protect' | 'post-protect' | 'code-block' | 'post-restore';
  /** 是否启用（可通过 options 动态控制） */
  enabled?: boolean;
  /** 处理函数 */
  process(input: string, context: SanitizerContext): string;
}

export interface SanitizerContext {
  /** 保护块列表（仅 code-block 阶段可用） */
  protectedBlocks: ProtectedBlock[];
  /** 运行选项 */
  options: SanitizerOptions;
}

export interface ProtectedBlock {
  /** 占位符 */
  placeholder: string;
  /** 原始内容 */
  content: string;
  /** 语言标识（从围栏提取，如 'json', 'mermaid', 'echarts'） */
  language?: string;
}

export interface SanitizerOptions {
  /** 是否提取图片（默认 false） */
  extractImages?: boolean;
  /** 是否启用中文智能换行（默认 true） */
  chineseLineBreaks?: boolean;
  /** 是否启用 AI 文本清洗（默认 true） */
  aiTextClean?: boolean;
}

export interface SanitizerResult {
  /** 清洗后的文本 */
  text: string;
  /** 提取的图片列表（如果 extractImages=true） */
  images?: Array<{ src: string; alt: string }>;
}
```

### 3.4 入口类伪代码

```typescript
// src/lib/sanitizer/index.ts

import { SanitizerPlugin, SanitizerResult, SanitizerOptions } from './types';

// 导入所有插件
import { aiTextCleaner } from './plugins/ai-text-cleaner';
import { latexNormalizer } from './plugins/latex-normalizer';
// ... 其他插件

const DEFAULT_PLUGINS: SanitizerPlugin[] = [
  aiTextCleaner,        // Phase 0
  latexNormalizer,      // Phase 2
  headingFixer,         // Phase 3a
  hrFixer,              // Phase 3b
  listFixer,            // Phase 3c
  tableFixer,           // Phase 3d
  panguSpacing,         // Phase 3e
  jsonRepairer,         // Phase 4a (code-block)
  mermaidFixer,         // Phase 4b (code-block)
  svgValidator,         // Phase 4c (code-block)
  lineBreaker,          // Phase 5
  imageExtractor,       // Phase 6 (post-restore)
];

export function sanitize(text: string, options?: SanitizerOptions): SanitizerResult {
  if (!text) return { text: '' };
  const opts = { chineseLineBreaks: true, aiTextClean: true, ...options };
  let processed = text;

  // Phase 0: pre-protect 插件
  for (const plugin of plugins.filter(p => p.phase === 'pre-protect')) {
    if (plugin.enabled !== false) processed = plugin.process(processed, context);
  }

  // Phase 1: 保护块提取
  const { text: withPlaceholders, blocks } = extractProtectedBlocks(processed);
  processed = withPlaceholders;

  // Phase 2-3: post-protect 插件
  for (const plugin of plugins.filter(p => p.phase === 'post-protect')) {
    if (plugin.enabled !== false) processed = plugin.process(processed, context);
  }

  // Phase 4: code-block 插件（操作 protectedBlocks）
  for (const plugin of plugins.filter(p => p.phase === 'code-block')) {
    if (plugin.enabled !== false) {
      for (const block of blocks) {
        block.content = plugin.process(block.content, { ...context, currentBlock: block });
      }
    }
  }

  // Phase 6-7: 恢复保护块 + post-restore 插件
  processed = restoreProtectedBlocks(processed, blocks);
  for (const plugin of plugins.filter(p => p.phase === 'post-restore')) {
    // imageExtractor 等
  }

  return result;
}
```

---

## 四、分阶段实施计划

### Phase A: 架构搭建与逻辑迁移 (优先级最高)

> 目标：将 `preprocessMarkdown` 拆分为插件，行为完全不变，仅改变代码组织。

**步骤：**

1. 创建 `src/lib/sanitizer/types.ts`，定义上述接口
2. 创建 `src/lib/sanitizer/index.ts`，实现管线入口
3. 将 `markdown-utils.ts` 中的各正则块拆分为独立插件文件：
   - `latex-normalizer.ts` ← 步骤 1 (LaTeX 分隔符转换)
   - `heading-fixer.ts` ← 步骤 3a/3a' (标题间距)
   - `hr-fixer.ts` ← 步骤 3b (HR 分隔线)
   - `list-fixer.ts` ← 步骤 3c/3d'/3d (列表间距)
   - `table-fixer.ts` ← 步骤 3e (`fixMalformedTables` 完整迁移)
   - `pangu-spacing.ts` ← 步骤 3.5 (中西文混排)
   - `line-breaker.ts` ← 步骤 4 (`addChineseLineBreaks` 完整迁移)
4. 将 `features/chat/utils/markdown-utils.ts` 中的 `extractImagesFromMarkdown` 迁移为 `image-extractor.ts` 插件
5. 更新 `markdown-utils.ts`：`preprocessMarkdown` 改为调用 `sanitize()` 的薄代理

   ```typescript
   import { sanitize } from '../sanitizer';
   export function preprocessMarkdown(text: string): string {
     return sanitize(text).text;
   }
   ```

6. 更新 `ChatBubble.tsx`：
   - 将 `extractImagesFromMarkdown` 的导入改为从 sanitizer 获取
   - 将 `processedContent` 中的预处理+图片提取合并为一次 `sanitize(content, { extractImages: true })` 调用

7. **验证**：确保改造后 UI 行为与之前完全一致（无功能变更）

**关键文件变更清单：**
- [NEW] `src/lib/sanitizer/types.ts`
- [NEW] `src/lib/sanitizer/index.ts`
- [NEW] `src/lib/sanitizer/plugins/latex-normalizer.ts`
- [NEW] `src/lib/sanitizer/plugins/heading-fixer.ts`
- [NEW] `src/lib/sanitizer/plugins/hr-fixer.ts`
- [NEW] `src/lib/sanitizer/plugins/list-fixer.ts`
- [NEW] `src/lib/sanitizer/plugins/table-fixer.ts`
- [NEW] `src/lib/sanitizer/plugins/pangu-spacing.ts`
- [NEW] `src/lib/sanitizer/plugins/line-breaker.ts`
- [NEW] `src/lib/sanitizer/plugins/image-extractor.ts`
- [MODIFY] `src/lib/markdown/markdown-utils.ts` — 改为薄代理
- [MODIFY] `src/features/chat/components/ChatBubble.tsx` — 统一调用入口
- [DELETE] `src/features/chat/utils/markdown-utils.ts` — 迁移至 sanitizer

---

### Phase B: 外部库集成 — jsonrepair

> 目标：用 `jsonrepair` 替代 EChartsRenderer 中手写的 `stripJsonComments` 逻辑

**步骤：**

1. 安装依赖：`npm install jsonrepair`
2. 创建 `src/lib/sanitizer/plugins/json-repairer.ts`：

   ```typescript
   import { jsonrepair } from 'jsonrepair';
   
   export const jsonRepairer: SanitizerPlugin = {
     name: 'json-repairer',
     phase: 'code-block',
     process(content, context) {
       const block = context.currentBlock;
       if (!block || !['json', 'echarts'].includes(block.language || '')) return content;
       try {
         return jsonrepair(content);
       } catch {
         return content; // 无法修复则原样返回
       }
     }
   };
   ```

3. 移除 `EChartsRenderer.tsx` 中的 `stripJsonComments` 函数及其相关的 try/catch 宽松解析逻辑，改为直接 `JSON.parse(content)`（因为内容已在清洗阶段被 jsonrepair 修复）
4. 在 `src/@types/modules.d.ts` 中添加类型声明（如需要）

---

### Phase C: 外部库集成 — @probelabs/maid

> 目标：在 Mermaid 渲染前自动修复常见语法错误

**步骤：**

1. 安装依赖：`npm install @probelabs/maid`
2. 创建 `src/lib/sanitizer/plugins/mermaid-fixer.ts`：

   ```typescript
   import { validate, fixText } from '@probelabs/maid';
   
   export const mermaidFixer: SanitizerPlugin = {
     name: 'mermaid-fixer',
     phase: 'code-block',
     process(content, context) {
       const block = context.currentBlock;
       if (!block || block.language !== 'mermaid') return content;
       try {
         const { errors } = validate(content);
         if (errors.length === 0) return content; // 幂等
         const { fixed } = fixText(content, { level: 'safe' });
         return fixed || content;
       } catch {
         return content;
       }
     }
   };
   ```

3. 移除 `MermaidRenderer.tsx` 中的自定义错误处理逻辑（如有），改为信任清洗器的输出

---

### Phase D: 外部库集成 — ai-text-sanitizer

> 目标：移除 LLM 输出中的零宽字符、引用残留等不可见噪声

**步骤：**

1. 安装依赖：`npm install ai-text-sanitizer`
2. 创建 `src/lib/sanitizer/plugins/ai-text-cleaner.ts`：

   ```typescript
   import { sanitizeAiText } from 'ai-text-sanitizer';
   
   export const aiTextCleaner: SanitizerPlugin = {
     name: 'ai-text-cleaner',
     phase: 'pre-protect',
     process(text, context) {
       if (!context.options.aiTextClean) return text;
       try {
         const { cleaned } = sanitizeAiText(text);
         return cleaned;
       } catch {
         return text;
       }
     }
   };
   ```

3. 在 `src/@types/modules.d.ts` 中添加类型声明（如需要）

---

### Phase E: SVG 校验插件 + 清理

> 目标：将 useMarkdownRules 中分散的 SVG 安全检查逻辑提取为插件

**步骤：**

1. 创建 `src/lib/sanitizer/plugins/svg-validator.ts`
2. 将 `useMarkdownRules.tsx` 中的 `hasObviousSyntaxErrors` 检测逻辑迁移至此
3. 在围栏阶段标记 SVG 块的安全状态，渲染层直接读取标记

---

## 五、执行注意事项

### 通用约束
- **幂等性**：每个插件对已正确的内容运行后结果不变
- **防御性**：所有外部库调用必须 try/catch，失败时返回原始内容
- **保护块优先**：任何正则操作需在保护块提取之后执行，避免破坏代码块内容
- **性能红线**：单次 `sanitize()` 调用耗时 < 5ms（100 token 级别文本）

### 迁移兼容
- Phase A 完成后，`preprocessMarkdown` 保留为薄代理，确保所有现有 import 不受影响
- `markdownStyles` 对象保留在 `markdown-utils.ts` 中不动
- 旧的 `features/chat/utils/markdown-utils.ts` 在确认无其他引用后删除

### 测试策略
- 每个插件编写 3-5 个纯函数单测（正常输入、畸形输入、幂等校验）
- 集成测试：用真实 MiniMax / GLM-4 / DeepSeek 输出做端到端验证

---

## 六、依赖安装命令

```bash
npm install jsonrepair @probelabs/maid ai-text-sanitizer --legacy-peer-deps
```

---

## 七、执行顺序总结

```
Phase A (架构搭建) → Phase D (ai-text-sanitizer) → Phase B (jsonrepair)
                                                  → Phase C (@probelabs/maid)
                                                  → Phase E (SVG 校验)
```

Phase A 是前置依赖，必须先完成。Phase B/C/D/E 可并行或任意顺序执行。
