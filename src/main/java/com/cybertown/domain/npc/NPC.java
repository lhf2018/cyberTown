package com.cybertown.domain.npc;

import lombok.Data;  // Lombok注解：自动生成getter、setter、toString等方法
import jakarta.persistence.*;  // JPA注解：用于对象关系映射

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * NPC（非玩家角色）实体类
 * 代表赛博小镇中的居民，每个NPC有独立的性格、状态和行为
 *
 * @Entity 表示这是一个JPA实体类，对应数据库中的表
 * @Table 指定表名为"npcs"
 */
@Entity
@Table(name = "npcs")
@Data
public class NPC {

    @Id  // 主键字段
    private String id;  // NPC唯一标识符

    // 基本信息字段
    private String name;           // NPC姓名
    private String occupation;     // 职业：程序员、警察、医生等
    private String personality;    // 性格描述：内向、外向、善良等
    private String currentLocation; // 当前位置
    private String status;         // 当前状态：工作中、休息中、社交中
    private String currentGoal;    // 当前目标：AI生成的目标

    /**
     * NPC属性值对象
     *
     * @Embedded 表示这个对象是嵌入到NPC表中的，不是独立表
     */
    @Embedded
    private NPCStats stats;  // NPC的各项属性值

    /**
     * NPC关系映射
     *
     * @ElementCollection 表示这是一个集合映射
     * 存储NPC与其他NPC的关系值（-100到100）
     * key: 目标NPC的ID，value: 关系值
     */
    @ElementCollection
    @CollectionTable(name = "npc_relationships")  // 指定关系表的表名
    @MapKeyColumn(name = "target_npc_id")         // 指定Map的key列名
    @Column(name = "relationship_value")          // 指定Map的value列名
    private Map<String, Integer> relationships = new HashMap<>();

    @Column(length = 2000)  // 指定字段长度，记忆可能较长
    private String memory;  // AI记忆：存储NPC的重要记忆和经历

    // 时间戳字段
    private LocalDateTime createdAt;  // 创建时间
    private LocalDateTime updatedAt;  // 最后更新时间

    /**
     * JPA生命周期回调：在持久化（保存到数据库）之前执行
     * 用于自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA生命周期回调：在更新之前执行
     * 用于自动更新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}