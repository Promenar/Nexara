# 010：MCP Server 连接池

> 来源：[Tool/MCP 审计报告](../tool-mcp-audit-report.md) §5.3
> 优先级：🟡 Medium

## 问题描述

`McpBridge` 中 MCP 工具执行为无状态设计：每次调用创建新 `McpClient` 实例，经历完整的 `connect()` → `callTool()` → `disconnect()` 生命周期。

对远程 MCP Server（SSE/HTTP），每次调用的 TCP 握手 + 认证开销约 100-500ms。

## 方案

### 连接池设计

```typescript
class McpConnectionPool {
    private pool: Map<string, { client: McpClient; lastUsed: number; busy: boolean }> = new Map();
    private ttl = 30_000; // 30秒空闲后断开

    async acquire(serverId: string, config: McpServerConfig): Promise<McpClient> {
        const existing = this.pool.get(serverId);
        if (existing && !existing.busy) {
            existing.lastUsed = Date.now();
            existing.busy = true;
            return existing.client;
        }
        // 创建新连接
        const client = new McpClient(config);
        await client.connect();
        this.pool.set(serverId, { client, lastUsed: Date.now(), busy: true });
        return client;
    }

    release(serverId: string): void {
        const entry = this.pool.get(serverId);
        if (entry) entry.busy = false;
    }

    // 定时清理空闲连接
    startCleanup(): void {
        setInterval(() => {
            for (const [id, entry] of this.pool) {
                if (!entry.busy && Date.now() - entry.lastUsed > this.ttl) {
                    entry.client.disconnect();
                    this.pool.delete(id);
                }
            }
        }, 10_000);
    }
}
```

### 修改 `McpBridge.syncServer`

将 `execute` 闭包中的 `new McpClient()` 替换为 `McpConnectionPool.acquire()`。

## 影响范围

- `src/lib/mcp/mcp-bridge.ts`
- 新增 `src/lib/mcp/mcp-connection-pool.ts`

## 验证方式

1. 连续调用同一 MCP Server 的工具 3 次，确认仅建立 1 次连接
2. 等待 30 秒无调用后，确认连接自动断开
3. stdio 类型 Server 行为不受影响
