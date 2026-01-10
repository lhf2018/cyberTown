package com.cybertown.repository;

import com.cybertown.domain.world.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 位置数据访问接口
 */
@Repository
public interface LocationRepository extends JpaRepository<Location, String> {
    
    /**
     * 根据名称查找位置
     * @param name 位置名称
     * @return 位置对象或null
     */
    Location findByName(String name);
    
    // Spring Data JPA会自动实现这个方法
    // 相当于：SELECT * FROM locations WHERE name = ?
}