# 跨会话交接

## Done
- **模型过滤逻辑修复**: 彻底解决了原生 Android 设置页面中功能模型选择器（摘要、图像、嵌入、重排）列表为空的问题。
  - 通过在 `UserSettingsHomeScreen` 整合模型的 `type` 与 `capabilities` 字段，确保所有功能模型在 UI 映射层都能获得正确的 `ModelCapability` 标签。
  - 修复了 `ModelItem` 映射过程中 `contextLength` 丢失的问题。
- **UI 增强**: 在原生版本 `ProviderModelsScreen` 中补充了缺失的“图片”、“嵌入”、“重排”功能标签，防止在编辑过程中丢失元数据，并支持手动配置。
- **规格库更新**: 补全了 `native-ui` 中 `ModelSpecs.kt` 的常用模型规格，与 Web 端对齐。
- **Native RAG 持久化与动态模型修复**: 解决了原生 Android 版本高级 RAG 设置无法保存和抽取模型 mock 数据的问题。
  - 在 `RagViewModel` 中实现了 `SharedPreferences` 存储，覆盖了 30+ 项 RAG 配置参数。
  - 移除了 `RagAdvancedScreen` 中的 mock 抽取模型列表，改为从 `nexara_settings` 动态加载已配置模型。
  - 修复了抽取模型选择器点击后列表为空及显示名称不匹配的问题。
- **文档同步**: 更新了 `CHANGELOG.md` 至 v1.3.0，并增加了 RAG 专项说明。

## Next Steps
- 持续观察 RAG 设置在复杂场景下的持久化稳定性（如系统低内存回收后的恢复）。
- 建议未来考虑将 RAG 配置统一化存储，避免多屏重复定义 persistence key（当前已通过 ViewModel 统一管理，风险较低）。

## Risks
- 如果用户手动清除了应用数据，RAG 设置将重置为全局默认。
- `kgExtractionModel` ID 在对应的 Provider 被删除时，UI 目前仅显示“未选择”，需引导用户重新选择。

## DIA Status
- **CHANGELOG.md**: 已更新。
- **handover.md**: 已更新。
- **registry.md**: 无变更。
- **ARCHITECTURE.md**: 本项目暂无此文件。
- **strings.xml**: 已补全缺失的国际化字符串。
