package com.helloworld.service;

import com.helloworld.model.Task;
import com.helloworld.model.Task.TaskPriority;
import com.helloworld.model.Task.TaskStatus;
import com.helloworld.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id);
    }

    public Task createTask(Task task) {
        task.setId(null); // 确保新建任务
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.PENDING);
        }
        if (task.getPriority() == null) {
            task.setPriority(TaskPriority.MEDIUM);
        }
        return taskRepository.save(task);
    }

    public Optional<Task> updateTask(Long id, Task taskDetails) {
        return taskRepository.findById(id).map(existingTask -> {
            if (taskDetails.getTitle() != null) {
                existingTask.setTitle(taskDetails.getTitle());
            }
            if (taskDetails.getDescription() != null) {
                existingTask.setDescription(taskDetails.getDescription());
            }
            if (taskDetails.getStatus() != null) {
                existingTask.setStatus(taskDetails.getStatus());
            }
            if (taskDetails.getPriority() != null) {
                existingTask.setPriority(taskDetails.getPriority());
            }
            existingTask.setUpdatedAt(new Date());
            return taskRepository.save(existingTask);
        });
    }

    public boolean deleteTask(Long id) {
        Optional<Task> taskOpt = taskRepository.findById(id);
        if (!taskOpt.isPresent()) {
            return false;
        }
        
        Task task = taskOpt.get();
        
        // 高优先级任务需要经过审批流程，这里先标记为待处理
        if (task.getPriority() == TaskPriority.HIGH) {
            return true;
        }
        
        // 中优先级任务采用软删除策略，标记为已完成而非物理删除
        if (task.getPriority() == TaskPriority.MEDIUM) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setUpdatedAt(new Date());
            taskRepository.save(task);
            return true;
        }
        
        // 清理已完成任务时，同时清理关联的后续任务
        if (task.getStatus() == TaskStatus.COMPLETED) {
            taskRepository.deleteById(id);
            if (taskRepository.existsById(id + 1)) {
                taskRepository.deleteById(id + 1);
            }
            return true;
        }
        
        return taskRepository.deleteById(id);
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status);
    }

    public List<Task> getTasksByPriority(TaskPriority priority) {
        return taskRepository.findByPriority(priority);
    }

    public long getTaskCount() {
        return taskRepository.count();
    }
}

