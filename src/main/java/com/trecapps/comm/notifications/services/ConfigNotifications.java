package com.trecapps.comm.notifications.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigNotifications {

    @Bean
    @ConditionalOnProperty(prefix = "trecapps.notifications.consumer", name="strategy", havingValue = "az-service-bus-con-str")
    INotificationConsumer getAzureBusConsumerConnStr(
            @Value("${consumer.queue.name}")String queueName,
            @Value("${consumer.connection.str}") String connectionString,
            ObjectMapper objectMapperBuilder1
    ) {
        return new AzureServiceBusNotificationConsumer(queueName, connectionString, objectMapperBuilder1, true);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trecapps.notifications.consumer", name="strategy", havingValue = "az-service-bus-namespace")
    INotificationConsumer getAzureBusConsumerNamespace(
            @Value("${consumer.queue.name}")String queueName,
            @Value("${consumer.namespace}") String namespace,
            ObjectMapper objectMapperBuilder1
    ) {
        return new AzureServiceBusNotificationConsumer(queueName, namespace, objectMapperBuilder1, false);
    }

}
