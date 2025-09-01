package com.trecapps.comm.messages.repos;

import org.springframework.core.convert.converter.Converter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {
    @Override
    public OffsetDateTime convert(String source) {
        return OffsetDateTime.parse(source, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}

