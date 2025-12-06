package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 导出控制器
 * 
 * 问题场景：大对象临时分配，造成 GC 压力
 * 
 * 业务逻辑（看起来合理）：
 * - 导出数据时需要在内存中组装完整数据
 * - 导出完成后数据会被释放
 * 
 * 实际问题：
 * - 每次导出都分配大量临时对象
 * - 高并发或频繁调用时，GC 来不及回收
 * - 导致频繁 Full GC，响应时间上升
 * - 极端情况下，分配速度超过 GC 速度，也会 OOM
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    /**
     * 导出任务数据
     * 
     * @param format 导出格式 (csv/json/xlsx)
     * @param records 导出记录数
     */
    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportTasks(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "10000") int records) {
        
        long startTime = System.currentTimeMillis();
        
        // 根据记录数计算需要的内存
        // 假设每条记录约 1KB，加上格式化开销
        int dataSizeMB = Math.max(1, records / 1000);
        
        // 分配临时内存用于数据组装
        // 这些对象在方法结束后可以被 GC 回收
        byte[] exportBuffer = allocateExportBuffer(dataSizeMB);
        
        // 模拟数据处理（CPU 时间）
        processExportData(exportBuffer);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("format", format);
        result.put("records", records);
        result.put("dataSizeMB", dataSizeMB);
        result.put("processingTimeMs", elapsed);
        result.put("exportId", "EXP-" + UUID.randomUUID().toString().substring(0, 8));
        result.put("status", "completed");
        
        // exportBuffer 在这里离开作用域，可以被 GC 回收
        return ResponseEntity.ok(ApiResponse.success(result, "Export completed"));
    }

    /**
     * 导出报表数据（更大的数据量）
     * 
     * @param dateRange 日期范围 (last_week/last_month/last_quarter/last_year)
     */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportReports(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "last_month") String dateRange) {
        
        long startTime = System.currentTimeMillis();
        
        // 根据日期范围决定数据量
        int dataSizeMB;
        switch (dateRange) {
            case "last_year":
                dataSizeMB = 50;
                break;
            case "last_quarter":
                dataSizeMB = 30;
                break;
            case "last_month":
                dataSizeMB = 15;
                break;
            case "last_week":
            default:
                dataSizeMB = 5;
        }
        
        // 分配临时内存
        byte[] exportBuffer = allocateExportBuffer(dataSizeMB);
        
        // 模拟数据处理
        processExportData(exportBuffer);
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("format", format);
        result.put("dateRange", dateRange);
        result.put("dataSizeMB", dataSizeMB);
        result.put("processingTimeMs", elapsed);
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 批量导出（同时处理多种数据）
     * 
     * 这个接口会分配更多内存，更容易触发 GC
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchExport(
            @RequestBody Map<String, Object> request) {
        
        long startTime = System.currentTimeMillis();
        
        Object typesObj = request.getOrDefault("types", Arrays.asList("tasks", "reports"));
        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) typesObj;
        
        int totalSizeMB = 0;
        List<Map<String, Object>> exportResults = new ArrayList<>();
        
        for (String type : types) {
            // 每种类型分配独立的缓冲区
            int sizeMB = 10 + new Random().nextInt(10);
            byte[] buffer = allocateExportBuffer(sizeMB);
            processExportData(buffer);
            totalSizeMB += sizeMB;
            
            Map<String, Object> typeResult = new HashMap<>();
            typeResult.put("type", type);
            typeResult.put("sizeMB", sizeMB);
            typeResult.put("exportId", "BATCH-" + type.toUpperCase() + "-" + System.currentTimeMillis());
            exportResults.add(typeResult);
            
            // buffer 在每次循环结束后可以被 GC
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("exports", exportResults);
        result.put("totalSizeMB", totalSizeMB);
        result.put("processingTimeMs", elapsed);
        result.put("status", "completed");
        
        return ResponseEntity.ok(ApiResponse.success(result, "Batch export completed"));
    }

    /**
     * 压力测试接口 - 快速分配大量临时内存
     * 
     * @param sizeMB 每次分配的大小
     * @param iterations 分配次数
     */
    @GetMapping("/stress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stressTest(
            @RequestParam(defaultValue = "10") int sizeMB,
            @RequestParam(defaultValue = "5") int iterations) {
        
        long startTime = System.currentTimeMillis();
        int totalAllocated = 0;
        
        for (int i = 0; i < iterations; i++) {
            // 分配临时内存
            byte[] buffer = allocateExportBuffer(sizeMB);
            processExportData(buffer);
            totalAllocated += sizeMB;
            // buffer 可以被 GC，但如果分配太快，GC 来不及回收
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        // 获取 GC 信息
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        
        Map<String, Object> result = new HashMap<>();
        result.put("sizeMB", sizeMB);
        result.put("iterations", iterations);
        result.put("totalAllocatedMB", totalAllocated);
        result.put("processingTimeMs", elapsed);
        result.put("currentHeapUsedMB", heapUsed / (1024 * 1024));
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 分配导出缓冲区
     */
    private byte[] allocateExportBuffer(int sizeMB) {
        byte[] buffer = new byte[sizeMB * 1024 * 1024];
        // 填充数据，确保内存真的被分配（避免 JVM 优化）
        Random random = new Random();
        for (int i = 0; i < buffer.length; i += 4096) {
            buffer[i] = (byte) random.nextInt(256);
        }
        return buffer;
    }

    /**
     * 模拟数据处理
     */
    private void processExportData(byte[] buffer) {
        // 简单的数据处理，模拟 CPU 时间
        long sum = 0;
        for (int i = 0; i < buffer.length; i += 4096) {
            sum += buffer[i];
        }
        // 防止被优化掉
        if (sum == Long.MIN_VALUE) {
            System.out.println("Unlikely");
        }
    }
}
