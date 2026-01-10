package com.cybertown;

import com.cybertown.service.NPCSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot主启动类
 * 应用入口点
 */
@Slf4j  // 日志
@SpringBootApplication  // 标记为Spring Boot应用，包含：@Configuration, @EnableAutoConfiguration, @ComponentScan
@EnableScheduling      // 启用定时任务支持
@RequiredArgsConstructor
public class CyberTownApplication implements CommandLineRunner {
    // 实现CommandLineRunner接口，可以在应用启动后执行代码

    private final NPCSimulatorService npcSimulatorService;

    /**
     * 主方法：应用入口
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用
        SpringApplication.run(CyberTownApplication.class, args);
    }

    /**
     * 应用启动后执行
     * CommandLineRunner接口的方法
     */
    @Override
    public void run(String... args) {
        log.info("🚀 赛博小镇启动中...");

        // 初始化NPC数据（如果数据库为空）
        npcSimulatorService.initializeNPCs();

        // 启动完成提示
        log.info("🏙️ 赛博小镇已就绪！");
        log.info("💡 访问 http://localhost:8080/h2-console 查看数据库");
        log.info("   JDBC URL: jdbc:h2:mem:cybertown");
        log.info("   用户名: sa, 密码: (空)");
        log.info("💡 访问 API 端点:");
        log.info("   GET  /api/town/npcs - 获取所有NPC");
        log.info("   GET  /api/town/npc/{id} - 获取单个NPC");
        log.info("   POST /api/town/npc/{id}/talk - 与NPC对话（需要Postman）");
        log.info("   POST /api/town/init - 重新初始化小镇");
    }
}