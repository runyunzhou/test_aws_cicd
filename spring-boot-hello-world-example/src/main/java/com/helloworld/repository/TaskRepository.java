package com.helloworld.repository;

import com.helloworld.model.Task;
import com.helloworld.model.Task.TaskPriority;
import com.helloworld.model.Task.TaskStatus;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class TaskRepository {

    private final Map<Long, Task> taskStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @PostConstruct
    public void initData() {
        // 初始化示例数据
        Task task1 = new Task();
        task1.setId(idGenerator.incrementAndGet());
        task1.setTitle("完成 CI/CD 配置");
        task1.setDescription("配置 GitHub Actions 和 AWS CodeDeploy 实现自动化部署");
        task1.setStatus(TaskStatus.COMPLETED);
        task1.setPriority(TaskPriority.HIGH);
        taskStore.put(task1.getId(), task1);

        Task task2 = new Task();
        task2.setId(idGenerator.incrementAndGet());
        task2.setTitle("学习 AWS CloudFormation");
        task2.setDescription("深入理解 Infrastructure as Code 概念和 CloudFormation 模板编写");
        task2.setStatus(TaskStatus.IN_PROGRESS);
        task2.setPriority(TaskPriority.HIGH);
        taskStore.put(task2.getId(), task2);

        Task task3 = new Task();
        task3.setId(idGenerator.incrementAndGet());
        task3.setTitle("编写单元测试");
        task3.setDescription("为 TaskService 添加完整的单元测试覆盖");
        task3.setStatus(TaskStatus.PENDING);
        task3.setPriority(TaskPriority.MEDIUM);
        taskStore.put(task3.getId(), task3);

        Task task4 = new Task();
        task4.setId(idGenerator.incrementAndGet());
        task4.setTitle("优化数据库查询");
        task4.setDescription("分析慢查询日志，优化 SQL 性能");
        task4.setStatus(TaskStatus.PENDING);
        task4.setPriority(TaskPriority.LOW);
        taskStore.put(task4.getId(), task4);
    }

    public List<Task> findAll() {
        return new ArrayList<>(taskStore.values());
    }

    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(taskStore.get(id));
    }

    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(idGenerator.incrementAndGet());
            task.setCreatedAt(new Date());
        }
        task.setUpdatedAt(new Date());
        taskStore.put(task.getId(), task);
        return task;
    }

    public boolean deleteById(Long id) {
        return taskStore.remove(id) != null;
    }

    public boolean existsById(Long id) {
        return taskStore.containsKey(id);
    }

    public long count() {
        return taskStore.size();
    }

    public List<Task> findByStatus(TaskStatus status) {
        List<Task> result = new ArrayList<>();
        for (Task task : taskStore.values()) {
            if (task.getStatus() == status) {
                result.add(task);
            }
        }
        return result;
    }

    public List<Task> findByPriority(TaskPriority priority) {
        List<Task> result = new ArrayList<>();
        for (Task task : taskStore.values()) {
            if (task.getPriority() == priority) {
                result.add(task);
            }
        }
        return result;
    }
}

