package com.taskscheduler.scheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.observer.TaskEventPublisher;
import com.taskscheduler.repository.TaskRepository;
import com.taskscheduler.retry.RetryPolicy;

import java.util.concurrent.PriorityBlockingQueue;

public class WorkerThread {
    public WorkerThread(PriorityBlockingQueue<TaskCommand> taskQueue, TaskRepository repository, RetryPolicy retryPolicy, TaskEventPublisher eventPublisher, TaskScheduler taskScheduler) {
    }
}
