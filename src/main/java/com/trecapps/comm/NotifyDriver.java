package com.trecapps.comm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveRepositoriesAutoConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication(
        exclude = {
                MongoAutoConfiguration.class,
                MongoReactiveAutoConfiguration.class,
                DataMongoReactiveAutoConfiguration.class,
                DataMongoAutoConfiguration.class,
                DataMongoRepositoriesAutoConfiguration.class,
                DataMongoReactiveRepositoriesAutoConfiguration.class
        }
)
@ComponentScan(basePackages = {
        "com.trecapps.auth.common.*",               // Authentication library
        "com.trecapps.auth.webflux.*",
        "com.trecapps.comm.common",
        "com.trecapps.comm.notifications.*"
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
                "com.trecapps.comm.messages.models.*",
                "com.trecapps.comm.messages.services.*",
                "com.trecapps.comm.messages.repos.*",
                "com.trecapps.comm.messages.controllers.*",
        }
))
@EnableWebFlux
@EnableAutoConfiguration(exclude={MongoAutoConfiguration.class,
        MongoReactiveAutoConfiguration.class,
        DataMongoReactiveAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        DataMongoRepositoriesAutoConfiguration.class,
        DataMongoReactiveRepositoriesAutoConfiguration.class
})
@Configuration
public class NotifyDriver {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(NotifyDriver.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Enable timestamps
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        return mapper;
    }
}
