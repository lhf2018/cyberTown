package com.cybertown.graph;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIDecisionAssistant {

    @SystemMessage("""
            你是赛博朋克小镇的 NPC 决策助手。
            用户消息里已包含角色状态和「工具预检」结论，请直接据此决策，不要要求或暗示再调用工具。

            最终回复必须是【纯 JSON 对象】，不要 Markdown，不要代码围栏，不要其它说明。
            字段：
            {
              "decisionAnalysis": "简短分析",
              "finalDecision": "具体行动（短句，如：去仿生餐厅吃饭）",
              "newThought": "一句内心独白",
              "decisionReason": "一句决策理由"
            }
            约束：finalDecision / newThought / decisionReason 用中文，尽量简短。
            """)
    String analyzeAndDecide(
            @MemoryId String sessionId,
            @UserMessage String request
    );
}
