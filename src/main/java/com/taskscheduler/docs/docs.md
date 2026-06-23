# Task Scheduler — Concept Guide

# PART 1: FILE-BY-FILE WALKTHROUGH

## Model Layer

### `TaskStatus.java`, `TaskType.java`
**What it does:** Plain enums. `TaskStatus` = PENDING/RUNNING/SUCCESS/FAILED/CANCELLED. `TaskType` = FILE_PROCESSING/EMAIL/DATA_CLEANUP/NOTIFICATION.

**Why needed:** A task needs a fixed, known set of states and types. Using `String` for these would let someone create a task with `status = "Pending "` (typo, trailing space) and break every comparison silently.

**How it interacts:** `Task` holds one of each. `TaskFactory` switches on `TaskType` to decide which command to build. `WorkerThread` switches between `TaskStatus` values during execution.

### `TaskPriority.java`
**What it does:** Enum with a number attached: `HIGH(3), MEDIUM(2), LOW(1)`, plus a `weight` field and `getWeight()`.

**Why needed:** A plain enum has no ordering by default. The queue in `TaskScheduler` needs to compare priorities numerically to decide which task to dequeue first. The `weight` field gives it something to compare.

**How it interacts:** `TaskScheduler`'s `Comparator` calls `cmd.getTask().getPriority().getWeight()` to sort the queue.

### `TaskResult.java`
**What it does:** A plain object holding `taskId`, `finalStatus`, `output` (a `String`), `errorMessage`, `executionTimeMs`. Constructor + getters only, no setters.

**Why needed:** When a worker finishes a task, it needs to hand back a snapshot of what happened. This object is that snapshot — passed to `eventPublisher.publishCompleted(task, result)`.

**How it interacts:** Built inside `WorkerThread.executeCommand()` on success, passed to `TaskEventPublisher`, consumed by listeners like `MetricsListener` (reads `executionTimeMs` to compute averages).

### `Task.java`
**What it does:** The central domain object. Holds task identity (`id`, `name`, `type`, `priority` — all `final`), mutable execution state (`status`, `retryCount`, timestamps, `lastError`), and behavior methods (`updateStatus`, `incrementRetry`, `canRetry`).

**Why needed:** Every other layer (command, repository, scheduler, service, CLI) operates on a `Task`. It's the one object passed around everywhere.

**Important concepts used here:**

- **`AtomicReference<TaskStatus>` instead of a plain `TaskStatus` field.** Multiple worker threads read and write a task's status at the same time (one thread might be trying to start it while another reads it for a `list --status` query). A plain field read/written by multiple threads without synchronization is undefined behavior in Java — you could read a half-written value. `AtomicReference` makes every read/write a single atomic operation, and crucially gives you `compareAndSet`.

- **`AtomicInteger` for `retryCount`** — same reasoning: multiple threads might try to increment it.

- **Compare-And-Set (CAS) via `updateStatus(expected, newStatus)`:**
  ```java
  public boolean updateStatus(TaskStatus expected, TaskStatus newStatus) {
      return status.compareAndSet(expected, newStatus);
  }
  ```
  This says: "change status to `newStatus`, but ONLY if it's currently `expected`. Tell me if it worked." This is what stops two worker threads from both successfully grabbing the same `PENDING` task — only one CAS call can win when both threads race to flip `PENDING → RUNNING`.

- **Builder pattern (inner `Builder` class).** `Task` has 8+ fields. A constructor with 8 parameters is unreadable and error-prone (easy to swap two `String` arguments by mistake). The builder lets you write `Task.builder().name(...).taskType(...).priority(...).build()` — self-documenting, and you can add validation in `build()`.

- **Validation in `build()`:**
  ```java
  if (name == null) throw new IllegalStateException("name is required");
  ```
  Catches a missing required field at construction time instead of letting a broken `Task` flow deep into the system and fail somewhere confusing later.

- **`getStatus()` returns `TaskStatus`, not `AtomicReference<TaskStatus>`.** If you returned the raw `AtomicReference`, any caller could call `.set()` on it directly from outside the class, bypassing the CAS protection entirely. Returning the unwrapped value (`status.get()`) keeps the atomic object private and the guarantees intact.

**How it interacts:** Created by `Task.builder()` calls in `TaskService`. Read/written by `WorkerThread` during execution. Stored and fetched by `TaskRepository`. Read by `TaskCLI` for display.

---

## Exception Layer

### `TaskException.java` (abstract), `TaskExecutionException.java`, `TaskNotFoundException.java`, `TaskQueueFullException.java`

**What they do:** A small custom exception hierarchy. `TaskException` is abstract and carries a `UUID taskId`. `TaskExecutionException` and `TaskNotFoundException` extend it. `TaskQueueFullException` extends plain `RuntimeException` directly (no `taskId` makes sense for it — the whole queue is full, not one task).

**Why needed:** A generic `Exception("something failed")` tells you nothing actionable. These custom types tell you *which task* failed and *why*, so a `catch (TaskException e)` block in the CLI can print something like `"Task abc123 failed: File not found"` instead of a raw stack trace.

**Important concepts used here:**

- **Exception chaining via the `cause` constructor.** `TaskException` has a constructor `(UUID taskId, String message, Throwable cause)`. When `WorkerThread` catches a low-level exception from `command.execute()`, it should wrap it but pass the original exception in as `cause` — never swallow it. This preserves the original stack trace for debugging while adding the higher-level context (which task, what operation).

- **Custom fields beyond the message.** `TaskQueueFullException` carries `queueCapacity` and `tasksAttempted` — extra structured data a plain exception message string can't carry cleanly. A catcher can call `.getQueueCapacity()` programmatically instead of parsing a string.

**How it interacts:** Designed to be thrown inside `WorkerThread` (wrapping execution failures) and `TaskService` (e.g. `repository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId))`).

---

## Command + Factory Layer

### `TaskCommand.java`
**What it does:** A one-method interface: `void execute() throws Exception`, plus a `default Task getTask()` that throws `UnsupportedOperationException` unless overridden.

**Why needed:** Every task type (email, file processing, etc.) needs to be "a thing you can run." This interface is the common contract so the scheduler/worker can treat all task types identically — it only ever calls `.execute()`, never caring what's inside.

**Important concepts used here:**

- **`@FunctionalInterface` (implied by single abstract method).** Because the interface has exactly one abstract method (`execute()`), it *could* be implemented as a lambda. The project doesn't actually use lambdas for commands (it uses concrete classes via `AbstractTaskCommand`), but the shape of the interface keeps that option open.

- **`throws Exception` (broad, not a specific checked exception).** The actual work inside a command could fail in many different ways (I/O error, network timeout, anything). Declaring the broad `Exception` lets every command type throw whatever actually goes wrong, and pushes the decision of "what do we do about it" up to `WorkerThread`, which is the one place that knows about retries.

- **`default` method (`getTask()`).** Added because `WorkerThread` needs to read the task's ID/status off the command after it fails. Giving it a default body (that throws) means the interface still compiles even if some future implementer doesn't supply a real task — but in practice every concrete command goes through `AbstractTaskCommand`, which does override it.

**How it interacts:** Implemented by `AbstractTaskCommand`. Created by `TaskFactory`. Called by `WorkerThread.executeCommand()`.

### `AbstractTaskCommand.java`
**What it does:** Abstract class implementing `TaskCommand`. Holds `protected final Task task`. Implements `execute()` as `final` (cannot be overridden), wraps a call to an abstract `doExecute()` with timing and logging.

**Why needed:** Every concrete command needs the same boilerplate around its actual work: print a start message, time it, catch and log failures, print completion. Without this base class, that boilerplate would be copy-pasted into all four concrete commands.

**Important concepts used here:**

- **Template Method pattern.** `execute()` defines the fixed *skeleton* of "what happens around every task" (log start → run the real work → log success/time, or log failure and rethrow). The actual task-specific work is delegated to `doExecute()`, which subclasses implement. The skeleton never changes; only the filling does.

- **Why `execute()` is `final`.** If subclasses could override `execute()`, one of them might accidentally skip the timing/logging wrapper and break consistency. Making it `final` forces every subclass to plug into the one extension point (`doExecute()`) rather than reimplementing the whole flow.

- **Rethrowing in the catch block (`throw e;`).** `execute()` catches the exception just to log it, then rethrows the *same* exception unchanged. This is intentional — `AbstractTaskCommand` doesn't decide what to do about a failure (retry? give up?), it just records that something happened and lets the real decision-maker (`WorkerThread`) handle it.

**How it interacts:** Extended by `FileProcessingCommand`, `EmailCommand`, `DataCleanupCommand`, `NotificationCommand`. Constructed by `TaskFactory`. `execute()` is called from `WorkerThread`.

### `FileProcessingCommand.java`, `EmailCommand.java`, `DataCleanupCommand.java`, `NotificationCommand.java`
**What they do:** Each holds its own specific fields (e.g. `filePath`/`outputPath` for file processing, `recipient`/`subject`/`body` for email) and implements `doExecute()` with simulated work using `Thread.sleep()` and print statements.

**Why needed:** This is where the actual "what kind of task is this" logic lives. Each class represents one task type's behavior, fully isolated from the others.

**Important concepts used here:**

- **`Thread.sleep()` to simulate real work.** Real I/O (reading files, calling SMTP servers, querying databases) is slow and unpredictable. `Thread.sleep()` stands in for that delay so the project can demonstrate concurrency (multiple slow tasks overlapping) without needing real external systems.

**How it interacts:** Each is instantiated only by `TaskFactory`, never directly by other classes. Each extends `AbstractTaskCommand` and is otherwise independent — none of these four classes know about each other.

### `TaskFactory.java`
**What it does:** One public method `createCommand(Task task, Map<String, Object> params)` that switches on `task.getTaskType()` and returns the matching concrete command, pulling parameters out of the `params` map with `getOrDefault`.

**Why needed:** Something has to decide "given this task, which concrete class do I build, and with what constructor arguments?" Without a factory, that decision logic would be scattered across the codebase (CLI, service, scheduler) instead of living in one place.

**Important concepts used here:**

- **Factory pattern.** A single method that takes input data and returns an object whose concrete type is decided internally. The caller (`TaskService`) never writes `new EmailCommand(...)` directly — it just asks the factory for "the right command for this task" and gets back a `TaskCommand` reference. Adding a 5th task type later means changing only this one file.

- **`switch` on an enum (`task.getTaskType()`).** Enums work naturally with `switch` — the compiler can warn about unhandled cases, and the code reads as a direct mapping from type to behavior.

- **`Map<String, Object>` with `getOrDefault`.** Parameters differ wildly between task types (a file path vs. an email subject), so a generic key-value map is used instead of a rigid parameter object. `getOrDefault` avoids `NullPointerException` if the CLI didn't supply some optional parameter.

**How it interacts:** Called by `TaskService.submitTask()` and `TaskService.retryTask()`. Returns objects that `TaskScheduler.submit()` then queues.

---

## Repository Layer

### `TaskRepository.java`
**What it does:** An interface defining `save`, `findById` (returns `Optional<Task>`), `findAll`, `findByStatus`, `findByPriority`, `update`, `delete`, `count`.

**Why needed:** `TaskService` (and everything above it) shouldn't need to know *how* tasks are stored — in memory, in a database, wherever. The interface is the contract; the implementation is swappable.

**Important concepts used here:**

- **Repository pattern.** An abstraction layer between business logic and data storage. As long as something implements `TaskRepository`, the rest of the app doesn't care what's underneath.

- **`Optional<Task>` instead of returning `null` from `findById`.** Forces every caller to explicitly decide what happens when a task isn't found (`.orElseThrow(...)`, `.ifPresent(...)`, etc.) instead of risking an unchecked `NullPointerException` somewhere downstream.

**How it interacts:** Implemented by `InMemoryTaskRepository`. Used by `TaskService` and `WorkerThread` (to persist status changes).

### `InMemoryTaskRepository.java`
**What it does:** Implements `TaskRepository` using a `ConcurrentHashMap<UUID, Task>` as the backing store.

**Why needed:** This project doesn't use a real database — it needs *some* storage, and it needs that storage to be safe when accessed by multiple worker threads at once.

**Important concepts used here:**

- **`ConcurrentHashMap` instead of `Collections.synchronizedMap(new HashMap<>())`.** `synchronizedMap` locks the *entire* map on every single operation — if 3 worker threads are all touching different tasks at the same time, they'd queue up behind one lock for no reason. `ConcurrentHashMap` internally splits its storage so operations on different keys don't block each other. With 3 worker threads constantly reading/writing different tasks, this directly affects throughput.

- **Java 8 Streams (`filter().collect()`) in `findByStatus`/`findByPriority`:**
  ```java
  return store.values().stream()
      .filter(task -> task.getStatus() == status)
      .collect(Collectors.toList());
  ```
  Instead of writing a manual loop with an `if` and an `ArrayList.add()`, the stream expresses "give me all values, keep only the ones matching this predicate, collect them into a list" in one readable chain. Used because `TaskCLI`'s `list --status X` command needs exactly this filtering.

- **Defensive copying in `findAll()`** (`new ArrayList<>(store.values())`). Returns a brand-new list so a caller mutating the returned list can't accidentally corrupt the repository's internal state.

- **Fail-fast checks** — `save()` and `update()` throw `IllegalArgumentException`/`IllegalStateException` on bad input (null task, updating a task that was never saved). Catches bugs early instead of silently doing nothing.

**How it interacts:** Instantiated once in `Main`, injected into `TaskScheduler`, `WorkerThread` (indirectly via constructor), and `TaskService`.

---

## Retry Layer

### `RetryPolicy.java`
**What it does:** Interface with `shouldRetry(Task, Exception)`, `getDelayMs(int retryAttempt)`, `getMaxRetries()`, plus a `default getName()`.

**Why needed:** "Should this failed task be retried, and how long should we wait?" is a decision that varies depending on the situation. Pulling it out into an interface means `WorkerThread` doesn't need to contain `if/else` logic for every possible retry strategy — it just calls whatever policy was injected.

**Important concepts used here:**

- **Strategy pattern.** The actual *algorithm* for deciding retries is swapped in at construction time (`new TaskScheduler(retryPolicy, ...)`). `WorkerThread`'s code never changes regardless of which policy is plugged in.

**How it interacts:** Implemented by `NoRetryPolicy`, `FixedDelayRetryPolicy`, `ExponentialBackoffRetryPolicy`. Injected into `TaskScheduler`, passed down to every `WorkerThread`. Called inside `WorkerThread.handleFailure()`.

### `NoRetryPolicy.java`, `FixedDelayRetryPolicy.java`
**What they do:** `NoRetryPolicy.shouldRetry()` always returns `false`. `FixedDelayRetryPolicy` retries up to `maxRetries` times, always waiting the same `delayMs` between attempts.

**Why needed:** Simple baseline strategies — useful when you don't want backoff math, or want to disable retries entirely for certain task types.

### `ExponentialBackoffRetryPolicy.java`
**What it does:** `getDelayMs(retryAttempt)` computes `baseDelayMs * Math.pow(multiplier, retryAttempt)` — so each retry waits longer than the last.

**Why needed:** A fixed delay retries at a constant rate even if the underlying problem (e.g. an overloaded service) hasn't had time to recover — repeatedly hammering it at a steady interval can make things worse. Waiting progressively longer (500ms → 1s → 2s → 4s...) gives the system time to recover while still retrying automatically.

**Important concepts used here:**

- **`Math.pow()` for the exponential curve.** `delay = baseDelay × multiplier^attempt`. With `baseDelayMs=500, multiplier=2.0`: attempt 0 → 500ms, attempt 1 → 1000ms, attempt 2 → 2000ms, and so on.

- **Constructor validation** — throws `IllegalArgumentException` if `maxRetries < 0`, `baseDelayMs < 0`, or `multiplier <= 0`, so a misconfigured policy fails immediately at startup instead of producing nonsensical negative delays later.

**How it interacts:** Instantiated in `Main` and injected into `TaskScheduler`. `getDelayMs()` is called by `WorkerThread.handleFailure()` to know how long to wait before re-queuing a failed task.

---

## Observer Layer

### `TaskEventListener.java`
**What it does:** Interface with five methods (`onTaskSubmitted`, `onTaskStarted`, `onTaskCompleted`, `onTaskFailed`, `onTaskRetrying`, `onTaskCancelled`), every one of them a `default` method with an empty body.

**Why needed:** Different parts of the system want to "know" when something happens to a task (for logging, for metrics, potentially more later) — without the code that triggers the event needing to know who's listening or what they'll do.

**Important concepts used here:**

- **Java 8 `default` methods.** Because every method has a default (empty) implementation, a class implementing this interface only needs to override the events it actually cares about. `MetricsListener`, for example, doesn't need to implement `onTaskStarted()` at all — it inherits the no-op default.

**How it interacts:** Implemented by `ConsoleLogListener` and `MetricsListener`. Registered with `TaskEventPublisher.addListener()`.

### `ConsoleLogListener.java`, `MetricsListener.java`
**What they do:** `ConsoleLogListener` prints timestamped messages for each event. `MetricsListener` tracks counts (`submittedCount`, `completedCount`, `failedCount`, etc.) using `AtomicInteger`/`AtomicLong`, and exposes derived stats like `getSuccessRate()`.

**Why needed:** Two completely different consumers of the same events — one for human-readable real-time output, one for aggregated numeric tracking — without either knowing about the other.

**Important concepts used here:**

- **`AtomicInteger`/`AtomicLong` in `MetricsListener`.** Multiple worker threads can trigger events at the same time (e.g. two tasks completing simultaneously on different threads), both calling `metricsListener.onTaskCompleted(...)`. Plain `int`/`long` fields incremented from multiple threads can lose updates; atomics guarantee each increment is safely applied.

**How it interacts:** Both are registered with `TaskEventPublisher` in `Main`. `TaskEventPublisher` calls their methods whenever `WorkerThread` or `TaskService` publishes an event.

### `TaskEventPublisher.java`
**What it does:** Holds a list of `TaskEventListener`s. Has `addListener`/`removeListener`, and a `publishX(...)` method for each event type that loops over all listeners and calls the matching method on each, inside a try-catch.

**Why needed:** This is the actual "broadcaster." Whoever wants to announce "a task just completed" calls `publisher.publishCompleted(task, result)` and doesn't need to know or care how many listeners exist or what they do with that information.

**Important concepts used here:**

- **Observer pattern.** The publisher is the "subject" being observed; listeners are the "observers." Adding a new listener (or removing one) never requires touching the code that publishes events.

- **`CopyOnWriteArrayList` instead of a plain `ArrayList` with manual synchronization.** Listeners are added rarely (usually once, at startup in `Main`) but the `publishX` methods are called *constantly* from worker threads as tasks move through their lifecycle. `CopyOnWriteArrayList` makes reads (iterating to notify) completely lock-free, paying the cost (copying the whole list) only on the rare `add`/`remove`.

- **Per-listener try-catch inside the loop.** If one listener throws an exception while being notified, that's caught and logged — it does not stop the remaining listeners from being notified, and it does not crash the worker thread that triggered the event. One broken listener can't take down the whole system.

- **`synchronized` on `addListener`/`removeListener`.** These are the rare write operations on the `CopyOnWriteArrayList`. Marking them `synchronized` prevents two threads from both modifying the listener list at the exact same instant (mostly relevant if listeners were ever added dynamically at runtime, not just at startup).

**How it interacts:** Constructed once in `Main`. Injected into `TaskScheduler`, then implicitly available to every `WorkerThread`. Also injected into `TaskService` (used in `submitTask`/`cancelTask`).

---

## Scheduler Layer (the concurrency core)

### `TaskScheduler.java`
**What it does:** Owns a `PriorityBlockingQueue<TaskCommand>` and a `ThreadPoolExecutor`. `start(numWorkers)` creates and launches that many `WorkerThread` instances. `submit(command)` puts a command on the queue. `shutdown()`/`shutdownNow()` stop everything.

**Why needed:** This is the actual engine that turns "a list of tasks" into "tasks running concurrently across multiple threads." Every layer below this (model, command, repository, retry, observer) exists to support what happens here.

**Important concepts used here:**

- **`PriorityBlockingQueue` with a custom `Comparator`:**
  ```java
  new PriorityBlockingQueue<>(11,
      Comparator.comparing((TaskCommand cmd) -> cmd.getTask().getPriority().getWeight()).reversed());
  ```
  A regular `BlockingQueue` is FIFO (first-in-first-out) — it wouldn't respect priority. `PriorityBlockingQueue` keeps items ordered by a comparator, so HIGH-priority commands come out of `.take()` before LOW-priority ones, regardless of submission order. `.reversed()` is needed because higher weight (HIGH=3) should come *first*, but a plain ascending comparator would put the smallest weight first.

- **`ThreadPoolExecutor` built with the full constructor**, not `Executors.newFixedThreadPool()`:
  ```java
  new ThreadPoolExecutor(3, 5, 60, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(100), threadFactory, new AbortPolicy());
  ```
  Using the explicit constructor exposes every tunable parameter: `corePoolSize` (3 threads always alive), `maxPoolSize` (can grow to 5 under load), `keepAliveTime` (extra threads beyond core die after 60s idle), the internal work queue capacity, and a `RejectedExecutionHandler` (`AbortPolicy` — throw rather than silently drop work when overloaded).

- **`volatile boolean running`.** Multiple threads (the main thread calling `shutdown()`, and worker threads checking the flag) need to see the most up-to-date value immediately, without each other's writes getting cached/delayed in a thread-local view. `volatile` guarantees that visibility.

- **`AtomicInteger activeCount`.** Tracks how many workers are alive — incremented from `start()`, read for metrics. Same atomic-for-multithreaded-counting reasoning as elsewhere.

- **`synchronized` on `start()`/`shutdown()`.** These are administrative operations that should only ever be performed by one caller at a time and check/flip the `running` flag as a single unit — `synchronized` is simple and sufficient here because there's no need for timeouts or fairness, just basic mutual exclusion around a short block of code.

- **Graceful vs forced shutdown.** `shutdown()` stops accepting new work but lets in-flight tasks finish (`workerPool.shutdown()` + `awaitTermination(30s)`), falling back to `shutdownNow()` only if that times out. `shutdownNow()` interrupts everything immediately. The distinction matters because forcibly killing a worker mid-task could leave a task stuck in `RUNNING` forever.

**How it interacts:** Constructed in `Main` with a `RetryPolicy`, `TaskRepository`, and `TaskEventPublisher` injected. `start()` creates `WorkerThread`s, passing itself in (so a worker can call back into `scheduler.submit()` to re-queue a retry). `submit()` is called by `TaskService`.

### `WorkerThread.java`
**What it does:** Implements `Runnable`. In a loop, blocks on `queue.take()`, then executes whatever command comes out, handling success and failure.

**Why needed:** This is the thread body that actually does the work. Multiple instances of this class running on separate threads is what makes the system "multi-threaded."

**Important concepts used here:**

- **`BlockingQueue.take()` as the core synchronization mechanism.**
  ```java
  TaskCommand command = queue.take(); // blocks here, zero CPU usage
  ```
  This is the single most important line in the whole project. A worker thread calling `take()` on an empty queue doesn't spin in a loop checking "is there anything yet?" (which would waste CPU) — it actually sleeps, and the JVM wakes it up the instant another thread calls `.put()`/`.submit()`. This is how three threads can sit idle using effectively zero CPU while waiting for work.

- **CAS-based status transition to prevent double execution:**
  ```java
  boolean wasRunning = task.updateStatus(TaskStatus.PENDING, TaskStatus.RUNNING);
  if (!wasRunning) { return; }
  ```
  This is `Task`'s `compareAndSet` being put to direct use. If two threads somehow both pulled work referencing the same task, only one would win this CAS and proceed; the other sees `false` and backs off. This is the actual mechanism — not just a theoretical idea — that keeps the system correct under concurrency.

- **try-catch around the whole execution flow**, with a separate `handleFailure` method. Keeps the "happy path" (execute, mark success, publish event) visually separate from the "what do we do when it fails" logic.

- **Retry re-queuing via `ScheduledExecutorService`:**
  ```java
  ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  scheduler.schedule(() -> { this.scheduler.submit(command); ... }, delayMs, TimeUnit.MILLISECONDS);
  ```
  A failed-but-retryable task can't just go straight back on the queue — it needs to *wait* first (per the retry policy's delay). `ScheduledExecutorService.schedule()` runs a piece of code after a delay, on its own thread, without blocking the worker thread that's waiting for more work. This keeps the worker free to pick up other tasks while the retry timer ticks down in the background.

- **`InterruptedException` handling as a shutdown signal.** When `queue.take()` throws `InterruptedException` (because the thread pool is shutting down), the worker re-interrupts itself (`Thread.currentThread().interrupt()` — standard practice so the interrupt status isn't silently lost), sets `running = false`, and exits the loop cleanly instead of crashing or looping forever.

**How it interacts:** Created and started by `TaskScheduler.start()`. Reads from the queue owned by `TaskScheduler`. Calls into `TaskRepository.update()` for every status change. Calls into `TaskEventPublisher.publishX()` for every lifecycle event. Calls `retryPolicy.shouldRetry()`/`getDelayMs()` on failure. Calls back into `TaskScheduler.submit()` to re-queue retries.

---

## Service + CLI Layer

### `TaskService.java`
**What it does:** A facade. Holds references to `TaskRepository`, `TaskScheduler`, `TaskEventPublisher`, `TaskFactory`. Exposes simple, high-level methods: `submitTask(...)`, `getTask(...)`, `getTasksByStatus(...)`, `retryTask(...)`, `cancelTask(...)`, `getMetrics()`, `shutdown()`.

**Why needed:** Without this layer, `TaskCLI` would need to directly construct `TaskCommand`s via `TaskFactory`, push them onto `TaskScheduler`, and query `TaskRepository` — three different objects, three different concerns, all mixed into the CLI's parsing code. The facade collapses that into one simple call per user action.

**Important concepts used here:**

- **Facade pattern.** One class hides the coordination between several subsystems behind a small set of clear methods. `service.submitTask(name, type, priority, params)` internally builds a `Task`, saves it, publishes a "submitted" event, asks the factory for a command, and submits it to the scheduler — but the caller only sees one method call.

- **Dependency Injection via constructor.** `TaskService` receives all four dependencies (`repository`, `scheduler`, `eventPublisher`, `factory`) in its constructor rather than creating them itself. This means `TaskService` doesn't care *which* repository implementation it got, or how the scheduler was configured — those decisions are made once, in `Main`.

- **Using `Optional.orElseThrow()` in `retryTask`** — fetches the task and throws `TaskNotFoundException` in one expression if it's missing, rather than a separate null-check.

- **Java 8 streams in `getMetrics()`** — `tasks.stream().filter(t -> t.getStatus() == TaskStatus.SUCCESS).count()` to compute aggregate numbers on demand from the current task list.

**How it interacts:** Constructed in `Main` with all four dependencies injected. The only class `TaskCLI` talks to.

### `TaskCLI.java`
**What it does:** A `Scanner`-based loop reading lines of input, splitting them into command + arguments, and dispatching to handler methods (`handleAdd`, `handleList`, `handleRetry`, `handleCancel`, `handleStatus`, `handleMetrics`, `handleHelp`) that call `TaskService`.

**Why needed:** The project requirement is a terminal-based application — this is the actual user-facing entry point that turns typed text into method calls.

**Important concepts used here:**

- **Dependency Injection** (again) — `TaskCLI`'s constructor takes a `TaskService`, doesn't create one. This is what makes it possible to test the CLI by injecting a fake/mock service instead of a real one.

- **Simple manual argument parsing** (`parseArgs` helper) — splits `--key value` pairs out of the raw command line into a `Map<String, String>`. No external parsing library; just `String.split()` and a loop.

- **`UUID.fromString()` with try-catch for invalid IDs** — when the user types something that isn't a valid UUID for `retry`/`cancel`/`status`, the CLI catches `IllegalArgumentException` and prints a friendly error instead of crashing.

**How it interacts:** Only talks to `TaskService`. Constructed and started from `Main`.

### `Main.java`
**What it does:** Constructs every layer in dependency order — repository, then event publisher (with listeners registered), then retry policy, then scheduler (started with worker threads), then service, then CLI — and starts the CLI loop.

**Why needed:** Something has to be the one place where concrete implementations are chosen and wired together (which repository, which retry policy, how many workers). Without a single composition point, that decision-making would leak into every class that needs a dependency.

**Important concepts used here:**

- **Composition root / manual Dependency Injection.** Every other class in the project receives its dependencies via constructor parameters and never instantiates them itself (`new SomeOtherClass()`). `Main` is the only file where you see `new InMemoryTaskRepository()`, `new TaskScheduler(...)`, etc. This means swapping an implementation later (e.g. a database-backed repository) only requires changing this one file.

**How it interacts:** The entry point (`public static void main`). Touches every layer exactly once, at startup.

---

# PART 2: CONCEPT REFERENCE

Concepts that appear in multiple files, explained once, with every location they show up.

---

### AtomicReference / AtomicInteger / AtomicLong

**What is it?** Wrapper classes around a single value that let you safely read, write, and especially "compare-and-swap" that value from multiple threads — without using `synchronized`.

**Why do we use it?** A normal field (`int`, `enum`, etc.) can give wrong or stale results when read and written by different threads at the same time, because there's no guarantee one thread sees another's update immediately, and "read-then-write" isn't atomic (a `if (x < limit) x++` can be interleaved badly). Atomics fix both problems for single values.

**Where is it used?**
- `Task.status` (`AtomicReference<TaskStatus>`) and `Task.retryCount` (`AtomicInteger`)
- `TaskScheduler.activeCount` (`AtomicInteger`)
- `MetricsListener`'s counters (`AtomicInteger`/`AtomicLong`)

**Why there specifically?** In every case, the field is read and written from multiple worker threads concurrently (status during task execution, retryCount during retries, activeCount during scheduler lifecycle, metrics counters during simultaneous task completions across threads).

**Key things to remember:**
- `AtomicReference`/`AtomicInteger` give you `compareAndSet(expected, new)` — the building block for race-free state transitions.
- Never expose the atomic object itself through a getter — return the unwrapped value (`.get()`), or callers can bypass your safety.
- `incrementAndGet()` is a single atomic operation; doing `get()` then `+1` then `set()` manually reintroduces the race you were trying to avoid.

---

### Compare-And-Set (CAS)

**What is it?** An operation that says: "set this value to X, but only if it currently equals Y. Tell me whether it worked."

**Why do we use it?** It's how you safely let multiple threads "compete" to make a state change, with only one winner, without needing a lock around the whole operation.

**Where is it used?**
- `Task.updateStatus(expected, newStatus)` → `status.compareAndSet(expected, newStatus)`
- `WorkerThread.executeCommand()` → `task.updateStatus(TaskStatus.PENDING, TaskStatus.RUNNING)`

**Why there specifically?** Multiple worker threads pull commands off the same queue. If, for any reason, the same task could be picked up twice, CAS guarantees only one thread successfully transitions it `PENDING → RUNNING`; the loser's CAS call returns `false` and that thread backs off instead of double-executing the task.

**Key things to remember:**
- CAS only protects the single value it's applied to (here, `TaskStatus`). It doesn't lock anything else.
- `compareAndSet(status.get(), newStatus)` — reading your own current value right before comparing — is not real CAS protection (it'll basically always "succeed"). The `expected` value must come from the caller's own assumption about state, not a fresh read.

---

### volatile

**What is it?** A keyword on a field that guarantees every thread sees the latest write to that field immediately, instead of possibly working with a stale cached copy.

**Why do we use it?** For simple boolean "flags" that one thread sets and another thread checks in a loop, you need the checking thread to notice the change promptly.

**Where is it used?**
- `TaskScheduler.running`
- `WorkerThread.running`

**Why there specifically?** `shutdown()` sets `running = false` from the main thread. Worker threads are checking `while (running)` in their own loop. Without `volatile`, a worker thread might never observe that the flag changed and loop forever.

**Key things to remember:**
- `volatile` makes a single field's writes visible across threads — it does NOT make compound operations (like increment) atomic. That's what `AtomicInteger` is for.
- Use `volatile` for simple flags; use `Atomic*` when you need to read-modify-write safely.

---

### ConcurrentHashMap

**What is it?** A `Map` implementation built for safe concurrent access without locking the whole structure on every operation.

**Why do we use it?** `Collections.synchronizedMap(new HashMap<>())` locks the *entire* map for every single get/put, so concurrent threads queue up behind one lock even when accessing completely different keys. `ConcurrentHashMap` allows different keys to be accessed concurrently without one thread blocking another.

**Where is it used?**
- `InMemoryTaskRepository.store` (`ConcurrentHashMap<UUID, Task>`)

**Why there specifically?** Multiple worker threads update different tasks' status simultaneously (one finishing Task A while another starts Task B), and the CLI might be reading the full task list at the same moment. A `ConcurrentHashMap` lets all of that happen without one operation blocking the others.

**Key things to remember:**
- It's a drop-in replacement for `HashMap` in terms of API, but built for multithreaded access.
- Its `.values()` iteration is "weakly consistent" — you might see additions/removals reflected inconsistently mid-iteration, but for this project (status lookups, listing) that's an acceptable tradeoff for the throughput gain.

---

### CopyOnWriteArrayList

**What is it?** A `List` implementation where every write (add/remove) creates a brand-new internal copy of the array, but reads (iteration) never need any locking at all.

**Why do we use it?** When something is read/iterated very frequently but modified rarely, this trade is a clear win — readers pay zero synchronization cost.

**Where is it used?**
- `TaskEventPublisher.listeners`

**Why there specifically?** Listeners are registered once or twice, typically at startup in `Main`. But `publishX()` methods (which iterate the listener list) get called constantly, from every worker thread, every time a task changes state. Optimizing for fast, lock-free reads (and accepting a rare, slightly expensive write) fits this access pattern exactly.

**Key things to remember:**
- Wrong choice if you add/remove frequently (every write copies the whole underlying array).
- Right choice here because adds are rare (startup) and reads are constant (every task event).

---

### BlockingQueue / PriorityBlockingQueue (`queue.take()`, `queue.put()`)

**What is it?** A thread-safe queue where `take()` blocks (sleeps) the calling thread if the queue is empty, instead of returning immediately or throwing. `put()` blocks if the queue is full (not really hit in this project, since the queue is unbounded by default). `PriorityBlockingQueue` is a variant that dequeues items in priority order instead of insertion order.

**Why do we use it?** It's the actual hand-off mechanism between "threads producing work" (whoever calls `submit()`) and "threads consuming work" (the worker threads). Without it, workers would have to repeatedly check "is there anything to do?" in a loop, burning CPU for nothing (busy-waiting / polling).

**Where is it used?**
- `TaskScheduler.taskQueue` (`PriorityBlockingQueue<TaskCommand>`)
- `WorkerThread.run()` → `queue.take()`

**Why there specifically?** Multiple `WorkerThread`s share one queue. Each calls `take()` and sleeps until work shows up — and the queue's custom `Comparator` (built from `TaskPriority.getWeight()`) ensures HIGH priority commands are handed out before LOW priority ones, even if they were submitted later.

**Key things to remember:**
- `take()` blocking is what makes the system efficient: idle threads use ~0 CPU instead of spinning.
- The priority ordering only affects which task comes out *next* — it doesn't preempt a task that's already running.

---

### ThreadPoolExecutor (full constructor)

**What is it?** A managed pool of reusable worker threads, configured with explicit control over how many threads exist, how the pool grows, how idle threads are cleaned up, and what happens when it's overloaded.

**Why do we use it?** Creating a new `Thread` per task would be wasteful (thread creation has overhead) and uncontrolled (no limit on how many threads could pile up). A thread pool reuses a bounded set of threads.

**Where is it used?**
- `TaskScheduler.workerPool`

**Why there specifically?** The project deliberately uses the full constructor (`corePoolSize`, `maxPoolSize`, `keepAliveTime`, the internal queue, a `RejectedExecutionHandler`) instead of the shortcut `Executors.newFixedThreadPool()`, specifically so each parameter's purpose is visible and tunable rather than hidden behind a factory method.

**Key things to remember:**
- `corePoolSize` = threads kept alive even when idle.
- `maxPoolSize` = ceiling the pool can grow to under load.
- `keepAliveTime` = how long "extra" threads beyond core size survive while idle before being terminated.
- `RejectedExecutionHandler` decides what happens when the pool's internal queue is also full (`AbortPolicy` = throw and refuse the new task).

---

### ScheduledExecutorService

**What is it?** An executor that can run a task after a delay, on a separate thread, without blocking the thread that scheduled it.

**Why do we use it?** A failed-but-retryable task needs to wait (per the retry policy's delay) before going back on the queue. Calling `Thread.sleep(delayMs)` directly inside the worker thread would freeze that worker — it couldn't pick up any other task during the wait. Scheduling the re-queue on a separate timer thread keeps the worker free.

**Where is it used?**
- `WorkerThread.scheduleRetry()`

**Why there specifically?** This is the mechanism that actually implements the delay returned by `RetryPolicy.getDelayMs()` — it schedules a call back into `scheduler.submit(command)` after that many milliseconds have passed.

**Key things to remember:**
- This project creates a new one-off scheduler per retry call and shuts it down right after — simple, but means it's not shared/reused across retries. Worth knowing as a simplification rather than a "best practice" if asked about it.

---

### synchronized

**What is it?** A keyword that ensures only one thread at a time can execute a given block of code (or hold a given lock).

**Why do we use it?** For simple, infrequent, short operations where you just need "only one thread does this at a time" with automatic lock release — no timeout, no fairness requirement.

**Where is it used?**
- `TaskScheduler.start()` and `TaskScheduler.shutdown()`
- `TaskEventPublisher.addListener()` and `removeListener()`

**Why there specifically?** These are all administrative, rarely-called operations (start the scheduler once, shut it down once, register a listener at startup). `synchronized` is the simplest tool that gets the job done — no need for the extra flexibility of `ReentrantLock` (timeouts, fairness, condition variables) since none of that is required here.

**Key things to remember:**
- Lock is released automatically, even if an exception is thrown — no risk of forgetting to "unlock."
- This project does NOT use `ReentrantLock` anywhere — it's mentioned in discussion as an alternative, but every actual locking need in the code is satisfied by `synchronized`.

---

### Optional<T>

**What is it?** A wrapper type that explicitly represents "a value might or might not be present," instead of using `null`.

**Why do we use it?** Forces the calling code to consciously handle the "not found" case (`.orElseThrow()`, `.ifPresent()`, `.map()`) instead of risking a `NullPointerException` somewhere far from where the missing value originated.

**Where is it used?**
- `TaskRepository.findById()` return type
- `InMemoryTaskRepository.findById()` implementation (`Optional.ofNullable(store.get(id))`)
- `TaskService.retryTask()` (`repository.findById(taskId).orElseThrow(...)`)
- `TaskService.getTask()` return type
- `TaskCLI.handleStatus()` (`.ifPresentOrElse(...)`)

**Why there specifically?** Every place a task is looked up by ID, "not found" is a real, expected possibility (user typed a wrong ID, task was deleted). `Optional` makes that case impossible to silently ignore.

**Key things to remember:**
- Never call `.get()` on an `Optional` without checking presence first — that defeats the purpose and throws `NoSuchElementException` instead of `NullPointerException`, which isn't really an improvement.
- `.orElseThrow(() -> new SomeException(...))` is the pattern used here to convert "missing" into a meaningful custom exception.

---

### Java 8 Streams (`stream().filter().collect()` / `.count()`)

**What is it?** A way to process a collection by chaining operations (filter, map, collect, count) instead of writing manual loops with temporary variables.

**Why do we use it?** Filtering a collection by a condition and producing a new list (or count) is a very common operation in this project (find tasks by status, by priority, count successes for metrics). Streams express that intent directly: "take these, keep only the matching ones, do X with them."

**Where is it used?**
- `InMemoryTaskRepository.findByStatus()` / `findByPriority()`
- `TaskService.getMetrics()` (`.filter(...).count()`)
- `TaskCLI.handleList()` (`.stream().map(this::formatTask).toList()`)

**Why there specifically?** All of these are "take a collection of tasks, narrow it down by a condition, then do something with the result" — exactly the shape streams are built for.

**Key things to remember:**
- `.filter(predicate)` keeps only matching elements.
- `.collect(Collectors.toList())` (or `.toList()` in newer Java) turns the stream back into a concrete list.
- `.count()` short-circuits to a `long` count without building an intermediate list.

---

### Enums with fields/behavior (not just constants)

**What is it?** A Java enum where each constant can carry its own data and the enum can have fields, a constructor, and methods — not just be a named list of values.

**Why do we use it?** `TaskPriority` needs a numeric weight attached to each constant so it can be compared/sorted. A plain enum (`HIGH, MEDIUM, LOW`) has no inherent ordering usable by a `Comparator`.

**Where is it used?**
- `TaskPriority` (`HIGH(3), MEDIUM(2), LOW(1)` with a `weight` field and `getWeight()`)

**Why there specifically?** `TaskScheduler`'s priority queue comparator needs a number to compare on. Attaching `weight` directly to the enum constant keeps that data colocated with the value it describes, instead of maintaining a separate lookup table elsewhere.

---

### Builder Pattern (inner static `Builder` class)

**What is it?** An inner class with chainable setter-style methods (`.name(x).priority(y)...`) that collects values and produces the final object with `.build()`.

**Why do we use it?** `Task` has many fields, several with sensible defaults (`status` starts at `PENDING`, `retryCount` starts at `0`, `maxRetries` defaults to `3`). A constructor taking 8+ positional arguments is error-prone and unreadable; the builder lets you set only what matters and skip the rest.

**Where is it used?**
- `Task.Builder`

**Why there specifically?** Every place a `Task` is created (`TaskService.submitTask`, test code, `TaskCLI`) reads clearly: `Task.builder().name(...).taskType(...).priority(...).build()` — self-documenting about which value is which, and `build()` validates required fields before construction completes.

---

### Template Method Pattern

**What is it?** A base class defines the overall steps of an operation (in a `final` method), but delegates one specific step to subclasses via an abstract method.

**Why do we use it?** Every command type needs the same surrounding behavior (log start, time it, catch+log+rethrow failures) but different actual work. Rather than duplicating that wrapper in four classes, it lives once in the base class.

**Where is it used?**
- `AbstractTaskCommand.execute()` (final, fixed skeleton) calling `doExecute()` (abstract, implemented by each concrete command)

**Why there specifically?** `FileProcessingCommand`, `EmailCommand`, `DataCleanupCommand`, `NotificationCommand` all need identical logging/timing/error-propagation behavior around completely different work. Making `execute()` final forces every subclass through the same extension point (`doExecute()`), guaranteeing consistency.

---

### Factory Pattern

**What is it?** A class with a method that decides, based on input, which concrete class to instantiate — so the caller doesn't need to know about (or directly construct) every possible concrete type.

**Why do we use it?** `TaskService` shouldn't need a giant `if/else` checking `task.getTaskType()` and manually constructing the right command class with the right constructor arguments every time it submits a task. That logic belongs in one dedicated place.

**Where is it used?**
- `TaskFactory.createCommand()`

**Why there specifically?** Adding a new task type later (a 5th command class) means changing exactly one file (`TaskFactory`) — `TaskService`, `WorkerThread`, and everything else stay untouched because they only ever interact with the `TaskCommand` interface, never a concrete class name.

---

### Strategy Pattern

**What is it?** An interface representing "an algorithm" with multiple interchangeable implementations, chosen and injected at construction time rather than hardcoded.

**Why do we use it?** Retry behavior (fixed delay vs. exponential backoff vs. none) is exactly the kind of thing you want to be able to swap without touching the code that uses it.

**Where is it used?**
- `RetryPolicy` interface with `NoRetryPolicy`, `FixedDelayRetryPolicy`, `ExponentialBackoffRetryPolicy` as interchangeable implementations, injected into `TaskScheduler`

**Why there specifically?** `WorkerThread.handleFailure()` calls `retryPolicy.shouldRetry(...)` and `retryPolicy.getDelayMs(...)` without knowing or caring which concrete policy was configured in `Main`. Swapping `ExponentialBackoffRetryPolicy` for `FixedDelayRetryPolicy` requires changing one line in `Main` only.

---

### Observer Pattern

**What is it?** A "publisher" object keeps a list of "listener" objects and calls methods on all of them whenever something notable happens, without the publisher needing to know what each listener actually does with that notification.

**Why do we use it?** Multiple unrelated things need to react to the same task lifecycle events (console logging, metrics tracking) — the code that triggers those events (`WorkerThread`, `TaskService`) shouldn't need to know about both, or be modified every time a new kind of listener is added.

**Where is it used?**
- `TaskEventPublisher` (the publisher) and `TaskEventListener` implementations `ConsoleLogListener`, `MetricsListener` (the observers)

**Why there specifically?** `WorkerThread` and `TaskService` just call `eventPublisher.publishX(...)` at the right moments — they have zero knowledge of how many listeners exist or what each one does with the event.

---

### Facade Pattern

**What is it?** A single class exposing a small set of simple methods that internally coordinate several other classes/subsystems.

**Why do we use it?** Without it, `TaskCLI` would need to talk directly to `TaskRepository`, `TaskScheduler`, `TaskEventPublisher`, and `TaskFactory` separately for every user action — mixing CLI parsing concerns with business orchestration concerns.

**Where is it used?**
- `TaskService`

**Why there specifically?** `TaskCLI.handleAdd()` calls one method, `service.submitTask(...)`, instead of manually building a `Task`, saving it, publishing an event, asking the factory for a command, and submitting it to the scheduler — all of that coordination happens inside `TaskService`.

---

### Dependency Injection (constructor injection)

**What is it?** Giving a class its collaborators (other objects it needs) through its constructor, instead of the class creating those objects itself internally.

**Why do we use it?** It decouples *what* a class needs from *how* that dependency is built/configured. `TaskService` doesn't know or care whether it got an `InMemoryTaskRepository` or some other implementation — `Main` decides that once.

**Where is it used?**
- Every class in the project: `TaskCommand` implementations receive a `Task` via constructor; `TaskScheduler` receives `RetryPolicy`/`TaskRepository`/`TaskEventPublisher`; `WorkerThread` receives the queue/repository/policy/publisher/scheduler; `TaskService` receives all four of its collaborators; `TaskCLI` receives a `TaskService`.

**Why there specifically?** This is what makes `Main.java` the single "wiring" point for the whole application — every other class is just declaring "this is what I need," and `Main` is the only place deciding "here's what you get."

---

# QUICK-REFERENCE SUMMARY TABLE

| Concept | Files | One-line reason |
|---|---|---|
| AtomicReference/AtomicInteger/AtomicLong | Task, TaskScheduler, MetricsListener | Safe single-value read/write across threads |
| Compare-And-Set (CAS) | Task.updateStatus, WorkerThread | Prevent two threads executing the same task |
| volatile | TaskScheduler.running, WorkerThread.running | Immediate cross-thread visibility of a flag |
| ConcurrentHashMap | InMemoryTaskRepository | Concurrent access without locking the whole map |
| CopyOnWriteArrayList | TaskEventPublisher | Lock-free reads for rarely-changed listener list |
| BlockingQueue / PriorityBlockingQueue | TaskScheduler, WorkerThread | Thread-safe handoff + priority ordering, no busy-waiting |
| ThreadPoolExecutor (full constructor) | TaskScheduler | Explicit control over thread pool behavior |
| ScheduledExecutorService | WorkerThread.scheduleRetry | Non-blocking delayed re-queue for retries |
| synchronized | TaskScheduler, TaskEventPublisher | Simple mutual exclusion for rare admin operations |
| Optional<T> | TaskRepository, TaskService, TaskCLI | Force explicit handling of "not found" |
| Java 8 Streams | InMemoryTaskRepository, TaskService, TaskCLI | Concise filter/collect/count over task lists |
| Enums with fields | TaskPriority | Attach a comparable weight to each constant |
| Builder Pattern | Task.Builder | Readable construction with many optional fields |
| Template Method | AbstractTaskCommand | Shared wrapper logic, customizable core step |
| Factory Pattern | TaskFactory | Centralize "which concrete class to build" decision |
| Strategy Pattern | RetryPolicy + 3 implementations | Swappable retry algorithm |
| Observer Pattern | TaskEventPublisher + listeners | Decouple event producers from consumers |
| Facade Pattern | TaskService | Single simple entry point over several subsystems |
| Dependency Injection | Every class, wired in Main | Decouple "what's needed" from "how it's built" |
