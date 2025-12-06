package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 导出控制器 - 场景B测试
 * 隐藏问题：大内存分配
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final Random random = new Random();

    /**
     * 导出任务数据
     * 隐藏问题：
     * 1. 根据参数可能分配大量内存
     * 2. 没有限制导出大小
     * 3. 没有异步处理大数据导出
     */
    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportTasks(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "false") boolean includeHistory,
            @RequestParam(defaultValue = "1000") int limit) {
        
        // 根据参数决定内存使用量
        int dataSizeMB;
        if (includeHistory) {
            // 包含历史数据时，分配大量内存
            dataSizeMB = Math.min(limit / 10, 100);  // 最多 100MB
        } else {
            dataSizeMB = Math.min(limit / 100, 20);  // 最多 20MB
        }
        
        // 分配内存（模拟数据加载）
        // 注意：这里的内存在方法结束后会被 GC，但如果并发请求多，会瞬间占用大量内存
        byte[] exportData = new byte[dataSizeMB * 1024 * 1024];
        Arrays.fill(exportData, (byte) 'X');
        
        // 模拟导出处理时间
        try {
            Thread.sleep(1000 + dataSizeMB * 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("format", format);
        result.put("exportedRecords", limit);
        result.put("includeHistory", includeHistory);
        result.put("dataSizeMB", dataSizeMB);
        result.put("exportId", "EXP-" + System.currentTimeMillis());
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result, "Export completed"));
    }

    /**
     * 导出报表数据
     * 隐藏问题：可能触发大内存分配
     */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportReports(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = "last_month") String dateRange) {
        
        // 根据日期范围决定数据量
        int dataSizeMB;
        switch (dateRange) {
            case "last_year":
                dataSizeMB = 80;
                break;
            case "last_quarter":
                dataSizeMB = 40;
                break;
            case "last_month":
                dataSizeMB = 15;
                break;
            default:
                dataSizeMB = 5;
        }
        
        // 分配内存
        byte[] exportData = new byte[dataSizeMB * 1024 * 1024];
        Arrays.fill(exportData, (byte) 'R');
        
        try {
            Thread.sleep(500 + dataSizeMB * 30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("format", format);
        result.put("dateRange", dateRange);
        result.put("dataSizeMB", dataSizeMB);
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 批量导出 - 场景C测试
     * 隐藏问题：内存泄漏 + 慢响应 组合
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchExport(
            @RequestBody Map<String, Object> request) {
        
        // 获取导出类型列表
        Object typesObj = request.getOrDefault("types", Arrays.asList("tasks", "reports"));
        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) typesObj;
        
        int totalSizeMB = 0;
        List<String> exportIds = new ArrayList<>();
        
        for (String type : types) {
            // 每种类型分配内存（模拟数据处理）
            int sizeMB = 10 + random.nextInt(20);
            byte[] data = new byte[sizeMB * 1024 * 1024];
            Arrays.fill(data, (byte) 'B');  // 使用数据避免被优化掉
            totalSizeMB += sizeMB;
            
            // 添加随机延迟（不记录）
            if (random.nextInt(100) < 30) {
                try {
                    Thread.sleep(3000 + random.nextInt(5000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            exportIds.add("BATCH-" + type.toUpperCase() + "-" + System.currentTimeMillis());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("exportIds", exportIds);
        result.put("totalSizeMB", totalSizeMB);
        result.put("types", types);
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result, "Batch export completed"));
    }
}
