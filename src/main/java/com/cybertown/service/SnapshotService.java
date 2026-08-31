package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.Relationship;
import com.cybertown.domain.world.TownEvent;
import com.cybertown.repository.NPCRepository;
import com.cybertown.repository.RelationshipRepository;
import com.cybertown.repository.TownEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final NPCRepository npcRepository;
    private final RelationshipRepository relationshipRepository;
    private final TownEventRepository townEventRepository;
    private final TownEventService townEventService;
    private final ModeService modeService;

    public Map<String, Object> exportSnapshot() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("exportedAt", LocalDateTime.now().toString());
        snap.put("npcs", npcRepository.findAll());
        snap.put("relationships", relationshipRepository.findAll());
        snap.put("events", townEventService.recent(100));
        return snap;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> importSnapshot(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("快照为空");
        }
        npcRepository.deleteAll();
        relationshipRepository.deleteAll();
        townEventRepository.deleteAll();
        modeService.resetAll();

        Object npcsObj = body.get("npcs");
        int npcCount = 0;
        if (npcsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof NPC npc) {
                    npcRepository.save(npc);
                    npcCount++;
                } else if (o instanceof Map<?, ?> map) {
                    // Jackson 可能反序列化为 LinkedHashMap；交由控制器先转实体更稳
                }
            }
        }

        Object relObj = body.get("relationships");
        int relCount = 0;
        if (relObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Relationship r) {
                    relationshipRepository.save(r);
                    relCount++;
                }
            }
        }

        Object evtObj = body.get("events");
        int evtCount = 0;
        if (evtObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof TownEvent e) {
                    townEventRepository.save(e);
                    evtCount++;
                }
            }
        }

        return Map.of(
                "message", "快照导入完成",
                "npcCount", npcCount,
                "relationshipCount", relCount,
                "eventCount", evtCount
        );
    }

    @Transactional
    public Map<String, Object> importEntities(List<NPC> npcs, List<Relationship> relationships, List<TownEvent> events) {
        npcRepository.deleteAll();
        relationshipRepository.deleteAll();
        townEventRepository.deleteAll();
        modeService.resetAll();

        if (npcs != null && !npcs.isEmpty()) {
            npcRepository.saveAll(npcs);
        }
        if (relationships != null && !relationships.isEmpty()) {
            relationshipRepository.saveAll(relationships);
        }
        if (events != null && !events.isEmpty()) {
            townEventRepository.saveAll(events);
        }
        return Map.of(
                "message", "快照导入完成",
                "npcCount", npcs == null ? 0 : npcs.size(),
                "relationshipCount", relationships == null ? 0 : relationships.size(),
                "eventCount", events == null ? 0 : events.size()
        );
    }
}
