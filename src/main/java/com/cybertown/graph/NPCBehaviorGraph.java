package com.cybertown.graph;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.world.WorldState;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
            // 方法1：使用 ChatMemoryProvider
            ChatMemoryProvider chatMemoryProvider = new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    // 为每个对话创建独立的记忆
                    return MessageWindowChatMemory.withMaxMessages(10);
                }
            };

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
    public String decideWithAI(NPC npc, WorldState world) {
        log.info("🤖 AI决策开始: {}", npc.getName());

        if (aiDecisionAssistant == null) {
            log.warn("AI助手未启用，使用规则引擎决策");
            return decideWithRules(npc, world);
        }

        try {
            // 准备数据
            String npcName = npc.getName();
            String sessionId = "ai_" + npc.getId() + "_" + System.currentTimeMillis();

            // 构建prompt参数
            String request = String.format("""
                npcName=%s
                occupation=%s
                personality=%s
                location=%s
                energy=%d
                hunger=%d
                happiness=%d
                socialNeed=%d
                hour=%d
                timeOfDay=%s
                currentTime=%s
                """,
                    npcName, npc.getOccupation(), npc.getPersonality(),
                    npc.getCurrentLocation(),
                    npc.getStats().getEnergy(), npc.getStats().getHunger(),
                    npc.getStats().getHappiness(), npc.getStats().getSocialNeed(),
                    world.getGameTime().getHour(), world.getTimeOfDay(),
                    world.getGameTime().toString()
            );

            // 让AI分析并决策
            String aiResponse = aiDecisionAssistant.analyzeAndDecide(sessionId, request);

            // 解析AI响应，提取决策
            String decision = extractDecisionFromAIResponse(aiResponse);

            log.info("✅ AI决策完成: {} -> {}", npcName, decision);
            log.debug("AI完整响应: {}", aiResponse);

            return decision;

        } catch (Exception e) {
            log.error("❌ AI决策失败: {}", e.getMessage(), e);
            return decideWithRules(npc, world);
        }
    }

    /**
     * 从AI响应中提取决策
     */
    private String extractDecisionFromAIResponse(String aiResponse) {
        // 简单的提取逻辑，可以根据实际情况调整
        if (aiResponse.contains("决策：")) {
            return aiResponse.split("决策：")[1].split("\n")[0].trim();
        } else if (aiResponse.contains("建议：")) {
            return aiResponse.split("建议：")[1].split("\n")[0].trim();
        } else if (aiResponse.contains("行动：")) {
            return aiResponse.split("行动：")[1].split("\n")[0].trim();
        }

        // 默认返回第一行
        return aiResponse.split("\n")[0].trim();
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