package com.trecapps.comm.messages.repos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Configuration
public class MongoConfig {

    static class OffsetDateTimeToStringConverter implements Converter<OffsetDateTime, String> {
        @Override
        public String convert(OffsetDateTime source) {
            return source.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

//    static class DateToStringConverter  implements Converter<Date, String> {
//        @Override
//        public String convert(Date source) {
//            return DateTimeFormatter.
//        }
//    }


    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new StringToOffsetDateTimeConverter(),
                new OffsetDateTimeToStringConverter()
        ));
    }
}
