package com.taskscheduler.observer;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsListener implements TaskEventListener {
    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicInteger cancelledCount = new AtomicInteger(0);
    private final AtomicLong totalExecutionMs = new AtomicLong(0);


    @Override
    public void onTaskSubmitted(Task task) {
        submittedCount.incrementAndGet();
    }

    @Override
    public void onTaskCompleted(Task task, TaskResult result) {
        completedCount.incrementAndGet();
        totalExecutionMs.addAndGet(result.getExecutionTimeMs());
    }

    @Override
    public void onTaskFailed(Task task, Exception ex) {
        failedCount.incrementAndGet();
    }

    @Override
    public void onTaskRetrying(Task task, int retryCount) {
        this.retryCount.incrementAndGet();
    }

    @Override
    public void onTaskCancelled(Task task) {
        cancelledCount.incrementAndGet();
    }

    // Getters for metrics
    public int getSubmittedCount() {
        return submittedCount.get();
    }

    public int getCompletedCount() {
        return completedCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getCancelledCount() {
        return cancelledCount.get();
    }

    public double getSuccessRate() {
        int completed = completedCount.get();
        int submitted = submittedCount.get();
        if (submitted == 0) return 0.0;
        return (double) completed / submitted * 100;
    }

    public double getAverageExecutionMs() {
        int completed = completedCount.get();
        if (completed == 0) return 0.0;
        return (double) totalExecutionMs.get() / completed;
    }

    /**
     * Print a summary of metrics. Useful for debugging.
     */
    public void printSummary() {
        System.out.println("\n========== METRICS ==========");
        System.out.println("Submitted:     " + getSubmittedCount());
        System.out.println("Completed:     " + getCompletedCount());
        System.out.println("Failed:        " + getFailedCount());
        System.out.println("Retries:       " + getRetryCount());
        System.out.println("Cancelled:     " + getCancelledCount());
        System.out.printf("Success rate:  %.1f%%\n", getSuccessRate());
        System.out.printf("Avg exec time: %.0fms\n", getAverageExecutionMs());
        System.out.println("=============================\n");
    }
}
