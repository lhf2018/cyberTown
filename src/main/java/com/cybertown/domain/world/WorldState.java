package com.cybertown.domain.world;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 世界状态类
 * 管理游戏世界的全局状态：时间、天气等
 *
 */
@Component
@Data
public class WorldState {
    private static final String[] WEATHER_OPTIONS = {"SUNNY", "RAINY", "CYBER_RAIN", "FOGGY"};

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
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 初始时间随机在白天和夜晚交界区间，保证开局状态不单一。
        int hour = random.nextInt(6, 23); // 6:00 - 22:59
        int minute = random.nextInt(0, 4) * 15; // 00/15/30/45
        this.gameTime = LocalDateTime.of(2088, 1, 1, hour, minute);

        // 初始天气随机
        this.weather = WEATHER_OPTIONS[random.nextInt(WEATHER_OPTIONS.length)];
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