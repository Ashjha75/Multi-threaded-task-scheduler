package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

import java.io.IOException;

public class FixedDelayRetryPolicy implements RetryPolicy {
    private final int maxRetries;
    private final long delayMs;

    /**
     * @param maxRetries How many times to retry (0 = don't retry, 1 = retry once, etc.)
     * @param delayMs    How many milliseconds to wait between attempts
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
        if (task.getRetryCount() >= maxRetries) {
            return false;
        }

        // Only retry on transient errors
        if (ex instanceof IOException || ex instanceof java.sql.SQLException) {
            return true;
        }

        // Don't retry on programmer errors
        if (ex instanceof NullPointerException ||
                ex instanceof IllegalArgumentException) {
            return false;
        }

        // Default: don't retry unknown exceptions
        return false;
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
