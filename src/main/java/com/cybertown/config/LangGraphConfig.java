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

    /**
     * 配置聊天语言模型（可选）
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (openaiApiKey == null || openaiApiKey.isBlank()
                || openaiApiKey.equalsIgnoreCase("todo")
                || openaiApiKey.contains("your-key")
                || openaiApiKey.contains("sk-your")) {
            log.warn("未配置有效的 DeepSeek API Key（当前为占位符），AI 决策将不可用。请设置 DEEPSEEK_API_KEY 或 application-local.yml");
            return null;
        }
        try {
            log.info("DeepSeek Chat 模型已配置（key 前缀 {}…）", openaiApiKey.substring(0, Math.min(7, openaiApiKey.length())));
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .baseUrl(openaiBaseUrl)
                    .modelName("deepseek-chat")
                    .temperature(0.7)
                    .maxTokens(1200)
                    .logRequests(false)
                    .logResponses(false)
                    .build();
        } catch (Exception e) {
            log.error("创建OpenAI聊天模型失败", e);
            return null;
        }
    }

}
