package com.cybertown.domain.world;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小镇事件时间线（世界/社交/人生/决策）
 */
@Entity
@Table(name = "town_events")
@Data
public class TownEvent {

    @Id
    private String id;

    /** WORLD / SOCIAL / LIFE / DECISION */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String detail;

    /** 逗号分隔的 NPC id */
    @Column(length = 500)
    private String npcIds;

    /** 事件权重标签，如 LAYOFF, MARKET_UP, STORM, FESTIVAL */
    @Column(length = 64)
    private String severity;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
