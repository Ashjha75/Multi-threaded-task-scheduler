package com.taskscheduler.model;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Task {
    private final UUID id;
    private final String name;
    private final TaskType taskType;
    private final TaskPriority priority;
    private final AtomicReference<TaskStatus> status;
    private final AtomicInteger retryCount;
    private final int maxRetries;
    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String lastError;

    private Task(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.taskType = builder.taskType;
        this.priority = builder.priority;
        this.status = builder.status;
        this.retryCount = builder.retryCount;
        this.maxRetries = builder.maxRetries;
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.lastError = builder.lastError;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Only a CAS-style transition — caller must know the expected current state.
    // Returns false if another thread already moved the task elsewhere.
    public boolean updateStatus(TaskStatus expected, TaskStatus newStatus) {
        return status.compareAndSet(expected, newStatus);
    }

    public int incrementRetry() {
        return retryCount.incrementAndGet();
    }

    public boolean canRetry() {
        return retryCount.get() < maxRetries;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public TaskType getTaskType() { return taskType; }
    public TaskPriority getPriority() { return priority; }
    public TaskStatus getStatus() { return status.get(); }
    public int getRetryCount() { return retryCount.get(); }
    public int getMaxRetries() { return maxRetries; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getLastError() { return lastError; }

    public static class Builder {
        private UUID id = UUID.randomUUID();
        private String name;
        private TaskType taskType;
        private TaskPriority priority;
        private AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.PENDING);
        private AtomicInteger retryCount = new AtomicInteger(0);
        private int maxRetries = 3;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String lastError;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder taskType(TaskType taskType) { this.taskType = taskType; return this; }
        public Builder priority(TaskPriority priority) { this.priority = priority; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        public Task build() {
            if (name == null) throw new IllegalStateException("name is required");
            if (taskType == null) throw new IllegalStateException("taskType is required");
            if (priority == null) throw new IllegalStateException("priority is required");
            return new Task(this);
        }
    }
}