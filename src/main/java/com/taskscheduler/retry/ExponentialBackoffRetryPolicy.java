package com.taskscheduler.retry;

import com.taskscheduler.model.Task;

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
        int currentRetries = task.getRetryCount();
        return currentRetries < maxRetries;
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
