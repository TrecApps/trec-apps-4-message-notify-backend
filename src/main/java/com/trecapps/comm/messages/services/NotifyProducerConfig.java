package com.trecapps.comm.messages.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(0)
public class NotifyProducerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "trecapps.message.producer",
            name = {"strategy"},
            havingValue = "azure-service-bus-entra"
    )
    IMessageProducer getProducerServiceBusEntra(
            @Value("${trecapps.message.producer.queue}") String queue,
            @Value("${trecapps.message.producer.namespace}") String namespace,
            ObjectMapper objectMapperBuilder) {
        return new ServiceBusMessageProducer(queue, namespace, objectMapperBuilder, false);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "trecapps.message.producer",
            name = {"strategy"},
            havingValue = "azure-service-bus-connection-string"
    )
    IMessageProducer getProducerServiceBusConnString(
            @Value("${trecapps.message.producer.queue}") String queue,
            @Value("${trecapps.message.producer.connection}") String connection,
            ObjectMapper objectMapperBuilder) {
        return new ServiceBusMessageProducer(queue, connection, objectMapperBuilder, true);
    }
}
