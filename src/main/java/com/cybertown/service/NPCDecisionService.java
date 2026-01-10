package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.world.WorldState;
import com.cybertown.graph.NPCBehaviorGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NPCDecisionService {

    private final NPCBehaviorGraph npcBehaviorGraph;

    /**
     * 使用 LangGraph4j 进行智能决策
     */
    public String makeDecisionWithLangGraph(NPC npc, WorldState world) {
        return npcBehaviorGraph.decideWithAI(npc, world);
    }
}