package com.taskscheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.model.TaskType;
import com.taskscheduler.repository.InMemoryTaskRepository;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.ExponentialBackoffRetryPolicy;
import com.taskscheduler.retry.FixedDelayRetryPolicy;
import com.taskscheduler.retry.NoRetryPolicy;
import com.taskscheduler.retry.RetryPolicy;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Fixed Delay Policy ===");
        RetryPolicy fixedPolicy = new FixedDelayRetryPolicy(5, 1000);
        for (int attempt = 0; attempt < 5; attempt++) {
            long delay = fixedPolicy.getDelayMs(attempt);
            System.out.println("Attempt " + (attempt + 1) + ": wait " + delay + "ms");
        }

        System.out.println("\n=== Exponential Backoff Policy ===");
        RetryPolicy exponentialPolicy = new ExponentialBackoffRetryPolicy(5, 500, 2.0);
        for (int attempt = 0; attempt < 5; attempt++) {
            long delay = exponentialPolicy.getDelayMs(attempt);
            System.out.println("Attempt " + (attempt + 1) + ": wait " + delay + "ms");
        }

        System.out.println("\n=== No Retry Policy ===");
        RetryPolicy noRetryPolicy = new NoRetryPolicy();
        System.out.println("Max retries: " + noRetryPolicy.getMaxRetries());

        // Simulate a task with retries
        System.out.println("\n=== Task Retry Decision ===");
        Task task = Task.builder()
                .name("Flaky network task")
                .taskType(TaskType.EMAIL)
                .priority(TaskPriority.MEDIUM)
                .maxRetries(3)
                .build();

        RetryPolicy policy = new ExponentialBackoffRetryPolicy(3, 500, 2.0);
        Exception ex = new java.io.IOException("Network timeout");

        System.out.println("Task retries so far: " + task.getRetryCount());
        System.out.println("Should retry? " + policy.shouldRetry(task, ex));

        // Simulate incrementing retries
        task.incrementRetry();
        System.out.println("\nAfter retry 1, task retries: " + task.getRetryCount());
        System.out.println("Should retry? " + policy.shouldRetry(task, ex));
        System.out.println("Next delay: " + policy.getDelayMs(1) + "ms");

        task.incrementRetry();
        task.incrementRetry();
        System.out.println("\nAfter retry 3, task retries: " + task.getRetryCount());
        System.out.println("Should retry? " + policy.shouldRetry(task, ex));
        System.out.println("We are working 🪄");
    }
}
