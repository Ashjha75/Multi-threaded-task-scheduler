package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

import java.io.IOException;

public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final int maxRetries;
    private final long baseDelayMs;
    private final double multiplier;

    public ExponentialBackoffRetryPolicy(int maxRetries, long baseDelayMs, double multiplier) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.multiplier = multiplier;
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
        // retryAttempt is 0-indexed (0 = first retry, 1 = second, etc.)
        double delayFactor = Math.pow(multiplier, retryAttempt);
        return (long) (baseDelayMs * delayFactor);
    }

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public String getName() {
        return "ExponentialBackoff(" + maxRetries + "x, base=" + baseDelayMs +
                "ms, mult=" + multiplier + ")";
    }

}
