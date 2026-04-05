# Kafka Retry Flow - Complete Guide

## Current Configuration Summary

```
Framework: Spring Kafka with Fixed Backoff Strategy
Handler: ConsumerRecordRecoverer for fail-safe logging
Retry Strategy:
  - KafkaRetryException: 10-second intervals, up to 5 attempts
  - NullPointerException: NO RETRY (immediate failure)
  - Other Exceptions: DEFAULT strategy
```

---

## 1. SIMPLE MESSAGE FLOW (Updated)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  KAFKA CONSUMER THREAD RECEIVES MESSAGE                │
│                                                         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
           ┌─────────────────────┐
           │  @KafkaListener     │
           │  Try to Process     │
           │  Message            │
           └────────┬────────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
         ▼                     ▼
      ✅ SUCCESS           ❌ ERROR
         │                     │
         │                     ▼
         │         ┌──────────────────────┐
         │         │ Exception Type Check │
         │         └────┬────┬────────┬──┘
         │              │    │        │
         │         YES  │    │ NO     │
         │              │    │        │
         │              ▼    ▼        ▼
         │         ┌─────┐ ┌─────┐ ┌────────────┐
         │         │NULL │ │KAFKA│ │   OTHER    │
         │         │PTR? │ │RET? │ │ EXCEPTION  │
         │         └──┬──┘ └──┬──┘ └─────┬──────┘
         │            │       │         │
         │         FAIL   RETRY    RETRY
         │         (No)  (Yes)     (Yes)
         │            │       │         │
         │            ▼       ▼         ▼
         │         ┌────────────────────┐
         │         │ Apply Backoff      │
         │         │ Config             │
         │         │                    │
         │         │ 10 sec, 5 attempts │
         │         └────────┬───────────┘
         │                  │
         │                  ▼
         │         ┌─────────────────────┐
         │         │ Sleep for interval  │
         │         │ (10 seconds)        │
         │         └────────┬────────────┘
         │                  │
         │                  ▼
         │         ┌─────────────────────┐
         │         │ Try Again           │
         │         │ (Retry Attempt)     │
         │         └────────┬────────────┘
         │                  │
         │         ┌────────┴──────────┐
         │         │                   │
         │         ▼                   ▼
         │      ✅ SUCCESS        ❌ FAILED AGAIN
         │         │                   │
         │         │         Has more retries left?
         │         │              │        │
         │         │         YES  │ NO     │
         │         │              │        │
         │         │              ▼        ▼
         │         │         Loop back   Give up
         │         │         (Sleep &    (Max attempts
         │         │          Retry)      exhausted)
         │         │                      │
         │         └──────────┬───────────┘
         │                    │
         └────────────────────┼─────────────────┐
                              │                 │
                              ▼                 ▼
                    ┌──────────────────────────────┐
                    │  LOG FAIL-SAFE MESSAGE      │
                    │  (if all retries exhausted) │
                    │                              │
                    │  Details logged:            │
                    │  - Topic, Partition, Offset │
                    │  - Exception info           │
                    └──────────────────┬──────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────┐
                    │  SEND ACKNOWLEDGMENT TO KAFKA │
                    │  (Message marked consumed)    │
                    │                              │
                    │  AckMode = RECORD            │
                    │  (Ack after each message)    │
                    └──────────────────┬───────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────┐
                    │  OFFSET UPDATED              │
                    │  Ready for next message      │
                    └──────────────────────────────┘
```

---

## 2. RETRY ATTEMPT TIMELINE (Updated - 10 Seconds)

```
TIME         WHAT HAPPENS
──────────────────────────────────────────

T0:00        Message received
             Start processing

T0:01        ❌ ERROR! 
             Exception occurs

T0:01        Decision made:
             → Is KafkaRetryException?
             → Use 10-second interval
              
T0:01-11     ⏰ SLEEP (10 seconds)

T0:11        Try again (Attempt 1 of 5)

T0:12        ❌ Still failing

T0:12-22     ⏰ SLEEP (10 seconds)

T0:22        Try again (Attempt 2 of 5)

T0:23        ❌ Still failing

T0:23-33     ⏰ SLEEP (10 seconds)

T0:33        Try again (Attempt 3 of 5)

T0:34        ✅ SUCCESS!

T0:34        Log fail-safe message:
             "EXHAUSTED ALL RETRIES - FAIL-SAFE 
              MECHANISM TRIGGERED"
             (only if all 5 attempts failed)

T0:34        Send ACK to Kafka
             Message marked as consumed

T0:34+       Ready for next message
```

---

## 3. DECISION TREE (Updated)

```
                     Exception Occurs?
                            │
                     ┌──────┴──────┐
                     NO            YES
                     │             │
                     ▼             ▼
               ✅ SUCCESS      Exception Type?
                     │             │
                     │         ┌───┼───┬─────────┐
                     │         │   │   │         │
                     │      NULL  │   │      OTHER
                     │      PTR?  │   │   EXCEPTION
                     │         │  │   │         │
                     │        YES NO   │         │
                     │         │   │   │         │
                     │         ▼   ▼   ▼         │
                     │       FAIL RETRY RETRY    │
                     │       (No) (10s) (10s)    │
                     │         │   │    │        │
                     │         │   └────┴────┐   │
                     │         │             │   │
                     │         │             ▼   │
                     │         │        Max 5 attempts?
                     │         │             │
                     │         │         ┌───┴───┐
                     │         │        NO      YES
                     │         │         │       │
                     │         │         ▼       ▼
                     │         │       Retry  EXHAUSTED
                     │         │       (wait & (Log Msg)
                     │         │        retry)
                     │         │         │       │
                     │         └─────────┼───┬───┘
                     │                   │   │
                     └─────────────┬─────┘   │
                                   │         │
                                   ▼         ▼
                           ┌─────────────────────┐
                           │ Send ACK to Kafka   │
                           │ Offset advances     │
                           │ Next message ready  │
                           └─────────────────────┘
```

---

## 4. CURRENT RETRY CONFIGURATION

```
CONFIGURATION: KafkaRetryConfig.java
├─ Framework: Spring Kafka with DefaultErrorHandler
├─ Recovery: ConsumerRecordRecoverer (logs on exhaustion)
├─ Reset on exception change: TRUE
└─ Reset on recovery failure: FALSE
    (Allows proper tracking of exhausted retries)

RETRY STRATEGY:
┌─────────────────────────────────┐
│ Exception Type: KafkaRetryException
├─────────────────────────────────┤
│ Interval: 10 seconds (10000 ms)  │
│ Max Attempts: 5                  │
│ Total max time: ~50 seconds      │
│ Purpose: Handle application      │
│          exceptions              │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ Exception Type: NullPointerException
├─────────────────────────────────┤
│ Interval: N/A                   │
│ Max Attempts: 0 (NO RETRY)       │
│ Purpose: Fail immediately       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ Exception Type: Other            │
├─────────────────────────────────┤
│ Interval: 10 seconds             │
│ Max Attempts: 5                  │
│ Total max time: ~50 seconds      │
│ Purpose: Default retry behavior  │
└─────────────────────────────────┘
```

---

## 5. FAIL-SAFE MECHANISM (NEW)

```
When all retries are exhausted (max 5 attempts failed):

ConsumerRecordRecoverer.logExhaustedRetries() is called:

LOGS:
===============================================================
❌ EXHAUSTED ALL RETRIES - FAIL-SAFE MECHANISM TRIGGERED ❌
===============================================================
Topic: library-events
Partition: 0
Offset: 12345
Message processing failed permanently after maximum retries.
Exception Message: Connection timeout
===============================================================

This ensures:
✅ Every permanently failed message is tracked
✅ Error details are captured for debugging
✅ No message failure goes unnoticed
✅ Audit trail for failed messages
```

---

## 6. WHAT HAPPENS STEP BY STEP

### Without Error (Happy Path)
```
1. Message arrives
   ↓
2. Process message
   ↓
3. ✅ Success - no exception
   ↓
4. Send ACK to Kafka
   ↓
5. Get next message
```

### With Non-Retryable Error (NullPointerException)
```
1. Message arrives
   ↓
2. Process message
   ↓
3. ❌ NullPointerException thrown
   ↓
4. Check: Is it retryable?
   ├─ NO (Not in retryable list)
   └─ Fail immediately
   ↓
5. Send ACK to Kafka (message still consumed)
   ↓
6. Get next message
   (Error logged by Spring, not our custom logger)
```

### With KafkaRetryException (Retry Path)
```
1. Message arrives
   ↓
2. Process message
   ↓
3. ❌ KafkaRetryException thrown
   ↓
4. Check: Is it retryable?
   ├─ YES → Use 10-second backoff
   └─ Max 5 attempts
   ↓
5. Attempt 1: Sleep 10s → Try → ❌ FAIL
   Attempt 2: Sleep 10s → Try → ❌ FAIL
   Attempt 3: Sleep 10s → Try → ❌ FAIL
   Attempt 4: Sleep 10s → Try → ❌ FAIL
   Attempt 5: Sleep 10s → Try → ❌ FAIL
   ↓
6. All 5 retries exhausted
   ↓
7. Call ConsumerRecordRecoverer.logExhaustedRetries()
   ├─ Logs: Topic, Partition, Offset
   ├─ Logs: Exception details
   └─ Logs: "EXHAUSTED ALL RETRIES" message
   ↓
8. Send ACK to Kafka (message marked as consumed)
   ↓
9. Get next message
```

---

## 7. THREAD BEHAVIOR

```
SAME KAFKA CONSUMER THREAD HANDLES EVERYTHING:

T0:00 - Start processing message
        └─ Try to process business logic

T0:01 - Error occurs
        └─ Thread enters retry mode
           (No other messages processed)

T0:01-11 - Thread sleeps (10 seconds)
           └─ Waiting for first retry

T0:11 - Wake up, Attempt 1 retry
        └─ Same message, same thread

T0:12 - Error again
        └─ Thread sleeps again (10 seconds)

T0:22 - Attempt 2 retry
        └─ Still failing

... repeat until success or 5 attempts ...

T0:51 - Attempt 5 FAILS
        └─ All retries exhausted

T0:51 - Log fail-safe message
        └─ Detailed error logging

T0:51 - Thread ready for next message
        └─ Send ACK and get next message

KEY POINT:
Same thread blocks during all retries.
Other consumer threads can process other 
messages in parallel (if concurrency > 1)
```

---

## 8. ACKNOWLEDGMENT EXPLAINED

```
AckMode = RECORD means:
"Acknowledge after EACH message"

Timing: AFTER message processing ends (success or failure)

Scenarios:

✅ SUCCESS (first try)
   ↓
   Acknowledge immediately
   ↓
   Kafka: "OK, mark as consumed"
   ↓
   Offset: 12345 → 12346

✅ SUCCESS (after retries)
   ↓
   Retried multiple times, then succeeded
   ↓
   Acknowledge
   ↓
   Kafka: "OK, mark as consumed"
   ↓
   Offset: 12345 → 12346

❌ FAILED (5 retries exhausted)
   ↓
   Log fail-safe message
   ↓
   Still acknowledge (mark as consumed)
   ↓
   Kafka: "OK, mark as consumed"
   ↓
   Offset: 12345 → 12346
   ↓
   Next message processed

Why ACK even if FAILED?
- Prevents infinite retry loops
- Allows partition to continue
- Error is logged and tracked
- Message is not lost (in logs)
```

---

## 9. KEY POINTS (REMEMBER THESE)

```
1️⃣  SAME THREAD
    All retries happen on the SAME Kafka consumer thread
    Thread is BLOCKED during retries

2️⃣  SAME MESSAGE
    The exact same message is retried
    Not discarded, not sent elsewhere

3️⃣  BACKOFF INTERVAL (10 seconds)
    Thread SLEEPS between retries
    Prevents hammering the service
    Allows time for services to recover

4️⃣  EXCEPTION TYPE MATTERS
    NullPointerException → NO RETRY (immediate failure)
    KafkaRetryException → RETRY 5 times (10 sec interval)
    Other exceptions    → RETRY 5 times (10 sec interval)

5️⃣  MAX 5 ATTEMPTS
    Total retry time: ~50 seconds (worst case)
    Then give up and move to next message

6️⃣  FAIL-SAFE LOGGING
    When all 5 retries exhausted:
    └─ Detailed log with Topic, Partition, Offset
    └─ Exception type and message logged
    └─ "EXHAUSTED ALL RETRIES" message shown

7️⃣  ALWAYS ACKNOWLEDGE
    Even if all retries fail, message is still acknowledged
    Prevents infinite loops
    Error is tracked in logs

8️⃣  NO OFFSET COMMIT DURING RETRY
    During retry: Offset not committed yet
    After done: Offset committed, ready for next

9️⃣  PARTITION PAUSED (implicit)
    During retry, this consumer thread can't get new messages
    Other threads can still process their messages
    Maintains message order within partition
```

---

## 10. IMPLEMENTATION DETAILS

```
File: KafkaRetryConfig.java

Methods:
├─ errorHandler()
│  └─ Creates CommonErrorHandler bean
│     └─ Sets up recovery and backoff
│
├─ createRecoverer()
│  └─ Returns ConsumerRecordRecoverer
│     └─ Calls logExhaustedRetries() when retries exhausted
│
├─ createDefaultBackoff()
│  └─ Returns FixedBackOff(10 sec, 5 attempts)
│
└─ logExhaustedRetries(record, exception)
   └─ Logs comprehensive error info:
      ├─ Topic, Partition, Offset
      ├─ Exception message
      └─ "EXHAUSTED ALL RETRIES" banner

Properties:
├─ setResetStateOnExceptionChange(true)
│  └─ Reset retry counter if exception type changes
│
└─ setResetStateOnRecoveryFailure(false)
   └─ Keep tracking for proper exhaustion logging
```

---

## 11. COMPLETE SIMPLIFIED EXAMPLE

```
Event: Processing a library book creation message

T0:00 ───────────────────────────────────────────
      Message: {bookId: 123, action: "CREATE"}
      Thread: kafka-consumer-0
      
      START: @KafkaListener method called

T0:02 ───────────────────────────────────────────
      Attempt 1: Save to database
      ❌ FAILED: KafkaRetryException("DB connection timeout")
      
      Type Check: Is it KafkaRetryException?
      Answer: YES
      Decision: Retry with 10-second interval

T0:02-12 ─────────────────────────────────────
      Thread sleeping for 10 seconds
      (Waiting to retry)

T0:12 ───────────────────────────────────────────
      Attempt 2: Save to database
      ❌ FAILED: KafkaRetryException("DB still down")
      
      Try again? YES (Attempt 2 of 5)

T0:12-22 ─────────────────────────────────────
      Thread sleeping for 10 seconds

T0:22 ───────────────────────────────────────────
      Attempt 3: Save to database
      ❌ FAILED: KafkaRetryException("DB still down")

T0:22-32 ─────────────────────────────────────
      Thread sleeping for 10 seconds

T0:32 ───────────────────────────────────────────
      Attempt 4: Save to database
      ❌ FAILED: KafkaRetryException("DB still down")

T0:32-42 ─────────────────────────────────────
      Thread sleeping for 10 seconds

T0:42 ───────────────────────────────────────────
      Attempt 5: Save to database
      ❌ FAILED: KafkaRetryException("DB still down")
      
      All 5 attempts exhausted!

T0:42 ───────────────────────────────────────────
      Call: ConsumerRecordRecoverer.logExhaustedRetries()
      
      LOGGED:
      ===============================================================
      ❌ EXHAUSTED ALL RETRIES - FAIL-SAFE MECHANISM TRIGGERED ❌
      ===============================================================
      Topic: library-events
      Partition: 0
      Offset: 12345
      Message processing failed permanently after maximum retries.
      Exception Message: DB connection timeout
      ===============================================================

T0:42 ───────────────────────────────────────────
      Send ACK to Kafka
      
      Message marked as: CONSUMED
      Offset updated: 12345 → 12346

T0:42 ───────────────────────────────────────────
      READY FOR NEXT MESSAGE
      Thread polls for next message
```

---

## 12. COMPARISON: OLD vs NEW CONFIG

```
OLD CONFIG:
├─ KafkaRetryException: 2 min interval, 100 attempts (~200 min max)
├─ Other exceptions: 1 min interval, 10 attempts (~10 min max)
└─ Recovery: Mixed approach

NEW CONFIG:
├─ KafkaRetryException: 10 sec interval, 5 attempts (~50 sec max)
├─ NullPointerException: NO RETRY (fail immediately)
├─ Other exceptions: 10 sec interval, 5 attempts (~50 sec max)
└─ Recovery: ConsumerRecordRecoverer with detailed logging

BENEFITS OF NEW CONFIG:
✅ Much faster failure detection (~50s vs 200s)
✅ Explicit handling of NullPointerException
✅ Clearer fail-safe logging
✅ Better for quick-fail scenarios
✅ Easier to debug with detailed logs
```

---

## 13. QUICK REFERENCE

```
What is KafkaRetryConfig?
→ Configures HOW to retry failed messages

What happens when error occurs?
→ Check exception type → Apply backoff → Sleep → Retry

When is message acknowledged?
→ After success OR after max retries exhausted

How many times does it retry?
→ KafkaRetryException: up to 5 times
→ Other exceptions: up to 5 times  
→ NullPointerException: 0 times (no retry)

How long does it wait between retries?
→ All types: 10 seconds

What if retries still fail?
→ Message is acknowledged (marked as consumed)
→ Fail-safe log message is written
→ Error details captured
→ Next message is processed

Can the consumer crash during retry?
→ Yes, then new consumer restarts from same offset
→ Message will be reprocessed

Where does fail-safe message get logged?
→ ConsumerRecordRecoverer.logExhaustedRetries()
→ Logged via LOG4J2
→ Can be monitored/searched in logs
```

---

## 14. VISUAL SUMMARY (Updated)

```
┌──────────┐
│ Message  │
│ Received │
└────┬─────┘
     │
     ▼
┌──────────────┐
│   Try to     │
│   Process    │
└────┬─────────┘
     │
  Success? ──YES──→ ✅ ACK & Next Message
     │
     NO
     │
     ▼
┌────────────────────────┐
│ Exception Type Check   │
└──┬──────┬──────┬──────┘
   │      │      │
 NULL   KAFKA  OTHER
  PTR?  RET?   EXC?
   │      │      │
  FAIL  RETRY  RETRY
  (No)  (10s)  (10s)
   │      │      │
   └──┬───┴──┬───┘
      │      │
      ▼      ▼
┌──────────────────┐
│ Apply 10s        │
│ backoff, max 5   │
│ attempts         │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│ Sleep            │
│ 10 seconds       │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│ Retry Again      │
└────┬─────────────┘
     │
  Max 5? ──YES──→ ❌ Log Fail-Safe
  Retries?            ACK & Next
     │
     NO
     │
  Success? ──YES──→ ✅ ACK & Next Message
     │
     NO
     │
  Loop back to Sleep
```


---

## 2. RETRY ATTEMPT TIMELINE

```
TIME         WHAT HAPPENS
──────────────────────────────────────────

T0:00        Message received
             Start processing

T0:02        ❌ ERROR! 
             Exception occurs

T0:02        Decision made:
             → KafkaRetryException?
             → Use Extended Backoff (2 min)
             
T0:02-04     ⏰ SLEEP (2 minutes)

T0:04        Try again (Attempt 1)

T0:06        ❌ Still failing

T0:06-08     ⏰ SLEEP (2 minutes)

T0:08        Try again (Attempt 2)

T0:10        ❌ Still failing

T0:10-12     ⏰ SLEEP (2 minutes)

T0:12        Try again (Attempt 3)

T0:14        ✅ SUCCESS!

T0:14        Send ACK to Kafka
             Message marked as consumed

T0:14+       Ready for next message
```

---

## 3. DECISION TREE

```
                    Exception Occurs?
                           │
                    ┌──────┴──────┐
                    NO            YES
                    │             │
                    ▼             ▼
              ✅ SUCCESS      Is it
                    │      KafkaRetryException?
                    │             │
                    │         ┌───┴───┐
                    │        YES     NO
                    │         │       │
                    │         ▼       ▼
                    │       Use    Use
                    │      Extended Default
                    │      Backoff  Backoff
                    │      (2 min)  (1 min)
                    │      (100)    (10)
                    │         │       │
                    │         └───┬───┘
                    │             │
                    │             ▼
                    │        Max attempts
                    │        exhausted?
                    │             │
                    │         ┌───┴───┐
                    │        NO      YES
                    │         │       │
                    │         ▼       ▼
                    │       Retry  Give up
                    │       (wait & sleep)
                    │       retry
                    │         │       │
                    │         └───┬───┘
                    │             │
                    └─────────┬───┘
                              │
                              ▼
                        Send ACK to Kafka
                        Offset advances
                        Next message ready
```

---

## 4. WHAT HAPPENS STEP BY STEP

### Without Error (Happy Path)
```
1. Message arrives
   ↓
2. Process message
   ↓
3. ✅ Success - no exception
   ↓
4. Send ACK to Kafka
   ↓
5. Get next message
```

### With Error (Retry Path)
```
1. Message arrives
   ↓
2. Process message
   ↓
3. ❌ Exception thrown
   ↓
4. Is KafkaRetryException?
   ├─ YES → Wait 2 min, Retry (up to 100 times)
   └─ NO  → Wait 1 min, Retry (up to 10 times)
   ↓
5. Keep retrying until:
   ├─ ✅ Success, OR
   └─ Max attempts reached
   ↓
6. Send ACK to Kafka (either way)
   ↓
7. Get next message
```

---

## 5. THREAD BEHAVIOR

```
SAME KAFKA CONSUMER THREAD HANDLES EVERYTHING:

T0:00 - Start processing message
        └─ Try to save to database

T0:02 - Error occurs
        └─ Thread enters retry mode
           (No other messages processed during this time)

T0:02-04 - Thread sleeps
           └─ Waiting for retry

T0:04 - Wake up, try again
        └─ Same message, same thread

T0:06 - Error again
        └─ Thread sleeps again

... repeat until success or max retries ...

T0:14 - Finally done (success or gave up)
        └─ Thread ready for next message
```

---

## 6. ACKNOWLEDGMENT EXPLAINED

```
AckMode = RECORD means:
"Acknowledge after EACH message"

Timing: AFTER message processing ends

Scenarios:

✅ SUCCESS → ACK immediately
   ↓
   Kafka: "OK, I got it. Move to next message"
   ↓
   Offset: 12345 → 12346

❌ FAILED (max retries) → ACK still sent
   ↓
   Kafka: "OK, I got it. Mark as consumed"
   ↓
   Offset: 12345 → 12346

Why ACK even if failed?
- Prevents infinite retry loops
- Allows partition to continue
- Error is logged, not lost
```

---

## 7. RETRY STRATEGIES COMPARED

```
EXTENDED BACKOFF (for KafkaRetryException)
├─ Interval: 2 minutes
├─ Max Attempts: 100
├─ Total Time: ~200 minutes (worst case)
└─ Use: Temporary issues (timeout, db down, etc.)

DEFAULT BACKOFF (for other exceptions)
├─ Interval: 1 minute
├─ Max Attempts: 10
├─ Total Time: ~10 minutes (worst case)
└─ Use: General errors
```

---

## 8. COMPLETE SIMPLIFIED EXAMPLE

```
Event: Processing a library book creation message

T0:00 ───────────────────────────────────────────
      Message: {bookId: 123, action: "CREATE"}
      Thread: kafka-consumer-0
      
      START: @KafkaListener method called

T0:01 ───────────────────────────────────────────
      Attempt 1: Save to database
      ❌ FAILED: "Connection timeout"
      
      Type Check: Is it KafkaRetryException?
      Answer: YES
      Decision: Use Extended Backoff (2 min interval)

T0:01-03 ──────────────────────────────────────
      Thread sleeping for 2 minutes
      (Waiting to retry)

T0:03 ───────────────────────────────────────────
      Attempt 2: Save to database
      ❌ FAILED: "Connection timeout"
      
      Try again? YES (Attempt 2 of 100)

T0:03-05 ──────────────────────────────────────
      Thread sleeping for 2 minutes

T0:05 ───────────────────────────────────────────
      Attempt 3: Save to database
      ✅ SUCCESS! Book saved.
      
      Exit retry loop

T0:05 ───────────────────────────────────────────
      Send ACK to Kafka
      
      Message marked as: CONSUMED
      Offset updated: 12345 → 12346

T0:05 ───────────────────────────────────────────
      READY FOR NEXT MESSAGE
      Thread polls for next message
```

---

## 9. KEY POINTS (REMEMBER THESE)

```
1️⃣  SAME THREAD
    All retries happen on the SAME Kafka consumer thread
    No thread pool, no async

2️⃣  SAME MESSAGE
    The exact same message is retried
    Not discarded, not sent to different processor

3️⃣  BACKOFF INTERVAL
    Thread SLEEPS between retries
    Prevents hammering the service

4️⃣  EXCEPTION TYPE MATTERS
    KafkaRetryException → More patient (100 retries, 2 min interval)
    Other exceptions → Less patient (10 retries, 1 min interval)

5️⃣  ALWAYS ACKNOWLEDGE
    Even if all retries fail, message is still acknowledged
    Prevents infinite loops

6️⃣  PARTITION PAUSED
    During retry, this partition gets no new messages
    Maintains message order

7️⃣  OFFSET ONLY COMMITS AFTER
    During retry: Offset not committed yet
    After done: Offset committed, move to next message
```

---

## 10. QUICK REFERENCE

```
What is KafkaRetryConfig?
→ Configures HOW to retry failed messages

What is KafkaConsumerConfig?
→ Configures consumer properties and uses error handler

What happens when error occurs?
→ Exception caught → Backoff strategy selected → Wait → Retry

When is message acknowledged?
→ After success OR after max retries exhausted

How many times does it retry?
→ KafkaRetryException: up to 100 times
→ Other exceptions: up to 10 times

How long does it wait between retries?
→ KafkaRetryException: 2 minutes
→ Other exceptions: 1 minute

What if retries still fail?
→ Message is acknowledged (marked as consumed)
→ Error is logged
→ Next message is processed

Can the consumer crash during retry?
→ Yes, then new consumer restarts from same offset
→ Message will be reprocessed
```

---

## 11. VISUAL SUMMARY

```
┌──────────┐
│ Message  │
│ Received │
└────┬─────┘
     │
     ▼
┌──────────────┐
│   Try to     │
│   Process    │
└────┬─────────┘
     │
  Success? ──YES──→ ✅ ACK & Next Message
     │
     NO
     │
     ▼
┌──────────────────┐
│ Retry Strategy   │
│ Selected         │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│ Sleep for        │
│ interval         │
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│ Retry Again      │
└────┬─────────────┘
     │
  Max Retries? ──YES──→ ❌ ACK & Next Message
  Reached?
     │
     NO
     │
  Success? ──YES──→ ✅ ACK & Next Message
     │
     NO
     │
  Loop back to Sleep
```

