package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.domain.npc.Relationship;
import com.cybertown.repository.NPCRepository;
import com.cybertown.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private static final int MAX_PAIRS_PER_TICK = 3;
    private static final Pattern AFFINITY_PATTERN = Pattern.compile(
            "(?:对|和|与)\\s*(.+?)\\s*(?:好感|关系)\\s*([+-]?\\d+)|(?:和|与)\\s*(.+?)\\s*(结仇|成为朋友|告白|分手)");

    private final NPCRepository npcRepository;
    private final RelationshipRepository relationshipRepository;
    private final AIService aiService;
    private final TownEventService townEventService;
    private final WorldEventService worldEventService;

    private final Random random = new Random();

    /**
     * 心跳后：同地点配对社交
     */
    @Transactional
    public void processCoLocatedMeetings() {
        List<NPC> all = npcRepository.findAll();
        if (all.size() < 2) {
            return;
        }

        Map<String, List<NPC>> byLoc = all.stream()
                .filter(n -> n.getCurrentLocation() != null && !n.getCurrentLocation().isBlank())
                .collect(Collectors.groupingBy(NPC::getCurrentLocation));

        double socialMul = worldEventService.getModifiers().getSocialChanceMultiplier();
        int pairsDone = 0;
        boolean aiUsed = false;
        Set<String> used = new HashSet<>();

        List<String> locations = new ArrayList<>(byLoc.keySet());
        Collections.shuffle(locations, random);

        for (String loc : locations) {
            if (pairsDone >= MAX_PAIRS_PER_TICK) {
                break;
            }
            List<NPC> group = byLoc.get(loc).stream()
                    .filter(n -> !used.contains(n.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (group.size() < 2) {
                continue;
            }
            // 暴雨等会降低户外社交触发率
            boolean outdoor = loc.contains("街道") || loc.contains("公园");
            int chance = outdoor ? (int) (55 * socialMul) : (int) (75 * socialMul);
            if (random.nextInt(100) >= Math.max(15, Math.min(95, chance))) {
                continue;
            }

            Collections.shuffle(group, random);
            NPC a = group.get(0);
            NPC b = group.get(1);
            used.add(a.getId());
            used.add(b.getId());

            boolean useAi = !aiUsed;
            meetPair(a, b, loc, useAi);
            if (useAi) {
                aiUsed = true;
            }
            pairsDone++;
        }
    }

    private void meetPair(NPC a, NPC b, String location, boolean useAi) {
        Relationship rel = getOrCreate(a.getId(), b.getId());
        int delta = 5 + random.nextInt(16);
        boolean clash = random.nextInt(100) < 18 || "RIVAL".equals(rel.getType());
        if (clash) {
            delta = -(8 + random.nextInt(18));
        }

        String dialogue;
        if (useAi) {
            dialogue = aiService.generateNpcDialogue(a, b, location, rel.getAffinity(), clash);
        } else {
            dialogue = clash
                    ? a.getName() + " 与 " + b.getName() + " 在" + location + "发生口角。"
                    : a.getName() + " 与 " + b.getName() + " 在" + location + "闲聊片刻。";
        }

        applySocialStats(a, delta, clash);
        applySocialStats(b, delta, clash);
        rel.setAffinity(clamp(rel.getAffinity() + delta, -100, 100));
        rel.setLastInteractionAt(LocalDateTime.now());
        rel.setNote(shorten(dialogue, 200));
        rel.setType(inferType(rel.getAffinity(), rel.getType(), clash));
        relationshipRepository.save(rel);

        appendMemory(a, "与" + b.getName() + ": " + dialogue);
        appendMemory(b, "与" + a.getName() + ": " + dialogue);
        a.setCurrentAction(clash ? "与" + b.getName() + "争执" : "与" + b.getName() + "社交");
        b.setCurrentAction(clash ? "与" + a.getName() + "争执" : "与" + a.getName() + "社交");

        npcRepository.save(a);
        npcRepository.save(b);

        townEventService.record(
                "SOCIAL",
                a.getName() + (clash ? " 与 " : " 遇见 ") + b.getName(),
                dialogue,
                a.getId() + "," + b.getId(),
                clash ? "CLASH" : "MEET",
                12
        );
        log.info("社交事件: {} @ {}", dialogue, location);
    }

    private void applySocialStats(NPC npc, int affinityDelta, boolean clash) {
        NPCStats s = npc.getStats();
        s.setSocialNeed(Math.max(0, s.getSocialNeed() - 20 - random.nextInt(15)));
        if (clash) {
            s.setHappiness(Math.max(0, s.getHappiness() - 8));
            s.setEnergy(Math.max(0, s.getEnergy() - 4));
        } else {
            s.setHappiness(Math.min(100, s.getHappiness() + 8 + Math.max(0, affinityDelta / 3)));
            s.setEnergy(Math.max(0, s.getEnergy() - 2));
        }
    }

    private String inferType(int affinity, String current, boolean clash) {
        if (affinity <= -40) {
            return "RIVAL";
        }
        if (affinity >= 70 && ("FRIEND".equals(current) || "LOVER".equals(current))) {
            return current;
        }
        if (affinity >= 55) {
            return "FRIEND";
        }
        if (affinity >= 80 && random.nextInt(100) < 15) {
            return "LOVER";
        }
        if (clash && affinity < 0) {
            return "RIVAL";
        }
        return current == null || current.isBlank() ? "ACQUAINTANCE" : current;
    }

    public Relationship getOrCreate(String id1, String id2) {
        String[] pair = Relationship.orderedPair(id1, id2);
        return relationshipRepository.findByNpcAIdAndNpcBId(pair[0], pair[1])
                .orElseGet(() -> {
                    Relationship r = new Relationship();
                    r.setId(Relationship.normalizeId(id1, id2));
                    r.setNpcAId(pair[0]);
                    r.setNpcBId(pair[1]);
                    r.setAffinity(0);
                    r.setType("ACQUAINTANCE");
                    r.setLastInteractionAt(LocalDateTime.now());
                    return relationshipRepository.save(r);
                });
    }

    public List<Map<String, Object>> listForNpc(String npcId) {
        List<Relationship> list = relationshipRepository.findByNpcId(npcId);
        Map<String, NPC> npcMap = npcRepository.findAll().stream()
                .collect(Collectors.toMap(NPC::getId, n -> n, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Relationship r : list) {
            String otherId = r.otherId(npcId);
            NPC other = npcMap.get(otherId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("relationshipId", r.getId());
            row.put("otherId", otherId);
            row.put("otherName", other == null ? otherId : other.getName());
            row.put("otherOccupation", other == null ? "-" : other.getOccupation());
            row.put("affinity", r.getAffinity());
            row.put("type", r.getType());
            row.put("note", r.getNote());
            row.put("lastInteractionAt", r.getLastInteractionAt());
            out.add(row);
        }
        out.sort((x, y) -> Integer.compare((Integer) y.get("affinity"), (Integer) x.get("affinity")));
        return out;
    }

    public String summarizeNearbyRelations(NPC npc) {
        List<NPC> same = npcRepository.findByCurrentLocation(npc.getCurrentLocation());
        if (same.size() <= 1) {
            return "同地点暂无熟人";
        }
        StringBuilder sb = new StringBuilder();
        for (NPC other : same) {
            if (other.getId().equals(npc.getId())) continue;
            Relationship r = relationshipRepository.findById(Relationship.normalizeId(npc.getId(), other.getId())).orElse(null);
            if (r == null) {
                sb.append(other.getName()).append("(陌生人); ");
            } else {
                sb.append(other.getName()).append("(").append(r.getType()).append(",好感").append(r.getAffinity()).append("); ");
            }
        }
        return sb.length() == 0 ? "同地点暂无熟人" : sb.toString();
    }

    /**
     * 上帝模式：解析关系指令，返回是否生效说明
     */
    @Transactional
    public String applyGodRelationshipInstruction(NPC source, String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        Matcher m = AFFINITY_PATTERN.matcher(instruction);
        if (!m.find()) {
            // 简化：结仇/成为朋友 + 名字
            Pattern simple = Pattern.compile("(?:让)?\\s*(.+?)\\s*(?:和|与)\\s*(.+?)\\s*(结仇|成为朋友|好感\\s*([+-]?\\d+))");
            Matcher m2 = simple.matcher(instruction);
            if (!m2.find()) {
                return null;
            }
            String nameA = m2.group(1).trim();
            String nameB = m2.group(2).trim();
            NPC targetA = findByName(nameA);
            NPC targetB = findByName(nameB);
            if (targetA == null) targetA = source;
            if (targetB == null) {
                return null;
            }
            String action = m2.group(3);
            return mutateRelation(targetA, targetB, action, m2.groupCount() >= 4 ? m2.group(4) : null);
        }

        String name = m.group(1) != null ? m.group(1) : m.group(3);
        NPC target = findByName(name.trim());
        if (target == null || target.getId().equals(source.getId())) {
            return null;
        }
        if (m.group(2) != null) {
            int delta = Integer.parseInt(m.group(2));
            Relationship rel = getOrCreate(source.getId(), target.getId());
            rel.setAffinity(clamp(rel.getAffinity() + delta, -100, 100));
            rel.setType(inferType(rel.getAffinity(), rel.getType(), delta < 0));
            rel.setLastInteractionAt(LocalDateTime.now());
            rel.setNote("上帝调整好感 " + delta);
            relationshipRepository.save(rel);
            return source.getName() + " 对 " + target.getName() + " 好感调整为 " + rel.getAffinity();
        }
        String action = m.group(4);
        return mutateRelation(source, target, action, null);
    }

    private String mutateRelation(NPC a, NPC b, String action, String num) {
        Relationship rel = getOrCreate(a.getId(), b.getId());
        if (action != null && action.contains("结仇")) {
            rel.setAffinity(clamp(rel.getAffinity() - 50, -100, 100));
            rel.setType("RIVAL");
        } else if (action != null && action.contains("朋友")) {
            rel.setAffinity(clamp(Math.max(rel.getAffinity(), 55), -100, 100));
            rel.setType("FRIEND");
        } else if (action != null && action.contains("告白")) {
            rel.setAffinity(clamp(Math.max(rel.getAffinity(), 75), -100, 100));
            rel.setType("LOVER");
        } else if (action != null && action.contains("分手")) {
            rel.setAffinity(clamp(rel.getAffinity() - 30, -100, 100));
            rel.setType("ACQUAINTANCE");
        } else if (num != null) {
            rel.setAffinity(clamp(rel.getAffinity() + Integer.parseInt(num), -100, 100));
            rel.setType(inferType(rel.getAffinity(), rel.getType(), false));
        }
        rel.setLastInteractionAt(LocalDateTime.now());
        rel.setNote("上帝指令: " + action);
        relationshipRepository.save(rel);
        townEventService.record("SOCIAL", "上帝改写关系",
                a.getName() + " ↔ " + b.getName() + " → " + rel.getType() + "(" + rel.getAffinity() + ")",
                a.getId() + "," + b.getId(), "GOD", 6);
        return a.getName() + " 与 " + b.getName() + " 现为 " + rel.getType() + "，好感 " + rel.getAffinity();
    }

    private NPC findByName(String name) {
        return npcRepository.findAll().stream()
                .filter(n -> n.getName() != null && (n.getName().equals(name) || n.getName().contains(name) || name.contains(n.getName())))
                .findFirst()
                .orElse(null);
    }

    private void appendMemory(NPC npc, String line) {
        String old = npc.getDialogueMemory() == null ? "" : npc.getDialogueMemory().trim();
        String entry = shorten(line, 160);
        String merged = old.isBlank() ? entry : old + "\n" + entry;
        String[] lines = merged.split("\n");
        int keep = Math.min(6, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - keep; i < lines.length; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        npc.setDialogueMemory(sb.toString());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
