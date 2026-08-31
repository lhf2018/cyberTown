package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.repository.NPCRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小镇周目标 / 轻量剧情线
 */
@Service
@RequiredArgsConstructor
public class QuestService {

    private final NPCRepository npcRepository;
    private final TownEventService townEventService;
    private final WorldEventService worldEventService;

    @Getter
    private volatile QuestState state = QuestState.fresh();

    public record QuestState(
            String id,
            String title,
            String description,
            String winCondition,
            int progress,
            int target,
            String status,
            LocalDateTime startedAt,
            String tip
    ) {
        static QuestState fresh() {
            return new QuestState(
                    "quest-layoff-shield",
                    "裁员潮下的守护者",
                    "本周企业区动荡。帮助程序员群体稳住心情与现金流，避免集体崩溃。",
                    "让所有程序员 happiness≥45 且 money≥400",
                    0,
                    100,
                    "ACTIVE",
                    LocalDateTime.now(),
                    "可对话鼓励、上帝微调，或等待世界事件转向"
            );
        }
    }

    public Map<String, Object> getQuestView() {
        refreshProgress();
        QuestState s = state;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id());
        out.put("title", s.title());
        out.put("description", s.description());
        out.put("winCondition", s.winCondition());
        out.put("progress", s.progress());
        out.put("target", s.target());
        out.put("status", s.status());
        out.put("startedAt", s.startedAt().toString());
        out.put("tip", s.tip());
        out.put("worldTag", worldEventService.getModifiers().getActiveTag());
        return out;
    }

    public synchronized void refreshProgress() {
        List<NPC> npcs = npcRepository.findAll();
        List<NPC> programmers = npcs.stream()
                .filter(n -> n.getOccupation() != null && n.getOccupation().contains("程序"))
                .toList();
        if (programmers.isEmpty()) {
            programmers = npcs;
        }
        int ok = 0;
        for (NPC n : programmers) {
            if (n.getStats().getHappiness() >= 45 && n.getStats().getMoney() >= 400) {
                ok++;
            }
        }
        int progress = programmers.isEmpty() ? 0 : (int) Math.round(ok * 100.0 / programmers.size());
        String status = state.status();
        if (!"DONE".equals(status) && !"FAILED".equals(status)) {
            if (progress >= 100) {
                status = "DONE";
                townEventService.record("LIFE", "周目标完成", state.title() + " 已达成", null, "QUEST_WIN", 24);
            } else if ("LAYOFF".equals(worldEventService.getModifiers().getActiveTag()) && progress < 30
                    && state.startedAt().isBefore(LocalDateTime.now().minusHours(6))) {
                status = "FAILED";
            } else {
                status = "ACTIVE";
            }
        }
        state = new QuestState(
                state.id(), state.title(), state.description(), state.winCondition(),
                progress, 100, status, state.startedAt(), state.tip()
        );
    }

    public Map<String, Object> resetQuest() {
        state = QuestState.fresh();
        townEventService.record("WORLD", "新周目标下达", state.title(), null, "QUEST", 12);
        return getQuestView();
    }

    public void onTownPulse() {
        refreshProgress();
    }
}
