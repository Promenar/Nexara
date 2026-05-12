# Nexara 交接文档

## 状态摘要 (2026-05-12)
本次会话完成了对 native-ui **提供商管理—模型管理—全站模型选择器**三大模块的深度审计，并制定了可分阶段并行执行的系统升级方案。

## 已完成工作 (Done)
- **深度架构审计**：系统性分析了 ProviderFormScreen、SettingsViewModel、ModelSpecs、ModelPicker、各协议实现层的 5 大缺陷
- **并行升级方案**：产出 `20260512-provider-model-parallel-audit.md`，包含 1 个基石阶段 + 3 个并行阶段 + 1 个集成验证阶段的详细拆分
- **每个阶段包含完整提示词**：可直接在 Gemini 3 Flash 独立会话中执行

## 已完成工作 (Done)
- **Phase 0 (2026-05-12) — 基石层 ✅**：ProviderManager、ProtocolType sealed class、ModelSpecs 刷新、SettingsViewModel/NexaraApplication 重构
- **Phase 1A/1B/1C (2026-05-12) — 并行执行 ✅** (Gemini 3 Flash, 2分钟)：
  - 1A: ProviderFormScreen 编辑回填 + ProtocolSelector 组件 + 动态标题
  - 1B: 4协议多模态升级 + GenericOpenAICompatProtocol + ProtocolFactory
  - 1C: UserSettingsHomeScreen/ModelPicker/ProviderModelsScreen/NavGraph 同步修复
- **Phase 2 (2026-05-12) — 集成收尾 ✅**：编译修复(3处import缺失+WEB→INTERNET回退)、协议残留搜索(0主代码)、CHANGELOG/handover更新
- **总计**：12 个新/改文件（含3个新文件），290+行净增，BUILD SUCCESSFUL

## 已知残留问题 (Known Issues)
- **测试文件 ProtocolId 引用**：3个测试文件(LlmProtocolSerializationTest/LlmProviderTest/ChatViewModelTest)仍引用 `ProtocolId.OPENAI` 等deprecated别名，通过typealias编译兼容但建议后续统一迁移为 `ProtocolType.OpenAI_ChatCompletions`
- **ModelInfo.capabilities 映射不完整**：`addCustomModel()` 和 `refreshModels()` 中的 capability 字符串映射仅覆盖 chat/vision/internet/reasoning，新增的 audio/video/structured_output 等维度暂未映射（需同步更新 SettingsViewModel 中的 mapping 逻辑）

## 待办事项 (Next Steps)
- [P2] 测试文件 ProtocolId → ProtocolType 迁移
- [P2] ModelInfo capabilities 字符串映射补完（audio/video/structured_output 等6维度）
- [P3] 协议选择器 UI 增强：为每种 ProtocolType 添加专属图标
- [P3] ProviderManager 单元测试

## 风险与注意点 (Risks)
- **Phase 0 上下文可能过大**：建议拆为 0A（数据模型）和 0B（基础设施+数据库）两个 Flash 会话
- **ModelSpecs 匹配顺序**：精确匹配必须在泛模式之前，否则 `gpt-4o` 会被 `gpt-4` 误匹配
- **SharedPreferences 协议类型迁移**：旧 `protocol_id` 键需兼容 `ProtocolId → ProtocolType` 映射
- **向后兼容必须严格保证**：所有新增数据类字段必须有默认值

## DIA Status
- **registry.md**: 待 Phase 2 更新
- **CHANGELOG.md**: 待 Phase 2 更新
- **ARCHITECTURE.md**: 待 Phase 2 更新（架构有重大变更：引入 ProviderManager 单例 + ProtocolType sealed class）


