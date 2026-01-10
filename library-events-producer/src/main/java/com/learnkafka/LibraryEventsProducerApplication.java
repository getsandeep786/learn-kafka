package com.learnkafka;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LibraryEventsProducerApplication {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventsProducerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LibraryEventsProducerApplication.class, args);
        LOGGER.info("Library Events Producer Application Started...");
    }

}
