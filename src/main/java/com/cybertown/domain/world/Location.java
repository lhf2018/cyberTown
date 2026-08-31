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

    @Id
    private String id;

    private String name;
    private String type;
    private String description;
    private int capacity;

    /** SVG 地图坐标（0-100 百分比） */
    private double mapX;
    private double mapY;
}