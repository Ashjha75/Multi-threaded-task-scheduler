package com.taskscheduler.command;

import com.taskscheduler.model.Task;

public class EmailCommand extends AbstractTaskCommand {
    private final String recipient;
    private final String subject;
    private final String body;

    public EmailCommand(Task task, String recipient, String subject, String body) {
        super(task);
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    @Override
    protected void doExecute() throws Exception {
        // Simulated email send
        System.out.println("  [EmailService] Connecting to SMTP...");
        Thread.sleep(200);

        System.out.println("  [EmailService] Sending to: " + recipient);
        System.out.println("    Subject: " + subject);
        System.out.println("    Body: " + body);
        Thread.sleep(300);

        System.out.println("  [EmailService] Email sent!");
    }
}
