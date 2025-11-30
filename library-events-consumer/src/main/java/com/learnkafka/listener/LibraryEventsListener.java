package com.learnkafka.listener;

import com.learnkafka.avro.schema.LibraryEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class LibraryEventsListener {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventsListener.class);


    @KafkaListener(topics = "${spring.kafka.template.default-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void processLibraryEvent(LibraryEvent libraryEvent,
                                    @Header(KafkaHeaders.OFFSET) String msgOffset,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) String partitionId,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topicName) {
        LOGGER.info("Message received from topic: {}, partition: {}, offset: {}", topicName, partitionId, msgOffset);
        LOGGER.info("Received Library Event Message: {}", libraryEvent.toString());
    }

}
