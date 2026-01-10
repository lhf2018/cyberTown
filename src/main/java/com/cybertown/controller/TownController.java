package com.cybertown.controller;

import com.cybertown.domain.npc.NPC;
import com.cybertown.service.AIService;
import com.cybertown.service.NPCSimulatorService;
import com.cybertown.service.TimeService;
import com.cybertown.repository.NPCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API控制器 - 处理HTTP请求
 */
@RestController
@RequestMapping("/api/town")
@RequiredArgsConstructor
public class TownController {

    // 注入服务
    private final NPCRepository npcRepository;
    private final AIService aiService;
    private final NPCSimulatorService npcSimulatorService;

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
     * 3. 与NPC对话（调用AIService）
     */
    @PostMapping("/npc/{id}/talk")
    public Map<String, String> talkToNPC(@PathVariable String id,
                                         @RequestBody Map<String, String> request) {
        String message = request.get("message");
        NPC npc = getNPC(id);

        // 调用AI服务生成回应
        String response = aiService.generateDialogue(npc, message);

        return Map.of(
                "npc", npc.getName(),
                "response", response,
                "mood", getMoodEmoji(npc.getStats().getHappiness())
        );
    }

    /**
     * 4. 手动触发NPC更新（测试用）
     */
    @PostMapping("/npc/{id}/update")
    public String updateNPCMannualy(@PathVariable String id) {
        NPC npc = getNPC(id);

        // 这里可以调用NPCSimulatorService的方法
        // 或者直接模拟一次更新

        return "已手动更新NPC：" + npc.getName();
    }

    /**
     * 5. 初始化小镇（创建NPC）
     */
    @PostMapping("/init")
    public String initializeTown() {
        npcSimulatorService.initializeNPCs();
        return "小镇初始化完成";
    }

    /**
     * 6. 获取小镇状态
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "npcCount", npcRepository.count(),
                "message", "赛博小镇运行正常",
                "timestamp", System.currentTimeMillis()
        );
    }

    // 根据快乐度返回表情符号
    private String getMoodEmoji(int happiness) {
        if (happiness > 80) return "😊";
        if (happiness > 60) return "🙂";
        if (happiness > 40) return "😐";
        if (happiness > 20) return "😔";
        return "😢";
    }
}