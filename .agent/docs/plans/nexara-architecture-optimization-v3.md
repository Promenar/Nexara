# Nexara 架构优化实施方案 (v3)

> **Status**: Ready for Implementation
> **Created**: 2026-02-18
> **Based on**: 012_nexara_optimized_implementation_v1.md + vector-search-turbomodule-plan.md
> **Prerequisite**: React Native New Architecture (已启用)

---

## 1. 实施优先级排序

| 优先级 | 任务 | 工作量 | 风险 | 依赖 |
|--------|------|--------|------|------|
| 🔴 P0 | Vector Search TurboModule | 7.5h | 中 | 无 |
| 🟠 P1 | Audit Logging | 3h | 低 | 无 |
| 🟡 P2 | PDF Robustness | 3h | 低 | 无 |
| 🟢 P3 | 扩展功能面板集成 | 1h | 低 | 无 |

**推荐执行顺序**: P0 → P1 → P2 → P3

---

## 2. Phase 0: Vector Search TurboModule (7.5h)

### 2.1 问题背景

当前 `vector-store.ts` 在主线程执行相似度计算，即使有 `setTimeout` 让步机制，仍会阻塞 UI：

```
当前流程: UI Thread → DB Query → JS Loop (阻塞) → Sort → Return
目标流程: UI Thread → DB Query → Native C++ (并行) → Return
```

### 2.2 技术方案

| 技术 | 选择 | 理由 |
|------|------|------|
| 模块类型 | TurboModule | 新架构标准，支持 JSI 零拷贝 |
| 数据传递 | Float32Array (JSI) | 避免 JSON 序列化开销 |
| 并行计算 | OpenMP (可选) | 大规模向量时启用多线程 |
| 平台支持 | Android + iOS | 双平台原生实现 |

### 2.3 文件清单

#### 新建文件

| 文件路径 | 说明 |
|----------|------|
| `src/native/VectorSearch/NativeVectorSearch.ts` | TypeScript Spec |
| `src/native/VectorSearch/index.ts` | 公开接口 |
| `android/app/src/main/jni/VectorSearchModule.cpp` | C++ 实现 |
| `android/app/src/main/jni/VectorSearchModule.h` | C++ Header |
| `android/app/src/main/jni/CMakeLists.txt` | 构建配置 |
| `android/app/src/main/java/.../VectorSearchModule.java` | Java 桥接 |
| `android/app/src/main/java/.../VectorSearchPackage.java` | Package 注册 |

#### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `src/lib/rag/vector-store.ts` | 集成原生模块，添加 JS 降级 |
| `android/app/build.gradle` | 添加 CMake 配置 |
| `android/app/.../MainApplication.kt` | 注册 Package |

### 2.4 性能预期

| 向量数量 | JS 耗时 | Native 耗时 | 提升 |
|----------|---------|-------------|------|
| 100 | ~10ms | ~2ms | 5x |
| 500 | ~50ms | ~8ms | 6x |
| 1000 | ~100ms | ~15ms | 7x |
| 5000 | ~500ms | ~60ms | 8x |

### 2.5 实施步骤

```
Step 1: 创建 TypeScript Spec (0.5h)
Step 2: Android C++ 实现 (3h)
Step 3: iOS 实现 (2h) - 需先运行 expo prebuild
Step 4: VectorStore 集成 (1h)
Step 5: 测试验证 (1h)
```

---

## 3. Phase 1: Audit Logging (3h)

### 3.1 问题背景

文件操作缺乏审计追踪，无法满足安全合规需求。

### 3.2 数据库 Schema

```sql
CREATE TABLE IF NOT EXISTS audit_logs (
  id TEXT PRIMARY KEY NOT NULL,
  action TEXT NOT NULL,              -- 'read' | 'write' | 'delete' | 'list'
  resource_type TEXT NOT NULL,       -- 'file' | 'document' | 'sandbox'
  resource_path TEXT,
  session_id TEXT,
  agent_id TEXT,
  skill_id TEXT,
  status TEXT NOT NULL,              -- 'success' | 'error'
  error_message TEXT,
  metadata TEXT,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_session ON audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);
```

### 3.3 文件清单

#### 新建文件

| 文件路径 | 说明 |
|----------|------|
| `src/lib/services/audit-service.ts` | 审计服务 |

#### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `src/lib/db/schema.ts` | 添加 audit_logs 表 |
| `src/lib/skills/definitions/filesystem.ts` | 注入审计日志 |

### 3.4 实施步骤

```
Step 1: 添加 audit_logs 表 (0.5h)
Step 2: 创建 audit-service.ts (1.5h)
Step 3: 注入 Skill 审计 (1h)
```

---

## 4. Phase 2: PDF Robustness (3h)

### 4.1 问题背景

大型 PDF (10MB+) 的 Base64 字符串可能超过 JS 引擎字符串长度限制，导致 WebView 内存溢出。

### 4.2 解决方案

使用文件 URI 模式，让 PDF.js 直接读取文件，避免大字符串注入：

```
小文件 (<5MB): Base64 模式 (现有方案)
大文件 (>=5MB): 写入临时文件 → URI 模式
```

### 4.3 文件清单

#### 修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `src/components/rag/PdfExtractor.tsx` | 添加 URI 模式支持 |

### 4.4 实施步骤

```
Step 1: 修改 PdfExtractor.tsx (2h)
Step 2: 测试大文件 PDF (1h)
```

---

## 5. Phase 3: 扩展功能面板集成 (1h)

### 5.1 问题背景

统一扩展中心组件已开发完成，但未集成到 Expo 主布局。

### 5.2 集成步骤

**文件**: `app/_layout.tsx`

```tsx
// 1. 添加导入
import { UnifiedExtensionCenter } from '../src/components/extension-framework/UnifiedExtensionCenter';

// 2. 在 <Stack>...</Stack> 后添加
<UnifiedExtensionCenter style={{ position: 'absolute', bottom: 20, right: 20 }} />
```

### 5.3 验证清单

- [ ] 右下角出现"扩展"按钮
- [ ] 点击按钮弹出浮动面板
- [ ] 面板包含功能类别
- [ ] 动画流畅无卡顿

---

## 6. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| TurboModule 编译错误 | 高 | 使用 C++17 标准，避免新特性 |
| iOS 目录不存在 | 中 | 先运行 `npx expo prebuild` |
| OpenMP 兼容性 | 低 | 添加编译时检测，降级到单线程 |
| 审计日志影响性能 | 低 | 使用批量异步写入 |
| WebView 文件访问权限 | 中 | 添加 `allowFileAccessFromFileURLs` |

---

## 7. 实施时间表

| 阶段 | 任务 | 预计时间 | 状态 |
|------|------|----------|------|
| Phase 0 | Vector Search TurboModule | 7.5h | 📋 待实施 |
| Phase 1 | Audit Logging | 3h | 📋 待实施 |
| Phase 2 | PDF Robustness | 3h | 📋 待实施 |
| Phase 3 | 扩展功能面板集成 | 1h | 📋 待实施 |
| **总计** | | **14.5h** | |

---

## 8. 下一步行动

**建议从 Phase 3 开始**（扩展功能面板集成），原因：
1. 工作量最小（1h），可快速产出
2. 风险最低，无依赖
3. 可立即验证效果

**然后按优先级执行**：
1. Phase 0 (Vector Search) - 最高优先级，解决核心性能问题
2. Phase 1 (Audit Logging) - 安全合规需求
3. Phase 2 (PDF Robustness) - 稳定性改进
