package com.trecapps.comm;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.reactor.ReactorAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication(
        exclude = {CassandraDataAutoConfiguration.class, CassandraAutoConfiguration.class,
                ReactorAutoConfiguration.class, CassandraReactiveRepositoriesAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.trecapps.auth.common.*",               // Authentication library
        "com.trecapps.auth.webflux.*",
        "com.trecapps.comm.common.*",
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
@EnableAutoConfiguration(exclude={CassandraDataAutoConfiguration.class, CassandraAutoConfiguration.class,
        ReactorAutoConfiguration.class, CassandraReactiveRepositoriesAutoConfiguration.class })
public class MessageDriver {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(MessageDriver.class, args);
    }
}
