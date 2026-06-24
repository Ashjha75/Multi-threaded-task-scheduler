# Simple Task Scheduler Diagrams

## 1. SIMPLE ARCHITECTURE - What Components Exist

```mermaid
graph TB
    CLI["TaskCLI<br/>(Terminal)"]
    SERVICE["TaskService<br/>(Facade)"]
    FACTORY["TaskFactory<br/>(Creates Commands)"]
    REPO["TaskRepository<br/>(Storage)"]
    SCHEDULER["TaskScheduler<br/>(Queue + Threads)"]
    WORKERS["Worker Threads<br/>(Execute Tasks)"]
    EVENTS["EventPublisher<br/>(Broadcasts Events)"]
    
    CLI --> SERVICE
    SERVICE --> FACTORY
    SERVICE --> REPO
    SERVICE --> SCHEDULER
    SCHEDULER --> WORKERS
    WORKERS --> REPO
    WORKERS --> EVENTS
    EVENTS -.-> CLI
    
    style CLI fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style SERVICE fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style FACTORY fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style REPO fill:#9C27B0,stroke:#6A1B9A,color:#fff,stroke-width:2px
    style SCHEDULER fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style WORKERS fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:2px
    style EVENTS fill:#E91E63,stroke:#AD1457,color:#fff,stroke-width:2px
```

---

## 2. SIMPLE TASK SUBMISSION FLOW

```mermaid
graph LR
    User["User Inputs<br/>add --name Task<br/>--type EMAIL"]
    
    CLI["TaskCLI<br/>Parses Input"]
    
    Service["TaskService<br/>Creates Task<br/>Saves to Repo"]
    
    Factory["TaskFactory<br/>Builds Command<br/>EmailCommand"]
    
    Queue["Priority Queue<br/>Holds Commands"]
    
    User --> CLI
    CLI --> Service
    Service --> Factory
    Factory --> Queue
    
    style User fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style CLI fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Service fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Factory fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style Queue fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
```

---

## 3. SIMPLE TASK EXECUTION FLOW

```mermaid
graph TD
    Queue["Queue<br/>Has Command"]
    
    Worker["Worker Thread<br/>queue.take()"]
    
    Check{"Task Status<br/>is PENDING?"}
    
    CAS["CAS Operation<br/>PENDING to RUNNING<br/>Only One Wins"]
    
    Execute["Execute Command<br/>Do the work"]
    
    Success["Mark SUCCESS<br/>Save to Repo<br/>Publish Event"]
    
    Fail["Throws Exception<br/>Check: Retry?"]
    
    Loop["Back to Queue<br/>Wait for next task"]
    
    Queue --> Worker
    Worker --> Check
    Check -->|Yes| CAS
    Check -->|No| Loop
    CAS --> Execute
    Execute -->|No Error| Success
    Execute -->|Error| Fail
    Success --> Loop
    Fail --> Loop
    
    style Queue fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Worker fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:2px
    style Check fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style CAS fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Execute fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Success fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Fail fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
    style Loop fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:2px
```

---

## 4. SIMPLE RETRY FLOW

```mermaid
graph TD
    Fail["Task Fails<br/>Exception Thrown"]
    
    Check{"Retry Policy<br/>shouldRetry?"}
    
    NoRetry["Give Up<br/>Mark FAILED<br/>Save to Repo"]
    
    YesRetry["Retry<br/>Increment counter<br/>Reset to PENDING"]
    
    Wait["Wait<br/>FixedDelay: 1s<br/>OR<br/>Exponential: increases"]
    
    Requeue["Re-queue<br/>Back to Queue"]
    
    Again["Try Again<br/>Next execution"]
    
    Fail --> Check
    Check -->|No| NoRetry
    Check -->|Yes| YesRetry
    YesRetry --> Wait
    Wait --> Requeue
    Requeue --> Again
    NoRetry --> Done["Stop"]
    Again --> Either{"Succeeds?"}
    Either -->|Yes| Success["SUCCESS"]
    Either -->|No| Fail
    
    style Fail fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
    style NoRetry fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
    style YesRetry fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Wait fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Requeue fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:2px
    style Again fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Success fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Check fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Either fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Done fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
```

---

## 5. SIMPLE CONCURRENCY - How Multiple Threads Work

```mermaid
graph TB
    Queue["Priority Queue<br/>HIGH Task 1<br/>MEDIUM Task 2<br/>LOW Task 3"]
    
    W1["Worker 1<br/>Executing HIGH"]
    
    W2["Worker 2<br/>Blocked waiting"]
    
    W3["Worker 3<br/>Blocked waiting"]
    
    Queue --> W1
    Queue --> W2
    Queue --> W3
    
    W1 --> Done["Task Done<br/>Back to waiting"]
    W2 --> Gets["Gets MEDIUM Task<br/>Starts"]
    W3 --> Waits["Still waiting<br/>Will get LOW"]
    
    style Queue fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style W1 fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style W2 fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style W3 fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style Done fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Gets fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Waits fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
```

---

## 6. SIMPLE CLI COMMANDS

```mermaid
graph TB
    CLI["TaskCLI<br/>Terminal"]
    
    Add["add --name X<br/>--type EMAIL"]
    
    List["list<br/>list --status PENDING"]
    
    Retry["retry task-id<br/>Retry failed task"]
    
    Cancel["cancel task-id<br/>Cancel pending task"]
    
    Status["status task-id<br/>Show task details"]
    
    Metrics["metrics<br/>Show counts"]
    
    Shutdown["shutdown<br/>Exit"]
    
    CLI --> Add
    CLI --> List
    CLI --> Retry
    CLI --> Cancel
    CLI --> Status
    CLI --> Metrics
    CLI --> Shutdown
    
    style CLI fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Add fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style List fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Retry fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Cancel fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Status fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Metrics fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Shutdown fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
```

---

## 7. SIMPLE TASK LIFECYCLE (States)

```mermaid
graph LR
    Created["PENDING<br/>Waiting in Queue"]
    
    Running["RUNNING<br/>Worker executing"]
    
    Success["SUCCESS<br/>Completed"]
    
    Failed["FAILED<br/>No more retries"]
    
    Cancelled["CANCELLED<br/>User cancelled"]
    
    Created -->|Worker takes| Running
    Running -->|Success| Success
    Running -->|Fail once| Created
    Running -->|Fail max times| Failed
    Created -->|User cancels| Cancelled
    
    style Created fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Running fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style Success fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Failed fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
    style Cancelled fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
```

---

## 8. KEY CONCEPTS - ONE SENTENCE EACH

```mermaid
graph TB
    Atomic["AtomicInteger<br/>Safe read+write<br/>from multiple threads"]
    
    CAS["Compare-And-Set<br/>Only one thread<br/>can win"]
    
    Queue["BlockingQueue<br/>Threads wait here<br/>no CPU spinning"]
    
    Retry["RetryPolicy<br/>Decides: retry or give up<br/>how long to wait"]
    
    Events["Observer Pattern<br/>Publish events<br/>listeners react"]
    
    Factory["Factory Pattern<br/>One place to build<br/>all command types"]
    
    Service["TaskService<br/>Simple interface<br/>hiding complexity"]
    
    style Atomic fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style CAS fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style Queue fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Retry fill:#9C27B0,stroke:#6A1B9A,color:#fff,stroke-width:2px
    style Events fill:#E91E63,stroke:#AD1457,color:#fff,stroke-width:2px
    style Factory fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style Service fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
```

---

## 9. LAYERS VISUALIZATION

```mermaid
graph TB
    subgraph "User Layer"
        CLI["TaskCLI<br/>Terminal"]
    end
    
    subgraph "Service Layer"
        SERVICE["TaskService<br/>Orchestration"]
    end
    
    subgraph "Core Layer"
        FACTORY["TaskFactory"]
        RETRY["RetryPolicy"]
    end
    
    subgraph "Execution Layer"
        SCHEDULER["TaskScheduler"]
        WORKERS["Worker Threads"]
        QUEUE["Priority Queue"]
    end
    
    subgraph "Supporting Layer"
        REPO["Repository<br/>ConcurrentHashMap"]
        EVENTS["EventPublisher"]
    end
    
    CLI --> SERVICE
    SERVICE --> FACTORY
    SERVICE --> SCHEDULER
    SCHEDULER --> QUEUE
    SCHEDULER --> WORKERS
    WORKERS --> REPO
    WORKERS --> RETRY
    WORKERS --> EVENTS
    
    style CLI fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style SERVICE fill:#2196F3,stroke:#1565C0,color:#fff,stroke-width:2px
    style FACTORY fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style RETRY fill:#FF9800,stroke:#E65100,color:#fff,stroke-width:2px
    style SCHEDULER fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style WORKERS fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:2px
    style QUEUE fill:#FFC107,stroke:#F57F17,color:#000,stroke-width:2px
    style REPO fill:#9C27B0,stroke:#6A1B9A,color:#fff,stroke-width:2px
    style EVENTS fill:#E91E63,stroke:#AD1457,color:#fff,stroke-width:2px
```

---

## 10. WHAT MAKES IT WORK - The Secret

```mermaid
graph TB
    Problem["Problem<br/>Multiple threads<br/>Shared tasks<br/>Need coordination"]
    
    Solution1["BlockingQueue<br/>Threads wait safely<br/>No CPU wasted"]
    
    Solution2["CAS Transition<br/>Only one thread<br/>executes each task"]
    
    Solution3["Retry Policy<br/>Smart retry logic<br/>Exponential backoff"]
    
    Solution4["Thread Pool<br/>Bounded workers<br/>Controlled concurrency"]
    
    Problem --> Solution1
    Problem --> Solution2
    Problem --> Solution3
    Problem --> Solution4
    
    Solution1 --> Result["WORKS<br/>Safe + Efficient<br/>Easy to use"]
    Solution2 --> Result
    Solution3 --> Result
    Solution4 --> Result
    
    style Problem fill:#F44336,stroke:#C62828,color:#fff,stroke-width:2px
    style Solution1 fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Solution2 fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Solution3 fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Solution4 fill:#4CAF50,stroke:#2E7D32,color:#fff,stroke-width:2px
    style Result fill:#00BCD4,stroke:#00838F,color:#fff,stroke-width:3px
```

---

## COLOR LEGEND

```
🟢 GREEN (#4CAF50)     = Input/Execution/Success
🔵 BLUE (#2196F3)      = Service/Processing
🟠 ORANGE (#FF9800)    = Factory/Creation/Waiting
💛 YELLOW (#FFC107)    = Queue/Core/Decision
🔵 CYAN (#00BCD4)      = Worker Threads
🟣 PURPLE (#9C27B0)    = Storage/Repository
🔴 RED (#F44336)       = Error/Failure/Stop
💗 PINK (#E91E63)      = Events
```