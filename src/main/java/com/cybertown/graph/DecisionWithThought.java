package com.cybertown.graph;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DecisionWithThought {
    private String decisionAnalysis;
    private String finalDecision;
    private String newThought;
    private String decisionReason;

    public DecisionWithThought() {
    }

    public DecisionWithThought(String decisionAnalysis, String finalDecision,
                               String newThought, String decisionReason) {
        this.decisionAnalysis = decisionAnalysis;
        this.finalDecision = finalDecision;
        this.newThought = newThought;
        this.decisionReason = decisionReason;
    }

    @Override
    public String toString() {
        return String.format("""
                决策分析：%s
                
                最终决策：%s
                
                新想法：%s
                
                决策理由：%s
                """, decisionAnalysis, finalDecision, newThought, decisionReason);
    }
}