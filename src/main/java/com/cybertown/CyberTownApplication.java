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
    }
}