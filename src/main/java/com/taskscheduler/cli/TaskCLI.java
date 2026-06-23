package com.taskscheduler.cli;

import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import com.taskscheduler.model.TaskType;
import com.taskscheduler.service.TaskService;
import java.util.*;

/**
 * Terminal-based CLI for the task scheduler.
 * Reads commands, parses arguments, delegates to TaskService.
 */
public class TaskCLI {
    private final TaskService service;
    private final Scanner scanner;
    private volatile boolean running = true;

    public TaskCLI(TaskService service) {
        this.service = service;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Start the CLI loop. Runs until user types "shutdown".
     */
    public void start() {
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║   Multi-Threaded Task Scheduler       ║");
        System.out.println("║   Type 'help' for commands            ║");
        System.out.println("╚═══════════════════════════════════════╝\n");

        while (running) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "add":
                        handleAdd(parts);
                        break;
                    case "list":
                        handleList(parts);
                        break;
                    case "status":
                        handleStatus(parts);
                        break;
                    case "retry":
                        handleRetry(parts);
                        break;
                    case "cancel":
                        handleCancel(parts);
                        break;
                    case "metrics":
                        handleMetrics();
                        break;
                    case "help":
                        handleHelp();
                        break;
                    case "shutdown":
                        running = false;
                        service.shutdown();
                        break;
                    default:
                        System.out.println("✗ Unknown command: " + command + ". Type 'help' for options.");
                }
            } catch (Exception e) {
                System.out.println("✗ Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    /**
     * Parse: add --name "Task name" --type EMAIL --priority HIGH
     */
    private void handleAdd(String[] parts) {
        if (parts.length < 7) {
            System.out.println("Usage: add --name \"Task name\" --type EMAIL --priority HIGH");
            return;
        }

        Map<String, String> args = parseArgs(parts);
        String name = args.get("name");
        String typeStr = args.getOrDefault("type", "EMAIL").toUpperCase();
        String priorityStr = args.getOrDefault("priority", "MEDIUM").toUpperCase();

        if (name == null) {
            System.out.println("✗ Name is required");
            return;
        }

        TaskType type = TaskType.valueOf(typeStr);
        TaskPriority priority = TaskPriority.valueOf(priorityStr);

        // Build params based on task type
        Map<String, Object> params = new HashMap<>();
        switch (type) {
            case EMAIL:
                params.put("recipient", args.getOrDefault("recipient", "user@example.com"));
                params.put("subject", args.getOrDefault("subject", "Task completed"));
                params.put("body", args.getOrDefault("body", "Your task is complete"));
                break;
            case FILE_PROCESSING:
                params.put("filePath", args.getOrDefault("filePath", "/tmp/input.txt"));
                params.put("outputPath", args.getOrDefault("outputPath", "/tmp/output.txt"));
                break;
            case DATA_CLEANUP:
                params.put("tableName", args.getOrDefault("tableName", "logs"));
                params.put("daysOld", Integer.parseInt(args.getOrDefault("daysOld", "30")));
                break;
            case NOTIFICATION:
                params.put("message", args.getOrDefault("message", "Task complete"));
                params.put("channel", args.getOrDefault("channel", "slack"));
                break;
        }

        service.submitTask(name, type, priority, params);
    }

    /**
     * Parse: list --status PENDING
     * Or:    list --priority HIGH
     */
    private void handleList(String[] parts) {
        if (parts.length < 2) {
            // List all
            List<String> formatted = service.getAllTasks().stream()
                    .map(this::formatTask)
                    .toList();
            if (formatted.isEmpty()) {
                System.out.println("No tasks");
            } else {
                System.out.println("=== All Tasks ===");
                formatted.forEach(System.out::println);
            }
            return;
        }

        String filter = parts[1].toLowerCase();
        if (filter.equals("--status") && parts.length > 2) {
            TaskStatus status = TaskStatus.valueOf(parts[2].toUpperCase());
            List<String> formatted = service.getTasksByStatus(status).stream()
                    .map(this::formatTask)
                    .toList();
            System.out.println("=== Tasks with status " + status + " ===");
            formatted.forEach(System.out::println);
        } else if (filter.equals("--priority") && parts.length > 2) {
            TaskPriority priority = TaskPriority.valueOf(parts[2].toUpperCase());
            List<String> formatted = service.getTasksByPriority(priority).stream()
                    .map(this::formatTask)
                    .toList();
            System.out.println("=== Tasks with priority " + priority + " ===");
            formatted.forEach(System.out::println);
        }
    }

    /**
     * Parse: status <task-id>
     */
    private void handleStatus(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: status <task-id>");
            return;
        }

        try {
            UUID id = UUID.fromString(parts[1]);
            service.getTask(id).ifPresentOrElse(
                    task -> System.out.println(formatTask(task)),
                    () -> System.out.println("✗ Task not found: " + id)
            );
        } catch (IllegalArgumentException e) {
            System.out.println("✗ Invalid UUID format");
        }
    }

    /**
     * Parse: retry <task-id>
     */
    private void handleRetry(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: retry <task-id>");
            return;
        }

        try {
            UUID id = UUID.fromString(parts[1]);
            service.retryTask(id);
        } catch (IllegalArgumentException e) {
            System.out.println("✗ Invalid UUID format");
        }
    }

    /**
     * Parse: cancel <task-id>
     */
    private void handleCancel(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: cancel <task-id>");
            return;
        }

        try {
            UUID id = UUID.fromString(parts[1]);
            service.cancelTask(id);
        } catch (IllegalArgumentException e) {
            System.out.println("✗ Invalid UUID format");
        }
    }

    /**
     * Print metrics: success rate, queue size, active workers.
     */
    private void handleMetrics() {
        Map<String, Object> metrics = service.getMetrics();
        System.out.println("\n========== METRICS ==========");
        System.out.printf("Total tasks:      %d\n", metrics.get("total"));
        System.out.printf("Successful:       %d\n", metrics.get("success"));
        System.out.printf("Failed:           %d\n", metrics.get("failed"));
        System.out.printf("Pending:          %d\n", metrics.get("pending"));
        System.out.printf("Success rate:     %.1f%%\n", metrics.get("successRate"));
        System.out.printf("Queue size:       %d\n", metrics.get("queueSize"));
        System.out.printf("Active workers:   %d\n", metrics.get("activeWorkers"));
        System.out.println("=============================\n");
    }

    private void handleHelp() {
        System.out.println("\n=== Available Commands ===");
        System.out.println("  add --name \"Task name\" --type EMAIL --priority HIGH");
        System.out.println("  list                          (list all tasks)");
        System.out.println("  list --status PENDING         (filter by status)");
        System.out.println("  list --priority HIGH          (filter by priority)");
        System.out.println("  status <task-id>              (show task details)");
        System.out.println("  retry <task-id>               (retry failed task)");
        System.out.println("  cancel <task-id>              (cancel pending task)");
        System.out.println("  metrics                       (show scheduler metrics)");
        System.out.println("  shutdown                      (exit gracefully)");
        System.out.println("  help                          (show this message)");
        System.out.println();
    }

    // Helper: format task for display
    private String formatTask(com.taskscheduler.model.Task task) {
        return String.format(
                "[%s] %s | Status: %-8s | Priority: %-6s | Retries: %d",
                task.getId().toString().substring(0, 8),
                task.getName(),
                task.getStatus(),
                task.getPriority(),
                task.getRetryCount()
        );
    }

    // Helper: parse key=value pairs from command line
    private Map<String, String> parseArgs(String[] parts) {
        Map<String, String> args = new HashMap<>();
        for (int i = 1; i < parts.length - 1; i += 2) {
            if (parts[i].startsWith("--")) {
                String key = parts[i].substring(2);
                String value = parts[i + 1].replaceAll("^\"|\"$", "");  // Remove quotes
                args.put(key, value);
            }
        }
        return args;
    }
}