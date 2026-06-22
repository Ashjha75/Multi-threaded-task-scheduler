package com.taskscheduler.observer;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The observable. Holds a list of listeners and notifies them of task events.
 * <p>
 * Thread-safe by design:
 * - Use CopyOnWriteArrayList: notifications don't lock, but adding/removing listeners does
 * - All publish methods are synchronized to prevent interleaving of events
 */
public class TaskEventPublisher {
    /**
     * CopyOnWriteArrayList: write operations (add/remove) are expensive (copy the entire list),
     * but read operations (iteration in notify loops) are cheap (no locking).
     * <p>
     * Perfect for this use case:
     * - Listeners added once at startup
     * - publishX() methods called constantly during task execution
     */
    private final List<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Register a listener to receive all task events.
     */
    public synchronized void addListener(TaskEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
            System.out.println("[Publisher] Added listener: " + listener.getClass().getSimpleName());
        }
    }

    /**
     * Remove a listener.
     */
    public synchronized void removeListener(TaskEventListener listener) {
        if (listeners.remove(listener)) {
            System.out.println("[Publisher] Removed listener: " + listener.getClass().getSimpleName());
        }
    }

    /**
     * Notify all listeners that a task was submitted.
     */

    public void publishSubmitted(Task task) {
        listeners.forEach(listener ->
                {
                    try {
                        listener.onTaskSubmitted(task);
                    } catch (Exception e) {
                        // Don't let a broken listener crash the scheduler
                        System.err.println("Error notifying listener: " + e.getMessage());
                    }
                }
        );
    }

    /**
     * Notify all listeners that a task started.
     */
    public void publishStarted(Task task) {
        listeners.forEach(listener -> {
            try {
                listener.onTaskStarted(task);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        });
    }

    /**
     * Notify all listeners that a task completed successfully.
     */
    public void publishCompleted(Task task, TaskResult result) {
        listeners.forEach(listener -> {
            try {
                listener.onTaskCompleted(task, result);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        });
    }

    /**
     * Notify all listeners that a task failed.
     */
    public void publishFailed(Task task, Exception ex) {
        listeners.forEach(listener -> {
            try {
                listener.onTaskFailed(task, ex);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        });
    }

    /**
     * Notify all listeners that a task is being retried.
     */
    public void publishRetrying(Task task, int retryCount) {
        listeners.forEach(listener -> {
            try {
                listener.onTaskRetrying(task, retryCount);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        });
    }

    /**
     * Notify all listeners that a task was cancelled.
     */
    public void publishCancelled(Task task) {
        listeners.forEach(listener -> {
            try {
                listener.onTaskCancelled(task);
            } catch (Exception e) {
                System.err.println("Error notifying listener: " + e.getMessage());
            }
        });
    }

    public int getListenerCount() {
        return listeners.size();
    }
}
