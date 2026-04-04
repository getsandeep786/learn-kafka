package com.learnkafka.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KafkaRetryException extends RuntimeException {

    public KafkaRetryException(String message) {
        super(message);
    }
}
