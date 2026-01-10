# 🏙️ 赛博小镇 - Cyber Town

一个由 **Spring AI** 驱动、**DeepSeek** 赋能的赛博朋克风格虚拟世界模拟系统。在这里，每个NPC都拥有独立的"人格"
和记忆，能够自主决策、相互社交并与玩家进行智能对话。

---

## ✨ 核心特性

- **🤖 AI驱动的NPC** - 10个初始NPC，每个都有独特的性格、职业与行为模式
- **🧠 智能决策系统** - NPC会根据时间、地点、心情和人际关系自主决定下一步行动
- **💬 自然语言对话** - 使用DeepSeek API与NPC进行真实、有上下文的对话
- **⏰ 动态时间系统** - 游戏时间以6倍现实速度流动，影响NPC的日常作息
- **🎭 情感与关系模拟** - NPC拥有心情值，会建立并维护与其他NPC的关系网络
- **🏢 场景化地点** - 科技公司、酒吧、黑市等不同地点塑造不同的交互氛围
- **🔄 自动状态更新** - 每分钟自动更新所有NPC状态，世界持续"呼吸"与演进
- **🌐 完整REST API** - 提供开箱即用的API接口，便于前端集成与功能扩展

---

## 🚀 快速开始

### 先决条件

- **JDK 17** 或更高版本
- **Maven 3.6+**
- **DeepSeek API Key**（在[DeepSeek官网](https://www.deepseek.com/)免费申请）

### 步骤 1：获取代码

```bash
# 克隆项目到本地（请替换为实际的仓库地址）
git clone <your-repo-url>
cd cyber-town
```

### 步骤 2：配置API密钥

**方式一：环境变量（推荐）**

```bash
# Linux / macOS
export DEEPSEEK_API_KEY=你的实际密钥

# Windows (Command Prompt)
set DEEPSEEK_API_KEY=你的实际密钥

# Windows (PowerShell)
$env:DEEPSEEK_API_KEY="你的实际密钥"
```

**方式二：修改配置文件**
编辑 `src/main/resources/application.yml`，将 `sk-your-key-here` 替换为你的密钥。

### 步骤 3：启动项目

在项目根目录运行：

```bash
mvn clean spring-boot:run
```

或者在IDE（如IntelliJ IDEA）中直接运行 `CyberTownApplication.java`。

### 步骤 4：验证运行

启动成功后，控制台将显示类似日志：

```
🚀 赛博小镇启动中...
...
🏙️ 赛博小镇已就绪！
```

同时，你可以在浏览器中访问以下地址：

- **应用状态页**：`http://localhost:8080`
- **H2数据库控制台**：`http://localhost:8080/h2-console` (用于开发调试)

---

## 📡 API 接口速查

项目启动后，以下REST API接口立即可用：

### 🎭 NPC 相关接口

| 方法     | 端点                        | 说明              | 示例请求                                            |
|:-------|:--------------------------|:----------------|:------------------------------------------------|
| `GET`  | `/api/town/npcs`          | **获取所有NPC列表**   | `curl http://localhost:8080/api/town/npcs`      |
| `GET`  | `/api/town/npc/{id}`      | **获取单个NPC详情**   | `curl http://localhost:8080/api/town/npc/npc-1` |
| `POST` | `/api/town/npc/{id}/talk` | **与指定NPC对话**    | 见下方详细示例                                         |
| `GET` | `/api/town/npcs/status/stream` | **实时获取所有NPC状态** | 见下方详细示例                                         |

### ⏱️ 时间控制接口

| 方法     | 端点                                    | 说明          |
|:-------|:--------------------------------------|:------------|
| `POST` | `/api/town/time/pause`                | **暂停**游戏时间  |
| `POST` | `/api/town/time/resume`               | **恢复**游戏时间  |

### 🛠️ 系统管理接口

| 方法     | 端点                 | 说明                   |
|:-------|:-------------------|:---------------------|
| `GET`  | `/api/town/status` | 获取小镇运行**状态**         |
| `POST` | `/api/town/init`   | **重新初始化**小镇（重置NPC数据） |

---

## 💬 与NPC对话示例

### 使用 `curl` 命令

```bash
curl -X POST "http://localhost:8080/api/town/npc/npc-1/talk" \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，杰克！今天代码写得顺利吗？"}'
```

### 预期响应示例

```json
{
  "npc": "杰克",
  "response": "别提了，今天公司的服务器又被黑客试探攻击了，防火墙日志看了一下午。（揉了揉太阳穴）这世道，写代码还得兼职网络安全。",
  "mood": "😔"
}
```

### 使用 Postman 或 Insomnia

1. 选择 **POST** 方法
2. URL: `http://localhost:8080/api/town/npc/npc-1/talk`
3. Headers: 添加 `Content-Type: application/json`
4. Body (raw JSON):
   ```json
   {
     "message": "你的问题或对话内容"
   }
   ```

---

## 🏗️ 项目架构

```
com.cybertown/
├── CyberTownApplication.java          # 应用主入口
├── config/
│   └── AIConfig.java                  # AI提示词模板配置
├── controller/
│   └── TownController.java            # REST API控制器
├── domain/                            # 核心领域模型
│   ├── npc/
│   │   ├── NPC.java                   # NPC主实体
│   │   ├── NPCStats.java              # NPC属性（生命值、心情等）
│   │   └── NPCAction.java             # 行为枚举
│   └── world/
│       ├── WorldState.java            # 游戏世界状态（单例）
│       └── Location.java              # 地点实体
├── repository/                        # 数据访问层
│   ├── NPCRepository.java             # NPC数据接口
│   └── LocationRepository.java        # 地点数据接口
└── service/                           # 业务逻辑层
    ├── AIService.java                 # 封装DeepSeek API调用
    ├── TimeService.java               # 管理游戏时间流逝
    └── NPCSimulatorService.java       # NPC状态模拟与更新
```

**数据流向**：`API请求` → `Controller` → `Service` → `AI/Repository` → `DB`

---

## 👥 初始NPC居民介绍

小镇初始化时会自动创建以下10位居民：

| ID       | 姓名     | 职业    | 性格标签           | 初始位置  |
|:---------|:-------|:------|:---------------|:------|
| `npc-1`  | **杰克** | 程序员   | 内向、善良、逻辑性强     | 科技公司  |
| `npc-2`  | **莉莉** | 设计师   | 外向、时尚、有艺术感     | 科技公司  |
| `npc-3`  | **老王** | 酒吧老板  | 精明、务实、消息灵通     | 老王家酒吧 |
| `npc-4`  | **小李** | 警察    | 正义感强、责任心重      | 警察局   |
| `npc-5`  | **阿强** | 黑市商人  | 狡猾、但守信、利益至上    | 黑市    |
| `npc-6`  | **小美** | 医生    | 温柔、体贴、富有同情心    | 诊所    |
| `npc-7`  | **大壮** | 保安    | 强壮、忠诚、头脑简单     | 大厦门口  |
| `npc-8`  | **眼镜** | 黑客    | 技术天才、社交障碍、崇尚自由 | 网络空间  |
| `npc-9`  | **红姐** | 舞者    | 魅力四射、身世神秘、善于交际 | 霓虹酒吧  |
| `npc-10` | **老陈** | 出租车司机 | 见多识广、爱讲故事、老江湖  | 街道    |

---

## ⚙️ 配置详解

主要配置文件位于 `src/main/resources/application.yml`:

```yaml
# DeepSeek AI 配置
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY:sk-your-key-here} # 从环境变量读取密钥
      base-url: https://api.deepseek.com # 关键：指向DeepSeek端点
      chat:
        options:
          model: deepseek-chat   # 使用的模型
          temperature: 0.7       # 创造性（0-1，越高回答越随机）
          max-tokens: 500        # 回答的最大长度

  # H2 内存数据库（开发用）
  datasource:
    url: jdbc:h2:mem:cybertown
    username: sa

# 游戏参数
cybertown:
  time-scale: 6.0 # 时间流速：现实1秒 = 游戏内6秒
```

**关键配置项说明**：

- `base-url`: **必须正确设置为** `https://api.deepseek.com`
- `temperature`: 可调整为 `0.3`（更稳定）或 `0.9`（更有创意）
- `time-scale`: 调大可加快游戏进程

---

## 🔧 开发与调试

### 查看数据库内容

1. 应用启动后，访问 `http://localhost:8080/h2-console`
2. 登录信息：
    - **JDBC URL**: `jdbc:h2:mem:cybertown`
    - **User Name**: `sa`
    - **Password**: (留空)
3. 连接后可查看 `NPCS`、`LOCATIONS` 等表。

### 调整日志输出

编辑 `application.yml`，修改以下日志级别以获得更详细的信息：

```yaml
logging:
  level:
    com.cybertown: DEBUG # 查看业务逻辑详细日志
    org.hibernate.SQL: DEBUG # 查看所有执行的SQL语句
```

### 常见问题排查 (FAQ)

**Q: 启动时报错，提示依赖找不到或版本冲突？**
A: 请确保网络可访问 `https://repo.spring.io/milestone`（Spring AI的仓库）。可以尝试：

```bash
mvn clean compile -U # -U 强制更新快照依赖
```

**Q: 调用对话API，NPC回复“（信号干扰...）”？**
A: 这表示AI调用失败。请检查：

1. `DEEPSEEK_API_KEY` 环境变量是否正确设置
2. 网络是否能正常访问 `api.deepseek.com`
3. 查看应用日志中是否有详细的异常堆栈

**Q: NPC的状态不自动更新？**
A: 检查控制台日志，确认 `TimeService` 和 `NPCSimulatorService` 的定时任务是否正常执行。可能是游戏时间被暂停了，调用
`/api/town/time/resume` 接口恢复。

---

## 🚀 进阶开发与扩展

### 增加新的NPC

在 `NPCSimulatorService.initializeNPCs()` 方法中，参照现有格式添加新的NPC数据即可。

### 增加新的地点类型

1. 在 `Location` 实体中扩展地点属性。
2. 在 `getInitialLocation()` 方法中为相应职业配置新地点。
3. NPC的AI决策会自动将新地点纳入考虑范围。

### 扩展AI交互场景

1. 在 `AIConfig.java` 中定义新的 `PromptTemplate`。
2. 在 `AIService.java` 中创建新的方法使用该模板。
3. 在 `TownController.java` 中暴露新的API端点。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/awesome-feature`)
3. 提交更改 (`git commit -am 'Add awesome feature'`)
4. 推送到分支 (`git push origin feature/awesome-feature`)
5. 创建 Pull Request

---

## 🙏 致谢

- 感谢 [Spring AI](https://spring.io/projects/spring-ai) 项目提供了简洁强大的AI集成能力。
- 感谢 [DeepSeek](https://www.deepseek.com/) 提供优质、高效的AI模型服务。
- 感谢所有开源社区的建设者。

---

**踏入霓虹闪烁的赛博街道，开始你的冒险吧！** ✨

> 如有问题，请查看日志或提交Issue。欢迎提出改进建议！

## 计划后面做的
> 1. 对话干预决策
> 2. 初始化更随机
> 3. 世界信息服务
> 4. web服务、最新新闻来影响决策
> 5. 更多的事件、正向、负向或犯罪系统、随机天气等
> 6. 前端页面