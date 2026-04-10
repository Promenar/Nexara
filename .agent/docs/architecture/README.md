# Nexara Logic Core Architecture

This directory contains deep-dive architecture documentation for Nexara's critical systems.

## Core Systems
1.  **[RAG & Knowledge Graph System](./rag-system.md)**
    *   Covers: Vector Storage, Memory Management, Graph Extraction, Context Injection.
    *   Key Logic: `memory-manager.ts`, `vector-store.ts`, `graph-extractor.ts`.

2.  **[Steerable Agent Loop](./steerable-agent-loop.md)**
    *   Covers: Execution Modes (Auto/Semi/Manual), Task Management, Loop State Machine.
    *   Key Logic: `session-manager.ts`, `agent-loop.ts` (conceptual).

3.  **[LLM Abstraction Layer](./llm-abstraction-layer.md)**
    *   Covers: Model Client Interfaces, Streaming Handling, Tool Call Protocol.
    *   Key Logic: `llm-client/`, `stream-parser.ts`.

## Engineering Principles
*   **SSOT**: Single Source of Truth for all state.
*   **Isolation**: Complex sub-systems (like RAG) must be decoupled from UI rendering.
*   **Defensive IO**: Network/Storage operations must have retry/fallback mechanisms.
