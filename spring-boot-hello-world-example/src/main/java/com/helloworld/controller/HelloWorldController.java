package com.helloworld.controller;

import com.helloworld.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {

    @Autowired
    private TaskService taskService;

    @GetMapping("/")
    public String hello() {
        long taskCount = taskService.getTaskCount();
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<title>Task Manager API</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }");
        html.append(".container { background: white; padding: 40px; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }");
        html.append("h1 { color: #667eea; margin-bottom: 10px; }");
        html.append(".success { color: #28a745; font-size: 18px; margin-bottom: 30px; }");
        html.append(".stats { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }");
        html.append(".stats h3 { margin-top: 0; color: #495057; }");
        html.append(".api-section { margin-top: 30px; }");
        html.append(".api-section h3 { color: #495057; border-bottom: 2px solid #667eea; padding-bottom: 10px; }");
        html.append(".endpoint { background: #f1f3f4; padding: 12px 15px; margin: 10px 0; border-radius: 6px; font-family: 'Consolas', monospace; }");
        html.append(".method { display: inline-block; padding: 3px 8px; border-radius: 4px; color: white; font-size: 12px; font-weight: bold; margin-right: 10px; }");
        html.append(".get { background: #28a745; }");
        html.append(".post { background: #007bff; }");
        html.append(".put { background: #ffc107; color: #333; }");
        html.append(".delete { background: #dc3545; }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<div class='container'>");
        html.append("<h1>🚀 Task Manager API</h1>");
        html.append("<p class='success'>✅ Congratulations! You have successfully deployed the Spring Boot Application.</p>");
        
        // 统计信息
        html.append("<div class='stats'>");
        html.append("<h3>📊 Current Status</h3>");
        html.append("<p><strong>Total Tasks:</strong> ").append(taskCount).append("</p>");
        html.append("<p><strong>Server Status:</strong> <span style='color: #28a745;'>● Running</span></p>");
        html.append("</div>");
        
        // API 文档
        html.append("<div class='api-section'>");
        html.append("<h3>📖 Available API Endpoints</h3>");
        
        html.append("<div class='endpoint'><span class='method get'>GET</span>/health - Health check</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/info - Application info</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/api/tasks - Get all tasks</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/api/tasks/{id} - Get task by ID</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/api/tasks/stats - Get task statistics</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/api/tasks?status=PENDING - Filter by status</div>");
        html.append("<div class='endpoint'><span class='method get'>GET</span>/api/tasks?priority=HIGH - Filter by priority</div>");
        html.append("<div class='endpoint'><span class='method post'>POST</span>/api/tasks - Create new task</div>");
        html.append("<div class='endpoint'><span class='method put'>PUT</span>/api/tasks/{id} - Update task</div>");
        html.append("<div class='endpoint'><span class='method delete'>DELETE</span>/api/tasks/{id} - Delete task</div>");
        
        html.append("</div>");
        
        html.append("<p style='margin-top: 30px; color: #6c757d; font-size: 14px;'>Deployed via GitHub Actions + AWS CodeDeploy</p>");
        html.append("</div>");
        html.append("</body></html>");
        
        return html.toString();
    }
}
