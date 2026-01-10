package com.cybertown.service;

import com.cybertown.domain.world.WorldState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class WorldService {
    
    private final WorldState worldState;
    
    /**
     * 获取格式化的游戏时间
     */
    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return worldState.getGameTime().format(formatter);
    }
    
    /**
     * 获取当前天气
     */
    public String getCurrentWeather() {
        return worldState.getWeather();
    }
    
    /**
     * 获取时间段（早晨/下午等）
     */
    public String getTimePeriod() {
        return worldState.getTimeOfDay();
    }
}