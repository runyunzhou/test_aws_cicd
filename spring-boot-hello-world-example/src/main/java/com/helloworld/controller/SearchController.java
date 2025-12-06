package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 搜索控制器 - 场景A测试
 * 隐藏问题：随机延迟 + 缺乏日志/指标
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final Random random = new Random();
    
    // 模拟搜索数据
    private static final List<String> MOCK_RESULTS = Arrays.asList(
            "完成 CI/CD 配置",
            "学习 AWS CloudFormation",
            "编写单元测试",
            "优化数据库查询",
            "部署生产环境",
            "代码审查",
            "性能优化",
            "安全加固"
    );

    /**
     * 搜索功能
     * 隐藏问题：
     * 1. 随机产生慢响应（无规律，难以复现）
     * 2. 没有记录响应时间指标
     * 3. 没有日志记录慢查询
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        int count = requestCount.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        // 随机延迟逻辑（没有日志记录）
        int delayMs = 0;
        if (count % 7 == 0) {
            // 每 7 次请求有一次 3-8 秒的延迟
            delayMs = 3000 + random.nextInt(5000);
        } else if (count % 13 == 0) {
            // 每 13 次请求有一次 10-20 秒的延迟
            delayMs = 10000 + random.nextInt(10000);
        } else if (random.nextInt(100) < 5) {
            // 5% 概率随机延迟 2-5 秒
            delayMs = 2000 + random.nextInt(3000);
        }
        
        if (delayMs > 0) {
            try {
                // 故意不记录这个延迟
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 模拟搜索结果
        List<Map<String, Object>> results = new ArrayList<>();
        for (String item : MOCK_RESULTS) {
            if (item.toLowerCase().contains(query.toLowerCase())) {
                Map<String, Object> result = new HashMap<>();
                result.put("title", item);
                result.put("score", random.nextDouble());
                results.add(result);
            }
        }
        
        // 如果没有匹配，返回随机结果
        if (results.isEmpty()) {
            for (int i = 0; i < Math.min(size, MOCK_RESULTS.size()); i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("title", MOCK_RESULTS.get(i));
                result.put("score", random.nextDouble());
                results.add(result);
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("page", page);
        response.put("size", size);
        response.put("total", results.size());
        response.put("results", results);
        response.put("took", elapsed + "ms");
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 搜索建议
     * 隐藏问题：偶发超时
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @RequestParam String prefix) {
        
        // 10% 概率超时
        if (random.nextInt(100) < 10) {
            try {
                Thread.sleep(5000 + random.nextInt(5000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        List<String> suggestions = new ArrayList<>();
        for (String item : MOCK_RESULTS) {
            if (item.toLowerCase().startsWith(prefix.toLowerCase())) {
                suggestions.add(item);
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }
}
