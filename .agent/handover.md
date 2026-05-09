# 跨会话交接

## Done
- **模型过滤逻辑修复**: 彻底解决了原生 Android 设置页面中功能模型选择器（摘要、图像、嵌入、重排）列表为空的问题。
  - 通过在 `UserSettingsHomeScreen` 整合模型的 `type` 与 `capabilities` 字段，确保所有功能模型在 UI 映射层都能获得正确的 `ModelCapability` 标签。
  - 修复了 `ModelItem` 映射过程中 `contextLength` 丢失的问题。
- **UI 增强**: 在原生版本 `ProviderModelsScreen` 中补充了缺失的“图片”、“嵌入”、“重排”功能标签，防止在编辑过程中丢失元数据，并支持手动配置。
- **规格库更新**: 补全了 `native-ui` 中 `ModelSpecs.kt` 的常用模型规格，与 Web 端对齐。
- **文档同步**: 更新了 `CHANGELOG.md` 至 v1.3.0。

## Next Steps
- 观察用户反馈，确认在不同提供商（OpenAI, DeepSeek, 智谱）下模型识别的稳定性。
- 建议未来将 native-ui 的 `ModelSpecs.kt` 与 Web 端的 `model-specs.ts` 尝试通过某种自动化手段保持同步，避免维护多份。

## Risks
- 如果用户之前手动删除了某些模型的 tags，且这些模型不在规格库中，可能需要手动在模型管理页勾选新增加的标签。

## DIA Status
- **CHANGELOG.md**: 已更新。
- **handover.md**: 已更新。
- **registry.md**: 无变更。
- **ARCHITECTURE.md**: 本项目暂无此文件。
