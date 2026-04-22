# Phase 4：Agent 自主诊断修复引擎详细设计

> **文档版本**: v1.0  
> **创建日期**: 2026-04-22  
> **父文档**: agent-test-framework-v1.md

---

## 1. 概述

Phase 4 是整个 Agent 测试框架的**决策中枢**，负责：
1. 解析测试报告和基准数据
2. 匹配错误模式，定位根因
3. 生成修复策略，执行代码修改
4. 验证修复效果，必要时回滚

---

## 2. 诊断引擎

### 2.1 引擎主逻辑

**文件**: `scripts/agent-test/diagnostician/engine.ts`

```typescript
import { TestRunReport, TestResult, DiagnosisResult } from '../types/test-report';
import { DiagnosisCategory, Severity } from '../types/diagnosis';
import { ErrorPatternMatcher } from './error-patterns';
import { StackTraceParser } from './stack-trace';
import { RootCauseLocator } from './root-cause';
import { generateId } from '../utils/id-generator';
import fs from 'fs';
import path from 'path';

export class Diagnostician {
  constructor(
    private options: {
      maxProcessingTime?: number;
      confidenceThreshold?: number;
      enableBisect?: boolean;
    } = {}
  ) {
    this.patternMatcher = new ErrorPatternMatcher();
    this.stackParser = new StackTraceParser();
    this.rootCauseLocator = new RootCauseLocator();
  }

  async diagnose(report: TestRunReport): Promise<DiagnosisSession> {
    const session: DiagnosisSession = {
      id: generateId(),
      startedAt: new Date().toISOString(),
      testReport: report,
      diagnoses: [],
      summary: {
        totalDiagnosed: 0,
        byCategory: {} as Record<DiagnosisCategory, number>,
        bySeverity: {} as Record<Severity, number>,
        autoFixable: 0,
        needsManualReview: 0,
      },
    };

    for (const failedTest of report.failedTests) {
      const diagnosis = await this.diagnoseSingle(failedTest);
      session.diagnoses.push(diagnosis);

      // 更新汇总
      session.summary.totalDiagnosed++;
      session.summary.byCategory[diagnosis.category]++;
      session.summary.bySeverity[diagnosis.severity]++;

      if (diagnosis.fixConfidence >= (this.options.confidenceThreshold ?? 0.5)) {
        session.summary.autoFixable++;
      } else {
        session.summary.needsManualReview++;
      }
    }

    session.completedAt = new Date().toISOString();
    return session;
  }

  private async diagnoseSingle(test: TestResult): Promise<DiagnosisResult> {
    const startTime = Date.now();
    
    if (!test.error) {
      return this.unknownDiagnosis(test, startTime);
    }

    // 1. 错误模式匹配
    const pattern = this.patternMatcher.match(test.error);

    // 2. 堆栈解析
    const stackFrames = this.stackParser.parse(test.error.stack);
    const rootFrame = this.stackParser.findRootCause(stackFrames);

    // 3. 获取代码上下文
    let rootContext: string[] = [];
    if (rootFrame && rootFrame.isProjectFile) {
      rootContext = this.stackParser.getContext(
        rootFrame.file,
        rootFrame.line,
        5 // 前后 5 行
      );
    }

    // 4. 生成修复建议
    const suggestedFix = this.generateFixSuggestion(
      test,
      pattern,
      rootFrame,
      rootContext
    );

    return {
      id: generateId(),
      testResult: test,
      category: pattern.category,
      severity: pattern.severity,
      rootFile: rootFrame?.file || test.filePath || '',
      rootLine: rootFrame?.line || test.lineNumber || 0,
      rootFunction: rootFrame?.function,
      rootContext,
      suggestedFix,
      fixConfidence: pattern.confidence,
      timestamp: new Date().toISOString(),
      processingTimeMs: Date.now() - startTime,
    };
  }

  private unknownDiagnosis(test: TestResult, startTime: number): DiagnosisResult {
    return {
      id: generateId(),
      testResult: test,
      category: 'unknown',
      severity: 'medium',
      rootFile: test.filePath || '',
      rootLine: test.lineNumber || 0,
      rootContext: [],
      suggestedFix: '无法自动诊断，请人工审查。',
      fixConfidence: 0,
      timestamp: new Date().toISOString(),
      processingTimeMs: Date.now() - startTime,
    };
  }

  private generateFixSuggestion(
    test: TestResult,
    pattern: MatchedPattern,
    rootFrame: ParsedStackFrame | null,
    rootContext: string[]
  ): string {
    let suggestion = `## 诊断结果\n\n`;
    suggestion += `**错误类型**: ${pattern.category}\n`;
    suggestion += `**严重程度**: ${pattern.severity}\n`;
    suggestion += `**匹配模式**: ${pattern.name}\n\n`;
    
    if (rootFrame) {
      suggestion += `## 根因位置\n\n`;
      suggestion += `- 文件: \`${rootFrame.file}\`\n`;
      suggestion += `- 行号: ${rootFrame.line}\n`;
      suggestion += `- 函数: \`${rootFrame.function}\`\n\n`;
    }

    suggestion += `## 可能原因\n\n`;
    for (const cause of pattern.likelyCauses) {
      suggestion += `1. ${cause}\n`;
    }
    suggestion += `\n## 建议修复\n\n`;
    suggestion += pattern.suggestedFix;
    
    if (rootContext.length > 0) {
      suggestion += `\n\n## 相关代码\n\n`;
      suggestion += '```\n' + rootContext.join('\n') + '\n```\n';
    }

    return suggestion;
  }
}

interface DiagnosisSession {
  id: string;
  startedAt: string;
  completedAt?: string;
  testReport: TestRunReport;
  diagnoses: DiagnosisResult[];
  summary: DiagnosisSummary;
}

interface DiagnosisSummary {
  totalDiagnosed: number;
  byCategory: Record<DiagnosisCategory, number>;
  bySeverity: Record<Severity, number>;
  autoFixable: number;
  needsManualReview: number;
}

interface MatchedPattern {
  name: string;
  category: DiagnosisCategory;
  severity: Severity;
  confidence: number;
  likelyCauses: string[];
  suggestedFix: string;
}

interface ParsedStackFrame {
  file: string;
  line: number;
  column: number;
  function: string;
  isProjectFile: boolean;
}
```

### 2.2 错误模式库

**文件**: `scripts/agent-test/diagnostician/error-patterns.ts`

```typescript
import { DiagnosisCategory, Severity } from '../types/diagnosis';

export interface ErrorPattern {
  name: string;
  pattern: RegExp;
  category: DiagnosisCategory;
  severity: Severity;
  confidence: number;
  likelyCauses: string[];
  suggestedFix: string;
}

export const ERROR_PATTERNS: ErrorPattern[] = [
  // Type Error: 访问 undefined 属性
  {
    name: '访问 undefined 属性',
    pattern: /Cannot read properties? of (?:undefined|null) \(reading '(\w+)'\)/,
    category: 'type_error',
    severity: 'high',
    confidence: 0.85,
    likelyCauses: [
      '变量未初始化',
      'API 响应数据结构不符合预期',
      '可选链缺失',
      '异步数据未加载完成',
    ],
    suggestedFix: `建议修复方案：

1. **添加可选链**（推荐）：
\`\`\`typescript
// 之前
const value = obj.property;

// 之后
const value = obj?.property;
\`\`\`

2. **使用空值合并**：
\`\`\`typescript
const value = obj?.property ?? defaultValue;
\`\`\`

3. **添加防御性检查**：
\`\`\`typescript
if (obj && obj.property !== undefined) {
  // ...
}
\`\`\``,
  },

  // Type Error: 调用非函数
  {
    name: '调用非函数对象',
    pattern: /(?:TypeError: )?(.+) is not a (?:function|constructor)/,
    category: 'type_error',
    severity: 'high',
    confidence: 0.8,
    likelyCauses: [
      '导入路径错误',
      '模块未正确导出',
      '循环依赖',
      '变量名拼写错误',
    ],
    suggestedFix: `排查步骤：

1. 检查 \`import\` 语句路径是否正确
2. 确认模块是否正确导出该函数
3. 检查 \`package.json\` 中的 \`exports\` 字段
4. 验证循环依赖（使用 \`madge --circular\` 检测）`,
  },

  // Assertion Error
  {
    name: '断言失败',
    pattern: /Expected:? (.+?)\s+Received:? (.+)/s,
    category: 'logic_error',
    severity: 'medium',
    confidence: 0.7,
    likelyCauses: [
      '预期值计算错误',
      'Mock 数据不符合真实场景',
      '边界条件未覆盖',
      '浮点数比较精度问题',
    ],
    suggestedFix: `排查步骤：

1. 分析 Expected 和 Received 的差异
2. 检查被测函数的逻辑是否正确
3. 验证测试数据是否符合预期
4. 对于浮点数，使用 \`toBeCloseTo()\` 而非 \`toBe()\``,
  },

  // Async Timeout
  {
    name: '异步操作超时',
    pattern: /Async callback was not called within timeout of (\d+)ms/,
    category: 'async_error',
    severity: 'high',
    confidence: 0.75,
    likelyCauses: [
      '异步操作未完成',
      'Mock 未正确 resolve/reject',
      'Promise 链断裂',
      'setTimeout/setInterval 未清理',
    ],
    suggestedFix: `修复方案：

1. **增加超时时间**：
\`\`\`typescript
jest.setTimeout(10000);
\`\`\`

2. **使用 fake timers**：
\`\`\`typescript
beforeEach(() => { jest.useFakeTimers(); });
afterEach(() => { jest.useRealTimers(); });
\`\`\`

3. **确保 Promise resolve**：
\`\`\`typescript
await expect(promise).resolves.toBe(value);
\`\`\``,
  },

  // op-sqlite 错误
  {
    name: 'SQLite 操作错误',
    pattern: /op-sqlite: (.+)/,
    category: 'mock_error',
    severity: 'medium',
    confidence: 0.9,
    likelyCauses: [
      'op-sqlite Mock 未正确配置',
      'SQL 语法错误',
      '表结构不匹配',
      '参数绑定错误',
    ],
    suggestedFix: `排查步骤：

1. 检查 \`scripts/mocks/op-sqlite.ts\` Mock 配置
2. 验证 SQL 语句语法
3. 确认表结构定义与查询匹配
4. 检查参数数量和顺序`,
  },

  // Cannot find module
  {
    name: '模块未找到',
    pattern: /Cannot find module ['"]([^'"]+)['"]/,
    category: 'module_error',
    severity: 'critical',
    confidence: 0.95,
    likelyCauses: [
      '包未安装',
      '导入路径错误',
      'TypeScript 路径别名未配置',
      'ESM/CJS 混用问题',
    ],
    suggestedFix: `排查步骤：

1. 运行 \`npm install\` 确保依赖安装
2. 检查 \`import\` 路径是否正确
3. 检查 \`tsconfig.json\` 的 \`paths\` 配置
4. 检查 \`package.json\` 的 \`exports\` 字段`,
  },

  // ZodError
  {
    name: 'Zod 验证错误',
    pattern: /ZodError/,
    category: 'validation_error',
    severity: 'medium',
    confidence: 0.85,
    likelyCauses: [
      '输入数据不符合 schema',
      'schema 定义错误',
      '类型转换失败',
    ],
    suggestedFix: `修复方案：

1. 使用 \`safeParse()\` 而非 \`parse()\`
2. 检查输入数据的完整性和类型
3. 验证 schema 定义是否正确
4. 参考 ZodError 的 \`issues\` 数组定位具体错误`,
  },

  // JSON.parse
  {
    name: 'JSON 解析错误',
    pattern: /Unexpected token (.+) in JSON at position (\d+)/,
    category: 'parse_error',
    severity: 'medium',
    confidence: 0.8,
    likelyCauses: [
      'JSON 字符串格式错误',
      '多余的逗号或引号',
      '包含非转义字符',
    ],
    suggestedFix: `修复方案：

1. 使用 \`jsonrepair\` 库修复损坏的 JSON
2. 检查 JSON 字符串格式
3. 使用 \`JSON.stringify()\` 生成 JSON 而非手动拼接`,
  },

  // Maximum update depth
  {
    name: 'React 更新深度超出限制',
    pattern: /Maximum update depth exceeded/,
    category: 'react_error',
    severity: 'critical',
    confidence: 0.6,
    likelyCauses: [
      'useEffect 依赖数组配置错误',
      '状态更新在渲染中触发',
      'useCallback/useMemo 配置错误',
    ],
    suggestedFix: `修复方案：

1. 检查 \`useEffect\` 依赖数组
2. 使用 \`useCallback\` 包裹回调函数
3. 使用 \`useMemo\` 缓存计算结果
4. 确保状态更新在事件处理器中而非渲染中`,
  },

  // Jest Snapshot Mismatch
  {
    name: '快照不匹配',
    pattern: /Snapshot (.+) mismatched/,
    category: 'regression',
    severity: 'medium',
    confidence: 0.8,
    likelyCauses: [
      'UI 变更未更新快照',
      '预期 UI 变更',
      '随机内容导致差异',
    ],
    suggestedFix: `修复方案：

1. **如果是预期变更**：
\`\`\`bash
npm test -- -u  # 更新快照
\`\`\`

2. **如果是回归问题**：
   - 检查 UI 组件的最近修改
   - 回滚导致变更的提交`,
  },
];

export class ErrorPatternMatcher {
  match(error: TestError): MatchedPattern {
    for (const pattern of ERROR_PATTERNS) {
      if (pattern.pattern.test(error.message)) {
        return {
          name: pattern.name,
          category: pattern.category,
          severity: pattern.severity,
          confidence: pattern.confidence,
          likelyCauses: pattern.likelyCauses,
          suggestedFix: pattern.suggestedFix,
        };
      }
    }

    // 默认匹配
    return {
      name: '未知错误',
      category: 'unknown',
      severity: 'medium',
      confidence: 0.3,
      likelyCauses: ['未知错误类型'],
      suggestedFix: '请人工审查错误信息并定位根因。',
    };
  }
}
```

### 2.3 堆栈解析器

**文件**: `scripts/agent-test/diagnostician/stack-trace.ts`

```typescript
import fs from 'fs';

export interface StackFrame {
  file: string;
  line: number;
  column: number;
  function: string;
  isProjectFile: boolean;
}

export class StackTraceParser {
  /**
   * 解析堆栈跟踪字符串
   */
  parse(stackTrace: string): StackFrame[] {
    if (!stackTrace) return [];

    const lines = stackTrace.split('\n');
    const frames: StackFrame[] = [];

    for (const line of lines) {
      const frame = this.parseLine(line);
      if (frame) {
        frames.push(frame);
      }
    }

    return frames;
  }

  /**
   * 解析单行堆栈信息
   * 格式: at FunctionName (file:line:column)
   * 或: at file:line:column
   */
  private parseLine(line: string): StackFrame | null {
    // 匹配格式: at functionName (path:line:column)
    const matchWithFn = line.match(
      /at\s+(?:(.+?)\s+)?\((.+?):(\d+):(\d+)\)/
    );

    if (matchWithFn) {
      const [, functionName, file, lineNum, col] = matchWithFn;
      return {
        file,
        line: parseInt(lineNum, 10),
        column: parseInt(col, 10),
        function: functionName || '<anonymous>',
        isProjectFile: this.isProjectFile(file),
      };
    }

    // 匹配格式: at path:line:column
    const matchWithoutFn = line.match(/at\s+(.+?):(\d+):(\d+)/);
    if (matchWithoutFn) {
      const [, file, lineNum, col] = matchWithoutFn;
      return {
        file,
        line: parseInt(lineNum, 10),
        column: parseInt(col, 10),
        function: '<anonymous>',
        isProjectFile: this.isProjectFile(file),
      };
    }

    return null;
  }

  /**
   * 判断是否为项目文件
   */
  private isProjectFile(filePath: string): boolean {
    return (
      filePath.includes('/src/') ||
      filePath.includes('/scripts/') ||
      filePath.includes('/app/')
    );
  }

  /**
   * 找到最可能的根因帧
   * 优先返回第一个项目文件中的帧
   */
  findRootCause(frames: StackFrame[]): StackFrame | null {
    const projectFrames = frames.filter((f) => f.isProjectFile);
    return projectFrames[0] || null;
  }

  /**
   * 获取堆栈帧周围的代码上下文
   */
  getContext(file: string, line: number, contextLines: number = 5): string[] {
    try {
      if (!fs.existsSync(file)) {
        return [`文件不存在: ${file}`];
      }

      const content = fs.readFileSync(file, 'utf8').split('\n');
      const start = Math.max(0, line - contextLines - 1);
      const end = Math.min(content.length, line + contextLines);

      return content.slice(start, end).map((l, i) => {
        const lineNum = start + i + 1;
        const marker = lineNum === line ? '>>>' : '   ';
        const displayLine = l || '(空行)';
        return `${marker} ${lineNum.toString().padStart(4)}: ${displayLine}`;
      });
    } catch {
      return [`无法读取文件: ${file}`];
    }
  }

  /**
   * 从堆栈中提取相关文件列表
   */
  extractRelevantFiles(frames: StackFrame[]): string[] {
    return [...new Set(
      frames
        .filter((f) => f.isProjectFile)
        .map((f) => f.file)
    )];
  }
}

interface TestError {
  message: string;
  stack: string;
  type: 'assertion' | 'runtime' | 'timeout' | 'compile' | 'unknown';
  expected?: string;
  received?: string;
}

interface MatchedPattern {
  name: string;
  category: any;
  severity: any;
  confidence: number;
  likelyCauses: string[];
  suggestedFix: string;
}
```

---

## 3. 修复引擎

### 3.1 引擎主逻辑

**文件**: `scripts/agent-test/fixer/engine.ts`

```typescript
import { DiagnosisResult } from '../types/diagnosis';
import { FixResult, FileModification } from '../types/fix';
import { StrategyRegistry } from './strategies';
import { safeWriteFile, rollback } from '../utils/file-ops';
import { generateId } from '../utils/id-generator';
import { JestRunner } from '../runner/jest-runner';
import fs from 'fs';

export class FixEngine {
  constructor(
    private options: {
      maxRetries?: number;
      enableRollback?: boolean;
      requireHumanApproval?: boolean;
    } = {}
  ) {
    this.strategyRegistry = new StrategyRegistry();
    this.jestRunner = new JestRunner({ verbose: false });
  }

  async fix(diagnosisSession: any): Promise<FixSession> {
    const session: FixSession = {
      id: generateId(),
      diagnosisSessionId: diagnosisSession.id,
      startedAt: new Date().toISOString(),
      results: [],
      summary: {
        totalAttempted: diagnosisSession.diagnoses.length,
        succeeded: 0,
        rolledBack: 0,
        needsManual: 0,
        skipped: 0,
      },
    };

    for (const diagnosis of diagnosisSession.diagnoses) {
      if (diagnosis.fixConfidence < 0.5) {
        session.results.push({
          id: generateId(),
          diagnosis,
          status: 'skipped',
          filesModified: [],
          verificationPassed: false,
          rollbackNeeded: false,
          backupPaths: [],
          attemptedAt: new Date().toISOString(),
        });
        session.summary.skipped++;
        continue;
      }

      const fixResult = await this.attemptFix(diagnosis);
      session.results.push(fixResult);

      switch (fixResult.status) {
        case 'applied':
          session.summary.succeeded++;
          break;
        case 'rolled_back':
          session.summary.rolledBack++;
          break;
        case 'needs_manual':
          session.summary.needsManual++;
          break;
        case 'skipped':
          session.summary.skipped++;
          break;
      }
    }

    session.completedAt = new Date().toISOString();
    return session;
  }

  private async attemptFix(diagnosis: DiagnosisResult): Promise<FixResult> {
    const startTime = Date.now();
    const strategy = this.strategyRegistry.get(diagnosis.category);

    if (!strategy) {
      return this.createResult(diagnosis, 'needs_manual', startTime);
    }

    try {
      const fix = await strategy.apply(diagnosis);

      if (!fix.success) {
        return this.createResult(diagnosis, 'needs_manual', startTime);
      }

      // 验证修复
      const verified = await this.verifyFix(diagnosis);

      if (verified) {
        return {
          ...this.createResult(diagnosis, 'applied', startTime),
          filesModified: fix.modifiedFiles,
          verificationPassed: true,
          rollbackNeeded: false,
          backupPaths: fix.backupPaths,
        };
      } else {
        // 验证失败，回滚
        if (this.options.enableRollback && fix.backupPaths) {
          await this.performRollback(fix);
        }
        return {
          ...this.createResult(diagnosis, 'rolled_back', startTime),
          filesModified: fix.modifiedFiles,
          verificationPassed: false,
          rollbackNeeded: true,
          backupPaths: fix.backupPaths,
        };
      }
    } catch (error) {
      console.error(`修复失败: ${error}`);
      return this.createResult(diagnosis, 'needs_manual', startTime);
    }
  }

  private async verifyFix(diagnosis: DiagnosisResult): Promise<boolean> {
    const tempOutput = `/tmp/verify-${diagnosis.id}.json`;
    
    const result = await this.jestRunner.run({
      testNamePattern: diagnosis.testResult.testName,
      jsonOutput: tempOutput,
    });

    if (fs.existsSync(tempOutput)) {
      try {
        const report = JSON.parse(fs.readFileSync(tempOutput, 'utf8'));
        return report.numPassedTests > 0 && report.numFailedTests === 0;
      } catch {
        return false;
      }
    }

    return result.success;
  }

  private async performRollback(fix: any): Promise<void> {
    for (const mod of fix.modifiedFiles || []) {
      if (mod.backupPath && fs.existsSync(mod.backupPath)) {
        rollback(mod.filePath, mod.backupPath);
      }
    }
  }

  private createResult(
    diagnosis: DiagnosisResult,
    status: FixResult['status'],
    startTime: number
  ): FixResult {
    return {
      id: generateId(),
      diagnosis,
      status,
      filesModified: [],
      verificationPassed: false,
      rollbackNeeded: false,
      backupPaths: [],
      attemptedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      durationMs: Date.now() - startTime,
    };
  }
}

interface FixSession {
  id: string;
  diagnosisSessionId: string;
  startedAt: string;
  completedAt?: string;
  results: FixResult[];
  summary: FixSummary;
}

interface FixSummary {
  totalAttempted: number;
  succeeded: number;
  rolledBack: number;
  needsManual: number;
  skipped: number;
}
```

### 3.2 修复策略库

**文件**: `scripts/agent-test/fixer/strategies.ts`

```typescript
import { DiagnosisResult, DiagnosisCategory } from '../types/diagnosis';
import { safeWriteFile } from '../utils/file-ops';
import fs from 'fs';

export interface FixStrategy {
  category: DiagnosisCategory;
  condition?: (diagnosis: DiagnosisResult) => boolean;
  apply: (diagnosis: DiagnosisResult) => Promise<FixResult>;
  priority: number;
}

export interface FixResult {
  success: boolean;
  modifiedFiles: FileModification[];
  backupPaths: string[];
}

export interface FileModification {
  filePath: string;
  operation: 'create' | 'modify' | 'delete';
  diff: string;
  backupPath?: string;
}

export class StrategyRegistry {
  private strategies: FixStrategy[] = [];

  constructor() {
    this.registerBuiltInStrategies();
  }

  register(strategy: FixStrategy): void {
    this.strategies.push(strategy);
    this.strategies.sort((a, b) => a.priority - b.priority);
  }

  get(category: DiagnosisCategory): FixStrategy | undefined {
    return this.strategies.find((s) => {
      if (s.category !== category) return false;
      if (s.condition && !s.condition(this.toDiagnosis(s))) return false;
      return true;
    });
  }

  private toDiagnosis(strategy: FixStrategy): DiagnosisResult {
    // 临时诊断对象用于条件检查
    return {} as DiagnosisResult;
  }

  private registerBuiltInStrategies(): void {
    // 策略 1: Mock 缺失修复
    this.strategies.push({
      category: 'mock_error',
      condition: (d) => d.errorMessage?.includes('op-sqlite'),
      apply: async (diagnosis) => {
        const mockPath = 'scripts/mocks/op-sqlite.ts';
        const current = fs.readFileSync(mockPath, 'utf8');
        
        // 检查 Mock 是否支持所需操作
        const neededOps = extractSqlOps(diagnosis.errorMessage);
        const missingOps = neededOps.filter(
          (op) => !current.includes(`execute${op}`)
        );

        if (missingOps.length === 0) {
          return { success: false, modifiedFiles: [], backupPaths: [] };
        }

        // 添加缺失的 Mock 实现
        const newImpl = missingOps
          .map((op) => generateMockImpl(op))
          .join('\n');

        const updated = current + '\n' + newImpl;
        const result = safeWriteFile(mockPath, updated, current);

        return {
          success: true,
          modifiedFiles: [
            {
              filePath: mockPath,
              operation: 'modify',
              diff: result.diff,
              backupPath: result.backupPath,
            },
          ],
          backupPaths: [result.backupPath],
        };
      },
      priority: 1,
    });

    // 策略 2: 可选链修复
    this.strategies.push({
      category: 'type_error',
      condition: (d) =>
        d.errorMessage?.includes("Cannot read properties of undefined"),
      apply: async (diagnosis) => {
        const { rootFile, rootLine } = diagnosis;
        if (!rootFile) return { success: false, modifiedFiles: [], backupPaths: [] };

        const content = fs.readFileSync(rootFile, 'utf8').split('\n');
        const line = content[rootLine - 1];
        
        // 检测需要添加可选链的位置
        const fixedLine = addOptionalChaining(line);
        
        if (fixedLine === line) {
          return { success: false, modifiedFiles: [], backupPaths: [] };
        }

        content[rootLine - 1] = fixedLine;
        const newContent = content.join('\n');
        const original = fs.readFileSync(rootFile, 'utf8');
        const result = safeWriteFile(rootFile, newContent, original);

        return {
          success: true,
          modifiedFiles: [
            {
              filePath: rootFile,
              operation: 'modify',
              diff: result.diff,
              backupPath: result.backupPath,
            },
          ],
          backupPaths: [result.backupPath],
        };
      },
      priority: 2,
    });

    // 策略 3: 快照更新
    this.strategies.push({
      category: 'regression',
      condition: (d) => d.errorMessage?.includes('Snapshot'),
      apply: async (diagnosis) => {
        // 快照不匹配需要人工判断，不自动修复
        return { success: false, modifiedFiles: [], backupPaths: [] };
      },
      priority: 10,
    });
  }
}

function extractSqlOps(errorMessage: string): string[] {
  const ops: string[] = [];
  if (errorMessage.includes('CREATE')) ops.push('Create');
  if (errorMessage.includes('INSERT')) ops.push('Insert');
  if (errorMessage.includes('SELECT')) ops.push('Select');
  if (errorMessage.includes('UPDATE')) ops.push('Update');
  if (errorMessage.includes('DELETE')) ops.push('Delete');
  return ops;
}

function generateMockImpl(operation: string): string {
  const lowerOp = operation.toLowerCase();
  return `
  // Auto-generated mock for ${operation}
  db.${lowerOp}Async = async (...args) => {
    console.log('[Mock op-sqlite] ${operation}:', args);
    return { rows: [] };
  };`;
}

function addOptionalChaining(line: string): string {
  // 简化实现：检测 obj.property 并添加可选链
  return line.replace(
    /(\w+)\.(\w+)(?!\?)\s*=/g,
    '$1?.$2='
  ).replace(
    /(\w+)\.(\w+)(?!\?)\./g,
    '$1?.$2.'
  );
}
```

---

## 4. 报告生成器

### 4.1 Markdown 报告

**文件**: `scripts/agent-test/reporter/markdown-reporter.ts`

```typescript
import { DiagnosisSession } from '../diagnostician/engine';
import { FixSession } from '../fixer/engine';
import fs from 'fs';
import path from 'path';

export function generateMarkdownReport(
  diagnosisSession: DiagnosisSession,
  fixSession: FixSession,
  outputDir: string
): string {
  const timestamp = new Date().toISOString();
  const reportPath = path.resolve(
    outputDir,
    `agent-test-report-${Date.now()}.md`
  );

  const report = `# Agent Test Report

> 生成时间: ${timestamp}  
> Git 分支: ${diagnosisSession.testReport.meta.gitBranch}  
> Git 提交: ${diagnosisSession.testReport.meta.gitCommit.slice(0, 8)}

---

## 执行摘要

| 指标 | 数值 |
|------|------|
| 总测试数 | ${diagnosisSession.testReport.summary.totalTests} |
| 通过 | ${diagnosisSession.testReport.summary.passed} |
| 失败 | ${diagnosisSession.testReport.summary.failed} |
| 跳过 | ${diagnosisSession.testReport.summary.skipped} |
| 通过率 | ${(diagnosisSession.testReport.summary.passRate * 100).toFixed(1)}% |

---

## 诊断摘要

| 类别 | 数量 |
|------|------|
${Object.entries(diagnosisSession.summary.byCategory)
  .map(([cat, count]) => `| ${cat} | ${count} |`)
  .join('\n')}

### 严重程度分布

| 严重程度 | 数量 |
|----------|------|
${Object.entries(diagnosisSession.summary.bySeverity)
  .map(([sev, count]) => `| ${sev} | ${count} |`)
  .join('\n')}

---

## 修复结果

| 状态 | 数量 |
|------|------|
| 成功修复 | ${fixSession.summary.succeeded} |
| 回滚 | ${fixSession.summary.rolledBack} |
| 需人工处理 | ${fixSession.summary.needsManual} |
| 跳过 | ${fixSession.summary.skipped} |

---

## 详细诊断结果

${diagnosisSession.diagnoses
  .map(
    (d, i) => `### ${i + 1}. ${d.testResult.testName}

**类别**: ${d.category}  
**严重程度**: ${d.severity}  
**置信度**: ${(d.fixConfidence * 100).toFixed(0)}%  
**根因位置**: \`${d.rootFile}:${d.rootLine}\`

${d.suggestedFix}

---
`
  )
  .join('\n')}

---

## 修复详情

${fixSession.results
  .filter((r) => r.status === 'applied')
  .map(
    (r, i) => `### ${i + 1}. ${r.diagnosis.testResult.testName}

**状态**: ✅ 已修复  
**修改文件**: ${r.filesModified.map((f) => `\`${f.filePath}\``).join(', ')}  

\`\`\`diff
${r.filesModified.map((f) => f.diff).join('\n')}
\`\`\`

---
`
  )
  .join('\n')}

---

*Report generated by Agent Test Framework v1.0*
`;

  fs.writeFileSync(reportPath, report);
  return reportPath;
}
```

---

## 5. 实施步骤

### Week 9

- [ ] 创建 `scripts/agent-test/diagnostician/engine.ts`
- [ ] 创建 `scripts/agent-test/diagnostician/error-patterns.ts`
- [ ] 创建 `scripts/agent-test/diagnostician/stack-trace.ts`
- [ ] 创建 `scripts/agent-test/diagnostician/root-cause.ts`
- [ ] 创建 `scripts/agent-test/diagnostician/git-bisect.ts`

### Week 10

- [ ] 创建 `scripts/agent-test/fixer/engine.ts`
- [ ] 创建 `scripts/agent-test/fixer/strategies.ts`
- [ ] 创建 `scripts/agent-test/fixer/code-modifier.ts`
- [ ] 创建 `scripts/agent-test/reporter/markdown-reporter.ts`
- [ ] 集成诊断修复到 CLI

---

*文档结束 — Phase 4 详细设计，包含完整的诊断引擎、错误模式库、修复策略库和报告生成器实现。*
