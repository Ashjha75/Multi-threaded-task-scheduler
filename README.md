<p align="center">
  <img src="https://raw.githubusercontent.com/Ashjha75/Multi-threaded-task-scheduler/refs/heads/dev_v1/banner.png"
       alt="Multi-Threaded Task Scheduler"
       width="100%">
</p>

# Multi-Threaded Task Scheduler

A production-inspired Java application that demonstrates core concurrency patterns through a priority-based task scheduling system.

Submit tasks from a CLI, execute them across multiple worker threads, automatically retry failures with exponential backoff, and track execution through an event-driven architecture.

## Features

- Multi-threaded task execution
- Priority-based scheduling using `PriorityBlockingQueue`
- Automatic retries with configurable retry policies
- Event-driven notifications via Observer Pattern
- Thread-safe task storage with `ConcurrentHashMap`
- Graceful shutdown and task lifecycle management
- CLI for task submission and monitoring

## Technologies & Concepts

### Java Concurrency
- `ThreadPoolExecutor`
- `PriorityBlockingQueue`
- `ConcurrentHashMap`
- `AtomicInteger`
- `ReentrantLock`
- CAS operations

### Design Patterns
- Factory Pattern
- Command Pattern
- Strategy Pattern
- Observer Pattern
- Template Method Pattern

## Architecture

```text
CLI
 ↓
TaskService
 ↓
TaskScheduler
 ↓
Worker Threads
 ↓
Task Commands

Supporting Layers:
- Factory
- Repository
- Retry Policy
- Event Publisher
```

## Project Structure

```text
src/main/java/com/taskscheduler/
├── model/
├── command/
├── factory/
├── repository/
├── retry/
├── observer/
├── scheduler/
├── service/
├── cli/
└── Main.java
```

## Build & Run

### Prerequisites

- Java 17+
- Maven

### Build

```bash
mvn clean compile
```

### Run

```bash
mvn exec:java -Dexec.mainClass="com.taskscheduler.Main"
```

## CLI Commands

```text
add --name "Process invoices" \
    --type FILE_PROCESSING \
    --priority HIGH \
    --params filePath=/tmp/invoices.csv

list --status PENDING

list --priority HIGH

retry <task-id>

cancel <task-id>

shutdown
```

## Example Flow

1. Submit a task through the CLI
2. Task is stored and queued
3. Worker thread executes the task
4. Success → task marked completed
5. Failure → retry policy determines next attempt
6. Events are published to registered listeners

## Future Enhancements

- Pause/Resume support
- Database persistence
- Metrics and monitoring
- Scheduled/recurring jobs
- Distributed execution

---

Built to explore real-world backend concurrency, task execution, retry handling, and event-driven design using core Java.