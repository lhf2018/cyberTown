package com.cybertown.service;

import com.cybertown.domain.world.WorldState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 时间管理服务
 * 负责推进游戏时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeService {
    
    private final WorldState worldState;  // 依赖注入世界状态
    
    /**
     * 定时推进游戏时间
     * @Scheduled 表示这是一个定时任务，每1000毫秒（1秒）执行一次
     * fixedRate = 1000 表示每隔1000毫秒执行一次
     */
    @Scheduled(fixedRate = 1000)
    public void advanceTime() {
        // 现实1秒 = 游戏6秒（根据配置的time-scale）
        worldState.advanceTime(6);
        
        // 整点时打印日志（便于观察时间流动）
        if (worldState.getGameTime().getMinute() == 0) {
            log.info("游戏时间: {}, 天气: {}, 时间段: {}", 
                worldState.getGameTime(),      // 当前游戏时间
                worldState.getWeather(),       // 当前天气
                worldState.getTimeOfDay());    // 当前时间段
        }
    }
    
    /**
     * 暂停/恢复游戏时间
     * @param paused true=暂停，false=恢复
     */
    public void setPaused(boolean paused) {
        worldState.setPaused(paused);
        log.info("游戏时间已{}", paused ? "暂停" : "恢复");
    }
    
    /**
     * 快进时间
     * @param hours 要快进的小时数
     */
    public void fastForward(int hours) {
        // 将小时转换为秒：1小时=3600秒
        worldState.advanceTime(hours * 3600L);
        log.info("快进 {} 小时, 当前时间: {}", hours, worldState.getGameTime());
    }
}