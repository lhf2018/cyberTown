package com.cybertown.domain.npc;

import lombok.Data;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NPC实体 - 对应数据库中的npcs表
 * 每个NPC是小镇的一个居民
 */
@Entity
@Table(name = "npcs")
@Data
public class NPC {

    @Id
    private String id;           // 唯一ID：npc-1, npc-2...

    // 基本信息
    private String name;         // 姓名：杰克、莉莉
    private String occupation;   // 职业：程序员、警察
    private String personality;  // 性格：内向、外向

    // 当前状态
    private String currentLocation;  // 当前位置：科技公司、酒吧
    private String currentAction;    // 当前动作：工作、休息、社交
    private String currentGoal;      // 当前目标：赚钱、交友

    // NPC属性（嵌入对象）
    @Embedded
    private NPCStats stats;      // 数值属性：能量、心情等

    // 时间戳
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> currentThoughts = new ArrayList<>();

    // 创建时自动设置时间
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // 更新时自动更新时间
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 实用方法：检查是否饿了
    public boolean isHungry() {
        return stats.getHunger() > 70;
    }

    // 实用方法：检查是否累了
    public boolean isTired() {
        return stats.getEnergy() < 30;
    }
}