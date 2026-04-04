package com.learnkafka.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors Kafka consumer activity and tracks inactivity duration.
 * Provides utilities to check consumer idle time and update activity timestamps.
 */
@Component
public class KafkaConsumerActivityMonitor implements HealthIndicator {

    private static final Logger LOGGER = LogManager.getLogger(KafkaConsumerActivityMonitor.class);

    private static final long INACTIVITY_THRESHOLD_MS = 10 * 60 * 1000; // 5 minutes
    private final AtomicLong lastActivityTimestamp;

    public KafkaConsumerActivityMonitor() {
        this.lastActivityTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Updates the last activity timestamp to current time.
     * Should be called when a message is successfully consumed.
     */
    public void recordActivity() {
        long currentTime = System.currentTimeMillis();
        lastActivityTimestamp.set(currentTime);
        LOGGER.info("Consumer activity recorded at: {}", currentTime);
    }

    /**
     * Gets the duration of inactivity in milliseconds.
     *
     * @return inactivity duration in milliseconds
     */
    public long getInactivityDurationMs() {
        return System.currentTimeMillis() - lastActivityTimestamp.get();
    }

    /**
     * Checks if consumer has been inactive beyond the threshold.
     *
     * @return true if consumer is inactive, false otherwise
     */
    public boolean isInactive() {
        long inactivityDuration = getInactivityDurationMs();
        boolean inactive = inactivityDuration > INACTIVITY_THRESHOLD_MS;

        if (inactive) {
            LOGGER.warn("Consumer inactivity detected. Duration: {} ms, Threshold: {} ms",
                    inactivityDuration, INACTIVITY_THRESHOLD_MS);
        }

        return inactive;
    }

    /**
     * Gets the last activity timestamp.
     *
     * @return timestamp of last recorded activity
     */
    public long getLastActivityTimestamp() {
        return lastActivityTimestamp.get();
    }

    /**
     * Resets the activity timestamp to current time.
     */
    public void reset() {
        recordActivity();
        LOGGER.info("Consumer activity monitor reset");
    }

    @Override
    public Health health() {
        long inactivityDurationMs = getInactivityDurationMs();
        long lastActivityMs = getLastActivityTimestamp();
        LocalDateTime lastActivityTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastActivityMs), ZoneId.systemDefault());
        boolean isInactive = isInactive();

        if (isInactive) {
            return Health.down()
                    .withDetail("status", "Consumer Inactive")
                    .withDetail("inactivity_duration_ms", inactivityDurationMs)
                    .withDetail("inactivity_duration_minutes", inactivityDurationMs / 60000.0)
                    .withDetail("inactivity_threshold_ms", INACTIVITY_THRESHOLD_MS)
                    .withDetail("inactivity_threshold_minutes", INACTIVITY_THRESHOLD_MS / 60000.0)
                    .withDetail("last_activity_timestamp", lastActivityMs)
                    .withDetail("last_activity_time", lastActivityTime.toString())
                    .build();
        } else {
            return Health.up()
                    .withDetail("status", "Consumer Active")
                    .withDetail("inactivity_duration_ms", inactivityDurationMs)
                    .withDetail("inactivity_duration_minutes", inactivityDurationMs / 60000.0)
                    .withDetail("inactivity_threshold_ms", INACTIVITY_THRESHOLD_MS)
                    .withDetail("inactivity_threshold_minutes", INACTIVITY_THRESHOLD_MS / 60000.0)
                    .withDetail("last_activity_timestamp", lastActivityMs)
                    .withDetail("last_activity_time", lastActivityTime.toString())
                    .build();
        }
    }
}

