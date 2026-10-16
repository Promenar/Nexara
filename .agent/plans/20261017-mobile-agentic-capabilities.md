# 移动端 Agent 能力增强与知识图谱修复（2026-10-17）

## 目标

1. 让手机端 Agent 具备：原生 SSH、SAF 授权的本地文件读写、无固定上限的工具循环、隔离沙箱内可联网的原生脚本执行、Termux 本地终端接入。
2. 修复知识图谱（KG）的正确性问题，并让持久图谱进入聊天检索。

## 非目标

- 不做 Android 端完整 IDE；完整 Linux 环境交给 Termux。
- 不在主进程内运行任何用户 / 模型提供的脚本。
- 不把凭据、私钥、密码交给模型；模型只见主机别名与脱敏状态。

## 已确定的产品决定

| 编号 | 决定 |
|---|---|
| D1 | 持久知识图谱进入聊天检索（KG-C）。 |
| D2 | 以 Gradle 产品风味区分 `play` 与 `sideload` 两个发行版本；“所有文件访问”只存在于 `sideload`，代码共享，`play` 的清单不声明该权限。 |
| D3 | Python 不预先承诺；先完成 Termux（SSH 路线）与 QuickJS，再以技术验证判定 Chaquopy 的增量价值，安装包增加数十 MB 可接受。 |
| D4 | 无限制轮次模式允许与全自动审批组合，由用户选择；界面明确提示风险，预算与循环检测仍默认开启且可调。 |

## 现状事实（2026-10-17，HEAD 06c3c5b7）

- 工具注册见 `NexaraApplication.kt` 的 `presetSkillRegistry`；工作区工具只作用于应用私有目录；`exec_js` 为一次性 WebView，网络与文件全关、5 秒超时。
- `infra/fs/SafImportHandler.kt` 存在但无任何调用方。
- 工具预算：轮次 `coerceIn(1, 100)`（`DefaultChatGenerationRuntime.kt` 的 `toolBudget`），单次生成调用上限 `MAX_TOOL_CALLS_PER_GENERATION = 200`。
- 会话 `summary` 只有读取方，未发现自动写入；上下文按消息条数窗口截取，单轮内工具结果持续累积。
- `ToolRisk.PROCESS` 已定义但无工具使用；MCP 工具默认风险 `UNKNOWN`。
- MCP 仅支持 Streamable HTTP 且强制 HTTPS；全局 `cleartextTrafficPermitted="false"`。
- HTML 预览的 `RichContentWebViewPool` 开启 JS 且未限制网络。
- 当前无产品风味，构建类型为 debug / deviceTest / minifiedTest / release；CI 与脚本引用 `testDebugUnitTest`、`assembleDebug`、`lintDebug`、`assembleRelease`、`assembleDeviceTest`、`assembleMinifiedTest`。
- Termux 清单注册 `TermuxDocumentsProvider`，其主目录可经 SAF 授权访问。

## 工作包

### P0 执行底座

1. 启用 `ToolRisk.PROCESS`；审批卡展示执行目标（主机 / 路径 / 命令）；新增“本会话内对此目标始终允许”授权。
2. 无限制模式：轮次可设“不限”，并配套：
   - 单轮上下文压缩（早期工具结果折叠为摘要，摘要可持久化到会话）；
   - 大输出落工作区文件，模型只收头尾与路径；
   - 循环检测（同工具同参数重复 N 次暂停）；
   - 单任务 token / 费用预算与实时计数；
   - 前台通知进度与随时停止。
   与全自动审批组合时给出一次性明确风险确认（D4）。
3. 核实并收紧 HTML 预览 WebView 的网络与文件访问。
4. 产品风味 `distribution { play, sideload }`（D2）：同步 CI、发行脚本、验证账本与文档中的任务名。

验收：预算与循环检测单测；上下文压缩在长工具链下的回归测试；两个风味均可构建、`play` 产物清单不含 `MANAGE_EXTERNAL_STORAGE`。

### P1 SAF 工作区

- `WorkspaceBackend` 抽象：`Internal`（现有）、`SafTree`、`RealPath`（仅 `sideload`，所有文件访问）。
- SAF 写入使用乐观并发（大小 + 修改时间 + 哈希前置条件）；目录用 `DocumentsContract` 批量查询并在 Room 缓存索引；RAG 增量扫描。
- 删除：应用侧回收副本或强制 DELETE 级审批。
- 界面说明系统限制：Android 11+ 不可选内部存储根、可靠 SD 卡根、`Download`，不可选 `Android/data`、`Android/obb` 内文件；持久授权上限 512。
- 支持挂载 Termux 主目录。

验收：后端契约测试（三种实现共用）；冲突写入被拒；设备上授权、重启后恢复、撤销授权后的降级。

### P2 原生 SSH

- 技术验证在 sshj 与 mwiede/jsch 间选型：Ed25519 / rsa-sha2 / ECDSA、R8、known_hosts、PTY、SFTP、保活。
- 主机配置：密钥（应用内生成，Android Keystore 包裹）或密码（现有密钥存储）；首次连接指纹确认，变化即拦截。
- 工具：`ssh_exec`（非交互、超时、输出上限）、`sftp_list` / `sftp_read` / `sftp_write`；第二阶段 `ssh_session`（常驻会话）、用户终端界面、端口转发。
- Termux 路线 C：Termux 运行 sshd，Nexara 连接 `localhost:8022`。

验收：对测试 sshd 的集成测试（认证方式、指纹变更拦截、超时、截断）；凭据不出现在提示词、日志与工具结果中。

### P3 原生脚本运行时

- `isolatedProcess` 服务承载引擎；文件经工作区后端、网络经主进程代理（复用 `web_fetch` 公网策略，可加用户局域网白名单）；超时、内存、输出上限，超时结束进程。
- JS：QuickJS 替换 WebView 方案，提供 `fetch` / `fs` / `console`；`run_script(path)` 执行工作区脚本。
- Python（D3）：Chaquopy 技术验证——隔离进程能否初始化、实测包体增量、可用库范围；不满足隔离要求则不引入，交给 Termux。

验收：沙箱逃逸用例（读应用私有文件、访问内网地址、超时、超内存）全部被拒。

### P4 Termux 接入

- 路线 C（SSH）为首选，依赖 P2。
- 路线 A：`termux_exec` 通过 RUN_COMMAND 后台执行；需 Termux 0.109+、`allow-external-apps=true`、`com.termux.permission.RUN_COMMAND` 授权与 `<queries>` 包可见性；输出合计截断 100KB。
- 路线 B（本机 MCP，环回 HTTP + 令牌）列为高级选项。
- 引导页说明 Android 12+ 子进程限制可能结束 Termux 后台进程，以及唤醒锁设置。

### KG 修复

- KG-A 正确性（无表结构变更）：KG 模型按稳定 ID 路由到所属 Provider 并只发送远端模型 ID；成功空图合法化；手动抽取进入持久队列并事务提交；断点身份含内容版本 / 模型 / 提示词摘要 / 切块参数；JIT 超时与取消透传；入库、提示词、展示共用同一份已校验图；手动抽取复用文档解析器；配置变更使抽取器失效。
- KG-B 数据模型（Room 迁移）：实体与来源证据分表，按作用域唯一约束与事务 upsert；JIT 记录来源文档；删除只清证据，无存活引用才删实体；修正双库合并的文档 ID 映射；持久化建图意图与完成状态。
- KG-C 图检索（D1）：从查询与 RAG 命中选种子实体，1–2 跳扩展，节点 / 边 / token 预算，与向量结果融合；作用域与会话 / Agent 开关一致。
- KG-D：文档级调用 / token / 时长预算；大图按需加载、布局移出主线程、预建端点索引、线段视口相交；清理无消费者接口；按实现更正 PRD / ADR-018 / ARCHITECTURE_DESIGN。

## 顺序与并行

1. P0。
2. P1、P2、KG-A 并行（写入范围不重叠，各自 worktree）。
3. P4（C、A）、KG-B。
4. P3 JS、KG-C。
5. P3 Python 技术验证与取舍、KG-D。

SSH、脚本沙箱、KG-B 迁移完成后由未参与施工者独立审阅。

## 通用验收命令

```bash
cd native-ui && ./gradlew :app:testDebugUnitTest
cd native-ui && ./gradlew :app:lintDebug
python3 scripts/ci/check-text-wrapping.py
```

引入产品风味后，任务名随风味更新，以届时 CI 为准。
