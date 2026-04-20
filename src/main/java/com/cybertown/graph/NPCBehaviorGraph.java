package com.cybertown.graph;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.world.WorldState;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI驱动的NPC行为决策图
 * 真正利用大模型进行分析和决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NPCBehaviorGraph {

    private final NPCDecisionTools decisionTools;
    private final ChatLanguageModel chatLanguageModel;

    private AIDecisionAssistant aiDecisionAssistant;

    /**
     * 初始化AI决策助手
     */
    @PostConstruct
    public void init() {
        try {
            ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(10);

            this.aiDecisionAssistant = AiServices.builder(AIDecisionAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .chatMemoryProvider(chatMemoryProvider)  // 使用 ChatMemoryProvider
                    .tools(decisionTools)
                    .build();

            log.info("🤖 AI决策助手初始化成功 - 使用大模型驱动决策");

        } catch (Exception e) {
            log.error("AI决策助手初始化失败: {}", e.getMessage(), e);
            this.aiDecisionAssistant = null;
        }
    }

    /**
     * AI驱动决策（主入口）
     */
    public NPCDecisionTools.DecisionResult decideWithAI(NPC npc, WorldState world) {
        log.info("🤖 AI决策开始: {}", npc.getName());

        NPCDecisionTools.DecisionResult result = new NPCDecisionTools.DecisionResult();
        result.setNpcName(npc.getName());
        result.setTimestamp(LocalDateTime.now());

        if (aiDecisionAssistant == null) {
            String ruleDecision = decideWithRules(npc, world);
            result.setDecision(ruleDecision);
            result.setReason("规则引擎决策（AI未启用）");
            return result;
        }

        try {
            // 准备数据
            String npcName = npc.getName();
            String sessionId = "ai_" + npc.getId() + "_" + System.currentTimeMillis();

            // 获取最近的想法（最多3个）
            String recentThoughts = getRecentThoughts(npc);

            // 构建请求
            String request = String.format("""
                            npcName=%s
                            occupation=%s
                            personality=%s
                            location=%s
                            energy=%d
                            hunger=%d
                            happiness=%d
                            socialNeed=%d
                            money=%.2f
                            savings=%.2f
                            debt=%.2f
                            skillLevel=%d
                            knowledgeLevel=%d
                            health=%d
                            reputation=%d
                            educationLevel=%s
                            workExperience=%d
                            recentThoughts=%s
                            hour=%d
                            timeOfDay=%s
                            currentTime=%s
                            """,
                    npc.getName(),
                    npc.getOccupation(),
                    npc.getPersonality(),
                    npc.getCurrentLocation(),
                    npc.getStats().getEnergy(),
                    npc.getStats().getHunger(),
                    npc.getStats().getHappiness(),
                    npc.getStats().getSocialNeed(),
                    npc.getStats().getMoney(),
                    npc.getStats().getSavings(),
                    npc.getStats().getDebt(),
                    npc.getStats().getSkillLevel(),
                    npc.getStats().getKnowledgeLevel(),
                    npc.getStats().getHealth(),
                    npc.getStats().getReputation(),
                    npc.getStats().getEducationLevel(),
                    npc.getStats().getWorkExperience(),
                    recentThoughts,
                    world.getGameTime().getHour(),
                    world.getTimeOfDay(),
                    world.getGameTime().toString()
            );

            // 让AI分析并决策
            DecisionWithThought aiResponse = aiDecisionAssistant.analyzeAndDecide(sessionId, request);

            // 提取结果
            String decision = extractDecision(aiResponse);
            String newThought = extractNewThought(aiResponse);
            String reason = extractDecisionReason(aiResponse);

            result.setDecision(decision);
            result.setNewThought(newThought);
            result.setReason(reason);
            result.setSource("AI决策");
            result.setAiAnalysis(aiResponse.getDecisionAnalysis());

            log.info("✅ AI决策完成: {} -> {}", npcName, decision);
            log.debug("AI完整响应: {}", aiResponse);

            return result;

        } catch (Exception e) {
            log.error("❌ AI决策失败: {}", e.getMessage());
            // 降级到规则引擎
            String ruleDecision = decideWithRules(npc, world);
            result.setDecision(ruleDecision);
            result.setReason("规则引擎决策（AI失败: " + e.getMessage() + ")");
            return result;
        }
    }

    /**
     * 获取最近的想法
     */
    private String getRecentThoughts(NPC npc) {
        if (npc.getCurrentThoughts() == null || npc.getCurrentThoughts().isEmpty()) {
            return "暂无想法";
        }

        // 取最近3个想法
        int maxThoughts = Math.min(3, npc.getCurrentThoughts().size());
        List<String> recent = new ArrayList<>();

        for (int i = 0; i < maxThoughts; i++) {
            recent.add(npc.getCurrentThoughts().get(i));
        }

        return String.join("；", recent);
    }

    /**
     * 从AI响应中提取决策
     */
    private String extractDecision(DecisionWithThought response) {
        if (response == null || response.getFinalDecision() == null) {
            return "日常活动";
        }

        String decision = response.getFinalDecision().trim();

        // 清理可能的格式标记
        decision = decision.replace("【最终决策】", "")
                .replace("最终决策：", "")
                .replace("决策：", "")
                .trim();

        // 只取第一行或第一个句子
        if (decision.contains("\n")) {
            decision = decision.split("\n")[0].trim();
        }
        if (decision.contains("。")) {
            decision = decision.split("。")[0].trim();
        }

        return decision.isEmpty() ? "日常活动" : decision;
    }

    /**
     * 从AI响应中提取新想法
     */
    private String extractNewThought(DecisionWithThought response) {
        if (response == null || response.getNewThought() == null) {
            return null;
        }

        String thought = response.getNewThought().trim();

        // 清理可能的格式标记
        thought = thought.replace("【新想法】", "")
                .replace("新想法：", "")
                .replace("想法：", "")
                .trim();

        // 只取第一句
        if (thought.contains("\n")) {
            thought = thought.split("\n")[0].trim();
        }
        if (thought.contains("。")) {
            thought = thought.split("。")[0].trim();
        }

        return thought.isEmpty() ? null : thought;
    }

    /**
     * 从AI响应中提取决策理由
     */
    private String extractDecisionReason(DecisionWithThought response) {
        if (response == null || response.getDecisionReason() == null) {
            return "综合考虑NPC状态和环境";
        }

        String reason = response.getDecisionReason().trim();

        // 清理可能的格式标记
        reason = reason.replace("【决策理由】", "")
                .replace("决策理由：", "")
                .replace("理由：", "")
                .trim();

        return reason.isEmpty() ? "综合考虑NPC状态和环境" : reason;
    }

    /**
     * 规则引擎决策（备选方案）
     */
    public String decideWithRules(NPC npc, WorldState world) {
        log.info("⚙️ 规则引擎决策: {}", npc.getName());

        try {
            // 使用原有的决策逻辑
            String npcName = npc.getName();
            int energy = npc.getStats().getEnergy();
            int hunger = npc.getStats().getHunger();
            int happiness = npc.getStats().getHappiness();
            int socialNeed = npc.getStats().getSocialNeed();

            int hour = world.getGameTime().getHour();
            String location = npc.getCurrentLocation();

            // 简单的规则引擎
            if (energy < 20) {
                return "立即休息";
            }
            if (hunger > 85) {
                return "立即吃饭";
            }
            if (energy < 40) {
                return "考虑休息";
            }
            if (hunger > 65) {
                return "去吃饭";
            }

            // 工作时间判断
            boolean isWorkTime = isWorkTime(npc.getOccupation(), hour);
            if (isWorkTime) {
                return "继续工作";
            }

            // 社交需求
            if (socialNeed > 80) {
                return "进行社交活动";
            }

            // 根据位置选择
            if (location.contains("酒吧") || location.contains("娱乐")) {
                return "享受娱乐活动";
            }

            if (happiness < 30) {
                return "改善心情";
            }

            return "日常活动";

        } catch (Exception e) {
            log.error("规则引擎决策失败", e);
            return "保持现状";
        }
    }

    /**
     * 判断是否是工作时间
     */
    private boolean isWorkTime(String occupation, int hour) {
        return switch (occupation) {
            case "程序员", "设计师", "医生", "警察" -> hour >= 9 && hour < 18;
            case "酒吧老板" -> hour >= 16 || hour < 2;
            default -> hour >= 9 && hour < 18;
        };
    }
}