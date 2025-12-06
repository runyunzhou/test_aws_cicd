package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报表控制器 - 场景A+B测试
 * 隐藏问题：内存泄漏 + 缺乏日志
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    // 模拟报表缓存 - 故意不清理，造成内存泄漏
    private static final Map<String, byte[]> reportCache = new ConcurrentHashMap<>();
    
    // 报表生成计数
    private static int generateCount = 0;

    /**
     * 生成报表
     * 隐藏问题：每次生成都缓存大量数据，不释放
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateReport(
            @RequestBody Map<String, Object> request) {
        
        String reportType = (String) request.getOrDefault("type", "summary");
        String dateRange = (String) request.getOrDefault("dateRange", "last_week");
        
        generateCount++;
        String reportId = "RPT-" + System.currentTimeMillis();
        
        // 根据报表类型决定"缓存"大小
        int cacheSizeMB;
        switch (reportType) {
            case "detailed":
                cacheSizeMB = 10;  // 详细报表缓存 10MB
                break;
            case "full":
                cacheSizeMB = 20;  // 完整报表缓存 20MB
                break;
            default:
                cacheSizeMB = 5;   // 默认缓存 5MB
        }
        
        // 故意的内存泄漏：缓存数据但永不清理
        // 注意：没有任何日志记录这个操作
        byte[] cacheData = new byte[cacheSizeMB * 1024 * 1024];
        Arrays.fill(cacheData, (byte) 1);
        reportCache.put(reportId, cacheData);
        
        // 模拟处理时间
        try {
            Thread.sleep(500 + new Random().nextInt(1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("type", reportType);
        result.put("dateRange", dateRange);
        result.put("status", "completed");
        result.put("generatedAt", new Date());
        
        return ResponseEntity.ok(ApiResponse.success(result, "Report generated successfully"));
    }

    /**
     * 获取报表列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listReports() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalReports", reportCache.size());
        result.put("reportIds", new ArrayList<>(reportCache.keySet()));
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取报表详情
     * 隐藏问题：如果报表不存在，静默返回空而不是报错
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReport(
            @PathVariable String reportId) {
        
        byte[] data = reportCache.get(reportId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("exists", data != null);
        result.put("sizeBytes", data != null ? data.length : 0);
        
        // 没有日志记录访问情况
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取报表统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long totalCacheSize = reportCache.values().stream()
                .mapToLong(arr -> arr.length)
                .sum();
        
        Runtime runtime = Runtime.getRuntime();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedReports", reportCache.size());
        stats.put("totalCacheSizeMB", totalCacheSize / (1024 * 1024));
        stats.put("generateCount", generateCount);
        stats.put("heapUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        stats.put("heapMaxMB", runtime.maxMemory() / (1024 * 1024));
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
