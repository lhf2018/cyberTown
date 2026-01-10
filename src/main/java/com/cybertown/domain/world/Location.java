package com.cybertown.domain.world;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 位置实体类
 * 代表赛博小镇中的地点：家、公司、酒吧、街道等
 * @Entity 表示这是一个JPA实体
 * @Table 指定表名为"locations"
 */
@Entity
@Table(name = "locations")
@Data
public class Location {
    
    @Id  // 主键
    private String id;           // 位置唯一ID
    
    // 位置属性
    private String name;         // 位置名称：科技公司、老王家酒吧等
    private String type;         // 位置类型：HOME, WORK, MARKET, BAR, STREET
    private String description;  // 位置描述
    
    // 容量相关
    private int capacity;        // 最大容纳人数
    
    // 注意：这个实体比较简单，实际项目中可能需要更多字段
    // 如坐标、开放时间、特殊事件等
}