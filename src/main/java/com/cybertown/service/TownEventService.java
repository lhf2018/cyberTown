package com.cybertown.service;

import com.cybertown.domain.world.TownEvent;
import com.cybertown.repository.TownEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TownEventService {

    private static final int MAX_EVENTS = 200;

    private final TownEventRepository townEventRepository;

    @Transactional
    public TownEvent record(String type, String title, String detail, String npcIds, String severity, int ttlHours) {
        TownEvent event = new TownEvent();
        event.setId("evt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        event.setType(type);
        event.setTitle(title);
        event.setDetail(detail);
        event.setNpcIds(npcIds);
        event.setSeverity(severity);
        event.setCreatedAt(LocalDateTime.now());
        if (ttlHours > 0) {
            event.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
        } else {
            event.setExpiresAt(LocalDateTime.now().plusHours(24));
        }
        TownEvent saved = townEventRepository.save(event);
        trimIfNeeded();
        return saved;
    }

    public List<TownEvent> recent(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return townEventRepository.findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    public List<TownEvent> activeWorldEvents() {
        return townEventRepository.findByTypeAndExpiresAtAfterOrderByCreatedAtDesc("WORLD", LocalDateTime.now());
    }

    public String activeWorldBrief() {
        List<TownEvent> active = activeWorldEvents();
        if (active.isEmpty()) {
            return "暂无活跃世界事件";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(3, active.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("；");
            sb.append(active.get(i).getTitle());
        }
        return sb.toString();
    }

    private void trimIfNeeded() {
        long count = townEventRepository.count();
        if (count <= MAX_EVENTS) {
            return;
        }
        List<TownEvent> oldest = townEventRepository.findAll(
                PageRequest.of(0, (int) (count - MAX_EVENTS), Sort.by(Sort.Direction.ASC, "createdAt"))
        ).getContent();
        townEventRepository.deleteAll(oldest);
    }
}
