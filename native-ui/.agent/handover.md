# 跨会话交接文档

## 会话概况
- **时间**: 2026-05-10
- **核心任务**: 审计 native-ui 端侧本地模型功能完整性，确认占位符状态并规划补完实施方案

## Done
- [x] **审计完成**: 确认 `LocalModelsScreen` 及全套端侧推理链路为 100% 占位符（零 ML 依赖、零推理代码、零模型文件）
- [x] **技术选型**: llama.cpp + JNI 绑定 + GGUF 格式 + Vulkan GPU 加速
- [x] **方案文档**: 创建 `.agent/plans/20260510-local-model-implementation.md`，含 6 阶段 14-22 天实施计划
- [x] **文档同步**: 更新 `AGENTS.md`、`ARCHITECTURE.md`、`CHANGELOG.md`、`handover.md`

## TS 原版参考对照表

| 模块 | TS 原版 | Kotlin 待实现 |
|------|---------|---------------|
| 推理状态管理 | `LocalModelServer.ts` | `LocalInferenceEngine.kt` |
| 模型文件管理 | `ModelStorageManager.ts` | `ModelStorageManager.kt` |
| LLM 协议适配 | `local-llm.ts` | `LocalProtocol.kt` |
| JNI 桥接 | `llama.rn` npm 包 | `cpp/native-lib.cpp` |
| 本地 Embedding | `embedding.ts:embedLocal()` | `LocalInferenceEngine.embed()` |
| 本地 Reranker | `reranker.ts` | `LocalInferenceEngine.rerank()` |
| 设置开关 | `settings-store.ts:localModelsEnabled` | `SettingsViewModel.localModelsEnabled` |

## Next Steps
- [ ] **Phase 1**: 引入 llama.cpp 依赖 + 编译 JNI 桥接层 + 冒烟测试
- [ ] **Phase 2**: 实现 `LocalInferenceEngine` 三槽位推理引擎
- [ ] **Phase 3**: 实现 `ModelStorageManager` GGUF 文件管理
- [ ] **Phase 4**: 实现 `LocalProtocol` 并集成到现有 Provider 架构
- [ ] **Phase 5**: 改造 `LocalModelsScreen` + `SettingsViewModel`
- [ ] **Phase 6**: `NexaraApplication` 集成 + 生命周期管理

## Risks
- **JNI 稳定性**：llama.cpp JNI 层崩溃调试困难，需充分单元测试覆盖
- **OOM 风险**：7B 模型在 4GB RAM 设备上可能 OOM，需实现内存监控和自动降级
- **Vulkan 兼容性**：部分设备 Vulkan 驱动不完整，需自动检测降级 CPU NEON
- **依赖选型风险**：当前使用社区维护的 llama-android AAR，需定期追踪上游变更

## DIA Status
- **DIA: 已完成**。已更新 `AGENTS.md`（排除范围 → 待开发）、`ARCHITECTURE.md`（新增本地推理模块架构图）、`CHANGELOG.md`（记录审计与方案），方案文档已存档。
