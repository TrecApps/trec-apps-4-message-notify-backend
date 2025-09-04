package com.trecapps.comm;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication(
        exclude = {
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class,
                MongoReactiveDataAutoConfiguration.class,
                MongoReactiveAutoConfiguration.class,
                MongoReactiveRepositoriesAutoConfiguration.class
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
        MongoDataAutoConfiguration.class,
        MongoReactiveDataAutoConfiguration.class,
        MongoReactiveAutoConfiguration.class,
        MongoReactiveRepositoriesAutoConfiguration.class
})
public class NotifyDriver {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(NotifyDriver.class, args);
    }

}
