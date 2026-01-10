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

    // 实用方法
    public void decreaseEnergy(int amount) {
        this.energy = Math.max(0, this.energy - amount);
    }

    public void increaseHunger(int amount) {
        this.hunger = Math.min(100, this.hunger + amount);
    }
}