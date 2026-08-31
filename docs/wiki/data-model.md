# 数据模型

## 持久化策略

- H2 文件库：`jdbc:h2:file:./data/cybertown;AUTO_SERVER=TRUE`
- `spring.jpa.hibernate.ddl-auto: update`（本轮未引入 Flyway）
- `data/` 目录默认不入库（见 `.gitignore`）
- 结束模拟会清空 NPC、关系、事件，并重置干预配额

## 核心实体

### NPC (`npcs`)

基础身份、位置/动作/目标、嵌入 `NPCStats`、想法集合、对话记忆、教育轨迹、对话影响，以及：

- `lastDecision` / `lastDecisionReason` / `lastDecisionAt`
- 含异常栈的历史理由会在启动/心跳时被 scrub

### Relationship (`relationships`)

- `npcAId` / `npcBId`：字典序规范化
- `affinity`：-100~100
- `type`：ACQUAINTANCE / FRIEND / RIVAL / LOVER / MENTOR
- `note` / `lastInteractionAt`

### TownEvent (`town_events`)

- `type`：WORLD / SOCIAL / LIFE / DECISION
- `title` / `detail` / `npcIds` / `severity`
- `createdAt` / `expiresAt`
- 环形保留约 200 条

### Location (`locations`)

- `name` / `type` / `description` / `capacity`
- `mapX` / `mapY`（前端另有固定城区布局表，优先保证可读性）
- 启动时播种；已有数据会同步坐标

## 快照

`GET /api/town/snapshot` 导出 NPC + 关系 + 近期事件 JSON。  
`POST /api/town/snapshot/import` 清空后整包导入（多世界 lite）。
