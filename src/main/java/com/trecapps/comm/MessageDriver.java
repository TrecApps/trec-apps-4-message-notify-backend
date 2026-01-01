package com.trecapps.comm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraAutoConfiguration;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraReactiveAutoConfiguration;
import org.springframework.boot.data.cassandra.autoconfigure.DataCassandraReactiveRepositoriesAutoConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication(
        exclude = {DataCassandraAutoConfiguration.class, CassandraAutoConfiguration.class,
                DataCassandraReactiveAutoConfiguration.class, DataCassandraReactiveRepositoriesAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.trecapps.auth.common.*",               // Authentication library
        "com.trecapps.auth.webflux.*",
        "com.trecapps.comm.common",
        "com.trecapps.comm.messages.*"
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
                "com.trecapps.comm.notifications.model.*",
                "com.trecapps.comm.notifications.services.*"
        }
))
@EnableWebFlux
@EnableReactiveMongoRepositories
@EnableAutoConfiguration(exclude={DataCassandraAutoConfiguration.class, CassandraAutoConfiguration.class,
        DataCassandraReactiveAutoConfiguration.class, DataCassandraReactiveRepositoriesAutoConfiguration.class })
@Configuration
public class MessageDriver {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(MessageDriver.class, args);
    }
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Enable timestamps
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        return mapper;
    }
}
