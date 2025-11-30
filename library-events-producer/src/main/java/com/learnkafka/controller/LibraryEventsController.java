package com.learnkafka.controller;

import com.learnkafka.avro.schema.LibraryEvent;
import com.learnkafka.avro.schema.LibraryEventType;
import com.learnkafka.producer.LibraryEventProducer;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LibraryEventsController {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventsController.class);

    @Autowired
    LibraryEventProducer libraryEventProducer;

    @PostMapping("/v1/library/event")
    public ResponseEntity<com.learnkafka.domain.LibraryEvent> postLibraryEvent(@RequestBody @Valid com.learnkafka.domain.LibraryEvent libraryEvent) {
        LOGGER.info("LibraryEvent : {} ", libraryEvent);
        try {
            LibraryEvent libraryEventAvro = LibraryEvent.newBuilder()
                    .setLibraryEventId(libraryEvent.getLibraryEventId())
                    .setBook(com.learnkafka.avro.schema.Book.newBuilder()
                            .setBookId(libraryEvent.getBook().getBookId())
                            .setBookName(libraryEvent.getBook().getBookName())
                            .setBookAuthor(libraryEvent.getBook().getBookAuthor())
                            .build())
                    .build();
            //invoke kafka producer
            libraryEventAvro.setLibraryEventType(LibraryEventType.NEW);
            libraryEventProducer.sendLibraryEvent(libraryEventAvro);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(libraryEvent);
    }
}