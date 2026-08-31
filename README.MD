# 🏙️ Cyber Town（赛博小镇）

一个基于 Spring Boot + Spring AI 的 NPC 模拟系统：  
NPC 具备可持久化状态、对话记忆、动态决策、同地点社交、世界事件驱动、上帝模式可编辑属性，以及新闻/人生事件影响行为。

## 当前能力

- AI / 普通两种初始化方式
- NPC 每分钟心跳更新（状态衰减、行为决策、随机事件）
- **同地点 NPC 社交**（规则配对 + 每心跳最多 1 对 AI 对话）与人际关系
- **世界事件**进入广播与模拟权重（裁员/行情/暴雨/节日等）
- **人生事件**（升职、催收、破产边缘、中奖、告白/分手）
- 流式对话（SSE）：运行页对话框按“输入中 -> 分片返回”
- 对话记忆、对话影响（持续时间/强度）进入后续决策
- 世界新闻抓取 + 新闻摘要注入决策上下文
- 不定时“心里话气泡”
- 上帝模式（自然语言）支持修改位置、目标、数值、学历、工资、关系
- **旁观模式 + 干预配额**（对话/上帝次数限制）
- **城区地图**（固定区块布局 + 人数徽章）、**今日大事时间线**、决策理由展示
- 资产详情 / 学历详情 / 工资详情 / 人际关系弹框
- **快照导出/导入**（多世界 lite）
- H2 文件库持久化（重启不丢失）

## 技术栈

- Java 17（必须；Java 8 无法启动 Spring Boot 3）
- Spring Boot 3.x
- Spring AI + LangChain4j（DeepSeek 兼容 OpenAI 接口）
- H2（文件数据库）

## 快速开始

### 1) 准备环境

- JDK 17+
- Maven 3.6+
- 可用的 DeepSeek API Key（环境变量 `DEEPSEEK_API_KEY`）

### 2) 配置

在环境中设置：

```bash
# Windows PowerShell
$env:DEEPSEEK_API_KEY = "sk-..."
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
```

或编辑 `src/main/resources/application.yml` 中的 `spring.ai.openai.api-key`（勿提交真实密钥）。

### 3) 启动

```bash
mvn clean spring-boot:run
```

### 4) 页面入口

- 初始化页：`http://localhost:8080/`
- 运行页：`http://localhost:8080/runtime.html`（地图 / 大事 / 旁观 / 居民）
- AI 初始化页：`http://localhost:8080/ai-init.html`
- H2 控制台：`http://localhost:8080/h2-console`

## 数据持久化说明

当前已使用文件数据库持久化：

- 数据源：`jdbc:h2:file:./data/cybertown;AUTO_SERVER=TRUE`
- 表结构策略：`ddl-auto: update`
- 不再使用 `create-drop`

NPC 关键信息（包括对话记忆、学历历史、想法列表、资产/工资相关字段、决策理由）与关系、小镇事件会落库并在重启后保留。

## 核心接口（常用）

### NPC 与状态

- `GET /api/town/npcs` 获取全部 NPC
- `GET /api/town/npc/{id}` 获取单个 NPC
- `GET /api/town/npcs/status` 一次性状态
- `GET /api/town/npcs/status/stream` 实时状态 SSE（含 `lastDecision` / `lastDecisionReason`）

### 对话

- `POST /api/town/npc/{id}/talk`
- `GET /api/town/npc/{id}/talk/stream?message=...&clientKey=...`（流式）

### 上帝模式

- `POST /api/town/npc/{id}/god-command`
  - 支持学历关键词归一化（专科/研究生/PhD 等）
  - 支持工资相关自然语言覆盖（月薪/期权/年收入，元/千/万）
  - 支持关系指令（好感/结仇/成为朋友等）

### 世界 / 事件 / 地图

- `GET /api/town/world/broadcast`（优先活跃世界事件）
- `GET /api/town/world/news`
- `GET /api/town/events?limit=`
- `GET /api/town/locations`
- `GET /api/town/npcs/thought-bubbles`

### 关系 / 模式 / 快照

- `GET /api/town/npc/{id}/relationships`
- `GET /api/town/mode` / `POST /api/town/mode`（`spectator` | `operator`）
- `GET /api/town/snapshot` / `POST /api/town/snapshot/import`

### 详情面板

- `GET /api/town/npc/{id}/profile/extended`
  - assets：现金、储蓄、负债、净资产、风险
  - educationTimeline：学历轨迹（含学校与方向）
  - compensation：工资与期权展示数据

## 上帝模式示例

```json
POST /api/town/npc/npc-1/god-command
{
  "instruction": "把学历改成麻省理工的博士，月薪改成3万元，期权改成5000元，目标改成一年内做出高可用系统"
}
```

关系示例：

```json
{ "instruction": "对莉莉好感+30" }
```

## 已知规则（当前实现）

- 程序员岗位公司与学历有约束（本科及以上才会分配到头部大厂池）
- 期权不是所有岗位都有（受岗位/学历/公司条件约束）
- 学历详情为持久化字段，支持在上帝模式下增量修改
- 世界事件会影响社交触发率、能量消耗、投资收益与部分职业情绪
- 旁观模式不可对话/上帝；运营模式默认对话 20 次、上帝 5 次（结束模拟重置）
- AI 决策输出需为 JSON；解析失败降级规则引擎，不向页面展示异常栈

## 项目结构（简化）

```text
src/main/java/com/cybertown/
  controller/       # API 与页面数据聚合
  service/          # AI、模拟、社交、世界事件、人生事件等
  domain/npc/       # NPC、关系
  domain/world/     # 地点、世界状态、小镇事件
  repository/       # JPA Repository
  graph/            # AI 决策图与工具
src/main/resources/
  static/           # runtime.html + css/ + js/
  application.yml
docs/wiki/
  README.md
  iteration-log.md
  god-mode-spec.md
  data-model.md
  prompt-design.md
```

## 本地 Wiki

- 索引：`docs/wiki/README.md`
- 迭代记录：`docs/wiki/iteration-log.md`
- 上帝模式：`docs/wiki/god-mode-spec.md`
- 数据模型：`docs/wiki/data-model.md`
- Prompt：`docs/wiki/prompt-design.md`
