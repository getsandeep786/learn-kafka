package com.learnkafka.api;

import com.learnkafka.api.dto.LibraryEventDTO;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class LibraryEventApi {

    private static final Logger LOGGER = LogManager.getLogger(LibraryEventApi.class);

    private final RestClient restClient;

    @Autowired
    public LibraryEventApi(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://localhost:8084").build();
    }

    public void callLibraryEventApi(@NotNull String libraryEventId) {
        LOGGER.info("Calling Library Event API for ID: {}", libraryEventId);
        LibraryEventDTO libraryEventDTO = this.restClient.get()
                .uri("/v1/library/event/7")
                .retrieve()
                .body(LibraryEventDTO.class);
        LOGGER.info("Received response from Library Event API: {}", libraryEventDTO.toString());
    }
}
