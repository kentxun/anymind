# API 与数据模型（MVP）

## 1. 总览
- 本地为主：所有搜索、标签、周视图均在客户端完成。
- 云端仅同步：服务端不做搜索、不做复杂查询，仅负责保存与增量下发。
- 鉴权：spaceId + spaceSecret（无账号体系）。
- 冲突：LWW（最后写入覆盖）+ 可选冲突副本（由客户端生成）。

## 2. 客户端数据模型（SQLite）

### 2.1 记录表（records）
- id: TEXT (UUID, PK)
- content: TEXT
- created_at: DATETIME
- updated_at: DATETIME
- week_key: TEXT (如 2026-W06)
- deleted: BOOLEAN
- local_version: INTEGER (本地递增，用于本地变更排序)
- server_rev: INTEGER (上次同步到的服务端版本号，未同步为 NULL)
- last_sync_at: DATETIME (最近一次成功同步时间)

### 2.2 标签表（tags）
- id: INTEGER (PK)
- name: TEXT (标准化：小写)
- is_system: BOOLEAN

### 2.3 关联表（record_tags）
- record_id: TEXT
- tag_id: INTEGER

### 2.4 FTS 表（record_fts）
- content
- record_id

### 2.5 系统标签
- #temp / #longterm / #P1 / #P2

> 注：标签解析由客户端完成，写入时从 content 中抽取 #Tag，标准化后入库。

## 3. 服务端数据模型（SQLite）

### 3.1 记录表（records）
- id: TEXT (UUID, PK)
- content: TEXT
- system_tags_json: TEXT (JSON 数组)
- user_tags_json: TEXT (JSON 数组)
- created_at: DATETIME (客户端传入)
- updated_at_client: DATETIME (客户端传入)
- deleted: BOOLEAN
- server_rev: INTEGER (最后一次变更版本号)
- server_updated_at: DATETIME (服务端写入时间)
- last_device_id: TEXT

### 3.2 变更表（changes）
- rev: INTEGER (PK AUTOINCREMENT，全局递增)
- record_id: TEXT
- deleted: BOOLEAN
- server_updated_at: DATETIME

> 注：每次写入 records 时插入 changes，以支持增量 pull。

## 4. 同步状态
- 客户端保存：`sync_cursor`（最后一次 pull 的 server_rev）
- push：上传本地变更（自上次同步或本地 dirty 集合）
- pull：请求 `since_rev` 之后的服务端变更

## 5. 冲突处理（MVP）
- 客户端在 push 时携带 `base_rev`（上次已知的服务端版本）。
- 若服务端发现当前 `server_rev > base_rev`：
  - 仍接受写入（LWW）
  - 返回 `conflict=true`，客户端可生成“冲突副本”记录。
- 冲突副本建议字段：
  - is_conflict: BOOLEAN
  - conflict_of: TEXT (原记录 id)

## 6. API 设计（草案）

### 6.1 创建空间
**POST /spaces**

Request
```json
{
  "name": "optional-name"
}
```

Response
```json
{
  "space_id": "spc_xxxx",
  "space_secret": "sec_xxxx",
  "created_at": "2026-02-04T10:00:00Z"
}
```

### 6.2 推送变更
**POST /sync/push**

Request
```json
{
  "space_id": "spc_xxxx",
  "space_secret": "sec_xxxx",
  "device_id": "mac-xxxx",
  "changes": [
    {
      "id": "uuid",
      "content": "...",
      "system_tags": ["#P1"],
      "user_tags": ["#tag"],
      "created_at": "2026-02-04T10:00:00Z",
      "updated_at": "2026-02-04T11:00:00Z",
      "deleted": false,
      "base_rev": 12
    }
  ]
}
```

Response
```json
{
  "results": [
    {
      "id": "uuid",
      "server_rev": 15,
      "server_updated_at": "2026-02-04T11:00:03Z",
      "conflict": true
    }
  ],
  "server_rev_max": 15
}
```

### 6.3 拉取变更
**POST /sync/pull**

Request
```json
{
  "space_id": "spc_xxxx",
  "space_secret": "sec_xxxx",
  "since_rev": 10,
  "limit": 200
}
```

Response
```json
{
  "changes": [
    {
      "id": "uuid",
      "content": "...",
      "system_tags": ["#P1"],
      "user_tags": ["#tag"],
      "created_at": "2026-02-04T10:00:00Z",
      "updated_at": "2026-02-04T11:00:00Z",
      "deleted": false,
      "server_rev": 15,
      "server_updated_at": "2026-02-04T11:00:03Z"
    }
  ],
  "server_rev_max": 15
}
```

### 6.4 健康检查
**GET /health**

Response
```json
{ "ok": true }
```

## 7. 鉴权与错误码（建议）
- 401: space_secret 无效
- 404: space_id 不存在
- 413: 单次 push/pull 超限
- 429: 限流（可选）

## 8. 客户端本地搜索与标签查询
- 标签 OR：存在任一 tag
- 标签 AND：同时包含所有 tag
- 内容搜索：SQLite FTS5 MATCH（本地）

## 9. 版本演进建议
- 预留 API 版本：`/v1/...`
- 预留冲突三方合并字段：`base_content`、`base_rev`

