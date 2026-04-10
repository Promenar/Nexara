# 研发计划：MCP 客户端传输层扩展 (SSE 支持)

**状态**: ✅ 已完成 (Verified)
**验证时间**: 2026-02-03
**验证结论**: 核心传输层代码已合入，单元测试通过。

## 目标描述
扩展现有的模型上下文协议 (MCP) 客户端，使其支持 **服务器发送事件 (SSE)** 传输协议。
当前实现仅支持 **无状态 HTTP** (通过 POST 请求/响应)，这与需要持久流式连接 ("streamablehttp") 的标准 MCP 服务器（如 Alpha Vantage 参考实现）不兼容。

此次变更将允许 Nexara 连接到更广泛的、遵循官方 MCP SSE 规范的公共 MCP 服务。

## 用户审查事项
> [!IMPORTANT]
> **新增依赖**：我们需要添加 `react-native-sse` 以在 React Native 中可靠地支持 EventSource。
> **内部破坏性变更**：`McpClient` 类将被重构以使用传输接口模式。`mcp-bridge.ts` 中的现有调用代码需要同步更新。

## 拟定架构

### 传输层抽象 (Transport Abstraction)
将单体 `McpClient` 拆分为客户端包装器和可插拔的传输层。

```typescript
interface McpTransport {
    connect(): Promise<void>;
    disconnect(): Promise<void>;
    listTools(): Promise<McpTool[]>;
    callTool(name: string, args: any): Promise<any>;
}
```

### 1. HTTP 传输 (保留现有逻辑 - "Stateless")
- 保留现有行为。
- 通过 POST 发送 JSON-RPC，期望结果直接在响应体中返回。
- 适用于简单的/自定义的服务器（如目前项目中使用的）。

### 2. SSE 传输 (新增标准 - "Standard")
- 遵循 MCP SSE 规范：
    1. 连接到 SSE 端点 (GET)。
    2. 接收包含 POST URL 的 `endpoint` 事件。
    3. 向提供的 URL 发送 JSON-RPC (POST)。
    4. 通过 SSE 流异步接收 JSON-RPC 响应。
- 处理相关性 ID 匹配 (Request ID <-> Response ID)。

## 变更计划

### `src/lib/mcp/`
#### [NEW] `src/lib/mcp/transport.ts`
- 定义 `McpTransport` 接口。
- 定义 `McpTool` 类型 (从 client.ts 移动)。

#### [NEW] `src/lib/mcp/transports/http-transport.ts`
- 将当前 `mcp-client.ts` 中的 `fetch` 逻辑迁移至此。

#### [NEW] `src/lib/mcp/transports/sse-transport.ts`
- 使用 `react-native-sse` 实现 `SseTransport`。
- 管理 `EventSource` 连接。
- 维护 `pendingRequests` 映射表，以便在响应流回时解析 Promise。

#### [MODIFY] `src/lib/mcp/mcp-client.ts`
- 重构为接受 `McpTransport` 实例（或创建配置）。
- 将调用委托给活动的传输层。

### `src/store/`
#### [MODIFY] `src/store/mcp-store.ts`
- 更新 `McpServerConfig` 以包含：
    ```typescript
    type: 'http' | 'sse'; // 新增默认默认为 'sse'，'http' 用于向后兼容
    ```

### `src/lib/mcp/`
#### [MODIFY] `src/lib/mcp/mcp-bridge.ts`
- 更新 `syncServer`，根据配置实例化正确的传输层（或尝试自动检测）。

## 验证计划

### 自动化测试
- 由于缺乏本地 MCP 服务器环境，我们将依赖单元测试来验证消息相关性 (Correlation) 逻辑。

### 手动验证
1. **回归测试**：确保现有的 MCP 工具（如果有）在使用 HTTP 传输模式下仍能正常工作。
2. **新功能测试**：
    - 在设置中配置一个新的 MCP 服务器。
    - 选择 "SSE" 模式。
    - 连接到公共 MCP / fallback 演示服务。
    - 验证 `tools/list` 是否正确填充。
    - 验证 `tools/call` 是否能执行并接收数据。
