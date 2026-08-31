package com.cybertown.graph;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.world.WorldState;
import com.cybertown.service.AiMetricsService;
import com.cybertown.service.NewsService;
import com.cybertown.service.SocialService;
import com.cybertown.service.WorldEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI驱动的NPC行为决策图
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NPCBehaviorGraph {

    private final NPCDecisionTools decisionTools;
    private final ChatLanguageModel chatLanguageModel;
    private final NewsService newsService;
    private final WorldEventService worldEventService;
    private final SocialService socialService;
    private final ObjectMapper objectMapper;
    private final AiMetricsService aiMetricsService;

    private AIDecisionAssistant aiDecisionAssistant;

    private static final Pattern SECTION = Pattern.compile(
            "【\\s*(决策分析|最终决策|新想法|决策理由)\\s*】\\s*([\\s\\S]*?)(?=【\\s*(?:决策分析|最终决策|新想法|决策理由)\\s*】|$)"
    );

    @PostConstruct
    public void init() {
        try {
            if (chatLanguageModel == null) {
                log.warn("ChatLanguageModel 为空，AI 决策不可用，将使用规则引擎");
                this.aiDecisionAssistant = null;
                return;
            }
            ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(6);

            // 不注册 tools：DeepSeek 易陷入反复调工具，触发「exceeded 10 sequential tool executions」。
            // 预检由 buildRequest 在 Java 侧各调用一次后写入提示词。
            this.aiDecisionAssistant = AiServices.builder(AIDecisionAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();

            log.info("🤖 AI决策助手初始化成功 - 使用大模型驱动决策（无工具循环）");

        } catch (Exception e) {
            log.error("AI决策助手初始化失败: {}", e.getMessage(), e);
            this.aiDecisionAssistant = null;
        }
    }

    public NPCDecisionTools.DecisionResult decideWithAI(NPC npc, WorldState world) {
        log.info("🤖 AI决策开始: {}", npc.getName());

        NPCDecisionTools.DecisionResult result = new NPCDecisionTools.DecisionResult();
        result.setNpcName(npc.getName());
        result.setTimestamp(LocalDateTime.now());

        if (aiDecisionAssistant == null) {
            String ruleDecision = decideWithRules(npc, world);
            result.setDecision(ruleDecision);
            result.setNewThought(null);
            result.setReason("规则引擎决策（AI未启用）");
            result.setSource("规则引擎");
            return result;
        }

        try {
            String sessionId = "ai_" + npc.getId() + "_" + System.currentTimeMillis();
            String request = buildRequest(npc, world);
            long t0 = System.currentTimeMillis();
            String raw = aiDecisionAssistant.analyzeAndDecide(sessionId, request);
            long latency = System.currentTimeMillis() - t0;
            DecisionWithThought aiResponse = parseDecisionResponse(raw);

            String decision = extractDecision(aiResponse);
            String newThought = extractNewThought(aiResponse);
            String reason = extractDecisionReason(aiResponse);

            result.setDecision(decision);
            result.setNewThought(newThought);
            result.setReason(reason);
            result.setSource("AI决策");
            result.setAiAnalysis(aiResponse.getDecisionAnalysis());
            aiMetricsService.recordSuccess("decision", npc.getName(), latency, decision);

            log.info("✅ AI决策完成: {} -> {}", npc.getName(), decision);
            return result;

        } catch (Exception e) {
            log.error("❌ AI决策失败: {}", e.getMessage());
            aiMetricsService.recordFailure("decision", npc.getName(), 0, e.getMessage());
            String ruleDecision = decideWithRules(npc, world);
            result.setDecision(ruleDecision);
            result.setNewThought(null);
            result.setReason("规则引擎决策（AI暂不可用）");
            result.setSource("规则引擎");
            return result;
        }
    }

    private String buildRequest(NPC npc, WorldState world) {
        String nearby = socialService.summarizeNearbyRelations(npc);
        int hour = world.getGameTime().getHour();
        String timeOfDay = world.getTimeOfDay();

        NPCDecisionTools.BasicNeedsResult needs = decisionTools.checkBasicNeeds(
                npc.getName(),
                npc.getStats().getEnergy(),
                npc.getStats().getHunger(),
                npc.getStats().getHappiness(),
                npc.getStats().getSocialNeed()
        );
        NPCDecisionTools.ScheduleResult schedule = decisionTools.checkSchedule(
                npc.getOccupation(), hour, timeOfDay
        );
        NPCDecisionTools.SocialResult social = decisionTools.checkSocial(
                npc.getPersonality(),
                npc.getStats().getSocialNeed(),
                npc.getStats().getHappiness(),
                npc.getName(),
                nearby
        );
        NPCDecisionTools.LocationResult location = decisionTools.checkLocation(
                npc.getCurrentLocation(), timeOfDay, npc.getName()
        );

        return String.format("""
                        请为以下 NPC 决策，只输出 JSON（不要调用工具）。
                        姓名=%s
                        职业=%s
                        性格=%s
                        位置=%s
                        能量=%d
                        饥饿=%d
                        心情=%d
                        社交需求=%d
                        金钱=%.2f
                        储蓄=%.2f
                        负债=%.2f
                        技能=%d
                        知识=%d
                        健康=%d
                        声望=%d
                        学历=%s
                        工龄月=%d
                        近期想法=%s
                        对话影响生效=%s
                        对话影响=%s
                        对话影响强度=%d
                        对话影响失效=%s
                        新闻摘要=%s
                        活跃世界事件=%s
                        同地点熟人=%s
                        小时=%d
                        时段=%s
                        天气=%s
                        游戏时间=%s
                        ---工具预检（已由系统计算，直接参考）---
                        需求：%s | 紧急=%s | 建议=%s
                        日程：%s | 工作时间=%s | %s
                        社交：%s | 优先级=%s | 建议活动=%s
                        地点：%s | 类型=%s
                        """,
                npc.getName(),
                npc.getOccupation(),
                npc.getPersonality(),
                npc.getCurrentLocation(),
                npc.getStats().getEnergy(),
                npc.getStats().getHunger(),
                npc.getStats().getHappiness(),
                npc.getStats().getSocialNeed(),
                npc.getStats().getMoney(),
                npc.getStats().getSavings(),
                npc.getStats().getDebt(),
                npc.getStats().getSkillLevel(),
                npc.getStats().getKnowledgeLevel(),
                npc.getStats().getHealth(),
                npc.getStats().getReputation(),
                npc.getStats().getEducationLevel(),
                npc.getStats().getWorkExperience(),
                getRecentThoughts(npc),
                npc.hasActiveDialogueInfluence(),
                npc.hasActiveDialogueInfluence() ? npc.getDialogueInfluence() : "无",
                npc.hasActiveDialogueInfluence() ? npc.getDialogueInfluenceWeight() : 0,
                npc.hasActiveDialogueInfluence() ? npc.getDialogueInfluenceExpiresAt() : "无",
                newsService.getNewsBrief(),
                worldEventService.activeWorldBriefSafe(),
                nearby,
                hour,
                timeOfDay,
                world.getWeather(),
                world.getGameTime().toString(),
                nullToDash(needs.getAnalysis()),
                needs.isHasUrgentNeed(),
                nullToDash(needs.getSuggestedAction()),
                nullToDash(schedule.getSuggestion()),
                schedule.isWorkTime(),
                nullToDash(schedule.getReason()),
                nullToDash(social.getSuggestion()),
                nullToDash(social.getPriority()),
                nullToDash(social.getSuggestedActivity()),
                nullToDash(location.getSuggestion()),
                nullToDash(location.getLocationType())
        );
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    DecisionWithThought parseDecisionResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 返回为空");
        }
        String text = stripMarkdownFence(raw.trim());

        try {
            String json = extractJsonObject(text);
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                DecisionWithThought d = new DecisionWithThought();
                d.setDecisionAnalysis(textOr(node, "decisionAnalysis", "analysis", "决策分析"));
                d.setFinalDecision(textOr(node, "finalDecision", "decision", "最终决策"));
                d.setNewThought(textOr(node, "newThought", "thought", "新想法"));
                d.setDecisionReason(textOr(node, "decisionReason", "reason", "决策理由"));
                if (d.getFinalDecision() != null && !d.getFinalDecision().isBlank()) {
                    return d;
                }
            }
        } catch (Exception e) {
            log.debug("JSON 解析决策失败，尝试分段文本: {}", e.getMessage());
        }

        DecisionWithThought fromSections = parseSectionText(text);
        if (fromSections.getFinalDecision() != null && !fromSections.getFinalDecision().isBlank()) {
            return fromSections;
        }

        DecisionWithThought fallback = new DecisionWithThought();
        fallback.setFinalDecision(firstLine(text));
        fallback.setDecisionReason("模型未按 JSON 返回，已做宽松解析");
        fallback.setNewThought(null);
        fallback.setDecisionAnalysis(text);
        return fallback;
    }

    private DecisionWithThought parseSectionText(String text) {
        DecisionWithThought d = new DecisionWithThought();
        Matcher m = SECTION.matcher(text);
        while (m.find()) {
            String key = m.group(1).trim();
            String val = m.group(2).trim();
            switch (key) {
                case "决策分析" -> d.setDecisionAnalysis(val);
                case "最终决策" -> d.setFinalDecision(val);
                case "新想法" -> d.setNewThought(val);
                case "决策理由" -> d.setDecisionReason(val);
                default -> {
                }
            }
        }
        if (d.getFinalDecision() == null) {
            Matcher loose = Pattern.compile("最终决策[:：]\\s*(.+)").matcher(text);
            if (loose.find()) {
                d.setFinalDecision(loose.group(1).trim());
            }
        }
        return d;
    }

    private static String textOr(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                String t = v.asText("").trim();
                if (!t.isBlank()) {
                    return t;
                }
            }
        }
        return null;
    }

    private static String stripMarkdownFence(String raw) {
        String t = raw.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                t = t.substring(firstNl + 1, lastFence).trim();
            }
        }
        return t;
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String firstLine(String text) {
        String t = text.replace('\r', '\n').trim();
        int nl = t.indexOf('\n');
        return nl >= 0 ? t.substring(0, nl).trim() : t;
    }

    private String getRecentThoughts(NPC npc) {
        if (npc.getCurrentThoughts() == null || npc.getCurrentThoughts().isEmpty()) {
            return "暂无想法";
        }
        int maxThoughts = Math.min(3, npc.getCurrentThoughts().size());
        List<String> recent = new ArrayList<>();
        int start = Math.max(0, npc.getCurrentThoughts().size() - maxThoughts);
        for (int i = start; i < npc.getCurrentThoughts().size(); i++) {
            recent.add(npc.getCurrentThoughts().get(i));
        }
        return String.join("；", recent);
    }

    private String extractDecision(DecisionWithThought response) {
        if (response == null || response.getFinalDecision() == null) {
            return "日常活动";
        }
        String decision = response.getFinalDecision().trim()
                .replace("【最终决策】", "")
                .replace("最终决策：", "")
                .replace("决策：", "")
                .trim();
        if (decision.contains("\n")) {
            decision = decision.split("\n")[0].trim();
        }
        if (decision.contains("。")) {
            decision = decision.split("。")[0].trim();
        }
        return decision.isEmpty() ? "日常活动" : decision;
    }

    private String extractNewThought(DecisionWithThought response) {
        if (response == null || response.getNewThought() == null) {
            return null;
        }
        String thought = response.getNewThought().trim()
                .replace("【新想法】", "")
                .replace("新想法：", "")
                .replace("想法：", "")
                .trim();
        if (thought.contains("\n")) {
            thought = thought.split("\n")[0].trim();
        }
        return thought.isEmpty() ? null : thought;
    }

    private String extractDecisionReason(DecisionWithThought response) {
        if (response == null || response.getDecisionReason() == null) {
            return "综合考虑NPC状态和环境";
        }
        String reason = response.getDecisionReason().trim()
                .replace("【决策理由】", "")
                .replace("决策理由：", "")
                .replace("理由：", "")
                .trim();
        if (reason.length() > 180) {
            reason = reason.substring(0, 179) + "…";
        }
        return reason.isEmpty() ? "综合考虑NPC状态和环境" : reason;
    }

    public String decideWithRules(NPC npc, WorldState world) {
        log.info("⚙️ 规则引擎决策: {}", npc.getName());
        try {
            if (npc.hasActiveDialogueInfluence() && npc.getDialogueInfluenceWeight() >= 60) {
                return "优先响应玩家建议：" + npc.getDialogueInfluence().trim();
            }
            int energy = npc.getStats().getEnergy();
            int hunger = npc.getStats().getHunger();
            int happiness = npc.getStats().getHappiness();
            int socialNeed = npc.getStats().getSocialNeed();
            int hour = world.getGameTime().getHour();
            String location = npc.getCurrentLocation() == null ? "" : npc.getCurrentLocation();

            if (energy < 20) return "立即休息";
            if (hunger > 85) return "立即吃饭";
            if (energy < 40) return "考虑休息";
            if (hunger > 65) return "去吃饭";
            if (isWorkTime(npc.getOccupation(), hour)) return "继续工作";
            if (socialNeed > 80) return "进行社交活动";
            if (location.contains("酒吧") || location.contains("娱乐")) return "享受娱乐活动";
            if (happiness < 30) return "改善心情";
            return "日常活动";
        } catch (Exception e) {
            log.error("规则引擎决策失败", e);
            return "保持现状";
        }
    }

    private boolean isWorkTime(String occupation, int hour) {
        return switch (occupation == null ? "" : occupation) {
            case "程序员", "设计师", "医生", "警察" -> hour >= 9 && hour < 18;
            case "酒吧老板" -> hour >= 16 || hour < 2;
            default -> hour >= 9 && hour < 18;
        };
    }
}
