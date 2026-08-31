package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.Relationship;
import com.cybertown.domain.world.TownEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveSlotService {

    private final SnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    private Path root() throws IOException {
        Path p = Path.of("data", "saves");
        Files.createDirectories(p);
        return p;
    }

    public List<Map<String, Object>> listSlots() throws IOException {
        Path dir = root();
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong((Path f) -> f.toFile().lastModified()).reversed())
                    .map(f -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        String name = f.getFileName().toString().replace(".json", "");
                        m.put("name", name);
                        m.put("size", f.toFile().length());
                        m.put("updatedAt", LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(f.toFile().lastModified()),
                                java.time.ZoneId.systemDefault()).toString());
                        return m;
                    })
                    .collect(Collectors.toList());
        }
    }

    public Map<String, Object> saveSlot(String name) throws IOException {
        String safe = sanitize(name);
        Map<String, Object> snap = snapshotService.exportSnapshot();
        snap.put("slotName", safe);
        snap.put("savedAt", LocalDateTime.now().toString());
        Path file = root().resolve(safe + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), snap);
        return Map.of("message", "存档成功", "name", safe, "path", file.toString());
    }

    @Transactional
    public Map<String, Object> loadSlot(String name) throws IOException {
        String safe = sanitize(name);
        Path file = root().resolve(safe + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("存档不存在: " + safe);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(file.toFile(), Map.class);
        List<NPC> npcs = convertList(body.get("npcs"), NPC.class);
        List<Relationship> relationships = convertList(body.get("relationships"), Relationship.class);
        List<TownEvent> events = convertList(body.get("events"), TownEvent.class);
        return snapshotService.importEntities(npcs, relationships, events);
    }

    public Map<String, Object> deleteSlot(String name) throws IOException {
        String safe = sanitize(name);
        Path file = root().resolve(safe + ".json");
        boolean deleted = Files.deleteIfExists(file);
        return Map.of("message", deleted ? "已删除" : "存档不存在", "name", safe);
    }

    private <T> List<T> convertList(Object raw, Class<T> type) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<T> out = new ArrayList<>();
        for (Object o : list) {
            out.add(objectMapper.convertValue(o, type));
        }
        return out;
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("存档名不能为空");
        }
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return s;
    }
}
