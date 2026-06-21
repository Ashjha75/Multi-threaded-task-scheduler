package com.taskscheduler.exception;

import java.util.UUID;

public class TaskException extends RuntimeException {
    private final UUID taskId;

    public TaskException(UUID taskId, String message) {
        super(message);
        this.taskId = taskId;
    }

    public TaskException(UUID taskId, String message, Throwable cause) {
        super(message, cause);
        this.taskId = taskId;
    }

    public UUID getTaskId() {
        return taskId;
    }
}
