package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.domain.world.WorldState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI服务类：负责与DeepSeek AI交互
 *
 * @Service 表示这是一个业务服务组件
 * @Slf4j Lombok：自动注入日志对象
 * @RequiredArgsConstructor Lombok：为final字段生成构造函数
 */
@Slf4j  // 自动提供log变量，用于日志记录
@Service
@RequiredArgsConstructor  // 为所有final字段生成构造函数（依赖注入）
public class AIService {

    // 依赖注入（通过构造函数）
    private final ChatClient chatClient;      // Spring AI的聊天客户端
    private final WorldState worldState;      // 世界状态
    private final PromptTemplate npcDecisionPrompt;  // 决策提示词模板
    private final PromptTemplate npcDialoguePrompt;  // 对话提示词模板

    /**
     * 为NPC生成决策
     *
     * @param npc 需要决策的NPC
     * @return AI生成的决策文本
     */
    public String decideNPCAction(NPC npc) {
        try {
            // 1. 准备AI调用参数
            Map<String, Object> params = Map.of(
                    "name", npc.getName(),                  // NPC姓名
                    "occupation", npc.getOccupation(),      // 职业
                    "personality", npc.getPersonality(),    // 性格
                    "location", npc.getCurrentLocation(),   // 位置
                    "status", npc.getStatus(),              // 状态
                    "goal", npc.getCurrentGoal() != null ? npc.getCurrentGoal() : "无特定目标", // 目标
                    "mood", getMoodFromStats(npc.getStats()), // 心情（根据属性计算）
                    "time", worldState.getGameTime().toString(), // 游戏时间
                    "weather", worldState.getWeather()      // 天气
            );

            // 2. 渲染提示词（替换变量）
            String prompt = npcDecisionPrompt.render(params);
            log.debug("AI决策Prompt: {}", prompt);  // 调试日志

            // 3. 调用AI接口
            String decision = chatClient.call(prompt);
            log.info("AI决策结果: {} -> {}", npc.getName(), decision);  // 信息日志

            return decision;
        } catch (Exception e) {
            // 4. 异常处理：AI调用失败时返回默认行为
            log.error("AI决策失败", e);  // 错误日志
            return "在当前位置停留";      // 降级方案
        }
    }

    /**
     * 生成NPC对话
     *
     * @param npc           对话的NPC
     * @param playerMessage 玩家说的话
     * @return NPC的回应
     */
    public String generateDialogue(NPC npc, String playerMessage) {
        try {
            // 准备对话参数
            Map<String, Object> params = Map.of(
                    "name", npc.getName(),
                    "occupation", npc.getOccupation(),
                    "personality", npc.getPersonality(),
                    "mood", getMoodFromStats(npc.getStats()),
                    "playerMessage", playerMessage
            );

            // 渲染对话提示词
            String prompt = npcDialoguePrompt.render(params);
            log.debug("AI对话Prompt: {}", prompt);

            // 调用AI生成回应
            String response = chatClient.call(prompt);
            log.info("AI对话: {} 回复: {}", npc.getName(), response);

            return response;
        } catch (Exception e) {
            // 异常处理
            log.error("AI对话生成失败", e);
            return "（信号干扰...）";  // 赛博朋克风格的错误回应
        }
    }

    /**
     * 根据NPC属性计算心情状态
     *
     * @param stats NPC属性
     * @return 心情描述
     */
    private String getMoodFromStats(NPCStats stats) {
        // 根据happiness值返回不同心情
        if (stats.getHappiness() > 80) return "非常开心";
        if (stats.getHappiness() > 60) return "开心";
        if (stats.getHappiness() > 40) return "一般";
        if (stats.getHappiness() > 20) return "沮丧";
        return "非常沮丧";
    }
}