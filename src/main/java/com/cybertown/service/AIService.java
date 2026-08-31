package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatClient chatClient;
    private final PromptTemplate npcDialoguePrompt;
    private final ObjectMapper objectMapper;

    /**
     * 生成NPC对话回应 - 使用模板
     */
    public String generateDialogue(NPC npc, String playerMessage) {
        try {
            String prompt = buildRichDialoguePrompt(npc, playerMessage);
            log.debug("调用AI生成对话：{}\nPrompt: {}", npc.getName(), prompt);

            String response = chatClient.call(prompt);

            return response.trim();

        } catch (Exception e) {
            log.error("AI对话生成失败", e);
            return "（信号干扰...暂时无法回应）";
        }
    }

    /**
     * 评估玩家对话对NPC后续决策的影响。
     * 影响时长由NPC（模型）在当前状态下自行决定。
     */
    public DialogueInfluence evaluateDialogueInfluence(NPC npc, String playerMessage) {
        String prompt = """
                你现在扮演NPC“%s”（职业：%s，性格：%s）。
                玩家刚对你说：%s

                你当前状态：
                - 位置：%s
                - 动作：%s
                - 目标：%s
                - 心情：%d
                - 能量：%d
                - 饥饿：%d
                - 社交需求：%d

                请判断这句话是否会影响你接下来一段时间的行为决策，并输出JSON：
                {
                  "summary": "一句话描述影响，例如：玩家建议我先去补给并保持低调",
                  "durationMinutes": 影响持续分钟数(0-180，0表示不影响),
                  "weight": 影响强度(0-100，越高越可能改变短期决策)
                }

                规则：
                1) 只输出JSON，不要解释。
                2) durationMinutes 和 weight 必须是整数。
                3) 如果玩家话语与你当前处境冲突或你不认同，可以给较低时长和强度。
                """.formatted(
                npc.getName(),
                npc.getOccupation(),
                npc.getPersonality(),
                safeText(playerMessage),
                safeText(npc.getCurrentLocation()),
                safeText(npc.getCurrentAction()),
                safeText(npc.getCurrentGoal()),
                npc.getStats().getHappiness(),
                npc.getStats().getEnergy(),
                npc.getStats().getHunger(),
                npc.getStats().getSocialNeed()
        );

        try {
            String raw = chatClient.call(prompt);
            JsonNode node = objectMapper.readTree(stripMarkdownFence(raw));
            if (!node.isObject()) {
                return DialogueInfluence.none();
            }

            String summary = sanitize(node.path("summary").asText(""));
            int durationMinutes = clamp(node.path("durationMinutes").asInt(0), 0, 180);
            int weight = clamp(node.path("weight").asInt(0), 0, 100);
            if (summary.isBlank() || durationMinutes <= 0 || weight <= 0) {
                return DialogueInfluence.none();
            }
            return new DialogueInfluence(summary, durationMinutes, weight, LocalDateTime.now().plusMinutes(durationMinutes));
        } catch (Exception e) {
            log.warn("对话影响评估失败，降级为无影响: {}", e.getMessage());
            return DialogueInfluence.none();
        }
    }

    private String buildRichDialoguePrompt(NPC npc, String playerMessage) {
        String thoughts = "无";
        if (npc.getCurrentThoughts() != null && !npc.getCurrentThoughts().isEmpty()) {
            int start = Math.max(0, npc.getCurrentThoughts().size() - 3);
            thoughts = String.join("；", npc.getCurrentThoughts().subList(start, npc.getCurrentThoughts().size()));
        }

        return """
                你正在扮演赛博朋克世界里的NPC，请严格代入角色身份回复玩家。

                【角色设定】
                - 姓名：%s
                - 职业：%s
                - 公司/机构：%s
                - 性格：%s

                【当前状态】
                - 位置：%s
                - 当前动作：%s
                - 当前目标：%s
                - 心情：%s
                - 近期想法：%s

                【数值状态】
                - 能量：%d
                - 饥饿：%d
                - 快乐：%d
                - 社交需求：%d
                - 健康：%d

                【长期属性】
                - 现金：%.2f
                - 储蓄：%.2f
                - 负债：%.2f
                - 技能：%d
                - 知识：%d
                - 智力：%d
                - 魅力：%d
                - 声望：%d
                - 学历：%s
                - 工作经验(月)：%d

                玩家对你说：%s
                历史对话（最近几轮）：%s

                【回复要求】
                1) 用第一人称，保持该NPC的个性和职业语气。
                2) 回复 1~2 句话，简短但有趣，带一点赛博朋克风格。
                3) 必须体现当前状态和至少一个属性信息（如钱、负债、健康、技能等）。
                4) 不要跳出角色，不要解释你是AI，不要写旁白标签。
                5) 总长度控制在80字以内。
                6) 玩家是“上帝/管理者”：整体语气需尊重、礼貌，可用“您”称呼。
                7) 尊重不等于失去个性：仍需体现该NPC性格（内向者更克制，外向者更热情，强势者更直率但不失礼）。
                8) 当玩家要求与你当前状态冲突时，应先表达尊重，再给出基于自身处境的真实回应。
                """.formatted(
                npc.getName(),
                npc.getOccupation(),
                safeText(npc.getEmployer()),
                npc.getPersonality(),
                safeText(npc.getCurrentLocation()),
                safeText(npc.getCurrentAction()),
                safeText(npc.getCurrentGoal()),
                getMoodDescription(npc.getStats().getHappiness()),
                thoughts,
                npc.getStats().getEnergy(),
                npc.getStats().getHunger(),
                npc.getStats().getHappiness(),
                npc.getStats().getSocialNeed(),
                npc.getStats().getHealth(),
                npc.getStats().getMoney(),
                npc.getStats().getSavings(),
                npc.getStats().getDebt(),
                npc.getStats().getSkillLevel(),
                npc.getStats().getKnowledgeLevel(),
                npc.getStats().getIntelligence(),
                npc.getStats().getCharisma(),
                npc.getStats().getReputation(),
                safeText(npc.getStats().getEducationLevel()),
                npc.getStats().getWorkExperience(),
                safeText(playerMessage),
                buildDialogueContext(npc)
        );
    }

    private String buildDialogueContext(NPC npc) {
        if (npc.getDialogueMemory() == null || npc.getDialogueMemory().isBlank()) {
            return "无";
        }
        String[] lines = npc.getDialogueMemory().split("\n");
        int keep = Math.min(4, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - keep; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() > 120) {
                line = line.substring(0, 120) + "...";
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "暂无";
        }
        return value.trim();
    }

    // 根据快乐度返回心情描述
    private String getMoodDescription(int happiness) {
        if (happiness > 80) return "非常开心";
        if (happiness > 60) return "开心";
        if (happiness > 40) return "一般";
        if (happiness > 20) return "沮丧";
        return "非常沮丧";
    }

    /**
     * 使用大模型生成人物草案
     */
    public List<NPCBlueprint> generateNPCBlueprints(int count, List<String> existingNames) {
        int targetCount = Math.max(1, Math.min(20, count));
        String existing = (existingNames == null || existingNames.isEmpty())
                ? "无"
                : String.join("、", existingNames.stream().limit(30).toList());

        String prompt = """
                你是赛博朋克游戏策划，请生成 %d 个风格各异的NPC。
                已存在角色名：%s。请尽量避免重名。
                
                输出要求（必须严格遵守）：
                1) 只输出JSON，不要输出Markdown代码块，不要解释文字。
                2) 顶层是数组，每个元素包含以下字段：
                   - name: 中文名
                   - occupation: 职业
                   - employer: 公司或机构
                   - personality: 一句话性格描述
                   - location: 初始地点
                   - currentAction: 初始动作
                   - currentGoal: 初始目标
                   - energy: 能量(0-100)
                   - hunger: 饥饿(0-100)
                   - happiness: 快乐(0-100)
                   - socialNeed: 社交需求(0-100)
                   - money: 金钱(0-10000)
                   - intelligence: 智力(0-100)
                   - charisma: 魅力(0-100)
                   - skillLevel: 职业技能(0-100)
                   - knowledgeLevel: 知识储备(0-100)
                   - technicalKnowledge: 技术知识(0-100)
                   - businessKnowledge: 商业知识(0-100)
                   - socialKnowledge: 人际知识(0-100)
                   - practicalSkill: 实操技能(0-100)
                   - creativeSkill: 创造技能(0-100)
                   - health: 健康水平(0-100)
                   - reputation: 社会声望(0-100)
                   - savings: 储蓄(>=0)
                   - debt: 负债(>=0)
                   - workExperience: 工作经验月数(>=0)
                   - educationLevel: 学历(如高中/大专/本科/硕士/博士/职业认证)
                3) 内容要贴合赛博朋克都市背景，字段都不能为空。
                """.formatted(targetCount, existing);

        try {
            String raw = chatClient.call(prompt);
            String json = stripMarkdownFence(raw);
            JsonNode root = objectMapper.readTree(json);

            List<NPCBlueprint> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    NPCBlueprint npc = toBlueprint(item);
                    if (npc != null) {
                        result.add(npc);
                    }
                }
            }

            if (!result.isEmpty()) {
                return result.stream().limit(targetCount).toList();
            }
        } catch (Exception e) {
            log.warn("AI人物生成失败，使用本地回退方案: {}", e.getMessage());
        }

        return generateFallbackBlueprints(targetCount);
    }

    private NPCBlueprint toBlueprint(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String name = sanitize(item.path("name").asText(""));
        String occupation = sanitize(item.path("occupation").asText(""));
        String employer = sanitize(item.path("employer").asText(""));
        String personality = sanitize(item.path("personality").asText(""));
        String location = sanitize(item.path("location").asText(""));
        String currentAction = sanitize(item.path("currentAction").asText(""));
        String currentGoal = sanitize(item.path("currentGoal").asText(""));
        int energy = clamp(item.path("energy").asInt(70), 0, 100);
        int hunger = clamp(item.path("hunger").asInt(35), 0, 100);
        int happiness = clamp(item.path("happiness").asInt(60), 0, 100);
        int socialNeed = clamp(item.path("socialNeed").asInt(45), 0, 100);
        double money = clamp(item.path("money").asDouble(1000), 0, 10000);
        int intelligence = clamp(item.path("intelligence").asInt(55), 0, 100);
        int charisma = clamp(item.path("charisma").asInt(50), 0, 100);
        int skillLevel = clamp(item.path("skillLevel").asInt(45), 0, 100);
        int knowledgeLevel = clamp(item.path("knowledgeLevel").asInt(50), 0, 100);
        int technicalKnowledge = clamp(item.path("technicalKnowledge").asInt(knowledgeLevel), 0, 100);
        int businessKnowledge = clamp(item.path("businessKnowledge").asInt(40), 0, 100);
        int socialKnowledge = clamp(item.path("socialKnowledge").asInt(45), 0, 100);
        int practicalSkill = clamp(item.path("practicalSkill").asInt(skillLevel), 0, 100);
        int creativeSkill = clamp(item.path("creativeSkill").asInt(40), 0, 100);
        int health = clamp(item.path("health").asInt(75), 0, 100);
        int reputation = clamp(item.path("reputation").asInt(30), 0, 100);
        double savings = clamp(item.path("savings").asDouble(200), 0, 1_000_000);
        double debt = clamp(item.path("debt").asDouble(0), 0, 1_000_000);
        int workExperience = clamp(item.path("workExperience").asInt(0), 0, 600);
        String educationLevel = sanitize(item.path("educationLevel").asText("高中"));

        if (name.isBlank() || occupation.isBlank() || personality.isBlank()) {
            return null;
        }

        if (location.isBlank()) location = "霓虹街道";
        if (currentAction.isBlank()) currentAction = "活动中";
        if (currentGoal.isBlank()) currentGoal = "适应城市节奏";
        return new NPCBlueprint(name, occupation, employer, personality, location, currentAction, currentGoal,
                energy, hunger, happiness, socialNeed, money, intelligence, charisma,
                skillLevel, knowledgeLevel, technicalKnowledge, businessKnowledge, socialKnowledge, practicalSkill, creativeSkill,
                health, reputation, savings, debt, workExperience, educationLevel);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ").trim();
    }

    private String stripMarkdownFence(String text) {
        if (text == null) {
            return "[]";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf('\n');
            if (firstLineEnd > -1) {
                cleaned = cleaned.substring(firstLineEnd + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        return cleaned.trim();
    }

    private List<NPCBlueprint> generateFallbackBlueprints(int count) {
        String[] names = {"夜枭", "零度", "阿澈", "影狐", "弧光", "Vera", "洛恩", "尘羽", "霓桃", "铁穹"};
        String[] occupations = {"数据猎人", "义体工程师", "街头记者", "无人机调度员", "情报中间人"};
        String[] personalities = {"冷静理性但重承诺", "外向健谈且观察敏锐", "沉默寡言却富有同理心", "行动激进但讲原则", "幽默随和且执行力强"};
        String[] locations = {"霓虹街道", "科技公司", "赛博诊所", "地下黑市", "中央公园", "全息商场"};
        String[] actions = {"整理装备", "联络线人", "检查系统日志", "赶往下一个任务点", "和路人攀谈"};
        String[] goals = {"完成一单高风险委托", "积攒信用点升级义体", "拓展情报网络", "找到可靠合作伙伴", "提升职业技能"};

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<NPCBlueprint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new NPCBlueprint(
                    names[random.nextInt(names.length)] + (i + 1),
                    occupations[random.nextInt(occupations.length)],
                    randomPick("阿里巴巴", "腾讯云", "字节跳动", "赛博市政厅", "新纪元医疗", "霓虹夜场集团"),
                    personalities[random.nextInt(personalities.length)],
                    locations[random.nextInt(locations.length)],
                    actions[random.nextInt(actions.length)],
                    goals[random.nextInt(goals.length)],
                    55 + random.nextInt(41),
                    15 + random.nextInt(56),
                    40 + random.nextInt(56),
                    20 + random.nextInt(61),
                    300 + random.nextInt(2701),
                    35 + random.nextInt(66),
                    30 + random.nextInt(71),
                    30 + random.nextInt(61),
                    35 + random.nextInt(61),
                    35 + random.nextInt(61),
                    25 + random.nextInt(61),
                    30 + random.nextInt(61),
                    30 + random.nextInt(61),
                    35 + random.nextInt(61),
                    50 + random.nextInt(46),
                    10 + random.nextInt(41),
                    100 + random.nextInt(1501),
                    random.nextInt(801),
                    random.nextInt(120),
                    randomPick("高中", "大专", "本科", "硕士", "职业认证")
            ));
        }
        return list;
    }

    private String randomPick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 上帝指令：把自然语言转换为属性修改建议
     */
    public GodModification parseGodInstruction(NPC npc, String instruction) {
        String prompt = """
                你是游戏管理员助手。请根据管理员指令，输出针对单个NPC的属性修改JSON。
                NPC当前信息：
                - name: %s
                - occupation: %s
                - personality: %s
                - location: %s
                - action: %s
                - goal: %s
                - money: %.2f
                - savings: %.2f
                - debt: %.2f
                - intelligence: %d
                - charisma: %d
                - skillLevel: %d
                - knowledgeLevel: %d
                - health: %d
                - reputation: %d
                - energy: %d
                - hunger: %d
                - happiness: %d
                - socialNeed: %d
                - educationLevel: %s
                - workExperience: %d

                管理员指令：%s

                只输出JSON对象，不要任何解释。字段可选：
                {
                  "currentLocation": "string",
                  "currentAction": "string",
                  "currentGoal": "string",
                  "educationLevel": "string",
                  "money": number,
                  "savings": number,
                  "debt": number,
                  "intelligence": number,
                  "charisma": number,
                  "skillLevel": number,
                  "knowledgeLevel": number,
                  "health": number,
                  "reputation": number,
                  "energy": number,
                  "hunger": number,
                  "happiness": number,
                  "socialNeed": number,
                  "workExperience": number
                }
                约束：0-100 的字段必须在范围内；money/savings/debt/workExperience >= 0。
                """.formatted(
                npc.getName(),
                npc.getOccupation(),
                npc.getPersonality(),
                npc.getCurrentLocation(),
                npc.getCurrentAction(),
                npc.getCurrentGoal(),
                npc.getStats().getMoney(),
                npc.getStats().getSavings(),
                npc.getStats().getDebt(),
                npc.getStats().getIntelligence(),
                npc.getStats().getCharisma(),
                npc.getStats().getSkillLevel(),
                npc.getStats().getKnowledgeLevel(),
                npc.getStats().getHealth(),
                npc.getStats().getReputation(),
                npc.getStats().getEnergy(),
                npc.getStats().getHunger(),
                npc.getStats().getHappiness(),
                npc.getStats().getSocialNeed(),
                npc.getStats().getEducationLevel(),
                npc.getStats().getWorkExperience(),
                instruction == null ? "" : instruction.trim()
        );

        try {
            String raw = chatClient.call(prompt);
            String json = stripMarkdownFence(raw);
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return GodModification.empty();
            }
            return GodModification.from(node);
        } catch (Exception e) {
            log.warn("上帝指令解析失败: {}", e.getMessage());
            return GodModification.empty();
        }
    }

    /**
     * 上帝指令执行后的NPC反馈
     */
    public String generateGodReply(NPC npc, String instruction) {
        try {
            String prompt = """
                    你是%s（%s），性格是：%s。
                    现在“上帝”刚刚对你下达并生效了指令：%s

                    你当前状态：
                    - 位置：%s
                    - 动作：%s
                    - 目标：%s
                    - 心情：%s
                    - 金钱：%.2f
                    - 储蓄：%.2f
                    - 负债：%.2f
                    - 学历：%s
                    - 技能：%d
                    - 知识：%d
                    - 声望：%d

                    请你用第一人称说1-2句话回应玩家，表达你对这次变化的感受和下一步打算。
                    要求：中文、赛博朋克风格、自然口语、不要解释规则。
                    """.formatted(
                    npc.getName(),
                    npc.getOccupation(),
                    npc.getPersonality(),
                    instruction == null ? "（无）" : instruction,
                    npc.getCurrentLocation(),
                    npc.getCurrentAction(),
                    npc.getCurrentGoal(),
                    getMoodDescription(npc.getStats().getHappiness()),
                    npc.getStats().getMoney(),
                    npc.getStats().getSavings(),
                    npc.getStats().getDebt(),
                    npc.getStats().getEducationLevel(),
                    npc.getStats().getSkillLevel(),
                    npc.getStats().getKnowledgeLevel(),
                    npc.getStats().getReputation()
            );
            return chatClient.call(prompt).trim();
        } catch (Exception e) {
            log.warn("生成上帝反馈失败: {}", e.getMessage());
            return "信号有点乱，但我能感觉到命运刚被你改写了。";
        }
    }

    /**
     * NPC 之间短对话（控费用：每心跳至多调用一次）
     */
    public String generateNpcDialogue(NPC a, NPC b, String location, int affinity, boolean clash) {
        try {
            String prompt = """
                    生成两名赛博小镇居民的短对话（总共1-2句中文，口语，带一点赛博风）。
                    A：%s（%s，性格：%s）
                    B：%s（%s，性格：%s）
                    地点：%s
                    当前好感：%d
                    气氛：%s
                    只输出对话内容，不要旁白，不要引号。
                    """.formatted(
                    a.getName(), a.getOccupation(), a.getPersonality(),
                    b.getName(), b.getOccupation(), b.getPersonality(),
                    location, affinity, clash ? "争执/不合" : "友善闲聊"
            );
            return sanitize(chatClient.call(prompt));
        } catch (Exception e) {
            log.warn("NPC对话生成失败: {}", e.getMessage());
            return clash
                    ? a.getName() + "：" + b.getName() + "，你这次越界了。 / " + b.getName() + "：少教训我。"
                    : a.getName() + "：今晚信号还稳。 / " + b.getName() + "：稳就好，喝一杯？";
        }
    }

    public String generateThoughtBubble(NPC npc, String newsHeadline) {
        try {
            String prompt = """
                    你是%s（职业：%s，公司：%s，性格：%s）。
                    当前动作：%s
                    当前目标：%s
                    当前状态：能量%d，饥饿%d，心情%d，社交需求%d
                    最近对话上下文：%s
                    最近新闻：%s

                    请生成一句“内心独白气泡文案”（10~28字，中文，口语化，带一点赛博风）。
                    要求必须融合：当前状态 + 最近行为/目标 + 新闻影响 + 对话痕迹（若有）。
                    只输出一句话，不要解释，不要引号。
                    """.formatted(
                    npc.getName(),
                    npc.getOccupation(),
                    safeText(npc.getEmployer()),
                    npc.getPersonality(),
                    safeText(npc.getCurrentAction()),
                    safeText(npc.getCurrentGoal()),
                    npc.getStats().getEnergy(),
                    npc.getStats().getHunger(),
                    npc.getStats().getHappiness(),
                    npc.getStats().getSocialNeed(),
                    safeText(npc.getDialogueMemory()),
                    safeText(newsHeadline)
            );
            String response = chatClient.call(prompt);
            return sanitize(response);
        } catch (Exception e) {
            return npc.getName() + " 想：先稳住节奏，再看今晚新闻风向。";
        }
    }

    public record NPCBlueprint(
            String name,
            String occupation,
            String employer,
            String personality,
            String location,
            String currentAction,
            String currentGoal,
            int energy,
            int hunger,
            int happiness,
            int socialNeed,
            double money,
            int intelligence,
            int charisma,
            int skillLevel,
            int knowledgeLevel,
            int technicalKnowledge,
            int businessKnowledge,
            int socialKnowledge,
            int practicalSkill,
            int creativeSkill,
            int health,
            int reputation,
            double savings,
            double debt,
            int workExperience,
            String educationLevel
    ) {
    }

    public record GodModification(
            String currentLocation,
            String currentAction,
            String currentGoal,
            String educationLevel,
            Double money,
            Double savings,
            Double debt,
            Integer intelligence,
            Integer charisma,
            Integer skillLevel,
            Integer knowledgeLevel,
            Integer health,
            Integer reputation,
            Integer energy,
            Integer hunger,
            Integer happiness,
            Integer socialNeed,
            Integer workExperience
    ) {
        public static GodModification empty() {
            return new GodModification(null, null, null, null,
                    null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null);
        }

        public static GodModification from(JsonNode node) {
            return new GodModification(
                    text(node, "currentLocation"),
                    text(node, "currentAction"),
                    text(node, "currentGoal"),
                    text(node, "educationLevel"),
                    number(node, "money"),
                    number(node, "savings"),
                    number(node, "debt"),
                    integer(node, "intelligence"),
                    integer(node, "charisma"),
                    integer(node, "skillLevel"),
                    integer(node, "knowledgeLevel"),
                    integer(node, "health"),
                    integer(node, "reputation"),
                    integer(node, "energy"),
                    integer(node, "hunger"),
                    integer(node, "happiness"),
                    integer(node, "socialNeed"),
                    integer(node, "workExperience")
            );
        }

        private static String text(JsonNode node, String field) {
            JsonNode v = node.get(field);
            return (v == null || v.isNull()) ? null : v.asText();
        }

        private static Double number(JsonNode node, String field) {
            JsonNode v = node.get(field);
            return (v == null || v.isNull()) ? null : v.asDouble();
        }

        private static Integer integer(JsonNode node, String field) {
            JsonNode v = node.get(field);
            return (v == null || v.isNull()) ? null : v.asInt();
        }
    }

    public record DialogueInfluence(
            String summary,
            int durationMinutes,
            int weight,
            LocalDateTime expiresAt
    ) {
        public static DialogueInfluence none() {
            return new DialogueInfluence(null, 0, 0, null);
        }

        public boolean active() {
            return summary != null && !summary.isBlank() && durationMinutes > 0 && weight > 0 && expiresAt != null;
        }
    }
}