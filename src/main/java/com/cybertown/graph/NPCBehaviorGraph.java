package com.cybertown.graph;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.world.WorldState;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LangGraph4j NPC 行为决策图
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NPCBehaviorGraph {

    // 工具接口定义
    public interface DecisionAssistant {
        NPCDecisionTools.BasicNeedsResult checkBasicNeeds(
                String npcName, int energy, int hunger,
                int happiness, int socialNeed
        );

        NPCDecisionTools.ScheduleResult checkSchedule(
                String occupation, int hour, String timeOfDay
        );

        NPCDecisionTools.SocialResult checkSocial(
                String personality, int socialNeed,
                int happiness, String npcName
        );

        NPCDecisionTools.LocationResult checkLocation(
                String location, String timeOfDay, String npcName
        );

        NPCDecisionTools.DecisionResult makeFinalDecision(
                String npcName, String occupation, String personality,
                String needsAnalysis, boolean hasUrgentNeed,
                String scheduleSuggestion, boolean isWorkTime,
                String socialSuggestion, String socialPriority,
                String locationSuggestion, String locationType,
                String currentTime
        );
    }

    private final NPCDecisionTools decisionTools;
    private final ChatLanguageModel chatLanguageModel; // 可选

    // LangGraph4j 助手
    private DecisionAssistant decisionAssistant;

    /**
     * 初始化 LangGraph4j
     */
    public void init() {
        try {
            // 创建聊天记忆
            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            // 创建决策助手（LangGraph4j 自动代理）
            this.decisionAssistant = AiServices.builder(DecisionAssistant.class)
                    .chatLanguageModel(chatLanguageModel) // 可选的AI模型
                    .chatMemory(chatMemory)
                    .tools(decisionTools)
                    .build();

            log.info("LangGraph4j 决策助手初始化成功");
        } catch (Exception e) {
            log.error("LangGraph4j 初始化失败，将使用直接调用模式", e);
            this.decisionAssistant = null;
        }
    }

    /**
     * 主入口：执行决策流程
     */
    public String decideNPCAction(NPC npc, WorldState world) {
        log.info("🎯 LangGraph4j 决策开始: {}", npc.getName());

        try {
            // 准备数据
            String npcName = npc.getName();
            String occupation = npc.getOccupation();
            String personality = npc.getPersonality();
            String location = npc.getCurrentLocation();

            int energy = npc.getStats().getEnergy();
            int hunger = npc.getStats().getHunger();
            int happiness = npc.getStats().getHappiness();
            int socialNeed = npc.getStats().getSocialNeed();

            int hour = world.getGameTime().getHour();
            String timeOfDay = world.getTimeOfDay();
            String currentTime = world.getGameTime().toString();

            List<String> decisionLog = new ArrayList<>();

            // 1. 检查基础需求
            log.debug("步骤1: 检查基础需求");
            NPCDecisionTools.BasicNeedsResult needsResult;
            if (decisionAssistant != null) {
                needsResult = decisionAssistant.checkBasicNeeds(
                        npcName, energy, hunger, happiness, socialNeed
                );
            } else {
                needsResult = decisionTools.checkBasicNeeds(
                        npcName, energy, hunger, happiness, socialNeed
                );
            }

            decisionLog.add("需求分析: " + needsResult.getAnalysis());

            // 紧急需求直接返回
            if (needsResult.isHasUrgentNeed() && needsResult.getSuggestedAction() != null) {
                log.info("⚠️ 检测到紧急需求: {}", needsResult.getSuggestedAction());
                return needsResult.getSuggestedAction();
            }

            // 2. 检查日程
            log.debug("步骤2: 检查日程");
            NPCDecisionTools.ScheduleResult scheduleResult;
            if (decisionAssistant != null) {
                scheduleResult = decisionAssistant.checkSchedule(occupation, hour, timeOfDay);
            } else {
                scheduleResult = decisionTools.checkSchedule(occupation, hour, timeOfDay);
            }

            decisionLog.add("日程建议: " + scheduleResult.getSuggestion());

            // 3. 检查社交
            log.debug("步骤3: 检查社交");
            NPCDecisionTools.SocialResult socialResult;
            if (decisionAssistant != null) {
                socialResult = decisionAssistant.checkSocial(
                        personality, socialNeed, happiness, npcName
                );
            } else {
                socialResult = decisionTools.checkSocial(
                        personality, socialNeed, happiness, npcName
                );
            }

            decisionLog.add("社交建议: " + socialResult.getSuggestion());

            // 4. 检查位置
            log.debug("步骤4: 检查位置");
            NPCDecisionTools.LocationResult locationResult;
            if (decisionAssistant != null) {
                locationResult = decisionAssistant.checkLocation(location, timeOfDay, npcName);
            } else {
                locationResult = decisionTools.checkLocation(location, timeOfDay, npcName);
            }

            decisionLog.add("位置建议: " + locationResult.getSuggestion());

            // 5. 综合决策
            log.debug("步骤5: 综合决策");
            NPCDecisionTools.DecisionResult finalResult;
            if (decisionAssistant != null) {
                finalResult = decisionAssistant.makeFinalDecision(
                        npcName, occupation, personality,
                        needsResult.getAnalysis(), needsResult.isHasUrgentNeed(),
                        scheduleResult.getSuggestion(), scheduleResult.isWorkTime(),
                        socialResult.getSuggestion(), socialResult.getPriority(),
                        locationResult.getSuggestion(), locationResult.getLocationType(),
                        currentTime
                );
            } else {
                finalResult = decisionTools.makeFinalDecision(
                        npcName, occupation, personality,
                        needsResult.getAnalysis(), needsResult.isHasUrgentNeed(),
                        scheduleResult.getSuggestion(), scheduleResult.isWorkTime(),
                        socialResult.getSuggestion(), socialResult.getPriority(),
                        locationResult.getSuggestion(), locationResult.getLocationType(),
                        currentTime
                );
            }

            // 记录决策日志
            logDecisionProcess(npcName, decisionLog, finalResult);

            log.info("✅ LangGraph4j 决策完成: {} -> {} (置信度: {}%)",
                    npcName, finalResult.getDecision(), finalResult.getConfidence());

            return finalResult.getDecision();

        } catch (Exception e) {
            log.error("❌ LangGraph4j 决策流程异常", e);
            return generateFallbackDecision(npc, world);
        }
    }

    /**
     * 记录决策过程
     */
    private void logDecisionProcess(String npcName, List<String> decisionLog,
                                    NPCDecisionTools.DecisionResult finalResult) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n=== LangGraph4j 决策报告 ===\n");
        logMessage.append("NPC: ").append(npcName).append("\n");
        logMessage.append("最终决策: ").append(finalResult.getDecision()).append("\n");
        logMessage.append("决策理由: ").append(finalResult.getReason()).append("\n");
        logMessage.append("置信度: ").append(finalResult.getConfidence()).append("%\n");
        logMessage.append("决策流程:\n");

        for (int i = 0; i < decisionLog.size(); i++) {
            logMessage.append("  ").append(i + 1).append(". ").append(decisionLog.get(i)).append("\n");
        }

        log.debug(logMessage.toString());
    }

    /**
     * 生成降级决策
     */
    private String generateFallbackDecision(NPC npc, WorldState world) {
        // 简单的降级逻辑
        if (npc.getStats().getEnergy() < 30) {
            return "去休息";
        }
        if (npc.getStats().getHunger() > 70) {
            return "去吃饭";
        }

        int hour = world.getGameTime().getHour();
        if (hour >= 9 && hour < 18) {
            return "工作";
        } else {
            return "休息或娱乐";
        }
    }

    /**
     * 获取决策助手状态
     */
    public String getAssistantStatus() {
        if (decisionAssistant == null) {
            return "LangGraph4j 助手未启用（使用直接工具调用）";
        }
        return "LangGraph4j 助手已启用";
    }
}