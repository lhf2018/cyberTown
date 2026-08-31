# Cyber Town 迭代记录

## 2026-08-31（深夜：密钥与工具循环修复）

### 迭代主题
- DeepSeek 鉴权失败（`api key: todo`）
- AI 决策 `exceeded 10 sequential tool executions`

### 旧规则 → 新规则
- **密钥**：仅 `application.yml` 占位 `${DEEPSEEK_API_KEY:todo}` → 默认 `spring.profiles.active=local`，本地用 gitignore 的 `application-local.yml` 覆盖；`LangGraphConfig` 拒绝字面量 `todo`
- **决策工具**：AiServices 注册 4 个 `@Tool` 由模型反复调用 → **Java 侧各预检一次**写入提示词「工具预检」，AiServices **不再注册 tools**（避免 LangChain4j 硬上限 10 次连续工具）

### 复现 / 根因 / 修复
1. `Authentication Fails, Your api key: todo is invalid`
   - 根因：未设 `DEEPSEEK_API_KEY` 且未加载本地密钥
   - 修复：`application-local.yml` + profile `local`；勿提交真实密钥
2. `Something is wrong, exceeded 10 sequential tool executions`
   - 根因：模型在工具环中空转触顶
   - 修复：去工具绑定 + 预检注入；单次决策约 1 次 LLM 调用

---

## 2026-08-31（夜：体验二期全量）

### 迭代主题
- 关系可视化、周目标、事件回放、决策洞察、AI 看板
- 命名存档、日报海报、分区导航与氛围音

### 新增接口
- `GET /relationships/graph`
- `GET|POST /quest`、`POST /quest/reset`
- `GET /replay`、`GET /decision/insights`、`GET /metrics/ai`
- `GET /report/daily`
- `GET|POST /saves`、`POST /saves/{name}/load`、`DELETE /saves/{name}`

### 前端
- 六大 Tab：总览 / 地图与关系 / 事件回放 / 决策洞察 / 存档 / 居民
- 地图关系连线（朋友青 / 仇敌红 / 普通灰）
- 日报海报 Canvas 导出 PNG

### 服务
- `QuestService`：周目标剧情脉冲
- `AiMetricsService`：AI 调用成功/失败/耗时环形记录
- `SaveSlotService`：命名 JSON 存档（落在 `data/saves/`，已 gitignore）

### 决策洞察面板含义
- **AI 调用看板**：次数、失败率、耗时、最近明细
- **世界权重**：当前事件对社交/能量/投资的乘数
- **最近决策对比**：各 NPC 最近决策、来源（AI/规则）、理由

---

## 2026-08-31（晚间：体验与决策修复）

### 迭代主题
- 运行页视觉重做 + 城区地图布局
- AI 决策 JSON 解析修复
- 清理历史异常决策理由

### 主要改动
- **前端**
  - `runtime.html` 拆出 `css/runtime.css`、`js/runtime.js`
  - 品牌条 + 广播/大事双栏；地图改为固定城区卡片布局（非乱点 SVG）
  - 前端过滤含 `BEGIN_OBJECT` / Exception 的决策理由展示
- **决策**
  - `AIDecisionAssistant` 改为返回 `String`，自行解析 JSON / 【章节】文本
  - 旧问题：LangChain4j 把中文分段文本当 JSON 对象反序列化 → `Expected BEGIN_OBJECT`
  - 新规则：失败时用户侧仅显示「AI暂不可用」，不回传异常栈
  - 启动与心跳清理库内残留的异常 `lastDecisionReason`
- **运维**
  - 启动需 **JDK 17+**（Java 8 无法运行 Spring Boot 3）
  - `api-key` 改为 `${DEEPSEEK_API_KEY:todo}`，避免密钥入库

### 复现 / 根因 / 修复（决策报错）
- 复现：NPC 卡片「决策理由」出现 `规则引擎决策（AI失败: Expected BEGIN_OBJECT...）`
- 根因：AiServices 结构化输出与提示词格式不匹配；失败理由被持久化
- 修复：字符串返回 + 宽松解析；落库/SSE/前端三重消毒；启动 scrub

---

## 2026-08-31（全功能落地）

### 迭代主题
- NPC 同地点社交 + 关系网
- 世界事件真正影响模拟
- 人生事件、决策可观测
- 地图 / 事件时间线 / 旁观+干预配额
- 快照导出导入与 wiki 补齐

### 主要改动
- **领域**
  - `Location` 增加 `mapX`/`mapY`，启动播种/同步 10 个地点
  - 新增 `Relationship`、`TownEvent`
  - `NPC` 增加 `lastDecision` / `lastDecisionReason` / `lastDecisionAt`
- **模拟**
  - 心跳后：`WorldEventService.tick` → `SocialService` → `LifeEventService`
  - 世界事件权重：社交概率、能量消耗、投资收益、程序员情绪等
  - 同地点配对社交（每心跳最多 3 对，至多 1 对 AI 对话）
  - 人生事件：升职、催收、破产边缘、中奖、告白/分手
- **API**
  - `GET /events`、`/locations`、`/npc/{id}/relationships`
  - `GET|POST /mode`（spectator/operator + 配额）
  - `GET /snapshot`、`POST /snapshot/import`
  - SSE 状态携带决策理由；广播优先活跃世界事件

### 已知限制
- EventSource 对话通过 `clientKey` 查询参数传递配额身份
- 快照导入会清空现有 NPC/关系/事件
- 仍使用 `ddl-auto: update`，未引入 Flyway

---

## 2026-04-26（深夜文档与系统完善）

### 迭代主题
- 对话系统升级（微信风格 + 流式）
- 上帝模式能力扩展（学历/工资可编辑）
- 新闻影响决策与心里话气泡
- 数据持久化与规则修复

### 主要改动
- 运行页微信风格对话 + SSE；资产/学历/工资弹框；心里话气泡
- `NewsService` RSS + 决策上下文；上帝模式学历/工资自然语言
- H2 文件库 + `ddl-auto: update`

## 2026-04-20（晚间增量）

### 迭代主题
- 运行页体验完善；长期属性系统；上帝模式与 NPC 反馈

## 2026-04-20

### 迭代主题
- AI 驱动初始化；初始化 → 运行流程；结束模拟重开
