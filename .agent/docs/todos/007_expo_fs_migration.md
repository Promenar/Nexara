# Technical Debt: Expo FileSystem Upgrade (SDK 54+)

## 1. 背景 (Background)
**ID**: 007
**Type**: Technical Debt / Maintenance
**Status**: Backlog
**Date**: 2026-02-04

目前项目在使用 Expo SDK 54 时，依赖了 `expo-file-system/legacy` 模块来维持现有的同步/异步文件读写接口。Expo 官方文档指出 `legacy` 模块旨在提供过渡期的兼容性支持，未来版本（预计 SDK 60+）将彻底移除该模块，转而全面采用基于 Web 标准的 `File` 和 `Directory` API。

当前受影响的关键模块：
- `src/lib/cache/cache-manager.ts`
- `src/components/ui/CachedSvgUri.tsx`
- 潜在的其他媒体处理模块

## 2. 风险评估 (Risk Assessment)
- **短期 (Short Term)**: 低风险。Legacy 模块在 SDK 54 环境下稳定运行，仅在控制台输出 Deprecation Warning。
- **长期 (Long Term)**: 高风险。若不迁移，未来的 SDK 升级将导致文件、图片缓存、下载功能彻底瘫痪。

## 3. 迁移目标 (Goals)
1.  **移除依赖**: 彻底移除 `import ... from 'expo-file-system/legacy'`。
2.  **重构核心**: 使用 `FileSystem.documentDirectory` 返回的 `Directory` 对象进行链式操作。
3.  **标准对齐**: 采用 Web Standard IO 模式（blob, arrayBuffer 等）。

## 4. 实施步骤 (Implementation Steps)

### Phase 1: 封装层重构 (Core Refactor)
重写 `CacheManager` 类，屏蔽底层 API 差异。

```typescript
// 伪代码示例：新版 API 风格
const cacheDir = new Directory(FileSystem.cacheDirectory + 'svg_cache/');
if (!cacheDir.exists) cacheDir.create();

const file = cacheDir.file('icon.svg');
const content = await file.text();
```

### Phase 2: 消费者适配 (Consumer Adaption)
更新所有调用方，如 `CachedSvgUri`。

### Phase 3: 回归测试 (Regression Testing)
- [ ] SVG 图标首次加载（下载 + 缓存）
- [ ] SVG 图标二次加载（读取缓存）
- [ ] 离线模式测试
- [ ] 缓存清理功能验证

## 5. 参考文档 (References)
- [Expo FileSystem API Docs](https://docs.expo.dev/versions/v54.0.0/sdk/filesystem/)
- [Migration Guide](https://docs.expo.dev/versions/v54.0.0/sdk/filesystem/#migration-from-legacy-api)
