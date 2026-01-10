package com.cybertown.controller;

import com.cybertown.domain.npc.NPC;
import com.cybertown.service.AIService;
import com.cybertown.service.NPCSimulatorService;
import com.cybertown.service.TimeService;
import com.cybertown.repository.NPCRepository;
import com.cybertown.domain.npc.NPCStats;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 小镇API控制器
 * 提供RESTful API接口供前端或其他系统调用
 *
 * @RestController 表示这是一个REST控制器，返回值自动转为JSON
 * @RequestMapping 定义控制器的基础路径
 */
@RestController
@RequestMapping("/api/town")  // 所有接口的前缀：/api/town
@RequiredArgsConstructor
public class TownController {

    // 依赖注入
    private final NPCRepository npcRepository;
    private final AIService aiService;
    private final TimeService timeService;
    private final NPCSimulatorService npcSimulatorService;

    /**
     * 获取所有NPC
     * GET /api/town/npcs
     *
     * @return NPC列表（自动转为JSON）
     */
    @GetMapping("/npcs")
    public List<NPC> getAllNPCs() {
        return npcRepository.findAll();
    }

    /**
     * 获取单个NPC
     * GET /api/town/npc/{id}
     *
     * @PathVariable 表示从URL路径中获取参数
     */
    @GetMapping("/npc/{id}")
    public NPC getNPC(@PathVariable String id) {
        return npcRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NPC未找到: " + id));
    }

    /**
     * 与NPC对话
     * POST /api/town/npc/{id}/talk
     *
     * @RequestBody 表示从请求体中获取JSON参数
     */
    @PostMapping("/npc/{id}/talk")
    public Map<String, String> talkToNPC(@PathVariable String id,
                                         @RequestBody Map<String, String> request) {
        // 从请求体中获取玩家消息
        String message = request.get("message");

        // 获取NPC对象
        NPC npc = getNPC(id);

        // 调用AI服务生成回应
        String response = aiService.generateDialogue(npc, message);

        // 返回JSON响应
        return Map.of(
                "npc", npc.getName(),
                "response", response,
                "mood", getMoodFromStats(npc.getStats())  // 表情符号表示心情
        );
    }

    /**
     * 暂停时间
     * POST /api/town/time/pause
     */
    @PostMapping("/time/pause")
    public String pauseTime() {
        timeService.setPaused(true);
        return "游戏时间已暂停";
    }

    /**
     * 恢复时间
     * POST /api/town/time/resume
     */
    @PostMapping("/time/resume")
    public String resumeTime() {
        timeService.setPaused(false);
        return "游戏时间已恢复";
    }

    /**
     * 快进时间
     * POST /api/town/time/fast-forward/{hours}
     */
    @PostMapping("/time/fast-forward/{hours}")
    public String fastForward(@PathVariable int hours) {
        timeService.fastForward(hours);
        return "快进了 " + hours + " 小时";
    }

    /**
     * 初始化小镇（重新创建NPC）
     * POST /api/town/init
     */
    @PostMapping("/init")
    public String initializeTown() {
        npcSimulatorService.initializeNPCs();
        return "小镇初始化完成";
    }

    /**
     * 获取小镇状态
     * GET /api/town/status
     */
    @GetMapping("/status")
    public Map<String, Object> getTownStatus() {
        return Map.of(
                "npcCount", npcRepository.count(),
                "message", "赛博小镇运行中"
        );
    }

    /**
     * 根据快乐值返回表情符号
     * 私有工具方法，不在API中暴露
     */
    private String getMoodFromStats(NPCStats stats) {
        if (stats.getHappiness() > 80) return "😊";
        if (stats.getHappiness() > 60) return "🙂";
        if (stats.getHappiness() > 40) return "😐";
        if (stats.getHappiness() > 20) return "😔";
        return "😢";
    }
}