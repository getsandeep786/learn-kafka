package com.learnkafka.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class LibraryEventDTO {

    private String libraryEventId;

    private BookDTO book;
}
