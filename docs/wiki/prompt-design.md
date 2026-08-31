# Prompt 设计简记

## 决策（LangChain4j / NPCBehaviorGraph）

- 接口返回类型为 **String**（不要直接反序列化为 POJO，避免 `Expected BEGIN_OBJECT`）
- **不向 AiServices 注册 tools**：避免模型反复调工具触发 `exceeded 10 sequential tool executions`
- 系统提示要求：上下文已含状态与「工具预检」，直接决策；只输出纯 JSON：

```json
{
  "decisionAnalysis": "...",
  "finalDecision": "...",
  "newThought": "...",
  "decisionReason": "..."
}
```

- 解析顺序：JSON 对象 → 【章节】文本 → 首行兜底
- 用户请求为 `key=value` 文本，关键字段：
  - 生理与长期属性、近期想法、对话影响
  - `新闻摘要` / `活跃世界事件` / `同地点熟人` / 天气与时段
  - **工具预检**（Java 各调用一次后注入）：`checkBasicNeeds` / `checkSchedule` / `checkSocial` / `checkLocation`
- 解析结果写入 NPC：`lastDecision` / `lastDecisionReason` / `lastDecisionAt` / `lastDecisionSource`
- 失败时降级规则引擎；用户可见理由不得包含异常栈；指标记入 `AiMetricsService`

## 对话

- 玩家对话：全状态驱动，短中文、赛博口语
- NPC↔NPC：每心跳至多 1 次 AI，其余模板

## 上帝模式

要求模型只输出属性修改 JSON；关系指令由规则层二次解析。

## 心里话气泡

融合状态、目标、新闻、对话痕迹，强制 10~28 字单句。
