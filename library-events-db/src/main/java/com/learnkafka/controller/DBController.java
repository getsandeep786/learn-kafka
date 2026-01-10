package com.learnkafka.controller;

import com.learnkafka.dto.BookDTO;
import com.learnkafka.dto.LibraryEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DBController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBController.class);

    @GetMapping("/v1/library/event/{id}")
    public ResponseEntity<LibraryEventDTO> postLibraryEvent(@PathVariable Long id) {
        LOGGER.info("Inside Controller");
        LOGGER.info("LibraryEvent Id: {} ", id);
        BookDTO book = new BookDTO("7", "Kafka Using Spring Boot 4.X", "Sandeep N");
        LibraryEventDTO libraryEvent = new LibraryEventDTO("7", book);
        //TODO: Save to DB
        return ResponseEntity.status(HttpStatus.CREATED).body(libraryEvent);
    }
}
