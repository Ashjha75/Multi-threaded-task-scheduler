package com.taskscheduler;

import com.taskscheduler.cli.TaskCLI;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.observer.ConsoleLogListener;
import com.taskscheduler.observer.MetricsListener;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.InMemoryTaskRepository;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.ExponentialBackoffRetryPolicy;
import com.taskscheduler.retry.RetryPolicy;
import com.taskscheduler.scheduler.TaskScheduler;
import com.taskscheduler.service.TaskService;

/**
 * Application entry point. This is the ONLY place where we instantiate everything.
 * <p>
 * Design principle: Dependency Injection
 * - Each class receives its dependencies via constructor
 * - No class creates its own dependencies (except here in Main)
 * - Easy to test: mock dependencies and inject them
 * - Easy to refactor: change implementations without touching other code
 * <p>
 * Example: Want to swap in-memory storage for database?
 * Old: repository = new InMemoryTaskRepository();
 * New: repository = new JdbcTaskRepository(dataSource);
 * Everything else stays the same.
 */
public class Main {
    public static void main(String[] args) {
        // Layer 1: Data storage
        TaskRepository repository = new InMemoryTaskRepository();

        // Layer 2: Event publishing
        TaskEventPublisher eventPublisher = new TaskEventPublisher();
        eventPublisher.addListener(new ConsoleLogListener());
        eventPublisher.addListener(new MetricsListener());

        // Layer 3: Retry strategy
        TaskService service = getTaskService(repository, eventPublisher);

        // Layer 6: User interface
        TaskCLI cli = new TaskCLI(service);
        cli.start();
    }

    private static TaskService getTaskService(TaskRepository repository, TaskEventPublisher eventPublisher) {
        RetryPolicy retryPolicy = new ExponentialBackoffRetryPolicy(
                3,      // maxRetries
                500,    // baseDelayMs
                2.0     // multiplier (500ms → 1s → 2s → 4s)
        );

        // Layer 4: Task scheduling engine
        TaskScheduler scheduler = new TaskScheduler(retryPolicy, repository, eventPublisher);
        scheduler.start(3);  // 3 worker threads

        // Layer 5: Facade service
        return new TaskService(
                repository,
                scheduler,
                eventPublisher,
                new TaskFactory()
        );
    }
}