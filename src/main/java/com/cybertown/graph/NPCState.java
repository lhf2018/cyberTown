package com.cybertown.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 决策状态 - 存储决策过程中的所有信息
 */
@Data
public class NPCState {
    // 基础信息
    private String npcId;
    private String npcName;
    private String occupation;
    private String personality;
    private String currentLocation;

    // 属性状态
    private int energyLevel;      // 能量等级 (0-100)
    private int hungerLevel;      // 饥饿等级 (0-100)
    private int happinessLevel;   // 快乐等级 (0-100)
    private int socialNeedLevel;  // 社交需求 (0-100)

    // 时间信息
    private String timeOfDay;     // 时间段
    private int hour;             // 当前小时

    // 决策状态
    private boolean hasUrgentNeed;    // 是否有紧急需求
    private String urgentNeedType;    // 紧急需求类型
    private String urgentNeedReason;  // 紧急需求原因

    private String scheduleSuggestion; // 日程建议
    private String socialSuggestion;   // 社交建议

    private List<String> consideredOptions = new ArrayList<>(); // 考虑中的选项
    private List<String> decisionReasons = new ArrayList<>();   // 决策理由

    private String finalDecision;      // 最终决策
    private String decisionReason;     // 决策理由总结

    // 执行状态
    private boolean decisionMade = false;  // 是否已做出决策
    private boolean processCompleted = false; // 流程是否完成

    /**
     * 添加考虑选项
     */
    public void addConsideredOption(String option, String reason) {
        this.consideredOptions.add(option + " (" + reason + ")");
    }

    /**
     * 添加决策理由
     */
    public void addDecisionReason(String reason) {
        this.decisionReasons.add(reason);
    }

    /**
     * 设置最终决策
     */
    public void setFinalDecision(String decision, String reason) {
        this.finalDecision = decision;
        this.decisionReason = reason;
        this.decisionMade = true;
        addDecisionReason(reason);
    }

    /**
     * 设置紧急需求
     */
    public void setUrgentNeed(String type, String reason) {
        this.hasUrgentNeed = true;
        this.urgentNeedType = type;
        this.urgentNeedReason = reason;
    }

    /**
     * 检查是否有生理需求
     */
    public boolean hasPhysicalNeed() {
        return energyLevel < 30 || hungerLevel > 70;
    }

    /**
     * 获取最紧急的需求
     */
    public String getMostUrgentNeed() {
        if (energyLevel < 20) return "休息 (能量极低)";
        if (hungerLevel > 80) return "进食 (极度饥饿)";
        if (energyLevel < 30) return "休息 (能量低)";
        if (hungerLevel > 70) return "进食 (饥饿)";
        if (happinessLevel < 20) return "改善心情 (心情极差)";
        if (socialNeedLevel > 80) return "社交 (极度孤独)";
        return null;
    }

    /**
     * 生成状态摘要
     */
    public String getStatusSummary() {
        return String.format(
                "能量:%d%% 饥饿:%d%% 心情:%d%% 社交需求:%d%%",
                energyLevel, hungerLevel, happinessLevel, socialNeedLevel
        );
    }
}