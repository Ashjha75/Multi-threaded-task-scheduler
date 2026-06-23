package com.taskscheduler.scheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.model.Task;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.RetryPolicy;

import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskScheduler {
    private final RetryPolicy retryPolicy;
    private final TaskRepository repository;
    private final TaskEventPublisher eventPublisher;

    private final PriorityBlockingQueue<TaskCommand> taskQueue;
    private final ThreadPoolExecutor workerPool;
    private volatile boolean running = false;
    private final AtomicInteger activeCount = new AtomicInteger(0);

    public TaskScheduler(RetryPolicy retryPolicy, TaskRepository repository, TaskEventPublisher eventPublisher) {
        this.retryPolicy = retryPolicy;
        this.repository = repository;
        this.eventPublisher = eventPublisher;

        this.taskQueue = new PriorityBlockingQueue<>(
                11,  // initial capacity
                Comparator.comparing((TaskCommand cmd) -> cmd.getTask().getPriority().getWeight()).reversed() // reversed() so HIGH (weight=3) comes before LOW (weight=1)
        );

        // Build ThreadPoolExecutor with full constructor
        // This is what teaches  the parameters:
        // - corePoolSize: threads to keep alive even if idle
        // - maxPoolSize: max threads if queue overflows
        // - keepAliveTime/TimeUnit: how long to keep extra threads
        // - BlockingQueue: where tasks wait
        // - RejectedExecutionHandler: what to do if queue is full

        this.workerPool = new ThreadPoolExecutor(
                3,                                    // corePoolSize
                5,                                    // maxPoolSize (can grow to handle spikes)
                60,                                   // keepAliveTime
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),      // work queue for executor itself
                r -> new Thread(r, "TaskWorker-" + Thread.currentThread().getId()),
                new ThreadPoolExecutor.AbortPolicy() // throw if overloaded
        );
    }

    /**
     * Start worker threads. Each thread will block on queue.take() until work arrives.
     */
    public synchronized void start(int numWorkers) {
        if (numWorkers <= 0) {
            System.out.println("[Scheduler] Already running");
            return;
        }
        running = true;
        System.out.println("[Scheduler] Starting " + numWorkers + " worker threads");

        for (int i = 0; i < numWorkers; i++) {
            WorkerThread worker = new WorkerThread(
                    taskQueue,
                    repository,
                    retryPolicy,
                    eventPublisher,
                    this  // pass scheduler reference so worker can re-queue on retry
            );
            workerPool.execute(worker);
            activeCount.incrementAndGet();
        }
    }

    /**
     * Submit a task to the queue for execution.
     * If queue is full, throws TaskQueueFullException.
     */
    public void submit(TaskCommand command) throws InterruptedException {
        if (!running) {
            throw new IllegalStateException("Scheduler not running");
        }
        Task task = command.getTask();

        try {
            // put() blocks if queue is full, so we handle capacity limits gracefully
            taskQueue.put(command);
            System.out.println("[Scheduler] Queued task: " + task.getName() +
                    " | Priority: " + task.getPriority());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Graceful shutdown: stop accepting new tasks, but let in-flight tasks finish.
     */
    public synchronized void shutdown() {
        if (!running) {
            return;
        }

        running = false;
        System.out.println("[Scheduler] Initiating graceful shutdown...");
        workerPool.shutdown();

        try{
            if (!workerPool.awaitTermination(60, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
                System.out.println("[Scheduler] Forcefully shutting down workers");
            }
        }catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("[Scheduler] Shutdown complete");
    }

    /**
     * Immediate shutdown: interrupt everything.
     */
    public synchronized void shutdownNow() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[Scheduler] Initiating immediate shutdown...");
        workerPool.shutdownNow();
        System.out.println("[Scheduler] Shutdown complete");
    }

    public int getQueueSize() {
        return taskQueue.size();
    }

    public int getActiveWorkerCount() {
        return activeCount.get();
    }

    public boolean isRunning() {
        return running;
    }
}
