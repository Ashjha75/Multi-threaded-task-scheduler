package com.taskscheduler;

import com.taskscheduler.command.TaskCommand;
import com.taskscheduler.factory.TaskFactory;
import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.model.TaskType;
import com.taskscheduler.repository.InMemoryTaskRepository;
import com.taskscheduler.repository.TaskRepository;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        InMemoryTaskRepository repo = new InMemoryTaskRepository();

        // Create some tasks
        Task task1 = Task.builder()
                .name("Process invoices")
                .taskType(TaskType.FILE_PROCESSING)
                .priority(TaskPriority.HIGH)
                .build();

        Task task2 = Task.builder()
                .name("Send notifications")
                .taskType(TaskType.NOTIFICATION)
                .priority(TaskPriority.MEDIUM)
                .build();

        Task task3 = Task.builder()
                .name("Cleanup old logs")
                .taskType(TaskType.DATA_CLEANUP)
                .priority(TaskPriority.LOW)
                .build();

        // Save them
        repo.save(task1);
        repo.save(task2);
        repo.save(task3);

        System.out.println("\nTotal tasks: " + repo.count());

        // Find by status (all should be PENDING since we just created them)
        System.out.println("\n--- Tasks with status PENDING ---");
        repo.findByStatus(TaskStatus.PENDING).forEach(t ->
                System.out.println("  " + t.getName())
        );

        // Find by priority
        System.out.println("\n--- High priority tasks ---");
        repo.findByPriority(TaskPriority.HIGH).forEach(t ->
                System.out.println("  " + t.getName())
        );

        // Fetch one by ID
        System.out.println("\n--- Fetch task by ID ---");
        repo.findById(task1.getId()).ifPresent(t ->
                System.out.println("Found: " + t.getName())
        );

        // Update a task (change its status)
        task1.updateStatus(TaskStatus.PENDING, TaskStatus.RUNNING);
        repo.update(task1);

        // Verify the update
        System.out.println("\n--- After updating task1 to RUNNING ---");
        repo.printAll();

        // Delete a task
        boolean deleted = repo.delete(task2.getId());
        System.out.println("\nDeleted task2: " + deleted);
        System.out.println("Total tasks now: " + repo.count());
        System.out.println("We are working 🪄");
    }
}
