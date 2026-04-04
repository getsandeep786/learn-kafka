# Kafka Multi-Thread Concurrent Processing - Use Case

## SCENARIO: 100 Messages with Multiple Consumer Threads

```
You have:
- 100 messages in Kafka topic
- 4 consumer threads (concurrent.listeners = 4)
- 2 partitions in topic
- Exception occurs on 1 message

What happens?
→ Other threads continue processing
→ Only the failed message thread retries
→ Partitions process in parallel
→ NO BLOCKING!
```

---

## 1. CONCURRENT CONSUMER SETUP

### What is ConcurrentKafkaListenerContainerFactory?

```java
// From KafkaConsumerConfig.java
ConcurrentKafkaListenerContainerFactory<Object, Object> factory = 
    new ConcurrentKafkaListenerContainerFactory<>();
    
// This creates MULTIPLE consumer threads!
// Default: concurrent.listeners = 3 (configurable)
```

### Configuration for Concurrency

Add this to `application.yml`:

```yaml
spring:
  kafka:
    listener:
      concurrency: 4  # 4 concurrent threads (default is 3)
      poll-timeout: 3000  # 3 seconds between polls
```

---

## 2. 100 MESSAGES SCENARIO - Visual Timeline

### Setup:
```
Topic: library-events
├─ Partition 0: Messages 0-49
└─ Partition 1: Messages 50-99

Consumer Group: library-events-consumer-group
├─ Thread-1 → Partition 0
├─ Thread-2 → Partition 1
└─ Threads available for rebalancing
```

### Timeline - All Threads Processing Concurrently:

```
TIME      THREAD-1           THREAD-2           THREAD-3           THREAD-4
          (Partition 0)      (Partition 1)      (Standby)          (Standby)
─────────────────────────────────────────────────────────────────────────────

T0:00     Poll Msg #0        Poll Msg #50       Idle               Idle
          ↓                  ↓
T0:00     Processing #0      Processing #50

T0:01     ✅ Success #0      ✅ Success #50
          ACK #0             ACK #50
          
T0:01     Poll Msg #1        Poll Msg #51
          ↓                  ↓
T0:01     Processing #1      Processing #51

T0:02     ❌ ERROR! #1       ✅ Success #51
          Exception!         ACK #51
          
T0:02     Poll Msg #2        Poll Msg #52
          (Msg #1 retrying)  ↓
          
T0:02     Retry Mode:        Processing #52
          - Sleep 2 min
          - Partition 0
            paused
          
T0:03     [Sleeping...]      ✅ Success #52
                              ACK #52
                              
T0:03     [Sleeping...]      Poll Msg #53
                              ↓
T0:03     [Sleeping...]      Processing #53

T0:04     [Sleeping...]      ✅ Success #53
                              ACK #53

... (Meanwhile on Partition 0)

T0:04     ⏰ Retry Attempt 1  (Other messages
          Process #1 again   processed here)
          
T0:05     ✅ SUCCESS #1      Poll Msg #54
          ACK #1             ↓
                              
T0:05     Poll Msg #2        Processing #54
          ↓
          
T0:06     Processing #2      ✅ Success #54
                              ACK #54
                              
T0:07     ✅ Success #2       Poll Msg #55
          ACK #2
          
... continues until all 100 messages processed ...
```

---

## 3. DETAILED PARTITION & THREAD ASSIGNMENT

### How Messages are Distributed:

```
PARTITION 0 (50 messages: 0-49)
├─ Assigned to: Thread-1 (kafka-consumer-0)
├─ Processing order: Sequential within partition
└─ Retry affects only THIS partition

PARTITION 1 (50 messages: 50-99)
├─ Assigned to: Thread-2 (kafka-consumer-1)
├─ Processing order: Sequential within partition
└─ Independent from Partition 0

KEY POINT: Partitions process in PARALLEL
           But messages within a partition are sequential
           (maintains message order per partition)
```

### Thread Pool Information:

```
Default Configuration (if not specified):
├─ concurrent.listeners = 3
├─ Creates 3 consumer threads per listener container
└─ Each thread gets a partition to consume from

With 4 partitions and 4 listeners:
├─ Thread-1 → Partition 0
├─ Thread-2 → Partition 1
├─ Thread-3 → Partition 2
└─ Thread-4 → Partition 3

All 4 threads work INDEPENDENTLY
No blocking between threads!
```

---

## 4. RETRY SCENARIO - Multi-Thread Impact

### Message #45 (in Partition 0) Gets Exception:

```
BEFORE RETRY:

Thread-1 (Partition 0):
├─ Message #0-44: Already processed ✅
├─ Message #45: ❌ ERROR - Retrying
└─ Partition 0: PAUSED (can't get new messages until #45 done)

Thread-2 (Partition 1):
├─ Message #50-99: Continue processing normally ✅
└─ NO IMPACT - Independent thread!

Thread-3 (Partition 2):
└─ Continues normally (if exists) ✅

Thread-4 (Partition 3):
└─ Continues normally (if exists) ✅
```

### Retry Timeline:

```
T0:02  Thread-1: Message #45 fails
       ├─ Exception detected
       ├─ Retry strategy selected
       ├─ Thread enters sleep (2 minutes)
       ├─ Partition 0 PAUSED
       └─ ⏰ Waiting...

       Thread-2: Continues with #51, #52, #53...
       Thread-3: Continues with next assigned partition
       Thread-4: Continues with next assigned partition

T0:04  Thread-1: Still sleeping
       Thread-2: Already processed #51-#54 ✅

T0:04  Thread-1: Retry attempt #45
       Thread-2: Still processing #55

T0:05  Thread-1: ✅ Success #45
       └─ Partition 0 RESUMED
       └─ Ready for #46

       Thread-2: Processing #56
```

---

## 5. CONCURRENT PROCESSING FLOW

### All Threads Working in Parallel:

```
T0:00
│
├─ Thread-1: Poll → Process Msg #0 → ACK
├─ Thread-2: Poll → Process Msg #50 → ACK
├─ Thread-3: Poll → Process Msg (if 3 partitions)
└─ Thread-4: Poll → Process Msg (if 4 partitions)

T0:01
│
├─ Thread-1: Poll → Process Msg #1 → ACK
├─ Thread-2: Poll → Process Msg #51 → ACK
├─ Thread-3: Poll → Process Msg
└─ Thread-4: Poll → Process Msg

T0:02
│
├─ Thread-1: Error on Msg #1! ❌
│            ├─ Retry mode activated
│            ├─ Partition 0 paused
│            └─ Sleep for 2 minutes
│
├─ Thread-2: Poll → Process Msg #52 → ACK ✅ (NOT affected!)
├─ Thread-3: Poll → Process Msg → ACK ✅ (NOT affected!)
└─ Thread-4: Poll → Process Msg → ACK ✅ (NOT affected!)

T0:03
│
├─ Thread-1: Still sleeping (Msg #1)
├─ Thread-2: Poll → Process Msg #53 → ACK ✅
├─ Thread-3: Poll → Process Msg → ACK ✅
└─ Thread-4: Poll → Process Msg → ACK ✅

... continues independently ...

T0:04
│
├─ Thread-1: Retry Msg #1 → Success! ✅
│            └─ Resume Partition 0
├─ Thread-2: Already processed many more messages
├─ Thread-3: Continues independently
└─ Thread-4: Continues independently
```

---

## 6. 100 MESSAGE EXAMPLE - Complete Breakdown

### Initial State:
```
Total Messages: 100
Partition Distribution: 2 partitions (50 each)
Consumer Threads: 2 (one per partition)
Exception Location: Message #23 (in Partition 0)
```

### Processing Timeline:

```
THREAD-1 (Partition 0)              THREAD-2 (Partition 1)
Messages: 0-49                      Messages: 50-99
─────────────────────────────────────────────────────────

T0:00  Msg #0 → ✅                 Msg #50 → ✅
T0:01  Msg #1 → ✅                 Msg #51 → ✅
T0:02  Msg #2 → ✅                 Msg #52 → ✅
T0:03  Msg #3 → ✅                 Msg #53 → ✅
...
T0:23  Msg #23 → ❌                Msg #73 → ✅
       EXCEPTION!                   (continues normally)
       Retry mode ON
       Sleep 2 min
       Partition 0 PAUSED
       
T0:24  [Sleeping]                  Msg #74 → ✅
T0:25  [Sleeping]                  Msg #75 → ✅
...
T0:25  Retry Attempt #23 → ✅       Msg #85 → ✅
       Partition 0 RESUMED
       
T0:26  Msg #24 → ✅                Msg #86 → ✅
T0:27  Msg #25 → ✅                Msg #87 → ✅
...
T1:00  Msg #49 (final) → ✅        Msg #99 (final) → ✅

RESULT: All 100 messages processed!
Time taken: ~100 seconds (not 100+ 2min retries!)
Why? Because Thread-2 processed 50 messages while Thread-1 was retrying!
```

---

## 7. CONFIGURATION FOR MULTI-THREADING

### Update `application.yml`:

```yaml
spring:
  kafka:
    listener:
      # Number of concurrent listeners (threads per container)
      concurrency: 4
      
      # Max time to wait for messages (poll timeout)
      poll-timeout: 3000
      
      # Type of ack (RECORD = ack after each message)
      ack-mode: RECORD
      
      # Consumer poll records per fetch
      max-poll-records: 500
      
    consumer:
      # Minimum bytes before returning from poll
      fetch-min-bytes: 1
      
      # Maximum wait time for fetch
      fetch-max-wait-ms: 500
      
      # Session timeout (important for retries!)
      session-timeout-ms: 30000  # 30 seconds
      max-poll-interval-ms: 300000  # 5 minutes
```

### Update `KafkaConsumerConfig.java` (add concurrency):

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
        ConsumerFactory<Object, Object> consumerFactory) {
    
    ConcurrentKafkaListenerContainerFactory<Object, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
    
    factory.setConsumerFactory(consumerFactory);
    
    // Enable concurrent message processing
    factory.setConcurrency(4);  // 4 concurrent threads
    
    factory.getContainerProperties().setObservationEnabled(true);
    factory.getContainerProperties().setObservationRegistry(observationRegistry);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    
    factory.setCommonErrorHandler(kafkaRetryConfig.errorHandler());
    
    return factory;
}
```

---

## 8. THREAD NAMING & IDENTIFICATION

### How to Identify Which Thread is Processing:

```java
@KafkaListener(topics = "library-events", groupId = "library-events-consumer-group")
public void processLibraryEvent(
        LibraryEvent libraryEvent,
        @Header(KafkaHeaders.OFFSET) String msgOffset,
        @Header(KafkaHeaders.RECEIVED_PARTITION) String partitionId,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topicName) {
    
    // Get current thread name
    String threadName = Thread.currentThread().getName();
    long threadId = Thread.currentThread().getId();
    
    LOGGER.info("═════════════════════════════════════════════════════════");
    LOGGER.info("Thread Name: {}", threadName);
    LOGGER.info("Thread ID: {}", threadId);
    LOGGER.info("Partition: {}, Offset: {}", partitionId, msgOffset);
    LOGGER.info("Message: {}", libraryEvent);
    LOGGER.info("═════════════════════════════════════════════════════════");
    
    // Process...
    try {
        libraryService.save(libraryEvent);
        LOGGER.info("✅ SUCCESS - Thread: {}", threadName);
    } catch (KafkaRetryException e) {
        LOGGER.warn("❌ ERROR - Thread: {} will retry", threadName);
        throw e;
    }
}
```

### Log Output Example:

```
═════════════════════════════════════════════════════════
Thread Name: kafka-consumer-0
Thread ID: 23
Partition: 0, Offset: 0
Message: {bookId: 123}
═════════════════════════════════════════════════════════
✅ SUCCESS - Thread: kafka-consumer-0

═════════════════════════════════════════════════════════
Thread Name: kafka-consumer-1
Thread ID: 24
Partition: 1, Offset: 50
Message: {bookId: 124}
═════════════════════════════════════════════════════════
✅ SUCCESS - Thread: kafka-consumer-1

═════════════════════════════════════════════════════════
Thread Name: kafka-consumer-0
Thread ID: 23
Partition: 0, Offset: 23
Message: {bookId: 145}
═════════════════════════════════════════════════════════
❌ ERROR - Thread: kafka-consumer-0 will retry
(Thread sleeps, other threads continue)
```

---

## 9. KEY DIFFERENCES - Single vs Multi-Thread

### Single Thread (concurrency = 1):
```
Messages: 0, 1, 2, 3, 4, 5...100
Time: T0:00 → Process #0 (1 sec) → ACK
     T0:01 → Process #1 (1 sec) → ACK
     T0:02 → Process #2 (ERROR! - Retry mode)
              └─ Sleep 2 min
     T0:04 → Retry #2 → Success
     T0:05 → Process #3
     ...
     T2:00+ → Finish all 100

Total Time: ~100 seconds + retry delay = 120+ seconds
Problem: If message takes 2 min to retry, next 100 messages wait!
```

### Multi-Thread (concurrency = 4):
```
Thread-1 → Partition 0 (Messages 0-24)
Thread-2 → Partition 1 (Messages 25-49)
Thread-3 → Partition 2 (Messages 50-74)
Thread-4 → Partition 3 (Messages 75-99)

All 4 threads process CONCURRENTLY!

If Thread-1 has retry on Message #2:
├─ Thread-1: Sleep 2 min (for Msg #2)
├─ Thread-2: Process #25-49 independently
├─ Thread-3: Process #50-74 independently
└─ Thread-4: Process #75-99 independently

Total Time: ~25 seconds (not 100+ seconds!)
Benefit: Massive throughput improvement!
```

---

## 10. MONITORING MULTI-THREAD PROCESSING

### Log Format to Show All Threads:

```java
@KafkaListener(topics = "library-events", groupId = "library-events-consumer-group")
public void processLibraryEvent(LibraryEvent libraryEvent) {
    
    LOGGER.info("┌─────────────────────────────────────────────────────┐");
    LOGGER.info("│ THREAD: {} | OFFSET: {} | PARTITION: {} │",
            Thread.currentThread().getName(),
            libraryEvent.getOffset(),
            libraryEvent.getPartition());
    LOGGER.info("├─────────────────────────────────────────────────────┤");
    LOGGER.info("│ Processing: {} │", libraryEvent);
    LOGGER.info("└─────────────────────────────────────────────────────┘");
    
    try {
        libraryService.save(libraryEvent);
        LOGGER.info("✅ [{}] SUCCESS", Thread.currentThread().getName());
    } catch (Exception e) {
        LOGGER.warn("❌ [{}] FAILED: {}", Thread.currentThread().getName(), e.getMessage());
        throw new KafkaRetryException("Retry needed");
    }
}
```

### Output Shows Concurrent Processing:
```
┌─────────────────────────────────────────────────────┐
│ THREAD: kafka-consumer-0 | OFFSET: 0 | PARTITION: 0 │
├─────────────────────────────────────────────────────┤
│ Processing: Msg #0 │
└─────────────────────────────────────────────────────┘
✅ [kafka-consumer-0] SUCCESS

┌─────────────────────────────────────────────────────┐
│ THREAD: kafka-consumer-1 | OFFSET: 50 | PARTITION: 1 │
├─────────────────────────────────────────────────────┤
│ Processing: Msg #50 │
└─────────────────────────────────────────────────────┘
✅ [kafka-consumer-1] SUCCESS

┌─────────────────────────────────────────────────────┐
│ THREAD: kafka-consumer-0 | OFFSET: 23 | PARTITION: 0 │
├─────────────────────────────────────────────────────┤
│ Processing: Msg #23 │
└─────────────────────────────────────────────────────┘
❌ [kafka-consumer-0] FAILED: Connection timeout

(Thread-0 sleeping... Thread-1 continues)

┌─────────────────────────────────────────────────────┐
│ THREAD: kafka-consumer-1 | OFFSET: 51 | PARTITION: 1 │
├─────────────────────────────────────────────────────┤
│ Processing: Msg #51 │
└─────────────────────────────────────────────────────┘
✅ [kafka-consumer-1] SUCCESS

... (Thread-1 continues processing while Thread-0 retries) ...
```

---

## 11. SUMMARY TABLE

| Aspect | Single Thread | Multi-Thread (4) |
|--------|---------------|------------------|
| **Concurrency** | 1 thread processes all | 4 threads process in parallel |
| **100 Messages Time** | ~100+ seconds | ~25 seconds |
| **Retry Impact** | BLOCKS all messages | Only affects 1 partition |
| **Throughput** | Low | High (4x) |
| **CPU Usage** | Single core | Multiple cores |
| **Scalability** | Poor | Excellent |
| **Complexity** | Simple | Moderate |
| **Latency** | High (queued) | Low (parallel) |

---

## 12. REAL-WORLD EXAMPLE: 100 Messages, 1 Error

### Setup:
```
Total Messages: 100 (evenly distributed)
Partitions: 2
Threads: 2
Error on: Message #45 (in Partition 0)
```

### Execution:
```
T0:00  Thread-1 starts msg #0-24
       Thread-2 starts msg #50-74

T0:25  Thread-1 starts msg #25
       └─ Fails on msg #45 with exception
       └─ Retry mode: Sleep 2 min
       └─ Partition 0 PAUSED

T0:25  Thread-2 continues (not affected!)
       └─ Already processing msg #75+
       └─ Independent partition

T0:50  Thread-1 retries msg #45 → Success!
       Thread-2 finishes all remaining messages

T1:00  Both threads done
       All 100 messages processed!

Total Time: ~60 seconds (not 100+ 2min!)
Efficiency: 4x better than single thread!
```

---

## 13. KEY TAKEAWAYS

```
✅ CONCURRENT PROCESSING ENABLED

1️⃣  Multiple threads work in PARALLEL
    └─ Not sequentially

2️⃣  Retry on one thread = NO IMPACT on others
    └─ Only affects specific partition

3️⃣  Each partition has dedicated thread
    └─ Independent processing

4️⃣  Massive throughput improvement
    └─ 4 threads = ~4x faster

5️⃣  Message order maintained PER PARTITION
    └─ Across partitions: parallel

6️⃣  Retry doesn't block consumer group
    └─ Other partitions continue
    └─ Other listeners continue

7️⃣  Configure concurrency in application.yml
    └─ spring.kafka.listener.concurrency
    └─ Usually = number of partitions
```

---

## 14. FINAL ANSWER: Will Other Threads Be Blocked?

### Question:
"If Thread-1 has exception and retries, will it block Thread-2, Thread-3, Thread-4?"

### Answer:
```
❌ NO - Other threads are NOT blocked!

Why?
├─ Each thread processes independent partition
├─ Retry (sleep) happens on SAME partition only
├─ Other partitions have different threads
├─ All threads run concurrently
└─ No thread-to-thread blocking

Example:
Thread-1: Retrying Message #45 (Partition 0)
          └─ Sleeping for 2 minutes
          └─ Partition 0 PAUSED

Thread-2: Processing Message #75 (Partition 1)
          └─ Completely independent
          └─ NOT affected at all
          
Thread-3: Processing Partition 2
          └─ Independent
          
Thread-4: Processing Partition 3
          └─ Independent

All 4 threads work simultaneously!
```

**CONCURRENT PROCESSING = FULLY ENABLED** ✅

