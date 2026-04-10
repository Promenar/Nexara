# Backup System Audit Report
> Date: 2026-01-05
> Auditor: Antigravity Agent
> Version: v1.1.11

## 1. Executive Summary
The backup system (implemented in `BackupManager.ts`) provides **good coverage for core text data and metadata** but has significant gaps regarding **binary assets (images)** and **newer state stores**. 

A critical fix was applied during this audit to include Token Statistics. However, media attachments remain at risk of broken links upon restoration to a different device.

## 2. Coverage Analysis

### 2.1 Zustand Stores (AsyncStorage)
| Store Name | Storage Key | Status | Notes |
| :--- | :--- | :--- | :--- |
| `settings-store` | `settings-storage-v2` | ✅ Covered | Global settings |
| `chat-store` | `chat-storage` | ✅ Covered | Sessions & Messages |
| `api-store` | `api-storage-v2` | ✅ Covered | API Keys & Providers |
| `agent-store` | `agent-storage` | ✅ Covered | Custom Agents |
| `spa-store` | `spa-storage` | ✅ Covered | Super Assistant FAB |
| `token-stats-store` | `token-stats-storage` | ✅ **Fixed** | Added during audit (was missing) |
| `rag-store` | N/A | ⚪ Ignored | State is ephemeral/in-DB; correctly excluded |

### 2.2 SQLite Database
| Table | Status | Notes |
| :--- | :--- | :--- |
| `sessions` | ✅ Covered | |
| `messages` | ✅ Covered | |
| `attachments` | ✅ Covered | **Metadata only** (see Risks) |
| `folders` | ✅ Covered | |
| `documents` | ✅ Covered | |
| `vectors` | ✅ Covered | BLOBs correctly converted to Base64 |
| `kg_nodes` | ✅ Covered | KG 2.0 Data |
| `kg_edges` | ✅ Covered | KG 2.0 Data |
| `tags` | ✅ Covered | |
| `document_tags`| ✅ Covered | |

## 3. Critical Risks & Limitations

### ⚠️ 3.1 Local Media Attachments (High Risk)
- **Problem**: The system backs up the *path* to images (e.g., `file:///data/.../img.jpg`) stored in the `messages` or `attachments` table, but **does not back up the actual image files**.
- **Consequence**: Restoring this backup on a new device (or even the same device after a clear data) will result in broken images.
- **Recommended Fix**: 
    1.  Switch to a ZIP-based backup format (JSON + Assets).
    2.  Or convert images to Base64 and store in DB (high size impact).

### ℹ️ 3.2 Ephemeral States
- **RAG Queue**: The vectorization queue (`VectorizationQueue`) is in-memory and not persisted. This is acceptable behavior; unfinished tasks are lost on restart/backup.

## 4. Remediation Actions Taken
- [x] Added `token-stats-storage` to `BackupManager.exportData()` key list.

## 5. Next Steps
- [ ] **Feature Request**: Implement "Full Media Backup" (Zip export).
- [ ] **Testing**: Create a unit test to verify all registered Zustand stores are included in `BackupManager` via a registry check.
