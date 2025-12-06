package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报表控制器
 * 
 * 问题场景：报表缓存不释放，造成内存泄漏
 * 
 * 业务逻辑（看起来合理）：
 * - 生成报表后缓存起来，方便用户多次查看
 * - 缓存没有过期机制，也没有容量限制
 * 
 * 实际问题：
 * - 每次生成报表都往静态 Map 里添加数据
 * - 数据永远不会被 GC 回收
 * - 最终导致 OOM
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    /**
     * 报表缓存 - 常驻内存，不会被 GC
     * 问题：没有过期机制，没有容量限制
     */
    private static final Map<String, byte[]> reportCache = new ConcurrentHashMap<>();
    
    /**
     * 报表元数据
     */
    private static final Map<String, Map<String, Object>> reportMetadata = new ConcurrentHashMap<>();

    /**
     * 生成报表
     * 
     * @param request 包含 type (summary/detailed/full) 和 dateRange
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateReport(
            @RequestBody Map<String, Object> request) {
        
        String reportType = (String) request.getOrDefault("type", "summary");
        String dateRange = (String) request.getOrDefault("dateRange", "last_week");
        
        String reportId = "RPT-" + UUID.randomUUID().toString().substring(0, 8);
        
        // 根据报表类型决定数据大小（模拟真实业务场景）
        int dataSizeMB;
        switch (reportType) {
            case "detailed":
                dataSizeMB = 10;  // 详细报表 10MB
                break;
            case "full":
                dataSizeMB = 20;  // 完整报表 20MB
                break;
            case "summary":
            default:
                dataSizeMB = 5;   // 摘要报表 5MB
        }
        
        // 生成报表数据
        byte[] reportData = generateReportData(dataSizeMB);
        
        // 缓存报表数据 - 问题点：永不过期，永不清理
        reportCache.put(reportId, reportData);
        
        // 保存元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", reportType);
        metadata.put("dateRange", dateRange);
        metadata.put("sizeMB", dataSizeMB);
        metadata.put("createdAt", new Date());
        reportMetadata.put(reportId, metadata);
        
        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("type", reportType);
        result.put("dateRange", dateRange);
        result.put("sizeMB", dataSizeMB);
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result, "Report generated successfully"));
    }

    /**
     * 生成报表数据（模拟复杂计算）
     */
    private byte[] generateReportData(int sizeMB) {
        byte[] data = new byte[sizeMB * 1024 * 1024];
        // 填充数据，模拟真实报表内容
        Random random = new Random();
        for (int i = 0; i < data.length; i += 1024) {
            data[i] = (byte) random.nextInt(256);
        }
        return data;
    }

    /**
     * 获取报表列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listReports() {
        List<Map<String, Object>> reports = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : reportMetadata.entrySet()) {
            Map<String, Object> report = new HashMap<>(entry.getValue());
            report.put("reportId", entry.getKey());
            reports.add(report);
        }
        
        return ResponseEntity.ok(ApiResponse.success(reports, reports.size()));
    }

    /**
     * 获取报表详情
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReport(
            @PathVariable String reportId) {
        
        byte[] data = reportCache.get(reportId);
        Map<String, Object> metadata = reportMetadata.get(reportId);
        
        if (data == null || metadata == null) {
            return ResponseEntity.ok(ApiResponse.error("Report not found: " + reportId));
        }
        
        Map<String, Object> result = new HashMap<>(metadata);
        result.put("reportId", reportId);
        result.put("sizeBytes", data.length);
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取缓存和内存统计
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        // 计算缓存总大小
        long totalCacheSize = reportCache.values().stream()
                .mapToLong(arr -> arr.length)
                .sum();
        
        // 获取 JVM 内存信息
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedReports", reportCache.size());
        stats.put("cacheSizeMB", totalCacheSize / (1024 * 1024));
        stats.put("heapUsedMB", heapUsed / (1024 * 1024));
        stats.put("heapMaxMB", heapMax / (1024 * 1024));
        stats.put("heapUsagePercent", (int) (heapUsed * 100 / heapMax));
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 清理缓存（用于测试重置）
     */
    @DeleteMapping("/cache")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearCache() {
        int clearedCount = reportCache.size();
        reportCache.clear();
        reportMetadata.clear();
        
        // 建议 GC（不保证立即执行）
        System.gc();
        
        Map<String, Object> result = new HashMap<>();
        result.put("clearedReports", clearedCount);
        result.put("message", "Cache cleared, GC suggested");
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
