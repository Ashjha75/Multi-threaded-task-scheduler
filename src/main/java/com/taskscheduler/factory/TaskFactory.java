package com.taskscheduler.factory;

import com.taskscheduler.command.*;
import com.taskscheduler.model.Task;

import java.util.Map;

//The factory is the only place in  codebase that instantiates all concrete commands.
public class TaskFactory {

    /**
     * Creates a command for the given task.
     * The params map holds task-specific data pulled from user input or a config file.
     *
     * Example params for FILE_PROCESSING:
     *   params.get("filePath") -> "/path/to/file.txt"
     *   params.get("outputPath") -> "/path/to/output.txt"
     */

    public TaskCommand createCommand(Task task, Map<String,Object> params){
        return switch (task.getTaskType()) {
            case FILE_PROCESSING -> createFileProcessingCommand(task, params);
            case EMAIL -> createEmailCommand(task, params);
            case DATA_CLEANUP -> createDataCleanupCommand(task, params);
            case NOTIFICATION -> createNotificationCommand(task, params);
            default -> throw new IllegalArgumentException(
                    "Unknown task type: " + task.getTaskType()
            );
        };
    }


    private TaskCommand createFileProcessingCommand(Task task ,Map<String,Object> params){
        String filePath= (String) params.getOrDefault("filePath", "/tmp/input.txt");
        String outputPath= (String) params.getOrDefault("outputPath", "/tmp/output.txt");
        return  new FileProcessingCommand(task,filePath,outputPath);
    }
    private TaskCommand createEmailCommand(Task task ,Map<String,Object> params){
        String recipient= (String) params.getOrDefault("recipient", "user@example.com");
        String subject= (String) params.getOrDefault("recipient", "user@example.com");
        String body= (String) params.getOrDefault("recipient", "user@example.com");
        return  new EmailCommand(task,recipient,subject,body);
    }
    private TaskCommand createDataCleanupCommand(Task task,Map<String ,Object> params){
        String tableName = (String) params.getOrDefault("tableName", "logs");
        int daysOld = (int) params.getOrDefault("daysOld", 30);
        return new DataCleanupCommand(task, tableName, daysOld);
    }
    private TaskCommand createNotificationCommand(Task task, Map<String, Object> params) {
        String message = (String) params.getOrDefault("message", "Task completed");
        String channel = (String) params.getOrDefault("channel", "email");
        return new NotificationCommand(task, message, channel);
    }

}
