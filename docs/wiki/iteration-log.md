# 迭代记录

## 2026-04-20（晚间增量）

### 迭代主题
- 运行页体验完善与显示修复
- 长期属性系统接入模拟循环
- 上帝模式与NPC反馈对话

### 背景
- 运行页在不同数据源下出现字段展示不一致（刷新后目标/状态/心情/资产丢失）。
- 需要让长期属性不只是“展示字段”，而是实质影响决策与成长。
- 需要提供更强运营能力：管理员自然语言改属性，并让 NPC 产生反馈。

### 主要改动
- **运行页字段兼容修复**
  - 前端兼容 `n.xxx` 与 `n.stats.xxx` 两种数据结构。
  - 修复刷新后目标、心情、状态摘要、资产字段显示为空的问题。
- **世界广播**
  - 新增 `GET /api/town/world/broadcast`。
  - `runtime.html` 顶部新增“世界广播”动态大框，定时刷新。
  - 当前仅展示，不直接修改世界数值（为后续事件系统预留）。
- **长期属性扩展**
  - `NPCStats` 新增：`skillLevel`、`knowledgeLevel`、`health`、`reputation`、`savings`、`debt`、`workExperience`、`educationLevel`。
  - AI 初始化支持这些属性字段并做约束校验。
- **长期系统影响决策**
  - 工作收入与技能/经验挂钩，储蓄自动沉淀。
  - 学习可提升技能知识并触发学历晋升概率。
  - 投资行为引入收益波动，影响幸福感。
  - 债务利息、现金压力、健康与财务状态进入紧急需求与决策触发概率。
  - AI 决策上下文新增长期属性输入。
- **交互能力增强**
  - 运行页支持对单个 NPC 直接对话。
  - 新增上帝接口：`POST /api/town/npc/{id}/god-command`。
  - 上帝指令执行后，新增 `npcReply`（NPC基于变更后的状态第一人称反馈）。
- **对话质量提升**
  - `AIService.generateDialogue()` 升级为全状态驱动提示词：
    - 纳入动作/地点/目标/近期想法/长期属性/健康与财务状态
    - 强化“有趣、个性化、赛博风、简短”回复约束

### API 新增/调整
- `GET /api/town/world/broadcast`：获取随机世界广播文案
- `POST /api/town/npc/{id}/god-command`：管理员自然语言修改NPC属性（并返回NPC反馈）
- `POST /api/town/npc/{id}/talk`：提示词能力增强，回复更贴合当前状态

### 兼容性与说明
- 由于 `ddl-auto: create-drop`，重启会重建表结构；长期属性新增后建议重新初始化。
- 广播系统目前只展示，不影响行为结果，后续可接入事件影响权重。

## 2026-04-20

### 迭代主题
- AI 驱动初始化强化
- 前端流程改为“初始化 -> 运行”
- 支持结束模拟并重新初始化

### 背景
- 之前初始化随机性不够，且运行页面混合了初始化操作，使用路径不清晰。
- 需要让 NPC 初始化更贴近“由 AI 决定角色”的目标。

### 主要改动
- **世界初始化随机化**
  - `WorldState` 初始时间和天气改为随机，避免固定开局状态。
- **AI 初始化能力增强**
  - 新增 `POST /api/town/init/ai`。
  - AI 生成字段覆盖：`name/occupation/personality/location/currentAction/currentGoal`。
  - AI 生成数值属性覆盖：`energy/hunger/happiness/socialNeed/money/intelligence/charisma`。
  - 对 AI 返回进行范围校验和字段兜底，提升稳定性。
- **运行流程重构**
  - `index.html` 改为初始化入口页。
  - 新增 `runtime.html` 作为运行监控页。
  - 初始化成功后自动跳转到运行页；运行页不展示初始化逻辑。
- **重新开局能力**
  - 新增 `POST /api/town/simulation/end`，用于结束模拟并清空 NPC。
  - 运行页增加“结束模拟并返回初始化”按钮。
- **WebSocket 基础配置**
  - 新增 `WebSocketConfig`，解决 `SimpMessagingTemplate` 注入失败问题。

### 主要页面
- 初始化页：`/`（`src/main/resources/static/index.html`）
- 运行页：`/runtime.html`（`src/main/resources/static/runtime.html`）
- AI 初始化工具页：`/ai-init.html`（兼容保留）

### API 新增/调整
- `POST /api/town/init/ai`：AI 生成人物并入库
- `POST /api/town/simulation/end`：结束模拟并清空人物

### 已知限制
- 运行环境若缺少 Maven 命令，终端无法直接执行 `mvn`，建议使用 IDE Maven 面板或补齐本机 PATH。
- AI 输出异常时会走本地回退策略，确保初始化流程不中断。
