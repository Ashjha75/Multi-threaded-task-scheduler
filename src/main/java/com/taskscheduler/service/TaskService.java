package com.taskscheduler.service;


import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.exception.TaskNotFoundException;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.model.TaskType;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.scheduler.TaskScheduler;

import java.util.*;

/**
 * The facade layer. This is the ONLY class the CLI talks to.
 * It orchestrates the scheduler, repository, publisher, and factory.
 * <p>
 * Benefits:
 * - CLI doesn't need to know about TaskScheduler, Repository, etc.
 * - Easy to test: mock TaskService and test CLI independently
 * - Easy to change implementation: swap the scheduler without touching CLI
 */
public class TaskService {
    private final TaskRepository repository;
    private final TaskScheduler scheduler;
    private final TaskEventPublisher eventPublisher;
    private final TaskFactory factory;

    public TaskService(TaskRepository repository, TaskScheduler scheduler, TaskEventPublisher eventPublisher, TaskFactory factory) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.eventPublisher = eventPublisher;
        this.factory = factory;
    }

    /**
     * Submit a new task to the scheduler.
     *
     * @param name     Task name (user-friendly)
     * @param type     What kind of work (EMAIL, FILE_PROCESSING, etc.)
     * @param priority How urgent (HIGH, MEDIUM, LOW)
     * @param params   Task-specific parameters (recipient, filePath, etc.)
     * @return UUID of the created task
     */

    public UUID submitTask(String name, TaskType type, TaskPriority priority, Map<String, Object> params) {
        // Create the task
        Task task = Task.builder()
                .name(name)
                .taskType(type)
                .priority(priority)
                .maxRetries(3)
                .build();

        // Persist it
        repository.save(task);

        // Publish event so listeners know
        eventPublisher.publishSubmitted(task);
        // Create command from factory
        TaskCommand command = factory.createCommand(task, params);

        // Submit to scheduler (will throw if queue is full)
        try {
            scheduler.submit(command);
            System.out.println("✓ Task submitted: " + task.getId() + " (" + name + ")");
            return task.getId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to submit task", e);
        }
    }

    /**
     * Get a task by ID.
     */
    public Optional<Task> getTask(UUID id) {
        return repository.findById(id);
    }

    /**
     * List all tasks.
     */
    public List<Task> getAllTasks() {
        return repository.findAll();
    }

    /**
     * List tasks filtered by status.
     */
    public List<Task> getTasksByStatus(TaskStatus status) {
        return repository.findByStatus(status);
    }

    /**
     * List tasks filtered by priority.
     */
    public List<Task> getTasksByPriority(TaskPriority priority) {
        return repository.findByPriority(priority);
    }

    /**
     * Retry a failed task.
     * Throws if task doesn't exist or is not in a failed state.
     */
    public void retryTask(UUID id) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        if (task.getStatus() != TaskStatus.FAILED) {
            throw new IllegalStateException(
                    "Can only retry FAILED tasks. This task is: " + task.getStatus());
        }
        // Reset for retry
        task.updateStatus(TaskStatus.FAILED, TaskStatus.PENDING);
        task.setLastError(null);
        repository.update(task);
        // Re-queue
        Map<String, Object> params = new HashMap<>();
        TaskCommand command = factory.createCommand(task, params);
        try {
            scheduler.submit(command);
            System.out.println("✓ Task retrying: " + id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to retry task", e);
        }
    }

    /**
     * Cancel a task if it's still pending.
     */
    public boolean cancelTask(UUID id) {
        return repository.findById(id).map(task -> {
            if (task.getStatus() == TaskStatus.PENDING) {
                task.updateStatus(TaskStatus.PENDING, TaskStatus.CANCELLED);
                repository.update(task);
                eventPublisher.publishCancelled(task);
                System.out.println("✓ Task cancelled: " + id);
                return true;
            } else {
                System.out.println("Task is not in pending state");
                return false;
            }
        }).orElse(false);
    }

    /**
     * Get metrics (success rate, average execution time, etc.).
     */
    public Map<String, Object> getMetrics() {
        List<Task> tasks = repository.findAll();
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.SUCCESS)
                .count();
        long failed = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .count();
        long pending = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .count();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total", tasks.size());
        metrics.put("success", completed);
        metrics.put("failed", failed);
        metrics.put("pending", pending);
        metrics.put("successRate", tasks.isEmpty() ? 0.0 :
                (double) completed / tasks.size() * 100);
        metrics.put("queueSize", scheduler.getQueueSize());
        metrics.put("activeWorkers", scheduler.getActiveWorkerCount());
        return metrics;
    }

    /**
     * Graceful shutdown.
     */
    public void shutdown() {
        System.out.println("\nShutting down...");
        scheduler.shutdown();
        System.out.println("Goodbye!");
    }

}
