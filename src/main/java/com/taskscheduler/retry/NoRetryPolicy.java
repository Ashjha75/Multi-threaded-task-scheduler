package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

public class NoRetryPolicy implements RetryPolicy {
    @Override
    public boolean shouldRetry(Task task, Exception ex) {
        // Always return false — no retries ever
        return false;
    }

    @Override
    public long getDelayMs(int retryAttempt) {
        // Never called, but provide a sensible default
        return 0;
    }

    @Override
    public int getMaxRetries() {
        return 0;
    }
}
