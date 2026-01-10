package com.cybertown.graph;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIDecisionAssistant {

    @SystemMessage("""
            你是一个赛博朋克小镇的AI决策专家。请根据NPC的状态和环境情况，
            分析NPC的需求，选择合适的工具进行检查，然后做出最佳决策。
            
            决策原则：
            1. 紧急生理需求优先（能量<20%或饥饿>85%）
            2. 工作时间优先工作
            3. 考虑性格特点（外向/内向影响社交选择）
            4. 当前位置影响活动选择
            
            请按以下步骤思考：
            1. 分析NPC的当前状态
            2. 调用合适工具获取详细信息
            3. 综合所有信息做出决策
            4. 给出决策理由
            """)
    String analyzeAndDecide(
            @MemoryId String sessionId,
            @UserMessage("""
                    请为以下NPC做出决策：
                    
                    NPC档案：
                    - 姓名：{{npcName}}
                    - 职业：{{occupation}}
                    - 性格：{{personality}}
                    - 当前位置：{{location}}
                    
                    当前状态：
                    - 能量：{{energy}}%
                    - 饥饿：{{hunger}}%
                    - 心情：{{happiness}}%
                    - 社交需求：{{socialNeed}}%
                    
                    环境信息：
                    - 当前时间：{{hour}}:00 ({{timeOfDay}})
                    - 游戏时间：{{currentTime}}
                    
                    请仔细分析情况，调用合适的工具，给出最佳行动建议。
                    """) String request
    );
}