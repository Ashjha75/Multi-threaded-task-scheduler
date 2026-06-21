package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public abstract class AbstractTaskCommand implements TaskCommand {

    private final  Task task;

    public  AbstractTaskCommand( Task task ){
        this.task= task;
    }

//     final : Because you never want a subclass to accidentally override it
    @Override
    public final  void execute() throws Exception {
        long startTime = System.currentTimeMillis();
        try{
            // Pre-execute hook: set timestamps, update status, etc.
            System.out.println("[" + task.getId() + "] Executing: " + task.getName());
            // The hook — subclasses override this, never execute()
            doExecute();
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("[" + task.getId() + "] Completed in " + elapsed + "ms");

        } catch (Exception e) {
            System.out.println("[" + task.getId() + "] Failed: " + e.getMessage());
            throw e;  // <-- let WorkerThread handle it
        }
    }
    protected abstract void doExecute() throws Exception;
    @Override
    public Task getTask() {
        return task;
    }
}
