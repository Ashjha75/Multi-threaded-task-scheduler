package com.taskscheduler.scheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskResult;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.RetryPolicy;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WorkerThread implements Runnable {

    BlockingQueue<TaskCommand> queue;
    TaskRepository repository;
    RetryPolicy retryPolicy;
    TaskEventPublisher eventPublisher;
    TaskScheduler scheduler;
    private volatile boolean running = true;

    public WorkerThread(BlockingQueue<TaskCommand> queue,
                        TaskRepository repository,
                        RetryPolicy retryPolicy,
                        TaskEventPublisher eventPublisher,
                        TaskScheduler scheduler) {
        this.queue = queue;
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        System.out.println("[" + Thread.currentThread().getName() + "] Worker started");

        while (running) {
            try {
                // **THIS IS THE KEY LINE**
                // Block here until a task arrives. No CPU spinning, no polling.
                // Thread literally sleeps until queue.put() wakes it up.
                TaskCommand command = queue.take();
                executeCommand(command);
            } catch (InterruptedException e) {
                // Graceful shutdown signal
                Thread.currentThread().interrupt();
                running = false;
                System.out.println("[" + Thread.currentThread().getName() + "] Worker interrupted");
                break;
            } catch (Exception ex) {
                System.err.println("[" + Thread.currentThread().getName() + "] Unexpected error: " + ex.getMessage());
            }
        }
        System.out.println("[" + Thread.currentThread().getName() + "] Worker exiting");
    }

    /**
     * Execute a command and handle success/failure.
     */
    private void executeCommand(TaskCommand command) {
        Task task = command.getTask();
        long startTime = System.currentTimeMillis();

        try {
            // Try to transition task from PENDING to RUNNING
            // This CAS ensures only one thread executes this task
            boolean wasRunning = task.updateStatus(TaskStatus.PENDING, TaskStatus.RUNNING);
            if (!wasRunning) {
                System.out.println("[" + Thread.currentThread().getName() +
                        "] Task " + task.getId() + " is not PENDING, skipping");
                return;
            }

            task.setStartedAt(LocalDateTime.now());
            repository.save(task);
            eventPublisher.publishStarted(task);

            // Execute the actual work
            System.out.println("[" + Thread.currentThread().getName() +
                    "] Executing: " + task.getName());
            command.execute();
            long elapsed = System.currentTimeMillis() - startTime;
            task.setCompletedAt(LocalDateTime.now());
            task.updateStatus(TaskStatus.RUNNING, TaskStatus.SUCCESS);
            repository.update(task);
            TaskResult result = new TaskResult(
                    elapsed,
                    "Task completed successfully",
                    null,
                    TaskStatus.SUCCESS,
                    task.getId()
            );
            eventPublisher.publishCompleted(task, result);

            System.out.println("[" + Thread.currentThread().getName() +
                    "] ✓ Task " + task.getName() + " completed in " + elapsed + "ms");

        } catch (Exception ex) {
            handleFailure(task, ex, command);
        }
    }

    /**
     * Handle a failed task: decide whether to retry or mark as FAILED.
     */
    private void handleFailure(Task task, Exception ex, TaskCommand command) {
        System.out.println("[" + Thread.currentThread().getName() +
                "] ✗ Task " + task.getName() + " failed: " + ex.getMessage());

        eventPublisher.publishFailed(task, ex);
//now check retry policy
        if (retryPolicy.shouldRetry(task, ex)) {
            int currentRetry = task.getRetryCount();
            long delayMs = retryPolicy.getDelayMs(currentRetry);

            System.out.println("[" + Thread.currentThread().getName() +
                    "] Retrying task " + task.getName() +
                    " (attempt " + (currentRetry + 1) + ", delay=" + delayMs + "ms)");

            eventPublisher.publishRetrying(task, currentRetry);

            task.incrementRetry();
            task.setLastError(ex.getMessage());

            // Reset status back to PENDING so it can be picked up again
            task.updateStatus(TaskStatus.RUNNING, TaskStatus.PENDING);
            repository.update(task);
            // Re-queue after delay using a ScheduledExecutorService
            scheduleRetry(command, delayMs);

        } else {
            // No more retries, mark as FAILED permanently
            task.updateStatus(TaskStatus.RUNNING, TaskStatus.FAILED);
            task.setLastError(ex.getMessage());
            task.setCompletedAt(java.time.LocalDateTime.now());
            repository.update(task);

            System.out.println("[" + Thread.currentThread().getName() +
                    "] Task " + task.getName() + " FAILED (no more retries)");
        }
    }

    /**
     * Schedule a task to be retried after a delay.
     * Uses a one-off ScheduledExecutorService for simplicity.
     */
    private void scheduleRetry(TaskCommand command, long delayMs) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            try {
                // Re-queue the command
                this.scheduler.submit(command);
            } catch (InterruptedException e) {
                System.err.println("Failed to re-queue task for retry: " + e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                // Shut down the scheduler (it's a one-off)
                scheduler.shutdown();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    public void stop() {
        running = false;
    }
}
