package com.cybertown.domain.world;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 世界状态类
 * 管理游戏世界的全局状态：时间、天气等
 *
 */
@Component
@Data
public class WorldState {

    // 游戏内时间（2088年赛博朋克世界）
    private LocalDateTime gameTime;

    // 天气状态
    private String weather;  // SUNNY, RAINY, CYBER_RAIN, FOGGY

    // 游戏是否暂停
    private boolean paused;

    /**
     * 构造函数：初始化世界状态
     */
    public WorldState() {
        // 设置初始时间：2088年1月1日早上8点
        this.gameTime = LocalDateTime.of(2088, 1, 1, 8, 0);
        this.weather = "SUNNY";  // 初始晴天
        this.paused = false;     // 初始未暂停
    }

    /**
     * 推进游戏时间
     *
     * @param seconds 要推进的游戏秒数
     */
    public void advanceTime(long seconds) {
        if (!paused) {  // 只有未暂停时才推进时间
            gameTime = gameTime.plusSeconds(seconds);
        }
    }

    /**
     * 获取当前时间段
     *
     * @return 时间段字符串：MORNING, AFTERNOON, EVENING, NIGHT
     */
    public String getTimeOfDay() {
        int hour = gameTime.getHour();  // 获取当前小时

        // 根据小时判断时间段
        if (hour >= 6 && hour < 12) return "MORNING";     // 早晨 6-12
        if (hour >= 12 && hour < 18) return "AFTERNOON";  // 下午 12-18
        if (hour >= 18 && hour < 22) return "EVENING";    // 晚上 18-22
        return "NIGHT";                                   // 夜晚 22-6
    }
}