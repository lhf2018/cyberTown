package com.cybertown.domain.npc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Embeddable;

/**
 * NPC属性值对象
 * 使用值对象模式封装NPC的各项数值属性
 *
 * @Embeddable 表示这个类可以嵌入到其他实体类中
 * @Builder 提供建造者模式，便于创建对象：NPCStats.builder().energy(80).build()
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NPCStats {

    // 基础需求属性（0-100范围）
    @Builder.Default  // 设置建造者模式的默认值
    private int energy = 80;       // 能量：影响是否能行动
    @Builder.Default
    private int hunger = 30;       // 饥饿：随时间增加，需要进食
    @Builder.Default
    private int happiness = 60;    // 快乐：影响NPC的心情和行为

    // 资源属性
    @Builder.Default
    private double money = 1000.0; // 金钱：用于购物和消费

    // 能力属性
    @Builder.Default
    private int intelligence = 50; // 智力：影响AI决策的复杂度
    @Builder.Default
    private int charisma = 50;     // 魅力：影响社交成功率

    // 注意：这个类没有@Id注解，因为它不是实体，而是值对象
    // 会被嵌入到NPC表中作为列存在
}