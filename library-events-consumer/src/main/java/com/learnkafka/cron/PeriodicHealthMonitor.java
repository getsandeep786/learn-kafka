package com.learnkafka.cron;

import com.learnkafka.util.KafkaConsumerActivityMonitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PeriodicHealthMonitor {

    private static final Logger LOGGER = LogManager.getLogger(PeriodicHealthMonitor.class);

    // Health check interval: 10 minutes
    private static final long HEALTH_CHECK_INTERVAL_MS = 10 * 60 * 1000;

    @Autowired
    private KafkaConsumerActivityMonitor activityMonitor;

    /**
     * Periodically checks the health of the Kafka consumer.
     * Runs every 10 minutes to validate consumer activity status.
     */
    @Scheduled(fixedRate = HEALTH_CHECK_INTERVAL_MS)
    public void checkConsumerHealth() {
        try {
            LOGGER.info("Running periodic health check for Kafka consumer...");
            var health = activityMonitor.health();
            LOGGER.info("Consumer health status: {}", health.getStatus());
        } catch (Exception e) {
            LOGGER.error("Error during periodic health check", e);
        }
    }

}
