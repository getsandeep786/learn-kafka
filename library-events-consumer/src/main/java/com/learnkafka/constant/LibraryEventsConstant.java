package com.learnkafka.constant;

import java.util.concurrent.atomic.AtomicLong;

public class LibraryEventsConstant {

    public static AtomicLong LAST_CONSUMED_TIMESTAMP = new AtomicLong();

    public static final long RETRY_INTERVAL_10SEC = 10000;

    public static final long RETRY_INTERVAL_ATTEMPTS = 5;
}
