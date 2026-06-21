package com.taskscheduler.exception;

public class TaskNotFoundException extends TaskException {
    public TaskNotFoundException(UUID taskId) {
        super(taskId, "Task not found: " + taskId);
    }

    public TaskNotFoundException(UUID taskId, String message) {
        super(taskId, message);
    }
}
