package com.cybertown.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NewsService {

    private static final Pattern TITLE_PATTERN = Pattern.compile("<title><!\\[CDATA\\[(.*?)]]></title>|<title>(.*?)</title>");
    private static final String RSS_URL = "https://news.google.com/rss?hl=zh-CN&gl=CN&ceid=CN:zh-Hans";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private volatile List<String> cachedHeadlines = List.of(
            "全球AI产业竞争升级，多家科技公司宣布追加算力投资",
            "多地推进智慧城市改造，城市治理逐步进入实时决策阶段",
            "网络安全事件频发，企业级防护与应急响应需求上升"
    );
    private volatile LocalDateTime lastUpdated = LocalDateTime.now();

    @Scheduled(fixedRate = 600_000)
    public void refreshNews() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RSS_URL))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                log.warn("新闻抓取失败，status={}", response.statusCode());
                return;
            }
            List<String> parsed = parseRssTitles(response.body());
            if (!parsed.isEmpty()) {
                cachedHeadlines = parsed;
                lastUpdated = LocalDateTime.now();
            }
        } catch (Exception e) {
            log.warn("新闻刷新异常: {}", e.getMessage());
        }
    }

    public List<String> getTopHeadlines(int limit) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        List<String> source = cachedHeadlines;
        return source.subList(0, Math.min(source.size(), safeLimit));
    }

    public String getNewsBrief() {
        List<String> top = getTopHeadlines(3);
        return String.join("；", top);
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    private List<String> parseRssTitles(String xml) {
        Matcher matcher = TITLE_PATTERN.matcher(xml);
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (raw == null) continue;
            String title = raw.replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .trim();
            if (title.isBlank() || title.contains("Google 新闻")) {
                continue;
            }
            titles.add(title);
            if (titles.size() >= 10) {
                break;
            }
        }
        return titles;
    }
}
