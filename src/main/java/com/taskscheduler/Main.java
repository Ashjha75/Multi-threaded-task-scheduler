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
import com.taskscheduler.scheduler.TaskScheduler;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Set up components
        InMemoryTaskRepository repo = new InMemoryTaskRepository();
        TaskEventPublisher publisher = new TaskEventPublisher();
        publisher.addListener(new ConsoleLogListener());

        RetryPolicy retryPolicy = new ExponentialBackoffRetryPolicy(2, 500, 2.0);
        TaskScheduler scheduler = new TaskScheduler(retryPolicy, repo, publisher);
        TaskFactory factory = new TaskFactory();

        // Start scheduler with 3 worker threads
        scheduler.start(3);

        // Submit some tasks
        System.out.println("=== Submitting tasks ===");
        for (int i = 1; i <= 5; i++) {
            Task task = Task.builder()
                    .name("Task " + i)
                    .taskType(i % 2 == 0 ? TaskType.EMAIL : TaskType.FILE_PROCESSING)
                    .priority(i == 1 ? TaskPriority.HIGH : TaskPriority.MEDIUM)
                    .maxRetries(2)
                    .build();

            repo.save(task);
            Map<String, Object> params = new HashMap<>();
            params.put("filePath", "/tmp/file" + i);
            params.put("recipient", "user" + i + "@example.com");

            TaskCommand cmd = factory.createCommand(task, params);
            scheduler.submit(cmd);
        }

        // Let them execute
        System.out.println("\n=== Waiting for execution ===");
        Thread.sleep(5000);

        // Graceful shutdown
        System.out.println("\n=== Shutting down ===");
        scheduler.shutdown();

        // Print final status
        System.out.println("\n=== Final task statuses ===");
        repo.printAll();
       System.out.println("We are working 🪄");
    }
}
