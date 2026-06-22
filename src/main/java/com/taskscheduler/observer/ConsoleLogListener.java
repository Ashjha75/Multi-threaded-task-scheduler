package com.taskscheduler.observer;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleLogListener implements TaskEventListener {
    private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onTaskSubmitted(Task task) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [SUBMIT] " + task.getName() +
                " | Priority: " + task.getPriority());
    }

    @Override
    public void onTaskStarted(Task task) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [START]  " + task.getName());
    }

    @Override
    public void onTaskCompleted(Task task, TaskResult result) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [✓ OK]   " + task.getName() +
                " | " + result.getExecutionTimeMs() + "ms");
    }

    @Override
    public void onTaskFailed(Task task, Exception ex) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [✗ FAIL] " + task.getName() +
                " | " + ex.getMessage());
    }

    @Override
    public void onTaskRetrying(Task task, int retryCount) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [RETRY] " + task.getName() +
                " (attempt " + (retryCount + 1) + ")");
    }

    @Override
    public void onTaskCancelled(Task task) {
        String time = timeFormat.format(LocalDateTime.now());
        System.out.println("[" + time + "] [CANCEL] " + task.getName());
    }

}
