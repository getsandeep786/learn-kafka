package com.learnkafka.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class BookDTO {
    private String bookId;
    private String bookName;
    private String bookAuthor;
}


