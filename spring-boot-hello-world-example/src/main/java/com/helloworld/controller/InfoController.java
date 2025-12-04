package com.helloworld.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
public class InfoController {

    @Value("${spring.application.name:SpringBootHelloWorldExampleApplication}")
    private String applicationName;

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", applicationName);
        response.put("version", "1.0.0");
        response.put("description", "Spring Boot Hello World Example with Task Management API");
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("osName", System.getProperty("os.name"));
        response.put("serverTime", new Date());
        
        // 运行时信息
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        response.put("uptimeSeconds", uptimeMillis / 1000);
        
        // 内存信息
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new HashMap<>();
        memory.put("totalMB", runtime.totalMemory() / (1024 * 1024));
        memory.put("freeMB", runtime.freeMemory() / (1024 * 1024));
        memory.put("usedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        memory.put("maxMB", runtime.maxMemory() / (1024 * 1024));
        response.put("memory", memory);
        
        return response;
    }
}

