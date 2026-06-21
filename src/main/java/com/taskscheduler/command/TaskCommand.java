package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public interface TaskCommand {
    void execute() throws Exception;
    // Default method added here because WorkerThread needs it.
    // Subclasses don't override this.
    default Task getTask() {
        // Only AbstractTaskCommand knows how to return this;
        // for pure lambdas, you'd need a different approach.
        throw new UnsupportedOperationException(
                "Only AbstractTaskCommand subclasses can return a Task"
        );
    }
}