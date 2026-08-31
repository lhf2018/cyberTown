package com.cybertown.repository;

import com.cybertown.domain.npc.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelationshipRepository extends JpaRepository<Relationship, String> {

    Optional<Relationship> findByNpcAIdAndNpcBId(String npcAId, String npcBId);

    @Query("SELECT r FROM Relationship r WHERE r.npcAId = :npcId OR r.npcBId = :npcId")
    List<Relationship> findByNpcId(@Param("npcId") String npcId);
}
