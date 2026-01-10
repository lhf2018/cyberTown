package com.cybertown.controller;

import com.cybertown.domain.npc.NPC;
import com.cybertown.repository.NPCRepository;
import com.cybertown.service.AIService;
import com.cybertown.service.NPCSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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

    // 注入服务
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

        String response = aiService.generateDialogue(npc, message);

        return Map.of(
                "npc", npc.getName(),
                "response", response,
                "mood", getMoodEmoji(npc.getStats().getHappiness())
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

            // 基础状态值
            status.put("energy", npc.getStats().getEnergy());
            status.put("hunger", npc.getStats().getHunger());
            status.put("happiness", npc.getStats().getHappiness());
            status.put("socialNeed", npc.getStats().getSocialNeed());

            // 状态表情
            status.put("mood", getMoodEmoji(npc.getStats().getHappiness()));
            status.put("isHungry", npc.isHungry());
            status.put("isTired", npc.isTired());
            status.put("statusSummary", getStatusSummary(npc));

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
}