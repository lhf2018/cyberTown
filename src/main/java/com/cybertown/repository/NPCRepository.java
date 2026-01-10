package com.cybertown.repository;

import com.cybertown.domain.npc.NPC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * NPC数据访问接口
 * 继承JpaRepository，自动获得CRUD方法
 */
@Repository
public interface NPCRepository extends JpaRepository<NPC, String> {

    // 按位置查找NPC
    List<NPC> findByCurrentLocation(String location);

    // 按状态查找NPC
    List<NPC> findByCurrentAction(String action);
}