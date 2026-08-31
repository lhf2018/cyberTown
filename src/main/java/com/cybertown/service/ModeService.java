package com.cybertown.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 旁观 / 运营模式与干预配额（按 clientKey，缺省为 global）
 */
@Service
public class ModeService {

    public static final String MODE_SPECTATOR = "spectator";
    public static final String MODE_OPERATOR = "operator";

    private static final int DEFAULT_TALK = 20;
    private static final int DEFAULT_GOD = 5;

    private final ConcurrentHashMap<String, ClientQuota> quotas = new ConcurrentHashMap<>();

    public Map<String, Object> getStatus(String clientKey) {
        ClientQuota q = quota(clientKey);
        return Map.of(
                "mode", q.mode,
                "talkRemaining", q.talkRemaining.get(),
                "godRemaining", q.godRemaining.get(),
                "talkLimit", DEFAULT_TALK,
                "godLimit", DEFAULT_GOD
        );
    }

    public Map<String, Object> setMode(String clientKey, String mode) {
        ClientQuota q = quota(clientKey);
        if (MODE_SPECTATOR.equalsIgnoreCase(mode)) {
            q.mode = MODE_SPECTATOR;
        } else {
            q.mode = MODE_OPERATOR;
        }
        return getStatus(clientKey);
    }

    public void assertCanTalk(String clientKey) {
        ClientQuota q = quota(clientKey);
        if (MODE_SPECTATOR.equals(q.mode)) {
            throw new IllegalStateException("旁观模式不可对话，请切换为运营模式");
        }
        if (q.talkRemaining.get() <= 0) {
            throw new IllegalStateException("对话干预次数已用尽");
        }
    }

    public void consumeTalk(String clientKey) {
        quota(clientKey).talkRemaining.decrementAndGet();
    }

    public void assertCanGod(String clientKey) {
        ClientQuota q = quota(clientKey);
        if (MODE_SPECTATOR.equals(q.mode)) {
            throw new IllegalStateException("旁观模式不可使用上帝指令");
        }
        if (q.godRemaining.get() <= 0) {
            throw new IllegalStateException("上帝干预次数已用尽");
        }
    }

    public void consumeGod(String clientKey) {
        quota(clientKey).godRemaining.decrementAndGet();
    }

    public void resetAll() {
        quotas.clear();
    }

    private ClientQuota quota(String clientKey) {
        String key = (clientKey == null || clientKey.isBlank()) ? "global" : clientKey.trim();
        return quotas.computeIfAbsent(key, k -> new ClientQuota());
    }

    private static class ClientQuota {
        volatile String mode = MODE_OPERATOR;
        final AtomicInteger talkRemaining = new AtomicInteger(DEFAULT_TALK);
        final AtomicInteger godRemaining = new AtomicInteger(DEFAULT_GOD);
    }
}
