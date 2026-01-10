package com.cybertown.repository;

import com.cybertown.domain.npc.NPC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * NPC数据访问接口
 * 继承JpaRepository，自动获得基础的CRUD方法
 * 无需实现，Spring Data JPA会自动生成实现
 * @Repository 标记为数据访问组件
 */
@Repository  // Spring注解：标记为数据访问层组件
public interface NPCRepository extends JpaRepository<NPC, String> {
    // JpaRepository<NPC, String>：
    // 第一个泛型参数：实体类型
    // 第二个泛型参数：主键类型（String）
    
    // 下面定义查询方法，Spring Data JPA会根据方法名自动生成SQL
    
    /**
     * 根据位置查找NPC
     * @param location 位置名称
     * @return 在该位置的所有NPC列表
     */
    List<NPC> findByCurrentLocation(String location);
    
    /**
     * 根据状态查找NPC
     * @param status 状态：工作中、休息中等
     * @return 具有该状态的所有NPC列表
     */
    List<NPC> findByStatus(String status);
    
    // 更多查询方法可以根据需要添加
    // 如：findByOccupation、findByStats_HappinessGreaterThan等
}