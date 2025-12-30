# RollingWheel 🎡

**RollingWheel** is a high-performance, low-overhead Java library designed for scheduling massive amounts of time-sensitive tasks with $O(1)$ complexity.

Traditional scheduling (like `PriorityQueue` or `DelayQueue`) operates at $O(\log n)$ for insertions. **RollingWheel** implements a **Hierarchical Timing Wheel** algorithm to ensure that whether you have 100 or 10,000,000 tasks, the performance remains constant and predictable.

## 🚀 Features

* **O(1) Insertion & Expiration**: Constant time complexity for all scheduling operations.
* **Hierarchical Precision**: Uses four levels of wheels (Seconds, Minutes, Hours, Days) to manage short-term and long-term expirations with minimal CPU wakeups.
* **Asynchronous Execution**: Decouples the internal clock from business logic using a dedicated thread pool, preventing "slow callbacks" from causing ticker drift.
* **Thread-Safe**: Fully concurrent design using Lock Striping and `ConcurrentHashMap`.
* **Tombstone Deletion**: Supports $O(1)$ task cancellation via unique IDs.
* **GC Friendly**: Uses a fixed infrastructure footprint to minimize Garbage Collection overhead and memory fragmentation.

## 🛠 How it Works

The library organizes time into circular buffers. As the system "ticks" every second:

1.  **Placement**: A task is placed in the "highest" possible wheel based on its magnitude of time (TTL).
2.  **Cascading**: As higher-level wheels (Day/Hour/Minute) turn, tasks are "demoted" to the next finer-grained wheel as they get closer to their expiration.
3.  **Execution**: When a task reaches the "Seconds" wheel and its specific slot is hit, the callback is submitted to the execution pool.

## 💻 Usage

### 1. Initialization
Define the thread pool size for your task callbacks. A larger pool is recommended if your consumers perform I/O operations.

```java
// Initialize with a pool of 20 threads for expired task execution
RollingWheel<String> myWheel = new RollingWheel<>(20);
```

### 2. Insert a Task
Schedule an item by providing the payload, TTL in seconds, and a `Consumer` callback. It returns a unique `String` ID.

```java
String taskId = myWheel.insert("SensorData_Batch_A", 3600, (data) -> {
    System.out.println("Processing expired batch: " + data);
});
```

### 3. Delete/Cancel a Task
If a task is no longer needed, cancel it using its ID. This is an $O(1)$ operation.

```java
String cancelledItem = myWheel.delete(taskId);
```

### 4. Shutdown
Always perform a graceful shutdown to ensure the executor services are terminated correctly.

```java
myWheel.shutdown();
```

## ⚙️ Technical Specifications

| Wheel | Size (Slots) | Resolution |
| :--- | :--- | :--- |
| **Second Wheel** | 60 | 1 Second |
| **Minute Wheel** | 60 | 1 Minute |
| **Hour Wheel** | 24 | 1 Hour |
| **Day Wheel** | 1000 | 1 Day |

* **Inception Epoch**: The wheel calculates indices based on a static `INCEPTION_TIMESTAMP`, ensuring deterministic bucket placement even across application restarts.
* **Locking**: Uses per-bucket synchronization to allow high-concurrency insertions without blocking the main ticker.

## 📂 Project Structure

* **RollingWheel.java**: The core engine managing the timing wheels and ticker logic.
* **WheelBucket.java**: Thread-safe container for items residing in a specific time slot.
* **BucketItem.java**: The wrapper for your payload, metadata, and callback.
* **SystemUtil.java**: Constants and time calculation utilities.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1.  **Fork** the Project
2.  Create your **Feature Branch** (`git checkout -b feature/AmazingFeature`)
3.  **Commit** your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  **Push** to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a **Pull Request**
