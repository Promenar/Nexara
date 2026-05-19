# Room 数据库迁移 Schema 不匹配闪退修复方案

## 1. 问题背景与根本原因分析

### 1.1 闪退现象

用户合并远程的最新代码后，运行应用闪退，关键日志如下：
```
E FATAL EXCEPTION: java.lang.IllegalStateException: Migration didn't properly handle: sessions(com.promenar.nexara.data.local.db.entity.SessionEntity).
 Expected:
    TableInfo { name = 'sessions', columns = { ... 'active_task_tree_id' ... 'execution_mode' (defaultValue = 'auto') ... } }
 Found:
    TableInfo { name = 'sessions', columns = { ... [缺少 active_task_tree_id] ... 'execution_mode' (defaultValue = undefined) ... } }
```

### 1.2 根因分析

在 `NexaraDatabase.kt` 中，`MIGRATION_10_11` 使用了 `safeAddColumn` 来安全添加缺失的列。
```kotlin
private fun safeAddColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
    if (!columnExists(db, tableName, columnName)) {
        db.execSQL("ALTER TABLE `$tableName` ADD COLUMN $columnName $columnDef")
    }
}
```

这种增量式的 `safeAddColumn` 存在以下两个致命缺陷，导致数据库升级彻底崩溃：

1. **早期开发不一致遗留 (默认值缺失)**：
   在先前的开发/测试分支中，`execution_mode` 和 `loop_status` 可能在未升级 Room 数据库版本号的情况下，已经被手动写入或以不带默认值（`defaultValue = undefined`）的形式存在于用户的 `sessions` 表中。
   当用户升级到 v11 时，`safeAddColumn` 探测到 `execution_mode` 已经存在，因此**跳过**了 `ALTER TABLE ADD COLUMN`，使得表中依然是无默认值状态。而最新的 `SessionEntity` 指定了 `@ColumnInfo(defaultValue = "auto")`，Room 进行 Schema 强校验时发现不一致，遂抛出 `IllegalStateException` 崩溃。

2. **跨版本升级跳过 (列缺失)**：
   `active_task_tree_id` 列是在 `MIGRATION_9_10` 中添加的。如果开发者的本地数据库已经直接或间接地升级到了 v10 或 v11，但在执行 `MIGRATION_9_10` 时该列添加失败，或者直接跳过了该段逻辑，由于 `MIGRATION_10_11` 中并没有额外补齐 `active_task_tree_id` 这一列，导致最终升级后依然缺失这一列。

---

## 2. 架构设计与流程推演

### 2.1 数据库结构恢复拓扑图 (Mermaid)

```mermaid
graph TD
    A[旧 sessions 表 (表结构可能损坏/缺失列/无默认值)] -->|RENAME| B(sessions_old 临时表)
    C[根据最新 Schema] -->|CREATE TABLE| D[全新的 sessions 表 (完美结构)]
    B -->|动态探测 common_columns| E[读取 sessions_old 实际存在的列]
    E -->|INSERT INTO SELECT| D
    D -->|DROP TABLE| F(sessions_old 临时表)
    F -->|完成修复| G[Room Schema 校验 (100% 匹配通过)]
```

### 2.2 终极解决方案：表重建迁移 (Recreate Table Migration)

在 SQLite 中，不支持通过 `ALTER TABLE` 直接修改列的约束（例如添加 `DEFAULT` 默认值）。最优雅、最鲁棒的解决方案就是**重建表结构**，它的具体运行逻辑如下：

1. **获取旧表中实际存在的列**：
   通过 `PRAGMA table_info(sessions)` 获取原 `sessions` 表中的所有列名列表，这可以确保动态兼容各种不同的旧版本表结构。
2. **重命名旧表**：
   将 `sessions` 重命名为 `sessions_old`。
3. **创建完美结构的新表**：
   利用 Room 编译生成的精确 `CREATE TABLE` 语句，直接创建结构完全正确的新 `sessions` 表。
4. **安全导入数据**：
   计算新表与旧表中公共列 the intersection，通过 `INSERT INTO sessions (common_columns) SELECT common_columns FROM sessions_old` 将数据精准导回，没有交集的列则自动取新表定义的默认值。
5. **清理临时表**：
   `DROP TABLE sessions_old`。

---

## 3. 分阶段实施计划

### 3.1 阶段一：重构旧 Migration，提供最高保障
重新设计 `MIGRATION_10_11`，把原本脆弱的 `safeAddColumn` 升级为 100% 稳定的**表重建迁移**，以确保任何未来从 v10 升上来的用户都能 100% 稳健运行。

### 3.2 阶段二：升级版本至 12，提供热修复
为了完美热修复开发者本地由于之前的缺陷已经处于损坏状态的 v11 数据库（不需要清空应用数据或卸载重装）：
1. 在 `NexaraDatabase.kt` 中将 `@Database(version = 11)` 提升至 `version = 12`。
2. 声明 `MIGRATION_11_12`，在其中对 `sessions` 进行 100% 表重建修复。
3. 在 `NexaraApplication.kt` 的 `addMigrations` 中注册 `MIGRATION_11_12`。

---

## 4. 极端情况与边界推演

- **边界一：如果旧表中包含部分非空 (NOT NULL) 且无默认值的列，新表添加了默认值，如何兼容？**
  - 我们只迁移公共列。对于新表里要求 NOT NULL 且有默认值的列，如果旧表没有，我们由于没有迁移该列，SQLite 会在 INSERT 时自动填充新表的默认值，不会抛出约束异常。
- **边界二：如果在表重建迁移期间发生异常怎么办？**
  - Room 的 Migration 默认运行在数据库事务 (Transaction) 中，如果中间执行抛出任何 SQLite 异常，事务会自动回滚，不会损坏原来的数据，保持极高的健壮性。

---

## 5. messages 表 Schema 不匹配升级补充 (v12 -> v13)

### 5.1 messages 表闪退现象
在解决 `sessions` 表升级之后，Room 会开始校验 `messages` 表，抛出以下崩溃：
```
E AndroidRuntime: java.lang.IllegalStateException: Migration didn't properly handle: messages(com.promenar.nexara.data.local.db.entity.MessageEntity).
 Expected:
    Column { name = 'rag_references_loading', type = 'INTEGER', notNull = 'true', defaultValue = '0' }
    Column { name = 'is_archived', type = 'INTEGER', notNull = 'true', defaultValue = '0' }
    Column { name = 'is_error', type = 'INTEGER', notNull = 'true', defaultValue = '0' }
 Found:
    Column { name = 'rag_references_loading', type = 'INTEGER', notNull = 'true', defaultValue = 'undefined' }
```

### 5.2 messages 表重建迁移逻辑与外键/索引维护
由于 `messages` 表包含外键引用 `sessions(id) ON DELETE CASCADE`，在重命名和新建表时必须极为小心：
1. **外键约束定义**：
   ```sql
   FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) ON DELETE CASCADE
   ```
2. **索引定义**：
   重建新表后，必须显式重建以下两个索引：
   ```sql
   CREATE INDEX IF NOT EXISTS `index_messages_session_id` ON `messages` (`session_id`)
   CREATE INDEX IF NOT EXISTS `index_messages_session_id_created_at` ON `messages` (`session_id`, `created_at`)
   ```
3. **安全数据导入**：
   依然利用公共列交集进行 `INSERT INTO ... SELECT` 数据复制，最后安全 `DROP TABLE messages_old`。

### 5.3 分阶段实施计划
1. 将 `NexaraDatabase.kt` 中的版本从 `version = 12` 提升至 `version = 13`。
2. 编写 `recreateMessagesTable(db)` 私有辅助函数。
3. 新增 `MIGRATION_12_13` 并注册在 `NexaraApplication.kt` 中，调用 `recreateMessagesTable(db)`。
4. 将 `MIGRATION_10_11` 中脆弱的针对 `messages` 的 `safeAddColumn` 替换为稳健的 `recreateMessagesTable(db)`。
