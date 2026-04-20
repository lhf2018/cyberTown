package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

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
            Map<String, Object> params = Map.of(
                    "name", npc.getName(),
                    "occupation", npc.getOccupation(),
                    "personality", npc.getPersonality(),
                    "mood", getMoodDescription(npc.getStats().getHappiness()),
                    "playerMessage", playerMessage
            );

            String prompt = npcDialoguePrompt.render(params);
            log.debug("调用AI生成对话：{}\nPrompt: {}", npc.getName(), prompt);

            String response = chatClient.call(prompt);

            return response.trim();

        } catch (Exception e) {
            log.error("AI对话生成失败", e);
            return "（信号干扰...暂时无法回应）";
        }
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

        if (name.isBlank() || occupation.isBlank() || personality.isBlank()) {
            return null;
        }

        if (location.isBlank()) location = "霓虹街道";
        if (currentAction.isBlank()) currentAction = "活动中";
        if (currentGoal.isBlank()) currentGoal = "适应城市节奏";
        return new NPCBlueprint(name, occupation, personality, location, currentAction, currentGoal,
                energy, hunger, happiness, socialNeed, money, intelligence, charisma);
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
                    30 + random.nextInt(71)
            ));
        }
        return list;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record NPCBlueprint(
            String name,
            String occupation,
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
            int charisma
    ) {
    }
}