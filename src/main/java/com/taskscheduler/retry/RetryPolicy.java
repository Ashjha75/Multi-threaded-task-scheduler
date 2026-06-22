package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

public interface RetryPolicy {
    boolean shouldRetry(Task task, Exception ex);
    long getDelayMs(int retryAttempt);
    int getMaxRetries();
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
