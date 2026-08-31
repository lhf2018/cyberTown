package com.cybertown.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用可观测：次数、失败、耗时（内存聚合）
 */
@Service
public class AiMetricsService {

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong failCalls = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final Deque<Map<String, Object>> recent = new ArrayDeque<>();

    public void recordSuccess(String kind, String npcName, long latencyMs, String summary) {
        totalCalls.incrementAndGet();
        totalLatencyMs.addAndGet(Math.max(0, latencyMs));
        push("OK", kind, npcName, latencyMs, summary);
    }

    public void recordFailure(String kind, String npcName, long latencyMs, String error) {
        totalCalls.incrementAndGet();
        failCalls.incrementAndGet();
        totalLatencyMs.addAndGet(Math.max(0, latencyMs));
        push("FAIL", kind, npcName, latencyMs, error == null ? "unknown" : error);
    }

    private synchronized void push(String status, String kind, String npcName, long latencyMs, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("kind", kind);
        row.put("npcName", npcName);
        row.put("latencyMs", latencyMs);
        row.put("detail", detail == null ? "" : (detail.length() > 120 ? detail.substring(0, 119) + "…" : detail));
        row.put("at", LocalDateTime.now().toString());
        recent.addFirst(row);
        while (recent.size() > 40) {
            recent.removeLast();
        }
    }

    public Map<String, Object> snapshot() {
        long total = totalCalls.get();
        long fail = failCalls.get();
        long avg = total == 0 ? 0 : totalLatencyMs.get() / total;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalCalls", total);
        out.put("failCalls", fail);
        out.put("successCalls", Math.max(0, total - fail));
        out.put("failRate", total == 0 ? 0.0 : Math.round(fail * 1000.0 / total) / 10.0);
        out.put("avgLatencyMs", avg);
        out.put("recent", recent.toArray());
        out.put("updatedAt", LocalDateTime.now().toString());
        return out;
    }
}
