package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public class FileProcessingCommand extends AbstractTaskCommand {
    private final String filePath;
    private final String outputPath;

    public FileProcessingCommand(Task task, String filePath, String outputPath) {
        super(task);
        this.filePath = filePath;
        this.outputPath = outputPath;
    }

    @Override
    protected void doExecute() throws Exception {
        // Simulated work: read file, process, write output
        System.out.println("  [FileProcessor] Reading from: " + filePath);
        Thread.sleep(500);  // Simulate I/O

        System.out.println("  [FileProcessor] Processing...");
        Thread.sleep(500);  // Simulate CPU work

        System.out.println("  [FileProcessor] Writing to: " + outputPath);
        Thread.sleep(500);  // Simulate I/O

        System.out.println("  [FileProcessor] Done!");
    }
}
