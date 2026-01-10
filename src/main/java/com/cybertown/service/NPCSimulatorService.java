package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.repository.NPCRepository;
import com.cybertown.domain.world.WorldState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class NPCSimulatorService {
    
    // 依赖注入
    private final NPCRepository npcRepository;  // NPC数据访问
    private final WorldState worldState;        // 世界状态
    private final AIService aiService;          // AI服务
    private final Random random = new Random(); // 随机数生成器（用于随机事件）
    
    /**
     * 定时更新所有NPC状态
     * 每分钟执行一次（60000毫秒）
     */
    @Scheduled(fixedRate = 60000)
    public void updateAllNPCs() {
        // 如果游戏暂停，不更新NPC
        if (worldState.isPaused()) {
            log.debug("游戏暂停中，跳过NPC更新");
            return;
        }
        
        // 获取所有NPC
        List<NPC> npcs = npcRepository.findAll();
        log.info("开始更新 {} 个NPC状态", npcs.size());
        
        // 遍历更新每个NPC
        for (NPC npc : npcs) {
            updateNPC(npc);
        }
    }
    
    /**
     * 更新单个NPC
     * @param npc 要更新的NPC
     */
    private void updateNPC(NPC npc) {
        // 1. 更新基础属性（能量、饥饿等）
        updateNPCStats(npc);
        
        // 2. 20%的概率触发AI决策（控制AI调用频率，节省成本）
        if (random.nextInt(100) < 20) {
            String decision = aiService.decideNPCAction(npc);
            npc.setStatus(decision);
            
            // 简单的位置更新逻辑：如果决策包含"去"，则更新位置
            if (decision.contains("去")) {
                // 示例："去酒吧喝酒" -> 提取"酒吧"作为位置
                String location = decision.split("去")[1].trim();
                npc.setCurrentLocation(location);
            }
        }
        
        // 3. 保存更新到数据库
        npcRepository.save(npc);
        log.debug("更新NPC: {} -> {}", npc.getName(), npc.getStatus());
    }
    
    /**
     * 更新NPC属性
     * 模拟真实世界的影响：能量减少、饥饿增加等
     */
    private void updateNPCStats(NPC npc) {
        NPCStats stats = npc.getStats();
        
        // 能量随时间减少（每分钟减少1点）
        stats.setEnergy(Math.max(0, stats.getEnergy() - 1));
        
        // 饥饿随时间增加（每分钟增加2点）
        stats.setHunger(Math.min(100, stats.getHunger() + 2));
        
        // 能量低时影响心情
        if (stats.getEnergy() < 30) {
            stats.setHappiness(Math.max(0, stats.getHappiness() - 2));
        }
        
        // 10%的概率发生随机事件影响心情
        if (random.nextInt(100) < 10) {
            // 随机心情变化：-10到+10之间
            int moodChange = random.nextInt(21) - 10;
            stats.setHappiness(Math.max(0, Math.min(100, stats.getHappiness() + moodChange)));
            log.debug("NPC {} 心情随机变化: {}", npc.getName(), moodChange);
        }
    }
    
    /**
     * 初始化NPC数据
     * 第一次启动时创建初始NPC
     */
    public void initializeNPCs() {
        // 如果已有NPC，跳过初始化
        if (npcRepository.count() > 0) {
            log.info("NPC数据已存在，跳过初始化");
            return;
        }
        
        log.info("开始初始化NPC数据...");
        
        // NPC数据数组
        String[] names = {"杰克", "莉莉", "老王", "小李", "阿强", "小美", "大壮", "眼镜", "红姐", "老陈"};
        String[] occupations = {"程序员", "设计师", "酒吧老板", "警察", "黑市商人", "医生", "保安", "黑客", "舞者", "出租车司机"};
        String[] personalities = {"内向但善良", "外向时尚", "精明务实", "正义感强", "狡猾但守信", "温柔体贴", "强壮忠诚", "技术天才", "魅力四射", "见多识广"};
        
        // 循环创建NPC
        for (int i = 0; i < names.length; i++) {
            // 创建NPC对象
            NPC npc = new NPC();
            npc.setId("npc-" + (i + 1));  // 生成ID：npc-1, npc-2...
            npc.setName(names[i]);
            npc.setOccupation(occupations[i]);
            npc.setPersonality(personalities[i]);
            npc.setCurrentLocation(getInitialLocation(occupations[i]));  // 根据职业设置初始位置
            npc.setStatus("工作中");
            
            // 创建并设置NPC属性（使用建造者模式）
            NPCStats stats = NPCStats.builder()
                .energy(70 + random.nextInt(30))      // 能量：70-100
                .hunger(20 + random.nextInt(40))      // 饥饿：20-60
                .happiness(50 + random.nextInt(40))   // 快乐：50-90
                .money(500 + random.nextInt(2000))    // 金钱：500-2500
                .intelligence(40 + random.nextInt(50))// 智力：40-90
                .charisma(40 + random.nextInt(50))    // 魅力：40-90
                .build();
            npc.setStats(stats);
            
            // 保存到数据库
            npcRepository.save(npc);
            log.info("创建NPC: {} - {}", npc.getName(), npc.getOccupation());
        }
        
        log.info("初始化完成，创建了 {} 个NPC", names.length);
    }
    
    /**
     * 根据职业获取初始位置
     * @param occupation NPC职业
     * @return 对应的初始位置
     */
    private String getInitialLocation(String occupation) {
        // 使用switch表达式（Java 14+）
        return switch (occupation) {
            case "程序员", "设计师" -> "科技公司";
            case "酒吧老板" -> "老王家酒吧";
            case "警察" -> "警察局";
            case "黑市商人" -> "黑市";
            case "医生" -> "诊所";
            case "保安" -> "大厦门口";
            case "黑客" -> "网络空间";
            case "舞者" -> "霓虹酒吧";
            case "出租车司机" -> "街道";
            default -> "市中心";
        };
    }
}