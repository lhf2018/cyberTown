package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatClient chatClient;
    private final PromptTemplate npcDialoguePrompt;

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
}