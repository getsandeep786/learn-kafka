package com.learnkafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.avro.schema.LibraryEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class LibraryEventProducer {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventProducer.class);

    @Autowired
    private KafkaTemplate<String, LibraryEvent> kafkaTemplate;

    String topic = "library-events";

    private final ObjectMapper objectMapper;

    @Autowired
    public LibraryEventProducer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendLibraryEvent(LibraryEvent libraryEvent) throws JsonProcessingException {
        LOGGER.info("Inside Producer");
        String key = Optional.ofNullable(libraryEvent.getLibraryEventId())
                .map(Object::toString)
                .orElse(null);
        CompletableFuture<SendResult<String, LibraryEvent>> listenableFuture = kafkaTemplate.send(topic, libraryEvent);
        LOGGER.info("Message sent from our end : {}", key);
        listenableFuture.whenComplete((result, ex) -> {
            if (ex == null) {
                handleSuccess(key, libraryEvent, result);
            } else {
                handleFailure(key, libraryEvent, ex);
            }
        });
    }

    private void handleFailure(String key, LibraryEvent value, Throwable ex) {
        LOGGER.error("Error Sending the Message and the exception is {}", ex.getMessage());
        try {
            throw ex;
        } catch (Throwable throwable) {
            LOGGER.error("Error in OnFailure: {}", throwable.getMessage());
        }
    }

    private void handleSuccess(String key, LibraryEvent value, SendResult<String, LibraryEvent> result) {
        LOGGER.info("Message Sent SuccessFully for the key : {} and the value is {} , partition is {}  offset is {}", key, value.toString(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
    }
}