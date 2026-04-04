package com.learnkafka.constant;

import java.util.concurrent.atomic.AtomicLong;

public class LibraryEventsConstant {

    public static AtomicLong LAST_CONSUMED_TIMESTAMP = new AtomicLong();

    public static final long RETRY_INTERVAL_1MINS = 60000;

    public static final long RETRY_INTERVAL_2MINS = 120000;

    public static final long RETRY_INTERVAL_ATTEMPTS = 25;

    public static final long DEFAULT_RETRY_INTERVAL_ATTEMPTS = 10;

}
