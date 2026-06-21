package com.taskscheduler.model;

import java.util.UUID;

public class TaskResult {
     private  final UUID taskId;
     private  final  TaskStatus taskStatus;
     private  final  String output;
     private  final  String errorMessage;
     private  final long executionTimeMs;

    public TaskResult(long executionTimeMs, String errorMessage, String output, TaskStatus taskStatus, UUID taskId) {
        this.executionTimeMs = executionTimeMs;
        this.errorMessage = errorMessage;
        this.output = output;
        this.taskStatus = taskStatus;
        this.taskId = taskId;
    }


    public UUID getTaskId() {
        return taskId;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
}
