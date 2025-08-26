package com.trecapps.comm.notifications.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class ConfigNotifications {

    @Bean
    @ConditionalOnProperty(prefix = "trecapps.notifications.consumer", name="strategy", havingValue = "az-service-bus-con-str")
    INotificationConsumer getAzureBusConsumerConnStr(
            @Value("${consumer.queue.name}")String queueName,
            @Value("${consumer.connection.str}") String connectionString,
            Jackson2ObjectMapperBuilder objectMapperBuilder1
    ) {
        return new AzureServiceBusNotificationConsumer(queueName, connectionString, objectMapperBuilder1, true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trecapps.notifications.consumer", name="strategy", havingValue = "az-service-bus-namespace")
    INotificationConsumer getAzureBusConsumerNamespace(
            @Value("${consumer.queue.name}")String queueName,
            @Value("${consumer.namespace}") String namespace,
            Jackson2ObjectMapperBuilder objectMapperBuilder1
    ) {
        return new AzureServiceBusNotificationConsumer(queueName, namespace, objectMapperBuilder1, false);
    }

}
