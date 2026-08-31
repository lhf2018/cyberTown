package com.cybertown.service;

import com.cybertown.domain.npc.NPC;
import com.cybertown.domain.npc.NPCStats;
import com.cybertown.domain.npc.Relationship;
import com.cybertown.repository.NPCRepository;
import com.cybertown.repository.RelationshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifeEventService {

    private final NPCRepository npcRepository;
    private final RelationshipRepository relationshipRepository;
    private final TownEventService townEventService;

    private final Random random = new Random();

    /**
     * 每心跳低概率触发人生事件
     */
    @Transactional
    public void maybeTrigger() {
        List<NPC> npcs = npcRepository.findAll();
        if (npcs.isEmpty()) {
            return;
        }
        // 全镇本轮最多触发 1 个人生事件
        if (random.nextInt(100) >= 22) {
            return;
        }
        NPC npc = npcs.get(random.nextInt(npcs.size()));
        NPCStats s = npc.getStats();

        int roll = random.nextInt(100);
        if (s.getDebt() > 2000 && roll < 25) {
            debtCollection(npc);
        } else if (s.getMoney() < 80 && s.getDebt() > 800 && roll < 40) {
            nearBankruptcy(npc);
        } else if (s.getSkillLevel() >= 70 && s.getWorkExperience() >= 6 && roll < 55) {
            promotion(npc);
        } else if (roll < 70) {
            lotteryOrWindfall(npc);
        } else {
            romanceEvent(npc);
        }
    }

    private void debtCollection(NPC npc) {
        NPCStats s = npc.getStats();
        double pay = Math.min(s.getMoney(), 100 + random.nextInt(200));
        s.setMoney(Math.max(0, s.getMoney() - pay));
        s.setDebt(Math.max(0, s.getDebt() - pay * 0.5));
        s.setHappiness(Math.max(0, s.getHappiness() - 10));
        npc.setCurrentAction("应对债务催收");
        npc.setCurrentGoal("尽快还清债务");
        saveLife(npc, "债务催收", npc.getName() + " 被催收，支付 " + String.format("%.0f", pay) + " 信用点", "DEBT");
    }

    private void nearBankruptcy(NPC npc) {
        NPCStats s = npc.getStats();
        s.setHappiness(Math.max(0, s.getHappiness() - 15));
        s.setReputation(Math.max(0, s.getReputation() - 5));
        npc.setCurrentAction("财务危机中求生");
        npc.setCurrentGoal("保住现金流");
        saveLife(npc, "破产边缘", npc.getName() + " 现金告急，负债 " + String.format("%.0f", s.getDebt()), "BANKRUPT");
    }

    private void promotion(NPC npc) {
        NPCStats s = npc.getStats();
        s.setReputation(Math.min(100, s.getReputation() + 8));
        s.setMonthlyCashIncome(Math.max(s.getMonthlyCashIncome(), 8000) * 1.08);
        s.setMoney(s.getMoney() + 500 + random.nextInt(800));
        s.setHappiness(Math.min(100, s.getHappiness() + 12));
        s.setWorkExperience(s.getWorkExperience() + 1);
        npc.setCurrentAction("庆祝升职");
        npc.setCurrentGoal("在新岗位站稳脚跟");
        saveLife(npc, "升职加薪", npc.getName() + " 获得升职机会，收入上调", "PROMOTE");
    }

    private void lotteryOrWindfall(NPC npc) {
        NPCStats s = npc.getStats();
        double win = 200 + random.nextInt(1800);
        s.setMoney(s.getMoney() + win);
        s.setHappiness(Math.min(100, s.getHappiness() + 10));
        npc.setCurrentAction("意外之财到手");
        saveLife(npc, "意外中奖", npc.getName() + " 获得 " + String.format("%.0f", win) + " 信用点意外收入", "WINDFALL");
    }

    private void romanceEvent(NPC npc) {
        List<Relationship> rels = relationshipRepository.findByNpcId(npc.getId());
        Relationship best = rels.stream()
                .filter(r -> r.getAffinity() >= 50)
                .max(Comparator.comparingInt(Relationship::getAffinity))
                .orElse(null);
        if (best == null) {
            lotteryOrWindfall(npc);
            return;
        }
        String otherId = best.otherId(npc.getId());
        NPC other = npcRepository.findById(otherId).orElse(null);
        String otherName = other == null ? otherId : other.getName();

        if (best.getAffinity() >= 75 && !"LOVER".equals(best.getType()) && random.nextBoolean()) {
            best.setType("LOVER");
            best.setAffinity(Math.min(100, best.getAffinity() + 10));
            best.setNote("告白成功");
            relationshipRepository.save(best);
            npc.setCurrentAction("向 " + otherName + " 告白成功");
            npc.getStats().setHappiness(Math.min(100, npc.getStats().getHappiness() + 12));
            if (other != null) {
                other.setCurrentAction("与 " + npc.getName() + " 确立关系");
                other.getStats().setHappiness(Math.min(100, other.getStats().getHappiness() + 12));
                npcRepository.save(other);
            }
            saveLife(npc, "告白成功", npc.getName() + " 与 " + otherName + " 成为恋人", "ROMANCE");
        } else if ("LOVER".equals(best.getType()) && random.nextInt(100) < 30) {
            best.setType("ACQUAINTANCE");
            best.setAffinity(Math.max(-20, best.getAffinity() - 40));
            best.setNote("分手");
            relationshipRepository.save(best);
            npc.setCurrentAction("经历分手");
            npc.getStats().setHappiness(Math.max(0, npc.getStats().getHappiness() - 18));
            saveLife(npc, "分手", npc.getName() + " 与 " + otherName + " 分手", "BREAKUP");
        } else {
            best.setAffinity(Math.min(100, best.getAffinity() + 5));
            relationshipRepository.save(best);
            npc.setCurrentAction("与 " + otherName + " 约会");
            saveLife(npc, "约会", npc.getName() + " 与 " + otherName + " 外出约会", "DATE");
        }
        npcRepository.save(npc);
    }

    private void saveLife(NPC npc, String title, String detail, String severity) {
        npcRepository.save(npc);
        townEventService.record("LIFE", title, detail, npc.getId(), severity, 18);
        log.info("人生事件: {}", detail);
    }
}
