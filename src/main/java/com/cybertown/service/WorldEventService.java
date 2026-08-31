package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.domain.world.TownEvent;
import com.cybertown.domain.world.WorldState;
import com.cybertown.repository.NPCRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 世界事件生成与全局权重（影响心跳模拟）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldEventService {

    private final TownEventService townEventService;
    private final NewsService newsService;
    private final WorldState worldState;
    private final NPCRepository npcRepository;

    private final Random random = new Random();
    private final AtomicReference<WorldModifiers> modifiers = new AtomicReference<>(WorldModifiers.neutral());

    @Getter
    public static class WorldModifiers {
        private final double socialChanceMultiplier;
        private final double energyDrainMultiplier;
        private final double investReturnBias;
        private final double programmerMoodShift;
        private final String activeTag;
        private final String broadcastMessage;

        public WorldModifiers(double socialChanceMultiplier, double energyDrainMultiplier,
                              double investReturnBias, double programmerMoodShift,
                              String activeTag, String broadcastMessage) {
            this.socialChanceMultiplier = socialChanceMultiplier;
            this.energyDrainMultiplier = energyDrainMultiplier;
            this.investReturnBias = investReturnBias;
            this.programmerMoodShift = programmerMoodShift;
            this.activeTag = activeTag;
            this.broadcastMessage = broadcastMessage;
        }

        public static WorldModifiers neutral() {
            return new WorldModifiers(1.0, 1.0, 0.0, 0.0, "NONE", null);
        }
    }

    public WorldModifiers getModifiers() {
        return modifiers.get();
    }

    public String getBroadcastMessage() {
        WorldModifiers m = modifiers.get();
        if (m.getBroadcastMessage() != null && !m.getBroadcastMessage().isBlank()) {
            return m.getBroadcastMessage();
        }
        String brief = townEventService.activeWorldBrief();
        if (!"暂无活跃世界事件".equals(brief)) {
            return "【世界事件】" + brief;
        }
        return null;
    }

    public String activeWorldBriefSafe() {
        return townEventService.activeWorldBrief();
    }

    /**
     * 心跳后调用：有概率生成新世界事件，并刷新权重 / 对 NPC 施加即时影响
     */
    @Transactional
    public void tick() {
        refreshModifiersFromActive();

        // ~35% 概率尝试生成新事件（若已有未过期同类则跳过）
        if (random.nextInt(100) >= 35) {
            return;
        }

        String tag = pickEventTag();
        List<TownEvent> active = townEventService.activeWorldEvents();
        boolean sameExists = active.stream().anyMatch(e -> tag.equals(e.getSeverity()));
        if (sameExists) {
            return;
        }

        EventSpec spec = buildSpec(tag);
        TownEvent event = townEventService.record("WORLD", spec.title(), spec.detail(), null, tag, spec.ttlHours());
        applyImmediateEffects(tag);
        rebuildModifiers(event);
        log.info("世界事件生效: {} ({})", spec.title(), tag);
    }

    private void refreshModifiersFromActive() {
        List<TownEvent> active = townEventService.activeWorldEvents();
        if (active.isEmpty()) {
            modifiers.set(WorldModifiers.neutral());
            return;
        }
        rebuildModifiers(active.get(0));
    }

    private void rebuildModifiers(TownEvent primary) {
        String tag = primary.getSeverity() == null ? "NONE" : primary.getSeverity();
        WorldModifiers m = switch (tag) {
            case "LAYOFF" -> new WorldModifiers(0.85, 1.05, -0.05, -8,
                    tag, "【裁员潮】企业区裁员传闻扩散：" + primary.getTitle());
            case "MARKET_UP" -> new WorldModifiers(1.1, 1.0, 0.12, 3,
                    tag, "【行情上行】信用点市场走高：" + primary.getTitle());
            case "MARKET_DOWN" -> new WorldModifiers(0.9, 1.0, -0.15, -4,
                    tag, "【行情下行】市场承压：" + primary.getTitle());
            case "STORM" -> new WorldModifiers(0.55, 1.35, 0.0, -2,
                    tag, "【暴雨预警】户外活动受限：" + primary.getTitle());
            case "FESTIVAL" -> new WorldModifiers(1.45, 0.95, 0.05, 5,
                    tag, "【节日氛围】霓虹城进入庆典模式：" + primary.getTitle());
            default -> new WorldModifiers(1.0, 1.0, 0.0, 0.0, tag, primary.getTitle());
        };
        modifiers.set(m);
    }

    private String pickEventTag() {
        String weather = worldState.getWeather() == null ? "" : worldState.getWeather().toUpperCase(Locale.ROOT);
        if (weather.contains("RAIN") && random.nextInt(100) < 40) {
            return "STORM";
        }
        String news = newsService.getNewsBrief().toLowerCase(Locale.ROOT);
        if (news.contains("裁员") || news.contains("失业") || news.contains("layoff")) {
            return "LAYOFF";
        }
        if (news.contains("股市") || news.contains("上涨") || news.contains("行情") || news.contains("牛市")) {
            return "MARKET_UP";
        }
        if (news.contains("下跌") || news.contains("熊市") || news.contains("崩盘")) {
            return "MARKET_DOWN";
        }
        String[] pool = {"LAYOFF", "MARKET_UP", "MARKET_DOWN", "STORM", "FESTIVAL"};
        return pool[random.nextInt(pool.length)];
    }

    private EventSpec buildSpec(String tag) {
        return switch (tag) {
            case "LAYOFF" -> new EventSpec("企业区裁员潮", "多家科技公司宣布优化编制，程序员群体情绪波动。", 4);
            case "MARKET_UP" -> new EventSpec("信用点行情上扬", "黑市与交易所同时报喜，投资热情回暖。", 3);
            case "MARKET_DOWN" -> new EventSpec("市场震荡下行", "投机情绪降温，投资收益承压。", 3);
            case "STORM" -> new EventSpec("赛博暴雨来袭", "户外街道积水，社交与通勤受阻，能量消耗上升。", 2);
            case "FESTIVAL" -> new EventSpec("霓虹节启幕", "中央公园与酒吧区灯光秀，市民社交意愿飙升。", 5);
            default -> new EventSpec("城镇异动", "赛博小镇出现异常波动。", 2);
        };
    }

    private void applyImmediateEffects(String tag) {
        List<NPC> npcs = npcRepository.findAll();
        for (NPC npc : npcs) {
            NPCStats s = npc.getStats();
            switch (tag) {
                case "LAYOFF" -> {
                    if (npc.getOccupation() != null && npc.getOccupation().contains("程序")) {
                        s.setHappiness(Math.max(0, s.getHappiness() - 8));
                        s.setMoney(Math.max(0, s.getMoney() - 50 - random.nextInt(120)));
                    }
                }
                case "MARKET_UP" -> s.setHappiness(Math.min(100, s.getHappiness() + 2));
                case "MARKET_DOWN" -> {
                    if (s.getSavings() > 100) {
                        s.setSavings(Math.max(0, s.getSavings() * 0.97));
                    }
                    s.setHappiness(Math.max(0, s.getHappiness() - 2));
                }
                case "STORM" -> s.setEnergy(Math.max(0, s.getEnergy() - 3));
                case "FESTIVAL" -> {
                    s.setHappiness(Math.min(100, s.getHappiness() + 5));
                    s.setSocialNeed(Math.min(100, s.getSocialNeed() + 5));
                }
                default -> {
                }
            }
        }
        npcRepository.saveAll(npcs);
    }

    private record EventSpec(String title, String detail, int ttlHours) {
    }
}
