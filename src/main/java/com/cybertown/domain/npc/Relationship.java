package com.cybertown.domain.npc;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * NPC 双向关系（按 id 字典序规范化存成 A/B）
 */
@Entity
@Table(name = "relationships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"npc_a_id", "npc_b_id"})
})
@Data
public class Relationship {

    @Id
    private String id;

    @Column(name = "npc_a_id", nullable = false)
    private String npcAId;

    @Column(name = "npc_b_id", nullable = false)
    private String npcBId;

    /** -100 ~ 100 */
    private int affinity;

    /** ACQUAINTANCE / FRIEND / RIVAL / LOVER / MENTOR */
    private String type = "ACQUAINTANCE";

    private LocalDateTime lastInteractionAt;

    @Column(length = 500)
    private String note;

    public static String normalizeId(String id1, String id2) {
        if (id1.compareTo(id2) <= 0) {
            return id1 + "__" + id2;
        }
        return id2 + "__" + id1;
    }

    public static String[] orderedPair(String id1, String id2) {
        if (id1.compareTo(id2) <= 0) {
            return new String[]{id1, id2};
        }
        return new String[]{id2, id1};
    }

    public String otherId(String npcId) {
        return npcAId.equals(npcId) ? npcBId : npcAId;
    }
}
