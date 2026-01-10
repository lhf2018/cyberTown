package com.cybertown.config;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI相关配置类
 *
 * @Configuration 表示这是一个配置类，定义Spring Bean
 */
@Configuration
public class AIConfig {

    /**
     * NPC决策Prompt模板
     * PromptTemplate：Spring AI提供的提示词模板，支持变量替换
     *
     * @Bean 表示这个方法返回的对象会被Spring管理
     */
    @Bean
    public PromptTemplate npcDecisionPrompt() {
        // 定义NPC决策的提示词模板
        return new PromptTemplate("""
                你是{name}，一个{occupation}。      # 角色设定
                性格：{personality}                # 性格特征
                位置：{location}                   # 当前位置
                当前状态：{status}                 # 当前状态
                当前目标：{goal}                   # 当前目标（如果有）
                当前心情：{mood}                   # 心情状态
                当前时间：{time}                   # 游戏时间
                当前天气：{weather}                # 天气状况
                
                请决定接下来做什么（用中文回答，1-2句话）：
                # AI需要根据以上信息做出决策
                """);
    }

    /**
     * NPC对话Prompt模板
     */
    @Bean
    public PromptTemplate npcDialoguePrompt() {
        return new PromptTemplate("""
                角色：{name}（{occupation}）       # 对话角色
                性格：{personality}                # 角色性格
                当前心情：{mood}                   # 当前心情
                
                玩家说："{playerMessage}"          # 玩家输入
                
                请用角色身份回应用户（1-2句话，赛博朋克风格）：
                # AI需要以角色身份回应玩家
                """);
    }
}