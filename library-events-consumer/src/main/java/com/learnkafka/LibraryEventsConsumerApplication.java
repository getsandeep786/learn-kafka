package com.learnkafka;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LibraryEventsConsumerApplication {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventsConsumerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LibraryEventsConsumerApplication.class, args);
        LOGGER.info("Library Events Consumer Application Started...");
    }
}
