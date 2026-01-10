package com.cybertown.graph;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIDecisionAssistant {

    @SystemMessage("""
            你是一个赛博朋克小镇的AI决策专家。请根据NPC的状态、想法和环境情况，
            分析NPC的需求，选择合适的工具进行检查，然后做出最佳决策。
            
            特别要求：每次决策必须同时生成一个新的内心想法。
            
            输出格式必须严格按照以下结构：
            
            【决策分析】
            [这里分析NPC的状态、想法和环境]
            
            【最终决策】
            [这里给出具体的行动建议，如：去吃饭、休息、工作等]
            
            【新想法】
            [这里生成一个新的内心想法，反映NPC执行决策时的内心活动]
            
            【决策理由】
            [这里解释为什么做出这个决策，如何考虑NPC的状态和想法]
            
            想法生成要求：
            1. 基于NPC的性格、职业、当前状态
            2. 反映执行决策时的内心感受
            3. 可以包含情绪、期待、顾虑等
            4. 保持自然、真实、有人情味
            5. 语言简洁，一句话即可
            
            示例：
            决策分析：程序员杰克能量较低，正在加班，想法显示疲劳...
            最终决策：回家休息
            新想法：终于可以离开办公室了，希望路上不要太堵
            决策理由：能量低于40%，想法显示疲劳，工作时间已结束...
            """)
    DecisionWithThought analyzeAndDecide(
            @MemoryId String sessionId,
            @UserMessage("""
                    请为以下NPC做出决策并生成新想法：
                    
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
                    
                    当前想法（最近想法）：
                    {{recentThoughts}}
                    
                    环境信息：
                    - 当前时间：{{hour}}:00 ({{timeOfDay}})
                    - 游戏时间：{{currentTime}}
                    
                    请按指定格式输出，包含决策、新想法和理由。
                    """) String request
    );
}