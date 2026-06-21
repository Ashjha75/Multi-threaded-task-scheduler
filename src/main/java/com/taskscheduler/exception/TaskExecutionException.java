package com.taskscheduler.exception;
import java.util.UUID;

public class TaskExecutionException extends TaskException {
    public TaskExecutionException(UUID taskId, String message) {
        super(taskId, message);
    }

    public TaskExecutionException(UUID taskId, String message, Throwable cause) {
        super(taskId, message, cause);
    }
}
