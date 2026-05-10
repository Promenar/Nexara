# 跨会话交接文档

## 会话概况
- **时间**: 2026-05-10
- **核心任务**: RAG 体验细化与流式会话稳定性优化

## Done
- [x] **RAG 设置标准化**:
    - 统一使用 `ModelPicker` 替换手动模型选择器。
    - 在 KG 抽取模型选择中实装 `filterTag="chat"` 过滤。
    - 重命名“连接知识服务器”为“启用混合搜索”，实装可观测性开关。
- [x] **流式 UI 优化**:
    - `ThinkingBlock` 增加 `LaunchedEffect` 实现内容流式输出时自动展开。
    - 引入 `MarkdownText` 渲染思考内容。
- [x] **网络协议层加固**:
    - `OpenAIProtocol` 增加 30s 读超时保护（`withTimeoutOrNull`）。
    - 宽松 SSE 解析逻辑，支持 `data:` 无空格格式。
    - 修复 `ThinkingDetector` 的边界提取逻辑，增加 `flush` 机制确保内容完整性。
- [x] **文档同步**: 更新 `CHANGELOG.md` 及相关代码文档。

## Next Steps
- [ ] **RAG 向量检索验证**: 测试“启用混合搜索”开启后的实际检索链路连通性。
- [ ] **流式内容截断处理**: 针对超长思考内容增加最大显示高度 and 滚动支持。
- [ ] **本地模型 Phase 1**: 启动 llama.cpp 依赖引入与 JNI 桥接层编译。

## Risks
- **SSE 厂商差异**: 虽增加了宽松解析，但部分非标准 Provider 可能仍有特殊结束符。
- **内存压力**: 流式读取超大 buffer 结合 Markdown 频繁重组可能对低端机造成 GC 压力。

## DIA Status
- **DIA: 已完成**。更新了 `strings.xml`、`AdvancedRetrievalScreen`、`ChatInlineComponents`、`OpenAIProtocol`。CHANGELOG 已同步。
