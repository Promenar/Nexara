# Agent 自循环开发环境评估报告

> **评估日期**: 2026-05-03
> **评估目标**: 确定 Agent 自主完成 Kotlin 后端业务逻辑迁移所需的开发环境
> **工作分支**: `native-kotlin-refactor`

---

## 一、当前 IDE 环境评估

### 1.1 Codebuddy IDE（当前环境）

| 维度 | 评估 | 结论 |
|------|------|------|
| **文件读写** | ✅ 完备 | 可创建/编辑任意 Kotlin 文件 |
| **代码搜索** | ✅ 完备 | `code-explorer` subagent + `search_content` |
| **终端执行** | ⚠️ 受限 | PowerShell 可用，但无 `gradlew`/`kotlinc` |
| **Gradle 编译** | ❌ 不可用 | 项目缺少 `gradle/wrapper/` 目录，无法执行编译 |
| **Kotlin 编译** | ❌ 不可用 | `kotlinc` 不在 PATH |
| **单元测试执行** | ❌ 不可用 | 依赖 Gradle，而 Gradle 不可用 |
| **Android SDK** | ⚠️ 版本不匹配 | 本地有 `android-36.1`，项目需 `android-35`（36.1 向下兼容） |
| **Node.js** | ✅ v24.15.0 | 满足所有 JS/TS 工具需求 |
| **自循环能力** | ❌ 无法闭环 | 写代码→编译→测试→修复 的循环无法在终端完成 |

### 1.2 核心结论

**Codebuddy IDE 可以完成「架构设计 + 代码编写 + 文件管理」，但无法完成「编译验证 + 测试运行」环节。**

Agent 自循环的闭环是：
```
编写 Kotlin → 编译 → 运行测试 → 分析失败 → 修改 → 重新编译
                   ↑_________________________|
```

Codebuddy 在「编译」和「运行测试」两个节点断裂，无法形成闭环。

---

## 二、Agent 自循环开发的核心需求

要让 Agent 自主完成迁移，开发环境**必须**满足：

| # | 需求 | 原因 |
|---|------|------|
| 1 | **Gradle/Kotlin 编译能力** | 写完代码必须能编译验证语法和类型正确性 |
| 2 | **单元测试执行能力** | 对比测试是验证迁移正确性的核心手段 |
| 3 | **Shell 命令自主执行** | Agent 需要不经人工批准就执行 `./gradlew test` |
| 4 | **第三方 API 网络访问** | Agent 需要用真实 API Key 测试 LLM Provider |
| 5 | **长时间无中断运行** | 单个 Provider 迁移可能需要数小时迭代 |
| 6 | **上下文持久化** | 跨会话记忆迁移进度和未完成任务 |
| 7 | **错误输出捕获** | 编译错误和测试失败输出必须完整返回给 Agent |

---

## 三、候选工具评估

### 3.1 Codex CLI（OpenAI） — ★★★★★ 首选推荐

| 维度 | 能力 | 说明 |
|------|------|------|
| **自循环能力** | ✅ `full-auto` 模式 | 读写文件 + 执行 Shell 命令，无需人工批准 |
| **第三方 API** | ✅ 完全支持 | 内置 OpenRouter/DeepSeek/Gemini/Ollama，也支持自定义 base_url |
| **网络访问** | ⚠️ `full-auto` 禁用网络 | 可在 `auto-edit` 模式下人工批准网络命令，或配置沙盒白名单 |
| **编译/测试** | ✅ Shell 执行 | `./gradlew compileDebugKotlin` / `./gradlew test` 自主运行 |
| **长时间运行** | ✅ 无硬性限制 | Codex CLI 0.36+ 支持连续 7 小时自主工作 |
| **项目指令** | ✅ `AGENTS.md` | 可定义项目级规范（Kotlin 编码风格、测试策略等） |
| **记忆系统** | ✅ 自动记忆 | 跨会话自动提取关键信息 |
| **WSL2 支持** | ✅ 明确支持 | Windows 通过 WSL2 运行 |
| **安装** | ✅ 极简 | Rust 原生二进制，`npm install -g @openai/codex` |

**网络访问解决方案**:
- 将 LLM API 调用封装为 Gradle test task，测试本身不需要 Agent 直接访问网络
- 或使用 `suggest` 模式 + 人工批准关键网络命令
- 或通过 MCP Server 代理 API 调用

**推荐配置**:
```bash
# WSL2 中安装
npm install -g @openai/codex

# 配置模型（使用 OpenRouter 聚合，支持 GLM-5/DeepSeek/Claude 等）
export OPENROUTER_API_KEY="your-key"
codex --provider openrouter --model anthropic/claude-sonnet-4

# 或使用自定义中转
export OPENAI_API_KEY="your-key"
export OPENAI_BASE_URL="https://your-relay.com/v1"
```

### 3.2 Claude Code（Anthropic） — ★★★★☆ 备选

| 维度 | 能力 | 说明 |
|------|------|------|
| **自循环能力** | ✅ auto mode | 可自主执行编译和测试命令 |
| **第三方 API** | ⚠️ 需中转 | 原生仅支持 Anthropic 模型，需通过 Claude Code Router 接入其他模型 |
| **编译/测试** | ✅ 完备 | Shell 命令自主执行 |
| **长时间运行** | ✅ 支持 | 但 Anthropic API 限速策略较严格 |
| **上下文** | ✅ CLAUDE.md + 自动记忆 | |
| **WSL2** | ✅ 支持 | |

**限制**: 
- 强绑 Anthropic 生态，接入 GLM-5 等国产模型需额外配置
- API 限速和降智问题社区反馈较多

### 3.3 Android Studio Panda 3 Agent Mode — ★★★☆☆ 辅助

| 维度 | 能力 | 说明 |
|------|------|------|
| **自循环能力** | ⚠️ 部分支持 | 可执行 Shell 命令，但未明确描述全自动闭环 |
| **第三方 LLM** | ✅ 支持 | 可集成远程第三方 LLM |
| **Skills 系统** | ✅ `.skills/SKILL.md` | 可自定义工作流 |
| **Compose Live Edit** | ✅ 原生支持 | 视觉迭代最佳 |
| **长时间无人值守** | ⚠️ 需频繁授权 | 权限模型更保守 |

**定位**: 更适合前端 UI 迭代（Live Edit），不适合后端长时间自主迁移循环。

### 3.4 Cursor Agent Mode — ★★★☆☆

| 维度 | 能力 | 说明 |
|------|------|------|
| **自循环能力** | ✅ Agent Mode | 可自动创建文件、运行命令、修复 Bug |
| **第三方 API** | ✅ BYOK | 支持自定义 API 地址 |
| **长时间运行** | ⚠️ 受限 | 单次 Agent 运行有 token 上限 |
| **Kotlin 支持** | ⚠️ 一般 | 主要优化前端场景 |

---

## 四、推荐方案：Codex CLI + Android Studio 双轨

### 4.1 后端业务逻辑迁移 → Codex CLI

```
WSL2 终端:
  codex --approval-mode full-auto \
    "按照 AGENTS.md 中的迁移规范，将 src/lib/llm/stream-parser.ts 
     迁移为 Kotlin 实现。要求：
     1. 逐函数翻译，保持语义一致
     2. 编写对比单元测试
     3. 运行 ./gradlew test 验证通过
     4. 失败则分析原因并修复，直到全部测试通过"

Agent 自循环:
  读取 TS 源码 → 编写 Kotlin → 执行编译 → 运行测试
    → 失败？→ 分析错误 → 修改代码 → 重新编译测试
    → 成功？→ 提交 → 下一个文件
```

### 4.2 前端 UI 迭代 → Android Studio Panda 3

```
Android Studio:
  - Live Edit 实时预览 Compose UI 修改
  - Layout Inspector 调试布局
  - Agent Mode 辅助 UI 组件生成
```

### 4.3 架构设计 + 协调 → Codebuddy（当前环境）

```
Codebuddy IDE:
  - 架构设计（Mermaid 图 + 计划文档）
  - 大规模代码探索和依赖分析
  - 迁移任务拆解和进度追踪
  - 质量审计和代码审查
```

---

## 五、实施前环境准备清单

### 5.1 必须解决

| # | 准备项 | 操作 | 状态 |
|---|--------|------|------|
| 1 | **安装 WSL2 Ubuntu** | Windows 功能启用 + Ubuntu 发行版 | 需确认 |
| 2 | **安装 Codex CLI** | `npm install -g @openai/codex` | 待执行 |
| 3 | **恢复 Gradle Wrapper** | 项目缺少 `android/gradle/wrapper/`，需重新生成 | ❌ 缺失 |
| 4 | **安装 JDK 17+** | Kotlin 2.1.20 + AGP 8.2.1 要求 | 需确认 |
| 5 | **配置 API Key** | Codex CLI 的模型提供商 Key | 待配置 |
| 6 | **安装 android-35 SDK** | 项目 compileSdk=35，本地仅有 36.1（兼容但建议安装） | 可选 |

### 5.2 关于 Gradle Wrapper 缺失

当前项目 `android/gradle/wrapper/` 目录不存在。这通常是因为 `.gitignore` 排除了 Gradle Wrapper 文件。在 WSL2 环境中需要重新生成：

```bash
# WSL2 中
cd /mnt/k/Nexara/android
# 方式一：如果系统有 gradle
gradle wrapper --gradle-version 8.5
# 方式二：手动下载 gradlew
```

### 5.3 推荐的 AGENTS.md 配置

在项目根目录创建 `AGENTS.md`（Codex CLI 的项目指令文件），定义迁移规范：

```markdown
# Nexara Kotlin 迁移规范

## 架构约束
- 所有 Kotlin 代码位于 native-ui/ 模块中
- 使用 Ktor 替代 OkHttp（Multiplatform-Ready）
- 使用 Room 替代直接 SQLite 操作
- 使用 kotlinx.serialization 替代 Gson

## 编码规范
- 每个 TS 函数必须有对应的 Kotlin 函数
- 保持函数名语义一致（驼峰命名）
- 使用 Flow/StateFlow 替代回调
- 使用 sealed class 替代联合类型

## 测试策略
- 每个 Provider 必须有对比测试
- 测试 API Key 从环境变量读取
- 使用 JUnit 5 + MockK
- 运行测试: ./gradlew :native-ui:test

## 迁移顺序
1. data/model/ (数据类)
2. data/remote/ (SSE + Provider)
3. data/repository/ (仓库层)
4. 存储层 (Room)
```

---

## 六、结论

### 当前 Codebuddy IDE 能否支撑 Agent 自循环？

**不能。** 缺少 Gradle 编译和测试执行能力，无法闭合验证循环。

### 最优方案

**Codex CLI（WSL2） + Android Studio Panda 3（Windows） + Codebuddy（架构协调）**

- **Codex CLI**: 后端业务逻辑自主迁移（编译→测试→修复闭环）
- **Android Studio**: 前端 Compose UI 快速迭代
- **Codebuddy**: 架构设计、任务拆解、质量审计

### 下一步行动

1. 确认 WSL2 环境是否可用
2. 安装 Codex CLI 并配置 API Key
3. 恢复 Gradle Wrapper
4. 创建 `AGENTS.md` 迁移规范
5. 创建独立的 `:native-ui` 模块
6. 启动第一个文件的迁移自循环验证

---

*评估完成*
