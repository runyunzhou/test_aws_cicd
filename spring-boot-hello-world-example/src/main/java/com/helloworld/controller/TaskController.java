package com.helloworld.controller;

import com.helloworld.model.ApiResponse;
import com.helloworld.model.Task;
import com.helloworld.model.Task.TaskPriority;
import com.helloworld.model.Task.TaskStatus;
import com.helloworld.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 获取所有任务
     * GET /api/tasks
     * GET /api/tasks?status=PENDING
     * GET /api/tasks?priority=HIGH
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Task>>> getAllTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority) {
        
        List<Task> tasks;
        if (status != null) {
            tasks = taskService.getTasksByStatus(status);
        } else if (priority != null) {
            tasks = taskService.getTasksByPriority(priority);
        } else {
            tasks = taskService.getAllTasks();
        }
        
        return ResponseEntity.ok(ApiResponse.success(tasks, tasks.size()));
    }

    /**
     * 获取单个任务
     * GET /api/tasks/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Task>> getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id)
                .map(task -> ResponseEntity.ok(ApiResponse.success(task)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Task not found with id: " + id)));
    }

    /**
     * 创建任务
     * POST /api/tasks
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Task>> createTask(@RequestBody Task task) {
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Task title is required"));
        }
        
        Task createdTask = taskService.createTask(task);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdTask, "Task created successfully"));
    }

    /**
     * 更新任务
     * PUT /api/tasks/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Task>> updateTask(
            @PathVariable Long id,
            @RequestBody Task taskDetails) {
        
        return taskService.updateTask(id, taskDetails)
                .map(task -> ResponseEntity.ok(ApiResponse.success(task, "Task updated successfully")))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Task not found with id: " + id)));
    }

    /**
     * 删除任务
     * DELETE /api/tasks/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        if (taskService.deleteTask(id)) {
            return ResponseEntity.ok(ApiResponse.success(null, "Task deleted successfully"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Task not found with id: " + id));
    }

    /**
     * 获取任务统计
     * GET /api/tasks/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<TaskStats>> getTaskStats() {
        TaskStats stats = new TaskStats();
        stats.total = taskService.getTaskCount();
        stats.pending = taskService.getTasksByStatus(TaskStatus.PENDING).size();
        stats.inProgress = taskService.getTasksByStatus(TaskStatus.IN_PROGRESS).size();
        stats.completed = taskService.getTasksByStatus(TaskStatus.COMPLETED).size();
        stats.highPriority = taskService.getTasksByPriority(TaskPriority.HIGH).size();
        stats.mediumPriority = taskService.getTasksByPriority(TaskPriority.MEDIUM).size();
        stats.lowPriority = taskService.getTasksByPriority(TaskPriority.LOW).size();
        
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // 内部类用于统计信息
    public static class TaskStats {
        public long total;
        public int pending;
        public int inProgress;
        public int completed;
        public int highPriority;
        public int mediumPriority;
        public int lowPriority;
    }
}

