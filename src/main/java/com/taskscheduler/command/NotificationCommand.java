package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public class NotificationCommand extends AbstractTaskCommand {
    private final String message;
    private final String channel;  // "slack", "webhook", etc.
    public NotificationCommand(Task task, String message, String channel) {
        super(task);
        this.message = message;
        this.channel = channel;
    }
    @Override
    protected void doExecute() throws Exception {
        System.out.println("  [Notifier] Publishing to channel: " + channel);
        System.out.println("    Message: " + message);
        Thread.sleep(200);

        System.out.println("  [Notifier] Notification delivered!");
    }
}
