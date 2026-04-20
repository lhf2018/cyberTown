# 迭代记录

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
