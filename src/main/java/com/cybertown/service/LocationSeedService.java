package com.cybertown.service;

import com.cybertown.domain.world.Location;
import com.cybertown.repository.LocationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSeedService {

    private final LocationRepository locationRepository;

    @PostConstruct
    public void seedIfEmpty() {
        List<Location> desired = List.of(
                loc("loc-street", "霓虹街道", "STREET", "霓虹闪烁的主干道", 50, 50, 48),
                loc("loc-corp", "科技公司", "WORK", "赛博企业园区", 40, 62, 18),
                loc("loc-bar", "霓虹酒吧", "BAR", "夜间社交据点", 30, 50, 82),
                loc("loc-home", "公寓住宅", "HOME", "居民住宅区", 80, 82, 78),
                loc("loc-clinic", "赛博诊所", "CLINIC", "义体与医疗", 20, 38, 16),
                loc("loc-police", "警察总局", "WORK", "治安中枢", 25, 18, 18),
                loc("loc-market", "地下黑市", "MARKET", "灰色交易区", 20, 18, 78),
                loc("loc-restaurant", "仿生餐厅", "RESTAURANT", "用餐与会面", 35, 22, 48),
                loc("loc-mall", "全息商场", "MALL", "购物娱乐", 45, 84, 22),
                loc("loc-park", "中央公园", "PARK", "放松与散步", 60, 78, 48)
        );

        if (locationRepository.count() == 0) {
            locationRepository.saveAll(desired);
            log.info("已播种 {} 个地点", desired.size());
            return;
        }

        // 已有数据时同步坐标，避免旧布局导致地图混乱
        for (Location want : desired) {
            locationRepository.findById(want.getId()).ifPresentOrElse(existing -> {
                existing.setMapX(want.getMapX());
                existing.setMapY(want.getMapY());
                existing.setDescription(want.getDescription());
                existing.setType(want.getType());
                existing.setCapacity(want.getCapacity());
                locationRepository.save(existing);
            }, () -> locationRepository.save(want));
        }
        log.info("已同步地点坐标 {} 条", desired.size());
    }

    private static Location loc(String id, String name, String type, String desc, int capacity, double x, double y) {
        Location l = new Location();
        l.setId(id);
        l.setName(name);
        l.setType(type);
        l.setDescription(desc);
        l.setCapacity(capacity);
        l.setMapX(x);
        l.setMapY(y);
        return l;
    }
}
