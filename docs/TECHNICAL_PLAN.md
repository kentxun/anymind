# Prompt 记录工具技术方案（MVP）

## 1. 目标与范围
- 目标：快速记录与管理自己发过的 Prompt，支持标签、按周分类、列表查看与内容搜索，并可选云同步。
- 范围：先实现 Mac 客户端 + 轻量同步服务；Android/iOS 预留目录不实现。
- 非目标：暂不做账号注册/登录；云端不做搜索与复杂查询。

## 2. 关键约束
- 服务器资源：2 核 CPU / 40G 存储，需保持服务端逻辑轻量。
- 多端同步冲突：MVP 采用 LWW（最后写入覆盖）+ 可选冲突副本。
- 仅在客户端进行搜索与筛选，云端只做同步与存储。

## 3. 技术选型
### 3.1 客户端（macOS 14+）
- UI：SwiftUI
- 本地存储：SQLite（系统自带 SQLite3 或 GRDB 作为 ORM/封装）
- 全文搜索：SQLite FTS5（本地）

### 3.2 服务端（Java）
- 框架：Spring Boot
- 存储：SQLite（每个 space 一个数据库文件）
- 功能定位：同步与持久化，不做搜索

## 4. 总体架构
```
Mac App (SwiftUI)
  ├─ 本地 SQLite (主存储)
  ├─ FTS5 本地搜索
  └─ 同步模块（可选）
         │
         ▼
Server (Spring Boot)
  ├─ space 目录隔离
  └─ SQLite 文件存储（只写/读变更）
```

## 5. 数据模型（本地）
### 5.1 PromptRecord
- id: UUID
- content: TEXT
- created_at: DATETIME
- updated_at: DATETIME
- week_key: TEXT（如 2026-W06）
- system_tags: TEXT[]（#temp/#longterm/#P1/#P2 等）
- user_tags: TEXT[]（来自内容 #Tag）
- deleted: BOOLEAN（软删除）
- version: INTEGER（本地递增，用于同步）

### 5.2 表结构建议
- records(id, content, created_at, updated_at, week_key, deleted, version)
- tags(id, name, is_system)
- record_tags(record_id, tag_id)
- record_fts(content, record_id)  // FTS5 虚表

## 6. 标签与搜索
- 标签解析：写入时从 content 中解析 #Tag（大小写不敏感，标准化保存）
- 系统标签：内置 #temp/#longterm/#P1/#P2
- 标签查询：
  - OR：任一标签命中
  - AND：所有标签命中（JOIN + GROUP BY + HAVING）
- 内容搜索：本地 SQLite FTS5 MATCH

## 7. 周级别管理
- 采用 ISO Week（如 2026-W06）
- week_key 由 created_at 计算，默认以周为分类展示

## 8. 同步模型（云端轻量化）
### 8.1 存储隔离
- 以 spaceId 作为目录隔离：
  - /data/spaces/{spaceId}/db.sqlite

### 8.2 同步模式
- 本地-only：不开启同步，仅使用本地 SQLite
- 云同步：本地仍为主库，增量上传到服务端

### 8.3 同步协议（建议）
- 客户端维护 sync_cursor（最后同步版本）
- push：上传本地自 cursor 以来的变更
- pull：拉取服务端自 cursor 以来的变更

## 9. 冲突处理（MVP）
- 策略：LWW（最后写入覆盖）
- 可选：保留冲突副本
  - 当检测到同一记录的版本冲突时，生成一条“冲突副本”记录
  - UI 显示冲突标记，用户可手动合并

## 10. 安全与空间识别
- 不做账号登录
- 生成 spaceId + spaceSecret
- API 调用时携带 spaceSecret 作为访问校验

## 11. API 草案（服务端）
- POST /spaces (create)
- POST /sync/push
- POST /sync/pull
- POST /records/conflict-report (可选)

## 12. 里程碑计划
1. 输出详细 API 与数据模型文档
2. Mac 客户端 MVP（本地存储 + 标签 + 周视图 + 搜索）
3. Java 服务端 MVP（同步 + 存储）
4. 同步与冲突处理落地
5. 预留 iOS/Android 目录结构

