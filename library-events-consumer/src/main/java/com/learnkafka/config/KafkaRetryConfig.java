package com.learnkafka.config;

import com.learnkafka.constant.LibraryEventsConstant;
import com.learnkafka.exception.KafkaRetryException;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka retry configuration with configurable backoff strategies.
 * Uses ConsumerRecordRecoverer for custom handling when all retries are exhausted.
 * <p>
 * Retry Strategy:
 * - KafkaRetryException: 10-sec intervals, up to 5 attempts
 * - NullPointerException: Not retried (immediate failure)
 */
@Configuration
public class KafkaRetryConfig {

    private static final Logger LOGGER = LogManager.getLogger(KafkaRetryConfig.class);

    @PostConstruct
    public void init() {
        LOGGER.debug("Initializing Kafka Last Consumed Timestamp");
        LibraryEventsConstant.LAST_CONSUMED_TIMESTAMP.set(System.currentTimeMillis());
    }

    /**
     * Creates a CommonErrorHandler with custom recovery and backoff strategies.
     * This approach uses ConsumerRecordRecoverer to log when retries are exhausted.
     */
    @Bean("errorHandler")
    public CommonErrorHandler errorHandler() {
        FixedBackOff defaultBackOff = createDefaultBackoff();
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(createRecoverer(), defaultBackOff);

        // Configure retryable and non-retryable exceptions
        errorHandler.addRetryableExceptions(KafkaRetryException.class);
        errorHandler.addNotRetryableExceptions(NullPointerException.class);

        // Optional: Reset retry counter when exception type changes during retries
        errorHandler.setResetStateOnExceptionChange(true);
        // Optional: Do not reset retry counter if recovery fails, to allow for proper logging of exhausted retries
        errorHandler.setResetStateOnRecoveryFailure(false);

        return errorHandler;
    }

    /**
     * Creates the consumer record recoverer that handles messages after all retries are exhausted.
     * Logs detailed information about the failed message.
     */
    private ConsumerRecordRecoverer createRecoverer() {
        return this::logExhaustedRetries;
    }

    /**
     * Creates default backoff strategy.
     * Used for general exceptions when no specific backoff is configured.
     */
    private FixedBackOff createDefaultBackoff() {
        LOGGER.info("Configuring default backoff: {} ms interval, {} attempts", LibraryEventsConstant.RETRY_INTERVAL_10SEC, LibraryEventsConstant.RETRY_INTERVAL_ATTEMPTS);
        return new FixedBackOff(
                LibraryEventsConstant.RETRY_INTERVAL_10SEC,
                LibraryEventsConstant.RETRY_INTERVAL_ATTEMPTS);
    }

    /**
     * Logs comprehensive information when all retries are exhausted.
     * This is the fail-safe mechanism to track permanently failed messages.
     */
    private void logExhaustedRetries(ConsumerRecord<?, ?> record, Exception exception) {
        LOGGER.error("===============================================================");
        LOGGER.error("❌ EXHAUSTED ALL RETRIES - FAIL-SAFE MECHANISM TRIGGERED ❌");
        LOGGER.error("===============================================================");
        LOGGER.error("Topic: {}", record.topic());
        LOGGER.error("Partition: {}", record.partition());
        LOGGER.error("Offset: {}", record.offset());
        LOGGER.error("Message processing failed permanently after maximum retries.");
        LOGGER.error("Exception Message: {}", exception.getMessage());
        LOGGER.error("===============================================================");
    }

}
