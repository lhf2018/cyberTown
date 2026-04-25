package com.cybertown.domain.npc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Embeddable;

/**
 * NPC的数值属性
 * 嵌入到NPC表中，不是独立表
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NPCStats {

    // 基础需求（0-100）
    @Builder.Default
    private int energy = 80;     // 能量：低了需要休息
    @Builder.Default
    private int hunger = 30;     // 饥饿：高了需要吃饭
    @Builder.Default
    private int happiness = 60;  // 快乐度：影响行为
    @Builder.Default
    private int socialNeed = 50; // 社交需求：高了想找人

    // 资源
    @Builder.Default
    private double money = 1000.0; // 金钱

    // 能力
    @Builder.Default
    private int intelligence = 50; // 智力
    @Builder.Default
    private int charisma = 50;    // 魅力
    @Builder.Default
    private int skillLevel = 40;   // 职业技能
    @Builder.Default
    private int knowledgeLevel = 45; // 知识储备
    @Builder.Default
    private int technicalKnowledge = 45; // 技术知识
    @Builder.Default
    private int businessKnowledge = 35; // 商业知识
    @Builder.Default
    private int socialKnowledge = 40; // 人际知识
    @Builder.Default
    private int practicalSkill = 42; // 实操技能
    @Builder.Default
    private int creativeSkill = 38; // 创造技能
    @Builder.Default
    private int health = 75;       // 健康水平
    @Builder.Default
    private int reputation = 30;   // 社会声望

    // 长期积累
    @Builder.Default
    private double savings = 200.0; // 储蓄
    @Builder.Default
    private double debt = 0.0;      // 负债
    @Builder.Default
    private int workExperience = 0; // 工作经验（月）
    @Builder.Default
    private String educationLevel = "高中"; // 学历

    // 薪酬详情（可由上帝模式覆盖）
    @Builder.Default
    private double monthlyCashIncome = 0.0; // 月薪现金部分（元）
    @Builder.Default
    private double monthlyStockOptionIncome = 0.0; // 月度折算期权（元）

    // 实用方法
    public void decreaseEnergy(int amount) {
        this.energy = Math.max(0, this.energy - amount);
    }

    public void increaseHunger(int amount) {
        this.hunger = Math.min(100, this.hunger + amount);
    }
}