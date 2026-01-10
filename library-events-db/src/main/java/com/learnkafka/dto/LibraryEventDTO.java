package com.learnkafka.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class LibraryEventDTO {

    private String libraryEventId;

    private BookDTO book;
}
