# 会话 P2、图片附件与 RAG 检索指示器（2026-10-16）

## 目标

1. 修复图片附件发送必然失败的问题。
2. 修复会话主流程审计的 P2 项：预算耗尽收尾、待办面板、废弃代码、`web_fetch` 外带风险。
3. 新增 RAG 检索指示器：每条回复可见是否命中、命中哪些、排序分数，以及重排是否生效、如何改变名次。

## 非目标

- 不升级 Room 版本、不改表结构；新增的检索轨迹字段写入已有 JSON 列，旧数据按默认值读取。
- 不改动检索算法本身（阈值、融合公式、摘要类型的取舍）；只如实记录并展示。
- 不接入推理强度（reasoning effort）。

## 工作包与验收

| # | 问题 | 修复 | 验收 |
|---|------|------|------|
| A | 图片附件发送报 “Image read failed”：只读尺寸时 `decodeStream` 按设计返回 null，被当作读取失败 | 抽出 `ChatImageEncoder`，以尺寸是否有效判定可读；解码失败才报错，错误文案本地化 | Robolectric 真实图形模式：PNG/JPEG 编码成功、超大图降采样、非图片报错；修复前失败 |
| P2-1 | 预算耗尽后仍声明工具，模型再调用工具时以空回答“成功”；审批恢复后预算从零计数 | 收尾模式请求携带 `tool_choice=none`（OpenAI 系 `"none"`、Anthropic `{"type":"none"}`）；收尾轮仍返回工具调用且无正文时以明确失败结束；审批恢复按本轮已消耗的轮次与调用数续算 | Runner 与 Runtime 单测；协议序列化单测 |
| P2-2 | 待办面板只显示当前一步；全部完成后直接消失；“标记完成”不可撤销 | 面板可展开完整清单；全部完成显示完成态并可清除；“标记完成”移入 ViewModel，提供撤销 | TaskPanelState 与 ViewModel 单测 |
| P2-3 | 废弃代码：续跑审批分支与 `continuationBudget`、无调用方的审批超时、从未赋值的 `Session.activeTask` | 删除续跑分支（遗留的非工具审批请求直接关闭）、超时入口与预算字段；工具步骤关联改读当前计划焦点 | ApprovalManager 测试更新；编译期保证无残留引用 |
| P2-4 | 半自动模式下 `web_fetch` 免审批，可经网址参数外带数据 | 网址携带查询参数、用户信息、超长或编码样式路径段时，半自动模式要求审批 | ContentStrategy 单测覆盖放行与拦截样例 |
| R | RAG 检索缺少指示器 | 检索层产出检索轨迹（结果、召回数、采用数、计分方式、重排方式与耗时、来源分布）与逐条召回名次、召回分、重排分；未命中与不可用也持久化；检索卡显示命中摘要，详情页显示轨迹与重排前后名次 | MemoryManager、ContextBuilder、ViewModel 映射单测；截图基线 |

## 验收命令

```bash
cd native-ui && ./gradlew :app:testDebugUnitTest
cd native-ui && ./gradlew :app:lintDebug
python3 scripts/ci/check-text-wrapping.py
```

设备回归：API 35 专用模拟器运行 `scripts/ci/android-device-core-e2e.sh`（含经 MediaStore `content://` 图片的附件编码用例）。

## 风险

- 部分 OpenAI 兼容服务可能不接受 `tool_choice: "none"`；仅在收尾轮发送，失败时走既有错误呈现。
- `web_fetch` 拦截规则是启发式，不能防住所有编码方式；手动模式仍逐个审批。

## 执行结果（2026-10-16）

- 7 个工作包全部完成。图片附件先以 Robolectric 真实图形模式复现（旧逻辑下 2 条“应编码成功”用例失败），修复后通过；另加设备侧用例，经 MediaStore 生成真实 `content://` 图片走同一编码路径，纳入设备矩阵。
- P2-1：协议层新增 `ProtocolToolChoice`，收尾轮发送 `NONE`；收尾轮仍只返回工具调用时以新失败码 `TOOL_BUDGET_EXHAUSTED` 结束（原契约测试按“空回答成功”编写，已改为断言失败）；审批恢复按本轮已消耗用量续算。
- P2-2：面板清单、完成态与清除、标记完成撤销；`TaskPlanActions` 以内存 Room 验证完成与原样恢复。
- P2-3：续跑审批分支、预算字段与审批超时入口删除；遗留非精确审批在操作时关闭并持久化清空；工具步骤关联当前计划焦点。
- P2-4：`ToolArgumentApprovalRules` 对 `web_fetch` 网址做启发式判定，仅半自动模式生效。
- R：`RagRetrievalTrace` 写入 `RagMetadata` 既有 JSON 列，`RagReference` 增加召回名次与来源类型；重排客户端返回实际方式（API、大模型、跳过、失败）。检索卡与详情页按文本换行门禁以 `NexaraMetaLine` 分段展示。
- 验证：JVM 单元测试 2911 项全部通过；Lint 0 错误；文本换行门禁通过；截图校验通过；设备 API 35 完整矩阵 143/143、API 31 最低矩阵 103/103。
- 截图：待办面板三张预览与 RAG 详情预览按预期变化，新增“待办清单展开/完成态”“RAG 检索卡结果”两张预览，已人工复核并更新基线。DocEditor 平板预览存在既有的容差内渲染抖动，干净 HEAD 上同样出现，与本次无关。
- 设备回归中发现并修复：审批卡出现时可能被输入栏与键盘遮挡（自动跟随只对齐最后一条消息）。MainActivity 聊天流程用例原以已删除的续跑审批验证“批准后重新生成”，改为验证遗留审批经同一 ViewModel 关闭、持久化清空且不发起生成；精确审批的执行与续生成由 ViewModel 单测与真实网关用例覆盖。
- 未覆盖：物理设备；从相册选图并发给真实模型的完整界面流程；真实 Provider 对 `tool_choice: "none"` 的端到端请求（以序列化单测验证）；真实重排服务（以替身验证）。
