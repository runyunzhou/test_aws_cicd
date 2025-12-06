package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 搜索控制器
 * 
 * 正常业务逻辑，没有刻意注入问题
 * 但当系统 GC 压力大时，响应时间会自然上升
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    // 模拟搜索数据
    private static final List<Map<String, Object>> MOCK_DATA = new ArrayList<>();
    
    static {
        // 初始化模拟数据
        String[] titles = {
                "完成 CI/CD 配置",
                "学习 AWS CloudFormation",
                "编写单元测试",
                "优化数据库查询",
                "部署生产环境",
                "代码审查",
                "性能优化",
                "安全加固",
                "监控告警配置",
                "日志采集设置"
        };
        
        String[] categories = {"开发", "运维", "测试", "安全"};
        String[] priorities = {"HIGH", "MEDIUM", "LOW"};
        
        Random random = new Random(42);
        for (int i = 0; i < titles.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i + 1);
            item.put("title", titles[i]);
            item.put("category", categories[random.nextInt(categories.length)]);
            item.put("priority", priorities[random.nextInt(priorities.length)]);
            item.put("score", random.nextDouble());
            MOCK_DATA.add(item);
        }
    }

    /**
     * 搜索功能
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        long startTime = System.currentTimeMillis();
        
        // 执行搜索
        List<Map<String, Object>> results = new ArrayList<>();
        String queryLower = query.toLowerCase();
        
        for (Map<String, Object> item : MOCK_DATA) {
            String title = ((String) item.get("title")).toLowerCase();
            if (title.contains(queryLower)) {
                results.add(item);
            }
        }
        
        // 如果没有匹配结果，返回所有数据
        if (results.isEmpty()) {
            results.addAll(MOCK_DATA);
        }
        
        // 分页
        int start = (page - 1) * size;
        int end = Math.min(start + size, results.size());
        List<Map<String, Object>> pagedResults = results.subList(
                Math.min(start, results.size()), 
                end
        );
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("query", query);
        response.put("page", page);
        response.put("size", size);
        response.put("total", results.size());
        response.put("results", pagedResults);
        response.put("tookMs", elapsed);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 搜索建议
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggest(
            @RequestParam String prefix) {
        
        List<String> suggestions = new ArrayList<>();
        String prefixLower = prefix.toLowerCase();
        
        for (Map<String, Object> item : MOCK_DATA) {
            String title = (String) item.get("title");
            if (title.toLowerCase().contains(prefixLower)) {
                suggestions.add(title);
            }
        }
        
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    /**
     * 获取搜索统计
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", MOCK_DATA.size());
        stats.put("categories", Arrays.asList("开发", "运维", "测试", "安全"));
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
