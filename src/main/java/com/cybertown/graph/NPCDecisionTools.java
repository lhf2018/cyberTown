package com.cybertown.graph;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.V;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * LangGraph4j 工具定义
 * 正确的 @Tool 注解用法
 */
@Slf4j
@Component
public class NPCDecisionTools implements DecisionAssistant {

    /**
     * 检查NPC基础需求 - 正确的参数定义
     */
    @Tool("检查NPC的基础生理需求：能量、饥饿、心情、社交需求等。返回值包含是否有紧急需求和建议行动。")
    public BasicNeedsResult checkBasicNeeds(
            @V("NPC姓名") String npcName,
            @V("能量值，范围0-100") int energy,
            @V("饥饿度，范围0-100") int hunger,
            @V("心情值，范围0-100") int happiness,
            @V("社交需求，范围0-100") int socialNeed
    ) {
        log.debug("LangGraph工具调用: checkBasicNeeds for {}", npcName);

        BasicNeedsResult result = new BasicNeedsResult();
        result.setNpcName(npcName);
        result.setTimestamp(LocalDateTime.now());

        StringBuilder analysis = new StringBuilder();
        boolean hasUrgentNeed = false;
        String suggestedAction = null;

        // 检查能量
        if (energy < 20) {
            analysis.append("⚠️ 能量极低(").append(energy).append("%)，需要立即休息。");
            hasUrgentNeed = true;
            suggestedAction = "立即休息";
        } else if (energy < 40) {
            analysis.append("能量较低(").append(energy).append("%)，建议稍后休息。");
            suggestedAction = "考虑休息";
        } else {
            analysis.append("能量充足(").append(energy).append("%)。");
        }

        // 检查饥饿
        if (hunger > 85) {
            analysis.append("⚠️ 极度饥饿(").append(hunger).append("%)，需要立即进食。");
            hasUrgentNeed = true;
            suggestedAction = "立即吃饭";
        } else if (hunger > 65) {
            analysis.append("饥饿度较高(").append(hunger).append("%)，建议吃饭。");
            if (suggestedAction == null) {
                suggestedAction = "去吃饭";
            }
        } else if (hunger > 40) {
            analysis.append("轻微饥饿(").append(hunger).append("%)。");
        }

        // 检查心情
        if (happiness < 20) {
            analysis.append("心情极差(").append(happiness).append("%)，需要改善情绪。");
            if (!hasUrgentNeed && suggestedAction == null) {
                suggestedAction = "改善心情";
            }
        } else if (happiness < 50) {
            analysis.append("心情一般(").append(happiness).append("%)。");
        } else {
            analysis.append("心情良好(").append(happiness).append("%)。");
        }

        // 检查社交需求
        if (socialNeed > 80) {
            analysis.append("社交需求极高(").append(socialNeed).append("%)。");
            if (!hasUrgentNeed && suggestedAction == null) {
                suggestedAction = "社交互动";
            }
        } else if (socialNeed > 60) {
            analysis.append("社交需求较高(").append(socialNeed).append("%)。");
        }

        result.setAnalysis(analysis.toString());
        result.setHasUrgentNeed(hasUrgentNeed);
        result.setSuggestedAction(suggestedAction);

        return result;
    }

    /**
     * 检查日程安排
     */
    @Tool("根据NPC的职业和当前时间检查日程安排，返回日程建议和是否工作时间")
    public ScheduleResult checkSchedule(
            @V("NPC的职业") String occupation,
            @V("当前小时，0-23") int hour,
            @V("时间段，如DAY/NIGHT") String timeOfDay
    ) {
        log.debug("LangGraph工具调用: checkSchedule for {} at {}:00", occupation, hour);

        ScheduleResult result = new ScheduleResult();
        result.setHour(hour);
        result.setTimeOfDay(timeOfDay);
        result.setOccupation(occupation);

        // 获取日程建议
        String suggestion = getScheduleSuggestion(occupation, hour);
        result.setSuggestion(suggestion);

        // 判断是否工作时间
        boolean isWorkTime = isWorkTime(occupation, hour);
        result.setWorkTime(isWorkTime);

        result.setReason(isWorkTime ? "工作时间段" : "非工作时间段");
        result.setTimestamp(LocalDateTime.now());

        return result;
    }

    /**
     * 检查社交需求
     */
    @Tool("检查NPC的社交需求，考虑性格和当前心情")
    public SocialResult checkSocial(
            @V("NPC的性格") String personality,
            @V("社交需求值") int socialNeed,
            @V("当前心情") int happiness,
            @V("NPC姓名") String npcName
    ) {
        log.debug("LangGraph工具调用: checkSocial for {}", npcName);

        SocialResult result = new SocialResult();
        result.setPersonality(personality);
        result.setSocialNeed(socialNeed);
        result.setHappiness(happiness);
        result.setNpcName(npcName);

        StringBuilder suggestion = new StringBuilder();
        String priority;
        String suggestedActivity = null;

        // 根据社交需求判断优先级
        if (socialNeed > 80) {
            suggestion.append("强烈建议社交活动");
            priority = "高";
            suggestedActivity = "主动寻找社交机会";
        } else if (socialNeed > 60) {
            suggestion.append("建议社交");
            priority = "中";
            suggestedActivity = "参加社交活动";
        } else if (socialNeed > 40) {
            suggestion.append("可考虑社交");
            priority = "中低";
        } else {
            suggestion.append("社交需求不高");
            priority = "低";
            suggestedActivity = "独处或工作";
        }

        // 性格影响
        if (personality.contains("外向") || personality.contains("开朗")) {
            suggestion.append(" (外向性格更倾向社交)");
            if (suggestedActivity == null) {
                suggestedActivity = "主动与人交流";
            }
        } else if (personality.contains("内向") || personality.contains("安静")) {
            suggestion.append(" (内向性格可能偏好小范围交流)");
            if (suggestedActivity == null || suggestedActivity.contains("主动")) {
                suggestedActivity = "与熟悉的人交流";
            }
        }

        // 心情影响
        if (happiness < 30 && socialNeed > 50) {
            suggestion.append(" (心情低落时社交可能改善情绪)");
        }

        result.setSuggestion(suggestion.toString());
        result.setPriority(priority);
        result.setSuggestedActivity(suggestedActivity);
        result.setTimestamp(LocalDateTime.now());

        return result;
    }

    /**
     * 检查位置活动
     */
    @Tool("检查当前位置的可用活动和环境因素")
    public LocationResult checkLocation(
            @V("位置名称") String location,
            @V("时间段") String timeOfDay,
            @V("NPC姓名") String npcName
    ) {
        log.debug("LangGraph工具调用: checkLocation for {} at {}", npcName, location);

        LocationResult result = new LocationResult();
        result.setLocation(location);
        result.setTimeOfDay(timeOfDay);
        result.setNpcName(npcName);

        // 获取位置建议
        String suggestion = getLocationSuggestion(location, timeOfDay);
        result.setSuggestion(suggestion);

        // 判断位置类型
        String locationType = getLocationType(location);
        result.setLocationType(locationType);

        result.setTimestamp(LocalDateTime.now());
        return result;
    }

    /**
     * 综合决策
     */
    @Tool("综合所有因素为NPC做出最终决策")
    public DecisionResult makeFinalDecision(
            @V("NPC姓名") String npcName,
            @V("NPC职业") String occupation,
            @V("NPC性格") String personality,
            @V("需求分析文本") String needsAnalysis,
            @V("是否有紧急需求") boolean hasUrgentNeed,
            @V("日程建议") String scheduleSuggestion,
            @V("是否工作时间") boolean isWorkTime,
            @V("社交建议") String socialSuggestion,
            @V("社交优先级") String socialPriority,
            @V("位置建议") String locationSuggestion,
            @V("位置类型") String locationType,
            @V("当前时间字符串") String currentTime
    ) {
        log.debug("LangGraph工具调用: makeFinalDecision for {}", npcName);

        DecisionResult result = new DecisionResult();
        result.setNpcName(npcName);
        result.setTimestamp(LocalDateTime.now());

        // 构建决策上下文
        String context = buildDecisionContext(
                npcName, occupation, personality, needsAnalysis,
                hasUrgentNeed, scheduleSuggestion, isWorkTime,
                socialSuggestion, socialPriority, locationSuggestion,
                locationType, currentTime
        );

        // 生成决策
        String decision = generateDecision(context);
        String reason = generateDecisionReason(context);

        result.setDecision(decision);
        result.setReason(reason);
        result.setConfidence(calculateConfidence(context));
        result.setContext(context);

        return result;
    }

    // ======================== 辅助方法 ========================

    private String getScheduleSuggestion(String occupation, int hour) {
        if (hour >= 0 && hour < 6) {
            return switch (occupation) {
                case "警察" -> "夜间巡逻";
                case "医生" -> "急诊值班";
                case "酒吧老板" -> "酒吧打烊整理";
                case "黑市商人" -> "夜间交易";
                default -> "深夜休息";
            };
        } else if (hour >= 6 && hour < 9) {
            return switch (occupation) {
                case "程序员", "设计师" -> "早晨准备工作";
                case "警察" -> "早晨巡逻";
                case "医生" -> "早晨查房";
                case "出租车司机" -> "早班运营";
                default -> "早晨活动";
            };
        } else if (hour >= 9 && hour < 12) {
            return switch (occupation) {
                case "程序员", "设计师" -> "专注上午工作";
                case "警察" -> "上午执勤";
                case "医生" -> "上午门诊";
                case "酒吧老板" -> "营业准备";
                default -> "上午活动";
            };
        } else if (hour >= 12 && hour < 14) {
            return "午餐休息";
        } else if (hour >= 14 && hour < 18) {
            return switch (occupation) {
                case "程序员", "设计师" -> "继续下午工作";
                case "警察" -> "下午巡逻";
                case "医生" -> "下午诊疗";
                case "酒吧老板" -> "下午营业";
                case "舞者" -> "下午排练";
                default -> "下午活动";
            };
        } else if (hour >= 18 && hour < 22) {
            return switch (occupation) {
                case "酒吧老板", "舞者" -> "晚间营业";
                case "程序员", "设计师" -> "加班或学习";
                default -> "晚间活动";
            };
        } else {
            return switch (occupation) {
                case "警察" -> "夜间执勤";
                case "黑市商人" -> "夜晚交易";
                default -> "准备休息";
            };
        }
    }

    private boolean isWorkTime(String occupation, int hour) {
        // 定义工作时间
        boolean isDayTime = hour >= 9 && hour < 18;

        return switch (occupation) {
            case "程序员", "设计师", "医生", "警察" -> isDayTime;
            case "酒吧老板" -> hour >= 16 || hour < 2;
            case "出租车司机" -> true; // 出租车司机随时可能工作
            default -> isDayTime;
        };
    }

    private String getLocationSuggestion(String location, String timeOfDay) {
        // 根据位置和时间提供建议
        return switch (location) {
            case "科技公司" -> "可以工作、开会、学习或与同事交流";
            case "霓虹酒吧" -> timeOfDay.equals("NIGHT")
                    ? "可以喝酒、社交、跳舞或收集信息（夜晚最佳）"
                    : "可以喝酒或社交（白天较安静）";
            case "公寓住宅" -> "适合休息、学习、娱乐或个人事务";
            case "赛博诊所" -> "可以工作、就诊或休息";
            case "警察总局" -> "适合执勤、办公或处理案件";
            case "地下黑市" -> "适合交易、购买稀有物品或获取信息";
            case "仿生餐厅" -> "适合用餐、会面或休息";
            case "全息商场" -> "适合购物、闲逛或娱乐";
            case "中央公园" -> "适合散步、放松或运动";
            case "网络空间" -> "适合编程、黑客活动或信息检索";
            default -> "可以在当前位置探索或活动";
        };
    }

    private String getLocationType(String location) {
        return switch (location) {
            case "科技公司", "警察总局", "赛博诊所" -> "工作场所";
            case "霓虹酒吧", "仿生餐厅", "全息商场" -> "娱乐场所";
            case "公寓住宅" -> "居住场所";
            case "地下黑市", "网络空间" -> "特殊场所";
            case "中央公园", "霓虹街道" -> "公共场所";
            default -> "普通场所";
        };
    }

    private String buildDecisionContext(String npcName, String occupation, String personality,
                                        String needsAnalysis, boolean hasUrgentNeed,
                                        String scheduleSuggestion, boolean isWorkTime,
                                        String socialSuggestion, String socialPriority,
                                        String locationSuggestion, String locationType,
                                        String currentTime) {
        return String.format("""
                        === 决策上下文 ===
                        NPC: %s (%s)
                        性格: %s
                        当前时间: %s
                        
                        分析结果:
                        1. 基础需求: %s
                           - 紧急需求: %s
                        2. 日程: %s
                           - 工作时间: %s
                        3. 社交: %s
                           - 优先级: %s
                        4. 位置: %s
                           - 类型: %s
                        """,
                npcName, occupation, personality, currentTime,
                needsAnalysis, hasUrgentNeed ? "是" : "否",
                scheduleSuggestion, isWorkTime ? "是" : "否",
                socialSuggestion, socialPriority,
                locationSuggestion, locationType
        );
    }

    private String generateDecision(String context) {
        // 简单的规则引擎
        if (context.contains("紧急需求: 是")) {
            if (context.contains("能量极低")) return "立即休息";
            if (context.contains("极度饥饿")) return "立即去吃饭";
        }

        if (context.contains("工作时间: 是")) {
            return "继续工作";
        }

        if (context.contains("社交优先级: 高")) {
            return "进行社交活动";
        }

        if (context.contains("位置类型: 娱乐场所")) {
            return "享受娱乐活动";
        }

        return "进行日常活动";
    }

    private String generateDecisionReason(String context) {
        if (context.contains("紧急需求: 是")) {
            return "优先处理紧急生理需求";
        }

        if (context.contains("工作时间: 是")) {
            return "遵循工作日程安排";
        }

        if (context.contains("社交优先级: 高")) {
            return "满足高社交需求";
        }

        return "根据当前环境和状态做出的合理选择";
    }

    private int calculateConfidence(String context) {
        int confidence = 70; // 基础置信度

        if (context.contains("紧急需求: 是")) {
            confidence += 20; // 紧急需求决策置信度高
        }

        if (context.contains("工作时间: 是")) {
            confidence += 15; // 工作时间决策明确
        }

        if (!context.contains("社交优先级: 中") && !context.contains("社交优先级: 低")) {
            confidence += 10; // 社交需求明确
        }

        return Math.min(confidence, 95); // 最高95%
    }

    // ======================== 数据结构 ========================

    @Data
    public static class BasicNeedsResult {
        private String npcName;
        private String analysis;
        private boolean hasUrgentNeed;
        private String suggestedAction;
        private LocalDateTime timestamp;
    }

    @Data
    public static class ScheduleResult {
        private String occupation;
        private int hour;
        private String timeOfDay;
        private String suggestion;
        private boolean workTime;
        private String reason;
        private LocalDateTime timestamp;
    }

    @Data
    public static class SocialResult {
        private String npcName;
        private String personality;
        private int socialNeed;
        private int happiness;
        private String suggestion;
        private String priority;
        private String suggestedActivity;
        private LocalDateTime timestamp;
    }

    @Data
    public static class LocationResult {
        private String npcName;
        private String location;
        private String timeOfDay;
        private String suggestion;
        private String locationType;
        private LocalDateTime timestamp;
    }

    @Data
    public static class DecisionResult {
        private String npcName;
        private String decision;
        private String reason;
        private int confidence;
        private String context;
        private LocalDateTime timestamp;
    }
}