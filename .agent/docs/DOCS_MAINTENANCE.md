# 文档维护流程

## 目的
确保`.agent/docs/`中的文档与代码实现保持同步，避免文档过时导致的误导。

---

## 触发文档更新的场景

### 1. 重大架构变更
**触发条件**:
- 新增核心模块（如chat-store重构）
- 修改现有架构设计（如LLM抽象层升级）
- 数据库schema变更

**需要更新的文档**:
- `product-requirements.md` - 添加新功能到"现状"和Phase更新日志
- `README.md` - 更新文档索引
- 创建专门的架构文档（如`chat-store-refactor-*.md`）

**检查清单**:
- [ ] 更新PRD的版本号和日期
- [ ] 添加Phase更新日志
- [ ] 创建专项技术指南（如需要）
- [ ] 更新README.md文档索引

---

### 2. 新功能实现
**触发条件**:
- 实现PRD中规划的功能
- 添加新的用户可见功能

**需要更新的文档**:
- `product-requirements.md` - 标记功能为已完成✅
- `README.md` - 添加新功能文档链接（如有）

**检查清单**:
- [ ] 在PRD中将`[ ]`改为`[x]`
- [ ] 更新功能状态描述
- [ ] 添加实现细节到相关章节

---

### 3. 技术栈升级
**触发条件**:
- Expo SDK版本升级
- 主要依赖包升级
- 构建工具链变更

**需要更新的文档**:
- `product-requirements.md` - 更新技术栈章节
- `android-build-guide.md` - 更新构建步骤（如有变化）

**检查清单**:
- [ ] 更新技术栈版本号
- [ ] 验证构建流程是否变化
- [ ] 更新相关命令示例

---

### 4. 品牌/命名变更
**触发条件**:
- 项目名称变更
- 品牌标识变更
- 关键术语更新

**需要更新的文档**:
- **所有文档**需要全局搜索替换

**检查清单**:
- [ ] 使用`grep -r "旧名称" .agent/docs/`搜索
- [ ] 逐个文档替换
- [ ] 验证路径引用仍然正确
- [ ] 提交时注明品牌统一

---

## 定期维护计划

### 月度检查（每月最后一天）
- [ ] 检查README.md文档索引是否完整
- [ ] 验证所有文档链接有效
- [ ] 清理过时的临时文档

### 季度审查（每季度最后一周）
- [ ] 全面审查PRD内容
- [ ] 检查功能状态准确性
- [ ] 归档过时的架构文档
- [ ] 更新维护记录

### 发布前检查
- [ ] 确保PRD反映所有已实现功能
- [ ] 验证版本号正确
- [ ] 添加Release Notes到PRD

---

## 文档命名规范

### 核心文档
- `README.md` - 文档索引中心
- `product-requirements.md` - 产品需求文档

### 技术指南
格式: `{主题}-{类型}.md`
- `llm-abstraction-layer-guide.md` ✅
- `android-build-guide.md` ✅
- `chat-store-refactor-overview.md` ✅

### 参考文档
格式: `{功能}-reference.md`
- `settings-panels-reference.md` ✅

### 设计文档
格式: `{功能}-design.md`
- `steerable-agent-loop-design.md` ✅

### 协议/流程
格式: `{主题}-protocol.md`
- `release-protocol.md` ✅

---

## 版本控制最佳实践

### PRD版本号规则
- **主版本号（1.x.0）**: 重大功能上线或架构变更
- **次版本号（x.1.0）**: 新功能实现
- **修订号（x.x.1）**: 文档修正或小更新

### Commit Message规范
```bash
# 新增文档
git commit -m "docs: 添加chat-store重构Phase 2指南"

# 更新文档
git commit -m "docs: 更新PRD至v1.2.0并添加Phase 14"

# 品牌统一
git commit -m "docs: 品牌名统一更新 NeuralFlow → Nexara"

# 修正错误
git commit -m "docs: 修正android-build-guide中的路径错误"
```

---

## 快速检查清单

### 新功能上线后
```bash
# 1. 更新PRD
- [ ] 更新版本号（如1.1.11 → 1.2.0）
- [ ] 更新日期
- [ ] 在"现状"中添加新功能
- [ ] 添加Phase更新日志

# 2. 更新README.md
- [ ] 添加新文档链接（如有）
- [ ] 更新维护记录

# 3. 创建专项文档（如需要）
- [ ] 按命名规范创建
- [ ] 添加到README索引
```

### 发现文档过时时
```bash
# 1. 识别问题
- 文档描述与实际不符？
- 包含已废弃的功能？
- 缺少新实现的功能？

# 2. 确定影响范围
- 仅影响单个文档？
- 需要更新多个文档？
- 是否需要创建新文档？

# 3. 执行更新
- 按优先级更新（PRD > 技术指南 > 参考文档）
- 提交时注明"修正过时内容"
```

---

## 工具和自动化

### 推荐工具
```bash
# 搜索特定内容
grep -r "关键词" .agent/docs/

# 查找所有Markdown文件
find .agent/docs/ -name "*.md"

# 检查文档最后修改时间
ls -lt .agent/docs/
```

### 自动化检查（可选）
创建`.agent/scripts/check-docs.sh`：
```bash
#!/bin/bash
# 检查文档是否包含过时的品牌名
echo "检查过时的品牌名..."
grep -r "NeuralFlow" .agent/docs/ && echo "⚠️  发现旧品牌名"

# 检查PRD版本号
echo "检查PRD版本号..."
# 实现版本号自动检查逻辑
```

---

## 常见问题

### Q: 何时应该创建新文档而不是更新现有文档？
**A**: 当以下情况时创建新文档：
- 新的重大功能需要详细说明（如chat-store重构）
- 需要专门的技术指南或设计文档
- 内容与现有文档主题不相关

### Q: 如何处理临时文档？
**A**: 
- 放在`brain/`目录下的临时分析文档可以不归档
- 有长期价值的文档应移到`.agent/docs/`
- 定期清理过时的临时文档

### Q: 文档更新应该与代码提交一起吗？
**A**: 
- **是**，如果是新功能实现，应在同一PR中更新PRD
- **否**，如果是定期维护，可以单独提交
- **灵活**，根据实际情况判断

---

---

## 5. 任务追踪规范 (Task Tracking Standard)

### 核心原则
采用 **"索引-详情" (Index-Detail)** 模式，确保任务的可追溯性与上下文完整性。

### 角色定义
1.  **Dashboard (`.agent/TODO.md`)**: 
    - 唯一的项目进度仪表盘。
    - **严禁**直接在此处书写长篇大论的方案。
    - 必须包含指向具体设计文档的链接。

2.  **Inbox (`.agent/docs/todos/`)**:
    - 存放活动任务的详细设计方案 (RFC/Implementation Plan)。
    - 命名规范: `NNN_topic_name.md` (e.g., `002_rag_optimization.md`)。

3.  **Archive (`.agent/docs/archive/`)**:
    - 存放已完成任务的方案文档。
    - 任务完成后，必须将对应文档从 `todos/` 移动至此，并更新状态为 ✅ Verified。

### 状态流转
`Draft` (in todos/) -> `Active` (linked in TODO.md) -> `Completed` -> `Archived` (mv to archive/)

---

## 责任分配


### AI Assistant
- 主动提醒文档更新
- 执行文档更新操作
- 定期审查文档时效性

### 开发者
- 审查文档更新内容
- 确认功能描述准确性
- 批准重大文档变更

---

**维护者**: AI Assistant + 开发团队  
**创建日期**: 2026-01-15  
**下次审查**: 2026-02-15
