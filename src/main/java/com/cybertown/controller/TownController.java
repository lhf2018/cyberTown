package com.cybertown.controller;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.repository.NPCRepository;
import com.cybertown.service.AIService;
import com.cybertown.service.NPCSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * API控制器 - 整合SSE支持
 * 展示NPC基础状态
 */
@Slf4j
@RestController
@RequestMapping("/api/town")
@RequiredArgsConstructor
public class TownController {
    private final NPCRepository npcRepository;
    private final AIService aiService;
    private final NPCSimulatorService npcSimulatorService;
    private final SimpMessagingTemplate messagingTemplate;

    // SSE emitters 列表，用于HTTP长连接推送
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 存储最新的NPC状态缓存
    private Map<String, Object> latestNPCStatus = new HashMap<>();

    // ============= REST API 端点 =============

    /**
     * 1. 获取所有NPC
     */
    @GetMapping("/npcs")
    public List<NPC> getAllNPCs() {
        return npcRepository.findAll();
    }

    /**
     * 2. 获取单个NPC
     */
    @GetMapping("/npc/{id}")
    public NPC getNPC(@PathVariable String id) {
        return npcRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NPC未找到：" + id));
    }

    /**
     * 3. 与NPC对话
     */
    @PostMapping("/npc/{id}/talk")
    public Map<String, String> talkToNPC(@PathVariable String id,
                                         @RequestBody Map<String, String> request) {
        String message = request.get("message");
        NPC npc = getNPC(id);

        //todo 待完善
        String response = aiService.generateDialogue(npc, message);

        return Map.of(
                "npc", npc.getName(),
                "response", response,
                "mood", getMoodEmoji(npc.getStats().getHappiness())
        );
    }

    /**
     * 上帝模式：通过自然语言修改NPC属性
     */
    @PostMapping("/npc/{id}/god-command")
    public Map<String, Object> godCommand(@PathVariable String id, @RequestBody Map<String, String> request) {
        NPC npc = getNPC(id);
        String instruction = request == null ? null : request.get("instruction");
        AIService.GodModification mod = aiService.parseGodInstruction(npc, instruction);

        applyGodModification(npc, mod);
        NPC saved = npcRepository.save(npc);
        String npcReply = aiService.generateGodReply(saved, instruction);

        return Map.of(
                "message", "上帝指令执行完成",
                "npcId", saved.getId(),
                "npcName", saved.getName(),
                "npcReply", npcReply,
                "npc", saved
        );
    }

    /**
     * 5. 初始化小镇
     */
    @PostMapping("/init")
    public String initializeTown() {
        npcSimulatorService.initializeNPCs();
        return "小镇初始化完成，创建了 " + npcRepository.count() + " 个NPC";
    }

    /**
     * 使用大模型初始化人物
     */
    @PostMapping("/init/ai")
    public Map<String, Object> initializeTownWithAI(@RequestBody(required = false) Map<String, Object> request) {
        int count = 5;
        boolean clearExisting = false;

        if (request != null) {
            Object countObj = request.get("count");
            if (countObj instanceof Number number) {
                count = number.intValue();
            }
            Object clearObj = request.get("clearExisting");
            if (clearObj instanceof Boolean bool) {
                clearExisting = bool;
            }
        }

        List<NPC> created = npcSimulatorService.initializeNPCsWithAI(count, clearExisting);

        return Map.of(
                "message", "AI人物生成完成",
                "createdCount", created.size(),
                "totalCount", npcRepository.count(),
                "npcs", created
        );
    }

    /**
     * 6. 获取小镇状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "npcCount", npcRepository.count(),
                "message", "赛博小镇运行正常",
                "timestamp", System.currentTimeMillis(),
                "time", LocalDateTime.now().toString(),
                "sseConnections", emitters.size()
        );
    }

    /**
     * 7. 获取所有NPC的当前状态（一次性HTTP请求）
     */
    @GetMapping("/npcs/status")
    public Map<String, Object> getNPCsStatus() {
        Map<String, Object> statusData = buildNPCStatusData();
        latestNPCStatus = statusData; // 更新缓存
        return statusData;
    }

    /**
     * 8. SSE端点：实时获取NPC状态更新
     * 访问: http://localhost:8080/api/town/npcs/status/stream
     */
    @GetMapping(value = "/npcs/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNPCStatus() {
        SseEmitter emitter = new SseEmitterUTF8(12000_000L); // 120秒超时，足够了

        emitter.onError((e) -> {
            emitters.remove(emitter);
            log.error("SSE连接错误: {}", e.getMessage());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("SSE连接超时，剩余连接数: {}", emitters.size());
        });

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE连接关闭，剩余连接数: {}", emitters.size());
        });

        // 将emitter添加到列表
        emitters.add(emitter);
        log.info("新的SSE连接，当前连接数: {}", emitters.size());

        // 使用 CompletableFuture 异步发送初始数据
        CompletableFuture.runAsync(() -> {
            try {
                // 发送连接成功消息
                Map<String, Object> connectMsg = Map.of(
                        "type", "CONNECTED",
                        "message", "已连接到赛博小镇实时状态流",
                        "timestamp", LocalDateTime.now().toString(),
                        "npcCount", npcRepository.count(),
                        "connectionId", "sse-" + System.currentTimeMillis()
                );

                sendSSEEvent(emitter, "connected", connectMsg);

                // 发送初始数据（确保有数据）
                Thread.sleep(200);
                Map<String, Object> initialData = buildNPCStatusData();
                latestNPCStatus = initialData;

                sendSSEEvent(emitter, "initial", initialData);

                // 可选：发送欢迎消息
                sendSSEEvent(emitter, "message", Map.of(
                        "text", "欢迎使用赛博小镇实时状态监控",
                        "tip", "状态将每30秒自动更新",
                        "timestamp", LocalDateTime.now().toString()
                ));

            } catch (Exception e) {
                log.error("发送初始数据失败", e);
                try {
                    sendSSEEvent(emitter, "error", Map.of(
                            "message", "初始化失败: " + e.getMessage(),
                            "timestamp", LocalDateTime.now().toString()
                    ));
                } catch (IOException ex) {
                    log.error("发送错误消息失败", ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 优化的SSE事件发送方法，确保UTF-8编码
     */
    private void sendSSEEvent(SseEmitter emitter, String name, Object data) throws IOException {
        if (emitter == null) return;

        try {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                    .name(name)
                    .data(data, new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                    .reconnectTime(5000L)
                    .id(String.valueOf(System.currentTimeMillis()));

            emitter.send(eventBuilder);

        } catch (Exception e) {
        }
    }


    /**
     * 9. 手动触发状态更新广播
     */
    @PostMapping("/broadcast/status")
    public String broadcastStatus() {
        Map<String, Object> statusData = buildNPCStatusData();

        // 1. 更新缓存
        latestNPCStatus = statusData;

        // 2. 广播到所有SSE连接
        broadcastToSSE(statusData);

        return String.format("已广播NPC状态到 %d 个SSE连接", emitters.size());
    }

    /**
     * 世界广播（纯展示，不影响数值）
     */
    @GetMapping("/world/broadcast")
    public Map<String, Object> getWorldBroadcast() {
        List<NPC> npcs = npcRepository.findAll();
        String[] globalEvents = {
                "霓虹主干道出现临时集市，夜间人流激增。",
                "企业区发布义体打折广告，预约排队中。",
                "地下频道流传一条未知黑客宣言，引发热议。",
                "中央公园开启全息灯光秀，市民停留时间增加。",
                "仿生餐厅推出新菜单，外卖订单上涨。",
                "警方宣布今晚加大巡逻，但暂无新增冲突。"
        };

        String message = globalEvents[new Random().nextInt(globalEvents.length)];
        if (!npcs.isEmpty()) {
            NPC randomNpc = npcs.get(new Random().nextInt(npcs.size()));
            message = message + " 目击者称 " + randomNpc.getName() + " 正在 " + randomNpc.getCurrentLocation() + " 活动。";
        }

        return Map.of(
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 10. 获取单个NPC的详细状态
     */
    @GetMapping("/npc/{id}/status/detail")
    public Map<String, Object> getNPCDetailStatus(@PathVariable String id) {
        NPC npc = getNPC(id);

        Map<String, Object> response = new LinkedHashMap<>(); // 保持插入顺序

        // 分组存储，结构更清晰
        response.put("基本信息", Map.of(
                "id", npc.getId(),
                "name", npc.getName(),
                "occupation", npc.getOccupation(),
                "personality", npc.getPersonality()
        ));

        response.put("当前状态", Map.of(
                "location", npc.getCurrentLocation(),
                "action", npc.getCurrentAction(),
                "goal", npc.getCurrentGoal()
        ));

        response.put("数值状态", Map.of(
                "energy", npc.getStats().getEnergy(),
                "hunger", npc.getStats().getHunger(),
                "happiness", npc.getStats().getHappiness(),
                "socialNeed", npc.getStats().getSocialNeed()
        ));

        response.put("状态描述", Map.of(
                "mood", getMoodEmoji(npc.getStats().getHappiness()),
                "isHungry", npc.isHungry(),
                "isTired", npc.isTired()
        ));

        response.put("时间信息", Map.of(
                "createdAt", npc.getCreatedAt(),
                "updatedAt", npc.getUpdatedAt(),
                "timestamp", LocalDateTime.now()
        ));

        return response;
    }

    /**
     * 11. 获取NPC统计摘要
     */
    @GetMapping("/stats/summary")
    public Map<String, Object> getNPCStatsSummary() {
        List<NPC> npcs = npcRepository.findAll();

        long hungryCount = npcs.stream().filter(NPC::isHungry).count();
        long tiredCount = npcs.stream().filter(NPC::isTired).count();
        long happyCount = npcs.stream().filter(n -> n.getStats().getHappiness() > 70).count();
        long normalCount = npcs.stream().filter(n ->
                n.getStats().getHappiness() >= 40 && n.getStats().getHappiness() <= 70).count();
        long sadCount = npcs.stream().filter(n -> n.getStats().getHappiness() < 40).count();

        // 位置分布
        Map<String, Long> locationStats = new HashMap<>();
        npcs.forEach(npc -> {
            String location = npc.getCurrentLocation();
            locationStats.put(location, locationStats.getOrDefault(location, 0L) + 1);
        });

        // 职业分布
        Map<String, Long> occupationStats = new HashMap<>();
        npcs.forEach(npc -> {
            String occupation = npc.getOccupation();
            occupationStats.put(occupation, occupationStats.getOrDefault(occupation, 0L) + 1);
        });

        return Map.of(
                "total", npcs.size(),
                "hungryCount", hungryCount,
                "tiredCount", tiredCount,
                "happyCount", happyCount,
                "normalCount", normalCount,
                "sadCount", sadCount,
                "locationStats", locationStats,
                "occupationStats", occupationStats,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    /**
     * 结束模拟并清空NPC
     */
    @PostMapping("/simulation/end")
    public Map<String, Object> endSimulation() {
        long before = npcRepository.count();
        npcSimulatorService.endSimulation();
        return Map.of(
                "message", "模拟已结束，可重新初始化",
                "deletedCount", before,
                "currentCount", npcRepository.count()
        );
    }

// ============= 定时任务 =============

    /**
     * 定时广播NPC状态（每30秒）
     */
    @Scheduled(fixedRate = 30000)
    public void autoBroadcastNPCStatus() {
        if (emitters.isEmpty()) {
            return; // 没有连接时不广播
        }

        Map<String, Object> statusData = buildNPCStatusData();

        // 1. 更新缓存
        latestNPCStatus = statusData;

        // 2. 广播到SSE连接
        broadcastToSSE(statusData);

        log.debug("定时广播NPC状态，SSE连接数: {}", emitters.size());
    }

    /**
     * 定时广播小镇心跳（每60秒）
     */
    @Scheduled(fixedRate = 60000)
    public void autoBroadcastTownHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> heartbeat = Map.of(
                "type", "HEARTBEAT",
                "timestamp", LocalDateTime.now().toString(),
                "npcCount", npcRepository.count(),
                "sseConnections", emitters.size(),
                "message", "赛博小镇运行正常"
        );

        broadcastToSSE(heartbeat);
        messagingTemplate.convertAndSend("/topic/town/heartbeat", heartbeat);

        log.debug("定时广播小镇心跳");
    }

// ============= 辅助方法 =============

    /**
     * 构建NPC状态数据
     */
    private Map<String, Object> buildNPCStatusData() {
        List<NPC> npcs = npcRepository.findAll();

        List<Map<String, Object>> npcStatusList = new ArrayList<>();

        for (NPC npc : npcs) {
            Map<String, Object> status = new HashMap<>();
            status.put("id", npc.getId());
            status.put("name", npc.getName());
            status.put("occupation", npc.getOccupation());
            status.put("location", npc.getCurrentLocation());
            status.put("action", npc.getCurrentAction());
            status.put("currentGoal", npc.getCurrentGoal());

            // 基础状态值
            status.put("energy", npc.getStats().getEnergy());
            status.put("hunger", npc.getStats().getHunger());
            status.put("happiness", npc.getStats().getHappiness());
            status.put("socialNeed", npc.getStats().getSocialNeed());
            status.put("money", npc.getStats().getMoney());
            status.put("savings", npc.getStats().getSavings());
            status.put("debt", npc.getStats().getDebt());
            status.put("skillLevel", npc.getStats().getSkillLevel());
            status.put("knowledgeLevel", npc.getStats().getKnowledgeLevel());
            status.put("health", npc.getStats().getHealth());
            status.put("reputation", npc.getStats().getReputation());
            status.put("educationLevel", npc.getStats().getEducationLevel());
            status.put("workExperience", npc.getStats().getWorkExperience());

            // 状态表情
            status.put("mood", getMoodEmoji(npc.getStats().getHappiness()));
            status.put("isHungry", npc.isHungry());
            status.put("isTired", npc.isTired());
            status.put("statusSummary", getStatusSummary(npc));
            status.put("currentThought", CollectionUtils.isEmpty(npc.getCurrentThoughts()) ? null : npc.getCurrentThoughts().get(npc.getCurrentThoughts().size() - 1));

            npcStatusList.add(status);
        }

        return Map.of(
                "type", "NPC_STATUS_UPDATE",
                "timestamp", LocalDateTime.now().toString(),
                "count", npcStatusList.size(),
                "npcs", npcStatusList
        );
    }

    /**
     * 广播数据到所有SSE连接
     */
    private void broadcastToSSE(Map<String, Object> data) {
        if (emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("update")
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
                log.debug("SSE连接已失效，移除");
            }
        }

        // 清理失效的emitter
        emitters.removeAll(deadEmitters);
    }


    private String getMoodEmoji(int happiness) {
        if (happiness > 80) return "高兴";
        if (happiness > 60) return "还行";
        if (happiness > 40) return "一般";
        if (happiness > 20) return "沮丧";
        return "痛苦";
    }

    /**
     * 获取NPC状态摘要
     */
    private String getStatusSummary(NPC npc) {
        List<String> statuses = new ArrayList<>();

        if (npc.isHungry()) statuses.add("饥饿");
        if (npc.isTired()) statuses.add("疲劳");
        if (npc.getStats().getHappiness() < 30) statuses.add("低落");
        if (npc.getStats().getSocialNeed() > 70) statuses.add("需社交");

        if (statuses.isEmpty()) {
            return "正常";
        }

        return String.join("、", statuses);
    }

    private void applyGodModification(NPC npc, AIService.GodModification mod) {
        if (mod == null) {
            return;
        }
        NPCStats s = npc.getStats();
        if (mod.currentLocation() != null && !mod.currentLocation().isBlank()) npc.setCurrentLocation(mod.currentLocation().trim());
        if (mod.currentAction() != null && !mod.currentAction().isBlank()) npc.setCurrentAction(mod.currentAction().trim());
        if (mod.currentGoal() != null && !mod.currentGoal().isBlank()) npc.setCurrentGoal(mod.currentGoal().trim());
        if (mod.educationLevel() != null && !mod.educationLevel().isBlank()) s.setEducationLevel(mod.educationLevel().trim());

        if (mod.money() != null) s.setMoney(Math.max(0, mod.money()));
        if (mod.savings() != null) s.setSavings(Math.max(0, mod.savings()));
        if (mod.debt() != null) s.setDebt(Math.max(0, mod.debt()));
        if (mod.workExperience() != null) s.setWorkExperience(Math.max(0, mod.workExperience()));

        if (mod.intelligence() != null) s.setIntelligence(clamp(mod.intelligence(), 0, 100));
        if (mod.charisma() != null) s.setCharisma(clamp(mod.charisma(), 0, 100));
        if (mod.skillLevel() != null) s.setSkillLevel(clamp(mod.skillLevel(), 0, 100));
        if (mod.knowledgeLevel() != null) s.setKnowledgeLevel(clamp(mod.knowledgeLevel(), 0, 100));
        if (mod.health() != null) s.setHealth(clamp(mod.health(), 0, 100));
        if (mod.reputation() != null) s.setReputation(clamp(mod.reputation(), 0, 100));
        if (mod.energy() != null) s.setEnergy(clamp(mod.energy(), 0, 100));
        if (mod.hunger() != null) s.setHunger(clamp(mod.hunger(), 0, 100));
        if (mod.happiness() != null) s.setHappiness(clamp(mod.happiness(), 0, 100));
        if (mod.socialNeed() != null) s.setSocialNeed(clamp(mod.socialNeed(), 0, 100));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}