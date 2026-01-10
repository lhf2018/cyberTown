package com.cybertown.config;

import com.cybertown.graph.NPCBehaviorGraph;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LangGraphConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String openaiBaseUrl;

    private final NPCBehaviorGraph npcBehaviorGraph;

    /**
     * 配置聊天语言模型（可选）
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        // 检查是否有有效的API密钥
        if (openaiApiKey == null || openaiApiKey.isEmpty() ||
                openaiApiKey.contains("your-key") || openaiApiKey.contains("sk-your")) {
            log.warn("未配置有效的OpenAI API密钥，LangGraph4j将使用纯工具模式");
            return null;
        }

        try {
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .baseUrl(openaiBaseUrl)
                    .modelName("deepseek-chat")
                    .temperature(0.7)
                    .maxTokens(500)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } catch (Exception e) {
            log.error("创建OpenAI聊天模型失败", e);
            return null;
        }
    }

    /**
     * 初始化LangGraph4j
     */
    @PostConstruct
    public void initLangGraph() {
        try {
            npcBehaviorGraph.init();
            log.info("LangGraph4j 初始化完成");
        } catch (Exception e) {
            log.error("LangGraph4j 初始化失败", e);
        }
    }
}