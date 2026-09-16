//package com.trecapps.comm.common;
//
//
//import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.databind.json.JsonMapper;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class JacksonConfig {
//    @Bean
//    public ObjectMapper objectMapper() {
//        ObjectMapper mapper = JsonMapper.builder()
//                .addModule(new JavaTimeModule())
//                .serializationInclusion(JsonInclude.Include.NON_NULL)
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
//                .build();
//        return mapper;
//    }
//}
