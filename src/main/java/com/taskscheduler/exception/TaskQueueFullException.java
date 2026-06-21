package com.taskscheduler.exception;

public class TaskQueueFullException extends RuntimeException {

    private final int queueCapacity;
    private final int tasksAttempted;

    public TaskQueueFullException(String message, int queueCapacity, int tasksAttempted) {
        super(message);
        this.queueCapacity = queueCapacity;
        this.tasksAttempted = tasksAttempted;
    }

    public int getTasksAttempted() {
        return tasksAttempted;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

}
