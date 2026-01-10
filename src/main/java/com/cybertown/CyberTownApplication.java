package com.cybertown;

import com.cybertown.service.NPCSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 主启动类
 * 应用入口点
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
public class CyberTownApplication implements CommandLineRunner {

    private final NPCSimulatorService npcSimulatorService;

    public static void main(String[] args) {
        SpringApplication.run(CyberTownApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("🚀 赛博小镇启动中...");

        // 初始化NPC数据
        npcSimulatorService.initializeNPCs();

        log.info("🏙️ 赛博小镇准备就绪！");
        log.info("💡 你可以：");
        log.info("   1. 查看所有NPC: GET http://localhost:8080/api/town/npcs");
        log.info("   2. 与NPC对话: POST http://localhost:8080/api/town/npc/npc-1/talk");
        log.info("   3. 查看H2数据库: http://localhost:8080/h2-console");
    }
}