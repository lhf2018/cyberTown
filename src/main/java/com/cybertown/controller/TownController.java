package com.cybertown.controller;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.repository.NPCRepository;
import com.cybertown.service.AIService;
import com.cybertown.service.NewsService;
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
    private final NewsService newsService;
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
    public Map<String, Object> talkToNPC(@PathVariable String id,
                                         @RequestBody Map<String, String> request) {
        String message = request.get("message");
        NPC npc = getNPC(id);
        return processTalk(npc, message);
    }

    @GetMapping(value = "/npc/{id}/talk/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter talkToNPCStream(@PathVariable String id, @RequestParam("message") String message) {
        NPC npc = getNPC(id);
        SseEmitter emitter = new SseEmitterUTF8(120_000L);
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> result = processTalk(npc, message);
                String response = String.valueOf(result.getOrDefault("response", ""));
                for (String chunk : splitToChunks(response, 8)) {
                    sendSSEEvent(emitter, "chunk", Map.of("text", chunk));
                    Thread.sleep(30);
                }
                sendSSEEvent(emitter, "done", result);
                emitter.complete();
            } catch (Exception e) {
                try {
                    sendSSEEvent(emitter, "error", Map.of("message", "对话失败: " + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private Map<String, Object> processTalk(NPC npc, String message) {
        String response = aiService.generateDialogue(npc, message);
        appendDialogueMemory(npc, message, response);
        AIService.DialogueInfluence influence = aiService.evaluateDialogueInfluence(npc, message);
        if (influence.active()) {
            npc.setDialogueInfluence(influence.summary());
            npc.setDialogueInfluenceWeight(influence.weight());
            npc.setDialogueInfluenceExpiresAt(influence.expiresAt());
            npc.getCurrentThoughts().add("玩家对话影响: " + influence.summary());
        } else {
            npc.setDialogueInfluence(null);
            npc.setDialogueInfluenceWeight(0);
            npc.setDialogueInfluenceExpiresAt(null);
        }
        npcRepository.save(npc);

        return Map.of(
                "npc", npc.getName(),
                "response", response,
                "mood", getMoodEmoji(npc.getStats().getHappiness()),
                "influenceActive", influence.active(),
                "influenceSummary", influence.active() ? influence.summary() : null,
                "influenceWeight", influence.active() ? influence.weight() : 0,
                "influenceDurationMinutes", influence.active() ? influence.durationMinutes() : 0,
                "context", npc.getDialogueMemory() == null ? "" : npc.getDialogueMemory()
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
        mod = enhanceGodModificationFromInstruction(mod, instruction);

        applyGodModification(npc, mod);
        applyCompensationOverridesFromInstruction(npc, instruction);
        String requestedSchool = detectSchoolFromInstruction(instruction);
        refreshAndStoreEducationHistory(npc, requestedSchool);
        NPC saved = npcRepository.save(npc);
        String npcReply = aiService.generateGodReply(saved, instruction);

        return Map.of(
                "message", "上帝指令执行完成",
                "npcId", saved.getId(),
                "npcName", saved.getName(),
                "educationLevel", saved.getStats().getEducationLevel(),
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

    @GetMapping("/world/news")
    public Map<String, Object> getWorldNews() {
        return Map.of(
                "headlines", newsService.getTopHeadlines(8),
                "brief", newsService.getNewsBrief(),
                "updatedAt", newsService.getLastUpdated().toString()
        );
    }

    @GetMapping("/npcs/thought-bubbles")
    public Map<String, Object> getNPCThoughtBubbles() {
        List<NPC> npcs = npcRepository.findAll();
        if (npcs.isEmpty()) {
            return Map.of("bubbles", List.of(), "timestamp", LocalDateTime.now().toString());
        }
        Collections.shuffle(npcs);
        int size = Math.min(3, npcs.size());
        List<Map<String, Object>> bubbles = new ArrayList<>();
        String news = newsService.getNewsBrief();
        for (int i = 0; i < size; i++) {
            NPC npc = npcs.get(i);
            String text = aiService.generateThoughtBubble(npc, news);
            bubbles.add(Map.of(
                    "npcId", npc.getId(),
                    "npcName", npc.getName(),
                    "occupation", npc.getOccupation(),
                    "text", text
            ));
        }
        return Map.of(
                "bubbles", bubbles,
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
                "employer", npc.getEmployer() == null ? "自由职业" : npc.getEmployer(),
                "personality", npc.getPersonality()
        ));

        response.put("当前状态", Map.of(
                "location", npc.getCurrentLocation(),
                "action", npc.getCurrentAction(),
                "goal", npc.getCurrentGoal()
        ));

        Map<String, Object> detailedStats = new LinkedHashMap<>();
        detailedStats.put("energy", npc.getStats().getEnergy());
        detailedStats.put("hunger", npc.getStats().getHunger());
        detailedStats.put("happiness", npc.getStats().getHappiness());
        detailedStats.put("socialNeed", npc.getStats().getSocialNeed());
        detailedStats.put("skillLevel", npc.getStats().getSkillLevel());
        detailedStats.put("knowledgeLevel", npc.getStats().getKnowledgeLevel());
        detailedStats.put("technicalKnowledge", npc.getStats().getTechnicalKnowledge());
        detailedStats.put("businessKnowledge", npc.getStats().getBusinessKnowledge());
        detailedStats.put("socialKnowledge", npc.getStats().getSocialKnowledge());
        detailedStats.put("practicalSkill", npc.getStats().getPracticalSkill());
        detailedStats.put("creativeSkill", npc.getStats().getCreativeSkill());
        response.put("数值状态", detailedStats);

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

    @GetMapping("/npc/{id}/profile/extended")
    public Map<String, Object> getNPCExtendedProfile(@PathVariable String id) {
        NPC npc = getNPC(id);
        NPCStats s = npc.getStats();

        double money = s.getMoney();
        double savings = s.getSavings();
        double debt = s.getDebt();
        double totalAssets = money + savings;
        double netWorth = totalAssets - debt;
        double liquidityRatio = totalAssets <= 0 ? 0 : (money / totalAssets) * 100;
        double debtToAssetRatio = totalAssets <= 0 ? 0 : (debt / totalAssets) * 100;

        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("currency", "元");
        assets.put("cash", round2(money));
        assets.put("savings", round2(savings));
        assets.put("debt", round2(debt));
        assets.put("totalAssets", round2(totalAssets));
        assets.put("netWorth", round2(netWorth));
        assets.put("liquidityRatio", round2(liquidityRatio));
        assets.put("debtToAssetRatio", round2(debtToAssetRatio));
        assets.put("riskLevel", debtToAssetRatio > 70 ? "高风险" : (debtToAssetRatio > 40 ? "中风险" : "低风险"));

        if (npc.getEducationHistory() == null || npc.getEducationHistory().isBlank()) {
            refreshAndStoreEducationHistory(npc, null);
            npcRepository.save(npc);
        }
        List<Map<String, Object>> educationTimeline = parseEducationHistory(npc.getEducationHistory());
        Map<String, Object> compensation = buildCompensation(npc);

        return Map.of(
                "npcId", npc.getId(),
                "npcName", npc.getName(),
                "occupation", npc.getOccupation(),
                "educationLevel", s.getEducationLevel(),
                "workExperienceMonths", s.getWorkExperience(),
                "assets", assets,
                "compensation", compensation,
                "educationTimeline", educationTimeline,
                "timestamp", LocalDateTime.now().toString()
        );
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
            status.put("employer", npc.getEmployer());
            status.put("location", npc.getCurrentLocation());
            status.put("action", npc.getCurrentAction());
            status.put("currentGoal", npc.getCurrentGoal());
            status.put("dialogueMemory", npc.getDialogueMemory());

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
            status.put("technicalKnowledge", npc.getStats().getTechnicalKnowledge());
            status.put("businessKnowledge", npc.getStats().getBusinessKnowledge());
            status.put("socialKnowledge", npc.getStats().getSocialKnowledge());
            status.put("practicalSkill", npc.getStats().getPracticalSkill());
            status.put("creativeSkill", npc.getStats().getCreativeSkill());
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
        if (mod.educationLevel() != null && !mod.educationLevel().isBlank()) {
            String normalizedEducation = normalizeEducationLevel(mod.educationLevel());
            s.setEducationLevel(normalizedEducation);
            npc.setEmployer(getEmployerByOccupationAndEducation(npc.getOccupation(), normalizedEducation));
        }

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

    private void appendDialogueMemory(NPC npc, String playerMessage, String npcResponse) {
        String old = npc.getDialogueMemory() == null ? "" : npc.getDialogueMemory().trim();
        String playerText = shorten(playerMessage, 120);
        String npcText = shorten(npcResponse, 160);
        String entry = "玩家: " + playerText + " | " + npc.getName() + ": " + npcText;
        String merged = old.isBlank() ? entry : old + "\n" + entry;
        String[] lines = merged.split("\n");
        int keep = Math.min(6, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - keep; i < lines.length; i++) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(lines[i]);
        }
        npc.setDialogueMemory(sb.toString());
    }

    private String shorten(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.trim();
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen) + "...";
    }

    private List<String> splitToChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            chunks.add("");
            return chunks;
        }
        int size = Math.max(2, chunkSize);
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<Map<String, Object>> buildEducationTimeline(NPC npc) {
        String occupation = npc.getOccupation() == null ? "" : npc.getOccupation();
        String level = npc.getStats().getEducationLevel() == null ? "高中" : npc.getStats().getEducationLevel();

        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(step("高中", chooseSchool(occupation, "highSchool"), "基础教育与职业启蒙"));

        if (isAtLeast(level, "大专")) {
            timeline.add(step("大专", chooseSchool(occupation, "college"), "面向岗位的应用能力训练"));
        }
        if (isAtLeast(level, "本科")) {
            timeline.add(step("本科", chooseSchool(occupation, "bachelor"), majorByOccupation(occupation, "本科")));
        }
        if (isAtLeast(level, "硕士")) {
            timeline.add(step("硕士", chooseSchool(occupation, "master"), majorByOccupation(occupation, "硕士")));
        }
        if (isAtLeast(level, "博士")) {
            timeline.add(step("博士", chooseSchool(occupation, "phd"), majorByOccupation(occupation, "博士")));
        }
        if ("职业认证".equals(level)) {
            timeline.add(step("职业认证", chooseSchool(occupation, "cert"), "行业认证与在岗技能提升"));
        }
        return timeline;
    }

    private Map<String, Object> step(String stage, String school, String focus) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stage", stage);
        map.put("school", school);
        map.put("focus", focus);
        return map;
    }

    private boolean isAtLeast(String current, String target) {
        List<String> order = List.of("高中", "大专", "本科", "硕士", "博士");
        int c = order.indexOf(current);
        int t = order.indexOf(target);
        if (c == -1) c = 0;
        return c >= t;
    }

    private String majorByOccupation(String occupation, String stage) {
        if ("硕士".equals(stage)) {
            if (occupation.contains("程序员") || occupation.contains("黑客")) {
                return "研究方向：分布式系统性能优化、可信人工智能与网络攻防";
            }
            if (occupation.contains("医生")) {
                return "研究方向：精准医学、生物信息学与临床转化";
            }
            if (occupation.contains("警察") || occupation.contains("保安")) {
                return "研究方向：智能安防治理、数字取证与公共安全";
            }
            if (occupation.contains("设计")) {
                return "研究方向：计算设计、交互认知与数字艺术方法论";
            }
            return "研究方向：组织管理、数据决策与社会系统建模";
        }
        if ("博士".equals(stage)) {
            if (occupation.contains("程序员") || occupation.contains("黑客")) {
                return "博士课题：大规模软件演化理论、攻防博弈与形式化验证";
            }
            if (occupation.contains("医生")) {
                return "博士课题：复杂疾病机制、医疗大模型与循证医学体系";
            }
            if (occupation.contains("警察") || occupation.contains("保安")) {
                return "博士课题：城市韧性安全、犯罪预测模型与治理策略评估";
            }
            if (occupation.contains("设计")) {
                return "博士课题：人机协同设计理论、生成式美学与创作伦理";
            }
            return "博士课题：跨学科社会计算、制度优化与长期政策评估";
        }
        if (occupation.contains("程序员") || occupation.contains("黑客")) {
            return stage + "方向：计算机科学、网络安全、软件工程";
        }
        if (occupation.contains("医生")) {
            return stage + "方向：临床医学、生物医学工程";
        }
        if (occupation.contains("警察") || occupation.contains("保安")) {
            return stage + "方向：治安学、刑事科学技术";
        }
        if (occupation.contains("设计")) {
            return stage + "方向：数字媒体艺术、交互设计";
        }
        if (occupation.contains("司机")) {
            return stage + "方向：交通运输、智能网联驾驶";
        }
        return stage + "方向：管理学、信息系统、社会学";
    }

    private String chooseSchool(String occupation, String stage) {
        if (occupation.contains("程序员") || occupation.contains("黑客")) {
            return switch (stage) {
                case "highSchool" -> "北京第四中学";
                case "college" -> "深圳职业技术大学";
                case "bachelor" -> "浙江大学";
                case "master" -> "北京邮电大学";
                case "phd" -> "清华大学";
                default -> "中国计算机学会（CCF）认证";
            };
        }
        if (occupation.contains("医生")) {
            return switch (stage) {
                case "highSchool" -> "华中师范大学第一附属中学";
                case "college" -> "首都医科大学燕京医学院";
                case "bachelor" -> "复旦大学上海医学院";
                case "master" -> "中山大学";
                case "phd" -> "北京协和医学院";
                default -> "国家执业医师资格认证";
            };
        }
        if (occupation.contains("警察") || occupation.contains("保安")) {
            return switch (stage) {
                case "highSchool" -> "南京市金陵中学";
                case "college" -> "浙江警官职业学院";
                case "bachelor" -> "中国人民公安大学";
                case "master" -> "中国刑事警察学院";
                case "phd" -> "中国人民公安大学";
                default -> "公安机关人民警察培训中心";
            };
        }
        if (occupation.contains("设计") || occupation.contains("舞者")) {
            return switch (stage) {
                case "highSchool" -> "中央美术学院附属中等美术学校";
                case "college" -> "上海工艺美术职业学院";
                case "bachelor" -> "中国美术学院";
                case "master" -> "清华大学美术学院";
                case "phd" -> "中央美术学院";
                default -> "Adobe 国际认证中心";
            };
        }
        return switch (stage) {
            case "highSchool" -> "成都市第七中学";
            case "college" -> "北京电子科技职业学院";
            case "bachelor" -> "武汉大学";
            case "master" -> "上海交通大学";
            case "phd" -> "北京大学";
            default -> "国家职业技能鉴定中心";
        };
    }

    private String normalizeEducationLevel(String rawLevel) {
        if (rawLevel == null || rawLevel.isBlank()) {
            return "高中";
        }
        String level = rawLevel.trim();
        if (level.matches(".*(博士|PhD|phd|Doctor|doctor).*")) return "博士";
        if (level.matches(".*(硕士|研究生|Master|master).*")) return "硕士";
        if (level.matches(".*(本科|学士|Bachelor|bachelor|211|985).*")) return "本科";
        if (level.matches(".*(大专|专科|高职|职业院校).*")) return "大专";
        if (level.matches(".*(职业认证|认证|证书).*")) return "职业认证";
        if (level.matches(".*(高中|中学).*")) return "高中";
        return "高中";
    }

    private String getEmployerByOccupationAndEducation(String occupation, String educationLevel) {
        if (occupation == null) {
            return "自由职业";
        }
        String edu = educationLevel == null ? "高中" : educationLevel;
        return switch (occupation) {
            case "程序员" -> edu.matches("本科|硕士|博士")
                    ? randomPick("阿里巴巴", "腾讯云", "字节跳动")
                    : randomPick("本地软件外包公司", "中小型互联网公司", "创业工作室");
            case "设计师" -> randomPick("米哈游设计中心", "字节创意工作室", "网易视觉实验室");
            case "警察" -> "赛博市警署";
            case "医生" -> randomPick("新纪元医疗集团", "赛博诊所联合体");
            case "酒吧老板", "舞者" -> "霓虹夜场集团";
            case "黑市商人", "黑客" -> "自由接单";
            case "保安" -> "赛博安保公司";
            case "出租车司机" -> "霓虹出行平台";
            default -> "自由职业";
        };
    }

    private Map<String, Object> buildCompensation(NPC npc) {
        NPCStats s = npc.getStats();
        String occupation = npc.getOccupation() == null ? "" : npc.getOccupation();
        String education = s.getEducationLevel() == null ? "高中" : s.getEducationLevel();
        double levelFactor = (s.getSkillLevel() * 0.45 + s.getKnowledgeLevel() * 0.25 + s.getReputation() * 0.2 + s.getWorkExperience() * 0.1);
        double base = switch (occupation) {
            case "程序员", "黑客" -> 18_000;
            case "医生" -> 22_000;
            case "警察" -> 11_000;
            case "设计师", "舞者" -> 14_000;
            case "酒吧老板", "黑市商人" -> 16_000;
            case "出租车司机", "保安" -> 9_000;
            default -> 10_000;
        };
        double monthlyCashAuto = Math.max(4500, base + levelFactor * 55);
        boolean eligibleForOption = occupation.matches("程序员|设计师|黑客|产品经理|算法工程师")
                && education.matches("本科|硕士|博士")
                && npc.getEmployer() != null
                && npc.getEmployer().matches(".*(阿里巴巴|腾讯云|字节跳动|米哈游|网易).*");
        double monthlyOptionAuto = eligibleForOption
                ? Math.max(0, monthlyCashAuto * (0.08 + s.getKnowledgeLevel() / 1200.0))
                : 0;
        double monthlyCash = s.getMonthlyCashIncome() > 0 ? s.getMonthlyCashIncome() : monthlyCashAuto;
        double monthlyStockOption = s.getMonthlyStockOptionIncome() > 0 ? s.getMonthlyStockOptionIncome() : monthlyOptionAuto;
        double monthlyTotal = monthlyCash + monthlyStockOption;
        double annualTotal = monthlyTotal * 12;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", "元");
        result.put("monthlyCash", round2(monthlyCash));
        result.put("monthlyStockOption", round2(monthlyStockOption));
        result.put("monthlyTotal", round2(monthlyTotal));
        result.put("annualTotal", round2(annualTotal));
        result.put("monthlyDisplay", formatMoneyForDisplay(monthlyTotal));
        result.put("annualDisplay", formatMoneyForDisplay(annualTotal));
        result.put("payLevel", monthlyTotal > 35000 ? "高" : (monthlyTotal > 18000 ? "中" : "基础"));
        result.put("hasStockOption", monthlyStockOption > 0.1);
        return result;
    }

    private String formatMoneyForDisplay(double valueYuan) {
        if (valueYuan >= 10_000) {
            return round2(valueYuan / 10_000.0) + " 万元";
        }
        return round2(valueYuan / 1000.0) + " 千元";
    }

    private String randomPick(String... options) {
        if (options == null || options.length == 0) {
            return "";
        }
        return options[new Random().nextInt(options.length)];
    }

    private void refreshAndStoreEducationHistory(NPC npc, String preferredSchool) {
        String targetLevel = npc.getStats().getEducationLevel() == null ? "高中" : npc.getStats().getEducationLevel();
        List<Map<String, Object>> current = parseEducationHistory(npc.getEducationHistory());
        LinkedHashMap<String, Map<String, Object>> byStage = new LinkedHashMap<>();
        for (Map<String, Object> row : current) {
            String stage = safePart(row.get("stage"));
            if (!stage.isBlank()) {
                byStage.put(stage, new LinkedHashMap<>(row));
            }
        }

        int targetRank = educationRank(targetLevel);
        byStage.entrySet().removeIf(e -> educationRank(e.getKey()) > targetRank);

        Map<String, Object> target = byStage.get(targetLevel);
        if (target == null) {
            String school = preferredSchool != null && !preferredSchool.isBlank()
                    ? preferredSchool
                    : chooseSchool(npc.getOccupation(), toSchoolStage(targetLevel));
            target = step(targetLevel, school, majorByOccupation(npc.getOccupation() == null ? "" : npc.getOccupation(), targetLevel));
            byStage.put(targetLevel, target);
        } else if (preferredSchool != null && !preferredSchool.isBlank()) {
            target.put("school", preferredSchool);
        }

        List<Map<String, Object>> timeline = byStage.values().stream()
                .sorted(Comparator.comparingInt(it -> educationRank(safePart(it.get("stage")))))
                .toList();

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> step : timeline) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(safePart(step.get("stage"))).append("|")
                    .append(safePart(step.get("school"))).append("|")
                    .append(safePart(step.get("focus")));
        }
        npc.setEducationHistory(sb.toString());
    }

    private List<Map<String, Object>> parseEducationHistory(String educationHistory) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        if (educationHistory == null || educationHistory.isBlank()) {
            return timeline;
        }
        String[] lines = educationHistory.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) continue;
            timeline.add(step(parts[0], parts[1], parts[2]));
        }
        return timeline;
    }

    private String safePart(Object val) {
        if (val == null) return "";
        return String.valueOf(val).replace("|", "/").replace("\n", " ").trim();
    }

    private int educationRank(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "高中" -> 1;
            case "大专", "职业认证" -> 2;
            case "本科" -> 3;
            case "硕士" -> 4;
            case "博士" -> 5;
            default -> 1;
        };
    }

    private String toSchoolStage(String level) {
        if (level == null) return "highSchool";
        return switch (level) {
            case "高中" -> "highSchool";
            case "大专" -> "college";
            case "本科" -> "bachelor";
            case "硕士" -> "master";
            case "博士" -> "phd";
            default -> "cert";
        };
    }

    private AIService.GodModification enhanceGodModificationFromInstruction(AIService.GodModification mod, String instruction) {
        if (mod == null) {
            mod = AIService.GodModification.empty();
        }
        String parsedEducation = mod.educationLevel();
        if (parsedEducation == null || parsedEducation.isBlank()) {
            parsedEducation = detectEducationFromInstruction(instruction);
        }
        return new AIService.GodModification(
                mod.currentLocation(),
                mod.currentAction(),
                mod.currentGoal(),
                parsedEducation,
                mod.money(),
                mod.savings(),
                mod.debt(),
                mod.intelligence(),
                mod.charisma(),
                mod.skillLevel(),
                mod.knowledgeLevel(),
                mod.health(),
                mod.reputation(),
                mod.energy(),
                mod.hunger(),
                mod.happiness(),
                mod.socialNeed(),
                mod.workExperience()
        );
    }

    private String detectEducationFromInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        String text = instruction.trim();
        if (text.matches(".*(博士|PhD|phd|doctor|Doctor).*")) return "博士";
        if (text.matches(".*(硕士|研究生|Master|master).*")) return "硕士";
        if (text.matches(".*(本科|学士|Bachelor|bachelor|211|985).*")) return "本科";
        if (text.matches(".*(大专|专科|高职|职业院校).*")) return "大专";
        if (text.matches(".*(职业认证|证书|认证).*")) return "职业认证";
        if (text.matches(".*(高中|中学).*")) return "高中";
        return null;
    }

    private String detectSchoolFromInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return null;
        }
        String text = instruction.trim();
        if (text.matches(".*(麻省理工|MIT|mit).*")) return "麻省理工学院";
        if (text.matches(".*(斯坦福|Stanford|stanford).*")) return "斯坦福大学";
        if (text.matches(".*(哈佛|Harvard|harvard).*")) return "哈佛大学";
        if (text.matches(".*(牛津|Oxford|oxford).*")) return "牛津大学";
        if (text.matches(".*(剑桥|Cambridge|cambridge).*")) return "剑桥大学";
        if (text.matches(".*(清华|tsinghua|Tsinghua).*")) return "清华大学";
        if (text.matches(".*(北大|北京大学|Peking|peking).*")) return "北京大学";
        if (text.matches(".*(复旦|Fudan|fudan).*")) return "复旦大学";
        if (text.matches(".*(浙大|浙江大学|ZJU|zju).*")) return "浙江大学";
        if (text.matches(".*(上交|上海交通大学|SJTU|sjtu).*")) return "上海交通大学";
        return null;
    }

    private void applyCompensationOverridesFromInstruction(NPC npc, String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return;
        }
        Double monthlyCash = extractAmount(instruction, "(月薪|月收入|现金部分|现金工资)");
        Double monthlyOption = extractAmount(instruction, "(期权|股票|股权|RSU|rsu)");
        Double annualIncome = extractAmount(instruction, "(年收入|年薪)");

        NPCStats s = npc.getStats();
        if (monthlyCash != null) {
            s.setMonthlyCashIncome(Math.max(0, monthlyCash));
        }
        if (monthlyOption != null) {
            s.setMonthlyStockOptionIncome(Math.max(0, monthlyOption));
        }
        if (annualIncome != null && monthlyCash == null) {
            s.setMonthlyCashIncome(Math.max(0, annualIncome / 12.0));
        }
    }

    private Double extractAmount(String instruction, String contextRegex) {
        String text = instruction == null ? "" : instruction;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:" + contextRegex + ")" + ".*?(?<num>\\d+(?:\\.\\d+)?)\\s*(?<unit>万元|万|千元|千|元)?");
        java.util.regex.Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        String numberText = m.group("num");
        if (numberText == null || numberText.isBlank()) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(numberText);
        } catch (NumberFormatException ex) {
            return null;
        }
        String unit = m.group("unit");
        if (unit == null || unit.isBlank() || "元".equals(unit)) {
            return value;
        }
        if ("万".equals(unit) || "万元".equals(unit)) {
            return value * 10000;
        }
        if ("千".equals(unit) || "千元".equals(unit)) {
            return value * 1000;
        }
        return value;
    }
}