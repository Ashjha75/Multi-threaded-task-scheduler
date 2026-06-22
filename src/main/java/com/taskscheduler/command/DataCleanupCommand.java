package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public class DataCleanupCommand extends AbstractTaskCommand {
    private final String tableName;
    private final int daysOld;

    public DataCleanupCommand(Task task, String tableName, int daysOld) {
        super(task);
        this.tableName = tableName;
        this.daysOld = daysOld;
    }

    @Override
    protected void doExecute() throws Exception {
        System.out.println("  [DataCleanup] Connecting to database...");
        Thread.sleep(300);

        System.out.println("  [DataCleanup] Running cleanup on table: " + tableName);
        System.out.println("    Deleting records older than " + daysOld + " days");
        Thread.sleep(800);  // Long-running query

        System.out.println("  [DataCleanup] Deleted 1,245 records");
    }
}
