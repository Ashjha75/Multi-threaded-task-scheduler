package com.taskscheduler.observer;

import java.util.concurrent.atomic.AtomicInteger;

public class MetricsListener implements TaskEventListener {
    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final AtomicInteger cancelledCount = new AtomicInteger(0);
}
