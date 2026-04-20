package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.domain.world.WorldState;
import com.cybertown.graph.NPCBehaviorGraph;
import com.cybertown.graph.NPCDecisionTools;
import com.cybertown.repository.NPCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * NPC模拟服务 - 核心大脑
 * 职责：控制NPC的更新频率、调用决策服务、管理NPC生命周期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NPCSimulatorService {

    // ======================== 依赖注入 ========================
    private final NPCRepository npcRepository;       // 数据访问
    private final WorldState worldState;             // 游戏世界状态
    private final WorldService worldService;         // 世界信息服务
    private final NPCBehaviorGraph npcBehaviorGraph;
    private final AIService aiService;

    private final Random random = new Random();

    // ======================== NPC属性更新配置 ========================
    private static final int ENERGY_DECREASE_PER_MINUTE = 1;   // 每分钟减少能量
    private static final int HUNGER_INCREASE_PER_MINUTE = 2;   // 每分钟增加饥饿
    private static final int SOCIAL_NEED_INCREASE_PER_MINUTE = 1; // 每分钟增加社交需求

    // ======================== 决策概率配置 ========================
    private static final int DECISION_PROBABILITY = 60;
    private static final int MIN_DECISION_INTERVAL_MINUTES = 1;

    // ======================== 定时任务 ========================

    /**
     * 主定时任务：每分钟更新所有NPC
     * 这是整个系统的心跳
     */
    @Scheduled(fixedRate = 60000) // 60000毫秒 = 1分钟
    public void heartbeat() {
        if (worldState.isPaused()) {
            log.trace("游戏暂停，跳过NPC更新");
            return;
        }

        long startTime = System.currentTimeMillis();
        List<NPC> npcs = npcRepository.findAll();
        int updatedCount = 0;

        log.debug("开始心跳更新，共{}个NPC", npcs.size());

        for (NPC npc : npcs) {
            try {
                if (updateSingleNPC(npc)) {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("更新NPC失败: {}，错误: {}", npc.getName(), e.getMessage(), e);
                // 继续处理其他NPC，不中断整个流程
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("心跳完成: 更新{}/{}个NPC，耗时{}ms", updatedCount, npcs.size(), elapsedTime);
    }

    /**
     * 每小时整点执行：批量处理长期决策
     */
    @Scheduled(cron = "0 0 * * * *") // 每小时整点
    public void hourlyBatchProcessing() {
        if (worldState.isPaused()) return;

        log.info("=== 整点批量处理开始 ===");

        // 1. 查找需要长期规划的NPC
        List<NPC> npcsNeedingGoals = findNPCsNeedingGoals();
        log.info("找到{}个需要设定目标的NPC", npcsNeedingGoals.size());

        // 2. 处理每个NPC
        for (NPC npc : npcsNeedingGoals) {
            try {
                assignLongTermGoal(npc);
            } catch (Exception e) {
                log.error("为NPC {} 设定目标失败", npc.getName(), e);
            }
        }

        // 3. 清理过期状态
        cleanupExpiredStates();

        log.info("=== 整点批量处理完成 ===");
    }

    // ======================== 核心NPC更新逻辑 ========================

    /**
     * 更新单个NPC
     *
     * @return 是否进行了状态更新
     */
    private boolean updateSingleNPC(NPC npc) {
        boolean hasChanges = false;

        // 1. 检查是否需要跳过更新（最近刚更新过）
        if (shouldSkipUpdate(npc)) {
            log.trace("跳过NPC {} 的更新，更新间隔太短", npc.getName());
            return false;
        }

        // 2. 更新基础属性（时间流逝的影响）
        if (updateNPCStats(npc)) {
            hasChanges = true;
            log.trace("NPC {} 属性已更新", npc.getName());
        }

        // 3. 检查并执行决策
        if (shouldMakeDecision(npc)) {
            NPCDecisionTools.DecisionResult decision = npcBehaviorGraph.decideWithAI(npc, worldState);
            apply(npc, decision);
            hasChanges = true;

            // 记录决策时间
            npc.setUpdatedAt(LocalDateTime.now());
            log.info("NPC {} 执行决策: {}", npc.getName(), decision);
        } else {
            // 4. 继续当前行为
            continueCurrentAction(npc);
        }

        // 5. 检查并触发特殊事件
        if (shouldTriggerEvent(npc)) {
            triggerRandomEvent(npc);
            hasChanges = true;
        }

        // 6. 保存到数据库（如果有变化）
        if (hasChanges) {
            npcRepository.save(npc);
        }

        return hasChanges;
    }

    /**
     * 更新NPC的基础属性（模拟时间流逝的影响）
     */
    private boolean updateNPCStats(NPC npc) {
        NPCStats stats = npc.getStats();
        boolean changed = false;

        // 能量减少（随时间流逝）
        int oldEnergy = stats.getEnergy();
        stats.setEnergy(Math.max(0, stats.getEnergy() - ENERGY_DECREASE_PER_MINUTE));
        if (oldEnergy != stats.getEnergy()) changed = true;

        // 饥饿增加（随时间流逝）
        int oldHunger = stats.getHunger();
        stats.setHunger(Math.min(100, stats.getHunger() + HUNGER_INCREASE_PER_MINUTE));
        if (oldHunger != stats.getHunger()) changed = true;

        // 社交需求增加（随时间流逝）
        int oldSocialNeed = stats.getSocialNeed();
        stats.setSocialNeed(Math.min(100, stats.getSocialNeed() + SOCIAL_NEED_INCREASE_PER_MINUTE));
        if (oldSocialNeed != stats.getSocialNeed()) changed = true;

        // 随机心情波动（10%概率）
        if (random.nextInt(100) < 10) {
            int oldHappiness = stats.getHappiness();
            int change = random.nextInt(11) - 5; // -5 到 +5
            stats.setHappiness(Math.max(0, Math.min(100, stats.getHappiness() + change)));
            if (oldHappiness != stats.getHappiness()) {
                changed = true;
                log.trace("NPC {} 心情波动: {} -> {} ({})", npc.getName(), oldHappiness, stats.getHappiness(), change > 0 ? "提升" : "下降");
            }
        }

        // 属性过低影响心情
        if (stats.getEnergy() < 30 && stats.getHappiness() > 0) {
            stats.setHappiness(Math.max(0, stats.getHappiness() - 2));
            changed = true;
        }
        if (stats.getHunger() > 80 && stats.getHappiness() > 0) {
            stats.setHappiness(Math.max(0, stats.getHappiness() - 3));
            changed = true;
        }
        if (stats.getSocialNeed() > 80 && stats.getHappiness() > 0) {
            stats.setHappiness(Math.max(0, stats.getHappiness() - 1));
            changed = true;
        }

        // 长期发展：工作会累计经验/技能/知识；资产会自然变化
        if (isWorkingAction(npc.getCurrentAction()) && random.nextInt(100) < 35) {
            stats.setWorkExperience(stats.getWorkExperience() + 1);
            stats.setSkillLevel(Math.min(100, stats.getSkillLevel() + 1));
            if (random.nextInt(100) < 40) {
                stats.setKnowledgeLevel(Math.min(100, stats.getKnowledgeLevel() + 1));
            }
            changed = true;
        }
        if (stats.getDebt() > 0) {
            stats.setDebt(stats.getDebt() * 1.001);
            changed = true;
        }
        if (stats.getSavings() > 0 && random.nextInt(100) < 20) {
            stats.setSavings(stats.getSavings() * 1.0005);
            changed = true;
        }
        if (stats.getDebt() > stats.getMoney() * 2 && stats.getHappiness() > 0) {
            stats.setHappiness(Math.max(0, stats.getHappiness() - 1));
            changed = true;
        } else if (stats.getSavings() > 3000 && stats.getHappiness() < 100 && random.nextInt(100) < 20) {
            stats.setHappiness(Math.min(100, stats.getHappiness() + 1));
            changed = true;
        }

        return changed;
    }

    /**
     * 判断是否应该跳过更新
     * 防止短时间内频繁更新同一个NPC
     */
    private boolean shouldSkipUpdate(NPC npc) {
        if (npc.getUpdatedAt() == null) return false;

        // 计算距离上次更新的分钟数
        long minutesSinceLastUpdate = java.time.Duration.between(npc.getUpdatedAt(), LocalDateTime.now()).toMinutes();

        // 如果15分钟内更新过，且不是紧急需求，就跳过
        if (minutesSinceLastUpdate < MIN_DECISION_INTERVAL_MINUTES) {
            return !hasUrgentNeed(npc);
        }

        return false;
    }

    /**
     * 判断是否需要做新决策
     */
    private boolean shouldMakeDecision(NPC npc) {
        NPCStats stats = npc.getStats();
        // 1. 有紧急需求（100%触发决策）
        if (hasUrgentNeed(npc)) {
            log.debug("NPC {} 有紧急需求，触发决策", npc.getName());
            return true;
        }

        // 2. 动态概率：属性越极端，越倾向重新决策
        int dynamicProbability = DECISION_PROBABILITY;
        if (stats.getDebt() > 3000) dynamicProbability += 15;
        if (stats.getMoney() < 300) dynamicProbability += 15;
        if (stats.getHealth() < 40) dynamicProbability += 20;
        if (stats.getKnowledgeLevel() > 75 && stats.getSkillLevel() > 75) dynamicProbability -= 10;
        dynamicProbability = Math.max(20, Math.min(95, dynamicProbability));

        boolean randomDecision = random.nextInt(100) < dynamicProbability;
        if (randomDecision) {
            log.trace("NPC {} 随机触发决策", npc.getName());
            return true;
        }

        // 3. 当前动作已完成
        if (isCurrentActionCompleted(npc)) {
            log.debug("NPC {} 当前动作完成，触发新决策", npc.getName());
            return true;
        }

        // 4. 长时间没有动作
        if (hasBeenInactiveTooLong(npc)) {
            log.debug("NPC {} 长时间无动作，触发决策", npc.getName());
            return true;
        }

        return false;
    }

    /**
     * 应用决策结果
     */
    private void apply(NPC npc, NPCDecisionTools.DecisionResult result) {
        // 设置新动作
        npc.setCurrentAction(result.getDecision());

        // 更新位置（如果决策中包含位置信息）
        updateLocationFromDecision(npc, result.getDecision());

        // 根据决策类型更新属性
        updateStatsFromDecision(npc, result.getDecision());

        // 记录决策时间
        npc.setUpdatedAt(LocalDateTime.now());

        //记录新想法
        npc.getCurrentThoughts().add(result.getNewThought());
    }

    /**
     * 从决策中推断位置变化
     */
    private void updateLocationFromDecision(NPC npc, String decision) {
        String lowerDecision = decision.toLowerCase();
        String currentLocation = npc.getCurrentLocation();

        // 位置推断规则
        Map<String, String> locationKeywords = Map.of("酒吧|喝酒|饮酒|吧台", "霓虹酒吧", "公司|工作|办公|编程|代码|开会", "科技公司", "家|家里|回家|休息|睡觉|卧室", "公寓住宅", "诊所|医院|看病|医生|治疗", "赛博诊所", "警察局|执勤|巡逻|办案", "警察总局", "黑市|交易|买卖|走私", "地下黑市", "餐厅|吃饭|用餐|食堂|餐馆", "仿生餐厅", "商场|购物|逛街|商店", "全息商场", "公园|散步|运动|锻炼", "中央公园", "街道|路上|马路|行走", "霓虹街道");

        for (Map.Entry<String, String> entry : locationKeywords.entrySet()) {
            if (lowerDecision.matches(".*(" + entry.getKey() + ").*")) {
                String newLocation = entry.getValue();
                if (!newLocation.equals(currentLocation)) {
                    npc.setCurrentLocation(newLocation);
                    log.debug("NPC {} 位置更新: {} -> {}", npc.getName(), currentLocation, newLocation);
                }
                return;
            }
        }
    }

    /**
     * 根据决策更新属性
     */
    private void updateStatsFromDecision(NPC npc, String decision) {
        NPCStats stats = npc.getStats();
        String lowerDecision = decision.toLowerCase();

        // 工作消耗能量但赚钱
        if (lowerDecision.matches(".*(工作|编程|代码|办公|执勤|巡逻).*")) {
            stats.setEnergy(Math.max(0, stats.getEnergy() - 10));
            double earned = 30 + random.nextInt(90) + (stats.getSkillLevel() * 0.8) + (stats.getWorkExperience() * 0.5);
            stats.setMoney(stats.getMoney() + earned);
            stats.setSavings(stats.getSavings() + earned * 0.25);
            if (random.nextInt(100) < 20) {
                stats.setReputation(Math.min(100, stats.getReputation() + 1));
            }
            log.trace("NPC {} 工作赚取 {} 信用点", npc.getName(), earned);
        }

        // 休息恢复能量
        if (lowerDecision.matches(".*(休息|睡觉|小憩|午休).*")) {
            stats.setEnergy(Math.min(100, stats.getEnergy() + 20));
        }

        // 吃饭减少饥饿
        if (lowerDecision.matches(".*(吃饭|用餐|进食|餐厅|食堂).*")) {
            stats.setHunger(Math.max(0, stats.getHunger() - 40));
            // 花钱吃饭
            double cost = 30 + random.nextInt(70); // 30-100信用点
            stats.setMoney(Math.max(0, stats.getMoney() - cost));
        }

        // 学习投资：短期花钱，长期提升能力
        if (lowerDecision.matches(".*(学习|上课|研究|培训|读书).*")) {
            double tuition = 40 + random.nextInt(120);
            if (stats.getMoney() >= tuition) {
                stats.setMoney(stats.getMoney() - tuition);
            } else {
                stats.setDebt(stats.getDebt() + tuition);
            }
            stats.setKnowledgeLevel(Math.min(100, stats.getKnowledgeLevel() + 2));
            stats.setSkillLevel(Math.min(100, stats.getSkillLevel() + 1));
            maybePromoteEducation(stats);
        }

        // 投资理财：有波动，不直接影响世界事件
        if (lowerDecision.matches(".*(投资|理财|交易|炒股).*")) {
            double stake = Math.min(stats.getMoney() * 0.2, 300 + random.nextInt(400));
            if (stake > 0) {
                stats.setMoney(stats.getMoney() - stake);
                double ratio = (random.nextDouble() * 0.6) - 0.2; // -20% ~ +40%
                double result = stake * (1 + ratio);
                stats.setMoney(stats.getMoney() + Math.max(0, result));
                if (result >= stake) {
                    stats.setSavings(stats.getSavings() + (result - stake));
                    stats.setHappiness(Math.min(100, stats.getHappiness() + 2));
                } else {
                    stats.setHappiness(Math.max(0, stats.getHappiness() - 2));
                }
            }
        }

        // 社交提升心情但消耗能量
        if (lowerDecision.matches(".*(社交|聊天|聚会|约会|见面|喝酒).*")) {
            stats.setHappiness(Math.min(100, stats.getHappiness() + 15));
            stats.setSocialNeed(Math.max(0, stats.getSocialNeed() - 30));
            stats.setEnergy(Math.max(0, stats.getEnergy() - 5));
        }
    }

    /**
     * 继续当前行为（不改变决策）
     */
    private void continueCurrentAction(NPC npc) {
        String currentAction = npc.getCurrentAction();

        if (currentAction == null || currentAction.isEmpty()) {
            npc.setCurrentAction("发呆");
            log.trace("NPC {} 没有当前动作，设为发呆", npc.getName());
            return;
        }

        // 根据当前行为类型消耗/恢复属性
        String lowerAction = currentAction.toLowerCase();

        if (lowerAction.matches(".*(工作|编程|执勤|巡逻).*")) {
            // 工作持续消耗能量
            npc.getStats().setEnergy(Math.max(0, npc.getStats().getEnergy() - 3));
        } else if (lowerAction.matches(".*(休息|睡觉).*")) {
            // 休息恢复能量
            npc.getStats().setEnergy(Math.min(100, npc.getStats().getEnergy() + 5));
        }

        log.trace("NPC {} 继续行为: {}", npc.getName(), currentAction);
    }

    /**
     * 检查当前动作是否已完成
     */
    private boolean isCurrentActionCompleted(NPC npc) {
        String action = npc.getCurrentAction();
        if (action == null || action.isEmpty()) return true;

        // 短期动作的完成概率更高
        String lowerAction = action.toLowerCase();

        if (lowerAction.matches(".*(吃饭|用餐|休息|睡觉|小憩).*")) {
            return random.nextInt(100) < 40; // 40%概率完成
        }

        if (lowerAction.matches(".*(工作|编程|执勤|巡逻|办公).*")) {
            return random.nextInt(100) < 25; // 25%概率完成
        }

        if (lowerAction.matches(".*(社交|聊天|聚会|约会).*")) {
            return random.nextInt(100) < 30; // 30%概率完成
        }

        // 默认：20%概率完成
        return random.nextInt(100) < 20;
    }

    // ======================== 辅助判断方法 ========================

    /**
     * 检查是否有紧急需求
     */
    private boolean hasUrgentNeed(NPC npc) {
        NPCStats stats = npc.getStats();
        return stats.getEnergy() < 20 ||      // 能量极低
                stats.getHunger() > 85 ||       // 非常饥饿
                stats.getHappiness() < 15 ||    // 心情极差
                stats.getSocialNeed() > 90 ||   // 极度需要社交
                stats.getMoney() < 50 ||        // 现金见底
                stats.getDebt() > 5000;         // 负债过高
    }

    /**
     * 检查是否长时间没有动作
     */
    private boolean hasBeenInactiveTooLong(NPC npc) {
        if (npc.getUpdatedAt() == null) return true;

        long hoursSinceUpdate = java.time.Duration.between(npc.getUpdatedAt(), LocalDateTime.now()).toHours();

        return hoursSinceUpdate >= 2; // 2小时没有更新
    }

    /**
     * 检查是否应该触发随机事件
     */
    private boolean shouldTriggerEvent(NPC npc) {
        // 5%概率触发随机事件
        return random.nextInt(100) < 5;
    }

    /**
     * 触发随机事件
     */
    private void triggerRandomEvent(NPC npc) {
        int eventType = random.nextInt(10);
        NPCStats stats = npc.getStats();

        switch (eventType) {
            case 0: // 捡到钱
                double foundMoney = 10 + random.nextInt(100);
                stats.setMoney(stats.getMoney() + foundMoney);
                npc.setCurrentAction("捡到" + foundMoney + "信用点");
                log.info("NPC {} 捡到 {} 信用点", npc.getName(), foundMoney);
                break;

            case 1: // 遇到朋友
                stats.setHappiness(Math.min(100, stats.getHappiness() + 20));
                stats.setSocialNeed(Math.max(0, stats.getSocialNeed() - 30));
                npc.setCurrentAction("偶遇老朋友聊天");
                log.info("NPC {} 遇到朋友，心情变好", npc.getName());
                break;

            case 2: // 工作小成就
                stats.setHappiness(Math.min(100, stats.getHappiness() + 15));
                npc.setCurrentAction("完成一个小项目");
                log.info("NPC {} 工作有进展", npc.getName());
                break;

            case 3: // 遇到麻烦
                stats.setHappiness(Math.max(0, stats.getHappiness() - 15));
                npc.setCurrentAction("遇到点小麻烦");
                log.info("NPC {} 遇到麻烦", npc.getName());
                break;

            default:
                // 其他事件类型可以扩展
                break;
        }
    }

    // ======================== 批量处理方法 ========================

    /**
     * 查找需要设定长期目标的NPC
     */
    private List<NPC> findNPCsNeedingGoals() {
        return npcRepository.findAll().stream().filter(npc -> npc.getCurrentGoal() == null || npc.getCurrentGoal().isEmpty()).filter(npc -> npc.getUpdatedAt() != null).filter(npc -> {
            long hoursSinceGoalUpdate = java.time.Duration.between(npc.getUpdatedAt(), LocalDateTime.now()).toHours();
            return hoursSinceGoalUpdate >= 4; // 4小时没有目标更新
        }).toList();
    }

    /**
     * 为NPC设定长期目标
     */
    private void assignLongTermGoal(NPC npc) {
        String occupation = npc.getOccupation();
        String personality = npc.getPersonality();
        NPCStats stats = npc.getStats();

        if (stats.getDebt() > 3000) {
            npc.setCurrentGoal("优先偿还债务并稳定现金流");
            log.info("为NPC {} 设定长期目标: {}", npc.getName(), npc.getCurrentGoal());
            return;
        }
        if (stats.getMoney() < 300) {
            npc.setCurrentGoal("寻找高收入机会并积累第一桶金");
            log.info("为NPC {} 设定长期目标: {}", npc.getName(), npc.getCurrentGoal());
            return;
        }
        if (stats.getKnowledgeLevel() < 45 || stats.getSkillLevel() < 45) {
            npc.setCurrentGoal("参加培训提升技能与知识储备");
            log.info("为NPC {} 设定长期目标: {}", npc.getName(), npc.getCurrentGoal());
            return;
        }

        String goal = switch (occupation) {
            case "程序员" -> personality.contains("内向") ? "独立完成一个开源项目" : "与团队合作开发新产品";
            case "设计师" -> "创作一件标志性的数字艺术品";
            case "警察" -> "提升辖区治安等级";
            case "医生" -> "研究新的治疗方案";
            case "酒吧老板" -> "扩大酒吧知名度";
            case "黑市商人" -> "建立更安全的交易网络";
            default -> "提升职业技能";
        };

        npc.setCurrentGoal(goal);
        log.info("为NPC {} 设定长期目标: {}", npc.getName(), goal);
    }

    private boolean isWorkingAction(String action) {
        if (action == null) {
            return false;
        }
        String lowerAction = action.toLowerCase();
        return lowerAction.matches(".*(工作|编程|执勤|巡逻|办公|接单|营业|交易).*");
    }

    private void maybePromoteEducation(NPCStats stats) {
        int knowledge = stats.getKnowledgeLevel();
        String current = stats.getEducationLevel() == null ? "高中" : stats.getEducationLevel();
        if (knowledge > 85 && !"博士".equals(current) && random.nextInt(100) < 20) {
            stats.setEducationLevel("博士");
            return;
        }
        if (knowledge > 75 && !current.matches("博士|硕士") && random.nextInt(100) < 25) {
            stats.setEducationLevel("硕士");
            return;
        }
        if (knowledge > 60 && !current.matches("博士|硕士|本科") && random.nextInt(100) < 35) {
            stats.setEducationLevel("本科");
            return;
        }
        if (knowledge > 45 && "高中".equals(current) && random.nextInt(100) < 30) {
            stats.setEducationLevel("大专");
        }
    }

    /**
     * 清理过期状态
     */
    private void cleanupExpiredStates() {
        // 可以在这里清理长时间未更新的NPC状态
        // 比如重置长时间发呆的NPC
    }

    // ======================== 初始化方法 ========================

    /**
     * 初始化NPC数据 - 应用启动时调用
     */
    public void initializeNPCs() {
        if (npcRepository.count() > 0) {
            log.info("NPC数据已存在，共{}个NPC", npcRepository.count());
            return;
        }

        log.info("开始创建初始NPC...");

        // 10个初始NPC配置
        Object[][] npcConfigs = {
                // ID, 姓名, 职业, 性格, 初始位置
                {"npc-1", "杰克", "程序员", "内向但善良，技术狂热", "科技公司"}, {"npc-2", "莉莉", "设计师", "外向时尚，艺术感强", "科技公司"}, {"npc-3", "老王", "酒吧老板", "精明务实，消息灵通", "霓虹酒吧"}, {"npc-4", "小李", "警察", "正义感强，责任心重", "警察总局"}, {"npc-5", "阿强", "黑市商人", "狡猾但守信，利益至上", "地下黑市"}, {"npc-6", "小美", "医生", "温柔体贴，富有同情心", "赛博诊所"}, {"npc-7", "大壮", "保安", "强壮忠诚，头脑简单", "全息商场"}, {"npc-8", "眼镜", "黑客", "技术天才，社交障碍", "网络空间"}, {"npc-9", "红姐", "舞者", "魅力四射，身世神秘", "霓虹酒吧"}, {"npc-10", "老陈", "出租车司机", "见多识广，爱讲故事", "霓虹街道"}};

        for (Object[] config : npcConfigs) {
            createNPC((String) config[0], (String) config[1], (String) config[2], (String) config[3], (String) config[4]);
        }

        log.info("NPC初始化完成，创建了{}个NPC", npcConfigs.length);
    }

    /**
     * 使用大模型批量创建NPC
     */
    public List<NPC> initializeNPCsWithAI(int count, boolean clearExisting) {
        int targetCount = Math.max(1, Math.min(20, count));
        if (clearExisting) {
            log.warn("按请求清空现有NPC数据后重新生成");
            npcRepository.deleteAll();
        }

        List<String> existingNames = npcRepository.findAll().stream().map(NPC::getName).toList();
        List<AIService.NPCBlueprint> blueprints = aiService.generateNPCBlueprints(targetCount, existingNames);

        List<NPC> created = blueprints.stream()
                .limit(targetCount)
                .map(this::createFromBlueprint)
                .toList();

        log.info("AI初始化完成，新增{}个NPC", created.size());
        return created;
    }

    /**
     * 创建单个NPC
     */
    private void createNPC(String id, String name, String occupation, String personality, String location) {
        NPC npc = new NPC();
        npc.setId(id);
        npc.setName(name);
        npc.setOccupation(occupation);
        npc.setPersonality(personality);
        npc.setCurrentLocation(location);
        npc.setCurrentAction(getInitialAction(occupation));
        npc.setCurrentGoal(getInitialGoal(occupation));

        // 随机化属性（在合理范围内）
        NPCStats stats = NPCStats.builder().energy(60 + random.nextInt(30))          // 60-90
                .hunger(20 + random.nextInt(40))          // 20-60
                .happiness(50 + random.nextInt(40))       // 50-90
                .socialNeed(30 + random.nextInt(40))      // 30-70
                .money(500 + random.nextInt(1500))        // 500-2000
                .skillLevel(30 + random.nextInt(40))
                .knowledgeLevel(35 + random.nextInt(45))
                .health(60 + random.nextInt(35))
                .reputation(10 + random.nextInt(30))
                .savings(100 + random.nextInt(600))
                .debt(random.nextInt(300))
                .workExperience(random.nextInt(60))
                .educationLevel(randomPick("高中", "大专", "本科", "职业认证"))
                .build();
        npc.setStats(stats);

        npcRepository.save(npc);
        log.info("创建NPC: {} - {} (@{})，心情: {}，能量: {}，金钱: {}", name, occupation, location, stats.getHappiness(), stats.getEnergy(), stats.getMoney());
    }

    private NPC createFromBlueprint(AIService.NPCBlueprint blueprint) {
        NPC npc = new NPC();
        npc.setId("npc-ai-" + UUID.randomUUID().toString().substring(0, 8));
        npc.setName(blueprint.name());
        npc.setOccupation(blueprint.occupation());
        npc.setPersonality(blueprint.personality());
        npc.setCurrentLocation(blueprint.location());
        npc.setCurrentAction(blueprint.currentAction());
        npc.setCurrentGoal(blueprint.currentGoal());

        NPCStats stats = NPCStats.builder()
                .energy(blueprint.energy())
                .hunger(blueprint.hunger())
                .happiness(blueprint.happiness())
                .socialNeed(blueprint.socialNeed())
                .money(blueprint.money())
                .intelligence(blueprint.intelligence())
                .charisma(blueprint.charisma())
                .skillLevel(blueprint.skillLevel())
                .knowledgeLevel(blueprint.knowledgeLevel())
                .health(blueprint.health())
                .reputation(blueprint.reputation())
                .savings(blueprint.savings())
                .debt(blueprint.debt())
                .workExperience(blueprint.workExperience())
                .educationLevel(blueprint.educationLevel())
                .build();
        npc.setStats(stats);

        NPC saved = npcRepository.save(npc);
        log.info("AI创建NPC: {} - {} (@{})", saved.getName(), saved.getOccupation(), saved.getCurrentLocation());
        return saved;
    }

    /**
     * 获取初始动作（根据职业）
     */
    private String getInitialAction(String occupation) {
        return switch (occupation) {
            case "程序员" -> randomPick("工作中", "修复线上BUG", "写自动化脚本");
            case "设计师" -> randomPick("工作中", "调整界面配色", "打磨作品细节");
            case "酒吧老板" -> randomPick("准备营业", "清点库存", "招呼熟客");
            case "警察" -> randomPick("巡逻中", "处理报案", "检查监控记录");
            case "医生" -> randomPick("门诊中", "查看病历", "准备手术器材");
            case "保安" -> randomPick("执勤中", "巡查商场", "检查安防系统");
            case "黑客" -> randomPick("编写代码", "渗透测试", "排查系统漏洞");
            case "舞者" -> randomPick("练习舞蹈", "准备演出", "编排新动作");
            case "出租车司机" -> randomPick("等待乘客", "接单途中", "车辆保养");
            case "黑市商人" -> randomPick("查看货物", "联系买家", "评估行情");
            default -> randomPick("活动中", "闲逛", "整理思绪");
        };
    }

    /**
     * 获取初始目标（根据职业）
     */
    private String getInitialGoal(String occupation) {
        return switch (occupation) {
            case "程序员" -> randomPick("完成当前项目", "学习新框架", "优化系统性能");
            case "设计师" -> randomPick("创作新作品", "打造爆款视觉", "提高个人知名度");
            case "警察" -> randomPick("维持辖区治安", "侦破近期案件", "降低街区犯罪率");
            case "医生" -> randomPick("治疗更多病人", "提升诊断效率", "研究疑难病例");
            case "酒吧老板" -> randomPick("提升酒吧营业额", "扩大夜场影响力", "打造特色招牌活动");
            case "黑市商人" -> randomPick("完成一笔大交易", "建立稳定供货链", "避开执法追踪");
            default -> randomPick("做好本职工作", "提升职业技能", "结识更多人脉");
        };
    }

    private String randomPick(String... options) {
        return options[random.nextInt(options.length)];
    }

    // ======================== 公开方法（供Controller调用） ========================

    /**
     * 手动触发NPC更新（用于调试或特殊事件）
     */
    public String manualUpdateNPC(String npcId) {
        var npcOpt = npcRepository.findById(npcId);
        if (npcOpt.isEmpty()) {
            return "NPC未找到";
        }

        NPC npc = npcOpt.get();
        updateSingleNPC(npc);
        return String.format("已手动更新NPC: %s，当前动作: %s，位置: %s", npc.getName(), npc.getCurrentAction(), npc.getCurrentLocation());
    }

    /**
     * 获取NPC统计信息
     */
    public Map<String, Object> getNPCStatistics() {
        List<NPC> allNPCs = npcRepository.findAll();

        long activeNPCs = allNPCs.stream().filter(npc -> npc.getCurrentAction() != null && !npc.getCurrentAction().isEmpty()).count();

        double avgHappiness = allNPCs.stream().mapToInt(npc -> npc.getStats().getHappiness()).average().orElse(0);

        double avgEnergy = allNPCs.stream().mapToInt(npc -> npc.getStats().getEnergy()).average().orElse(0);

        return Map.of("totalNPCs", allNPCs.size(), "activeNPCs", activeNPCs, "avgHappiness", String.format("%.1f", avgHappiness), "avgEnergy", String.format("%.1f", avgEnergy), "timestamp", LocalDateTime.now());
    }

    /**
     * 重置NPC状态（用于调试）
     */
    public void resetAllNPCs() {
        log.warn("重置所有NPC状态...");
        npcRepository.deleteAll();
        initializeNPCs();
        log.info("所有NPC已重置");
    }

    /**
     * 结束当前模拟，清空所有NPC，便于重新初始化
     */
    public void endSimulation() {
        long count = npcRepository.count();
        npcRepository.deleteAll();
        log.warn("模拟已结束，清空{}个NPC", count);
    }
}