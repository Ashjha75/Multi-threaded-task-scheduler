package com.taskscheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.model.*;
import com.taskscheduler.observer.ConsoleLogListener;
import com.taskscheduler.observer.MetricsListener;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.InMemoryTaskRepository;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.ExponentialBackoffRetryPolicy;
import com.taskscheduler.retry.FixedDelayRetryPolicy;
import com.taskscheduler.retry.NoRetryPolicy;
import com.taskscheduler.retry.RetryPolicy;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Create publisher and listeners
        TaskEventPublisher publisher = new TaskEventPublisher();

        ConsoleLogListener consoleListener = new ConsoleLogListener();
        MetricsListener metricsListener = new MetricsListener();

        // Register listeners
        publisher.addListener(consoleListener);
        publisher.addListener(metricsListener);

        // Simulate task lifecycle
        Task task1 = Task.builder()
                .name("Email notification")
                .taskType(TaskType.EMAIL)
                .priority(TaskPriority.HIGH)
                .build();

        System.out.println("=== Simulating successful task ===");
        publisher.publishSubmitted(task1);
        publisher.publishStarted(task1);
        TaskResult result1 = new TaskResult(
                1200,
                "Email sent to 1,250 users",
                null,
                TaskStatus.SUCCESS,
                task1.getId()
        );
        publisher.publishCompleted(task1, result1);

        System.out.println("\n=== Simulating failed task with retry ===");
        Task task2 = Task.builder()
                .name("Database cleanup")
                .taskType(TaskType.DATA_CLEANUP)
                .priority(TaskPriority.MEDIUM)
                .build();

        publisher.publishSubmitted(task2);
        publisher.publishStarted(task2);
        publisher.publishFailed(task2, new Exception("Connection timeout"));
        publisher.publishRetrying(task2, 0);
        Thread.sleep(500);  // Simulate delay
        publisher.publishStarted(task2);
        TaskResult result2 = new TaskResult(
                800,
                "Cleaned 5,000 old records",
                null,
                TaskStatus.SUCCESS,
                task2.getId()

        );
        publisher.publishCompleted(task2, result2);

        // Print metrics
        metricsListener.printSummary();
       System.out.println("We are working 🪄");
    }
}
