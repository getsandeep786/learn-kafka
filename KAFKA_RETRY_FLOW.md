# Kafka Flow - Simplified

## 1. SIMPLE MESSAGE FLOW

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
         │         ┌───────────────────────┐
         │         │ Is KafkaRetryException?
         │         └────────┬──────┬───────┘
         │                  │      │
         │            YES   │ NO   │
         │                  │      │
         │                  ▼      ▼
         │         ┌──────────┐ ┌──────────┐
         │         │ Extended │ │ Default  │
         │         │ Backoff  │ │ Backoff  │
         │         │          │ │          │
         │         │ 2 min    │ │ 1 min    │
         │         │ 100 max  │ │ 10 max   │
         │         └─────┬────┘ └────┬─────┘
         │               │            │
         │               └────┬───────┘
         │                    │
         │                    ▼
         │         ┌─────────────────────┐
         │         │ Sleep for interval  │
         │         │ (1 or 2 minutes)    │
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
                    │  12345 → 12346               │
                    │  Ready for next message      │
                    └──────────────────────────────┘
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

