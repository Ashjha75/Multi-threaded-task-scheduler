package com.taskscheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskType;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Create a task
        Task task = Task.builder()
                .name("Send an email")
                .taskType(TaskType.EMAIL)
                .priority(TaskPriority.HIGH)
                .maxRetries(2)
                .build();

        // Build params (would come from CLI input or config file in real code)
        Map<String, Object> params = new HashMap<>();
        params.put("recipient", "alice@example.com");
        params.put("subject", "Hello from Task Scheduler");
        params.put("body", "This is an automated email");

        // Factory creates the right command
        TaskFactory factory = new TaskFactory();
        TaskCommand command = factory.createCommand(task, params);

        // Execute it (no threads, just sequential for now)
        System.out.println("=== Starting Task ===");
        try{command.execute();}catch(Exception e){e.printStackTrace();};
        System.out.println("=== Task Complete ===");
        System.out.println("We are working 🪄");
    }
}
