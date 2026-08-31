package com.cybertown.repository;

import com.cybertown.domain.world.TownEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TownEventRepository extends JpaRepository<TownEvent, String> {

    List<TownEvent> findTop50ByOrderByCreatedAtDesc();

    List<TownEvent> findByExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime now);

    List<TownEvent> findByTypeAndExpiresAtAfterOrderByCreatedAtDesc(String type, LocalDateTime now);
}
