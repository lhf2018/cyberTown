package com.cybertown.domain.npc;

/**
 * NPC行为枚举
 * 定义NPC可能执行的各种行为
 * 使用枚举而不是字符串，提高类型安全性
 */
public enum NPCAction {
    IDLE("发呆"),         // 无所事事
    WORK("工作"),         // 工作中
    SOCIALIZE("社交"),    // 与其他NPC或玩家交互
    EAT("吃饭"),          // 进食恢复饥饿
    REST("休息"),         // 休息恢复能量
    TRAVEL("移动"),       // 移动到其他位置
    SHOP("购物");         // 购买物品
    
    private final String description;  // 行为的中文描述
    
    /**
     * 枚举构造函数
     * @param description 行为描述
     */
    NPCAction(String description) {
        this.description = description;
    }
    
    /**
     * 获取行为描述
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}