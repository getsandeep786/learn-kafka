package com.learnkafka.config;

import com.learnkafka.constant.LibraryEventsConstant;
import com.learnkafka.exception.KafkaRetryException;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka retry configuration with Fixed Backoff strategy.
 * Provides error handling for Kafka consumer with configurable retry intervals and max attempts.
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
     * Creates a CommonErrorHandler bean with Fixed Backoff retry strategy.
     * <p>
     * - Retryable exceptions: KafkaRetryException with 1-minute intervals and up to 10 attempts
     * - Non-retryable exceptions: NullPointerException (stops retry)
     * - Default behavior: 2-minute intervals with up to 25 attempts
     *
     * @return CommonErrorHandler configured with backoff strategy
     */
    @Bean("errorHandler")
    public CommonErrorHandler errorHandler() {
        DefaultErrorHandler errorHandler = createDefaultErrorHandler();
        configureRetryableExceptions(errorHandler);
        configureBackoffFunction(errorHandler);
        LOGGER.info("===============================================================");
        return errorHandler;
    }

    /**
     * Creates DefaultErrorHandler with default fixed backoff strategy.
     */
    private DefaultErrorHandler createDefaultErrorHandler() {
        FixedBackOff defaultBackOff = new FixedBackOff(
                LibraryEventsConstant.RETRY_INTERVAL_1MINS,
                LibraryEventsConstant.DEFAULT_RETRY_INTERVAL_ATTEMPTS);
        return new DefaultErrorHandler(defaultBackOff);
    }

    /**
     * Configures which exceptions should trigger retries and which should not.
     */
    private void configureRetryableExceptions(DefaultErrorHandler errorHandler) {
        errorHandler.addRetryableExceptions(KafkaRetryException.class);
        errorHandler.addNotRetryableExceptions(NullPointerException.class);
    }

    /**
     * Configures custom backoff function based on exception type.
     * - KafkaRetryException: Uses 2-minute interval with more retry attempts
     * - Other exceptions: Uses default 1-minute interval with default attempts
     */
    private void configureBackoffFunction(DefaultErrorHandler errorHandler) {
        errorHandler.setBackOffFunction((consumerRecord, exception) -> {
            if (exception instanceof KafkaRetryException) {
                return createExtendedBackoff();
            } else {
                return createDefaultBackoff();
            }
        });
    }

    /**
     * Creates extended backoff for KafkaRetryException (1-minute interval, 10 attempts).
     */
    private FixedBackOff createExtendedBackoff() {
        LOGGER.warn("🔧 Creating Extended Backoff Strategy");
        LOGGER.warn("   Interval: {} ms (2 minutes)", LibraryEventsConstant.RETRY_INTERVAL_1MINS);
        LOGGER.warn("   Max Attempts: {}", LibraryEventsConstant.DEFAULT_RETRY_INTERVAL_ATTEMPTS);
        return new FixedBackOff(
                LibraryEventsConstant.RETRY_INTERVAL_1MINS,
                LibraryEventsConstant.DEFAULT_RETRY_INTERVAL_ATTEMPTS);
    }

    /**
     * Creates default backoff (2-minute interval, 25 attempts).
     */
    private FixedBackOff createDefaultBackoff() {
        LOGGER.warn("🔧 Creating Default Backoff Strategy");
        LOGGER.warn("   Interval: {} ms (1 minute)", LibraryEventsConstant.RETRY_INTERVAL_2MINS);
        LOGGER.warn("   Max Attempts: {}", LibraryEventsConstant.RETRY_INTERVAL_ATTEMPTS);
        return new FixedBackOff(
                LibraryEventsConstant.RETRY_INTERVAL_2MINS,
                LibraryEventsConstant.RETRY_INTERVAL_ATTEMPTS);
    }

}
