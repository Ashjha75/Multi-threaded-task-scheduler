package com.taskscheduler.observer;


//I am using default methods — implementers only override what they care about.

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskResult;

public interface TaskEventListener {
    /**
     * Called when a task is submitted to the scheduler.
     */
    default void onTaskSubmitted(Task task) {}

    /**
     * Called when a task starts executing on a worker thread.
     */
    default void onTaskStarted(Task task) {}

    /**
     * Called when a task completes successfully.
     *
     * @param task   The task that completed
     * @param result The result (output, execution time, etc.)
     */
    default void onTaskCompleted(Task task, TaskResult result) {}

    /**
     * Called when a task fails (exception thrown during execute()).
     */
    default void onTaskFailed(Task task, Exception ex) {}

    /**
     * Called when a task is about to be retried after a failure.
     *
     * @param task       The task being retried
     * @param retryCount How many retries we've done so far (0 = first failure)
     */
    default void onTaskRetrying(Task task, int retryCount) {}

    /**
     * Called when a task is cancelled by the user.
     */
    default void onTaskCancelled(Task task) {}
}
