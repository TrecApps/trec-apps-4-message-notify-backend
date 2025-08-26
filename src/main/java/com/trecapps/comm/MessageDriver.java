package com.trecapps.comm;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@ComponentScan({
        "com.trecapps.auth.common.*",               // Authentication library
        "com.trecapps.auth.webflux.*",
        "com.trecapps.comm.messages.*"
})
@EnableWebFlux
public class MessageDriver {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        SpringApplication.run(MessageDriver.class, args);
    }
}
