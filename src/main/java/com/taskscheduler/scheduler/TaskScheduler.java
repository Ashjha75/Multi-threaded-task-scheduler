package com.taskscheduler.scheduler;

import com.taskscheduler.command.TaskCommand;
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
t
}
