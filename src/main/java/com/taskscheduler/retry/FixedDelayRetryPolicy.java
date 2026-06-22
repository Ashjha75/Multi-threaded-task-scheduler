package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

public class FixedDelayRetryPolicy implements RetryPolicy {
    private final int maxRetries;
    private final long delayMs;

    /**
     * @param maxRetries   How many times to retry (0 = don't retry, 1 = retry once, etc.)
     * @param delayMs      How many milliseconds to wait between attempts
     */
    public FixedDelayRetryPolicy(int maxRetries, long delayMs) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
        this.maxRetries = maxRetries;
        this.delayMs = delayMs;
    }
    @Override
    public boolean shouldRetry(Task task, Exception ex) {
        int currentRetries=task.getRetryCount();
        return currentRetries<maxRetries;
    }

    @Override
    public long getDelayMs(int retryAttempt) {
        return delayMs;
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }
    @Override
    public String getName() {
        return "FixedDelay(" + maxRetries + "x, " + delayMs + "ms)";
    }
}
