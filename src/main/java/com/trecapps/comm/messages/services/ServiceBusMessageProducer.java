package com.trecapps.comm.messages.services;


import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.trecapps.comm.notifications.model.NotificationPost;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import reactor.core.publisher.Mono;

@Slf4j
public class ServiceBusMessageProducer implements IMessageProducer {
    ServiceBusSenderAsyncClient serviceBusSenderClient;
    ObjectMapper objectMapper;

    ServiceBusMessageProducer(String queueName, String connection, Jackson2ObjectMapperBuilder objectMapperBuilder1, boolean useConnectionString) {
        if (useConnectionString) {
            this.serviceBusSenderClient = (new ServiceBusClientBuilder()).connectionString(connection).sender().queueName(queueName).buildAsyncClient();
        } else {
            DefaultAzureCredential credential = (new DefaultAzureCredentialBuilder()).build();
            this.serviceBusSenderClient = (new ServiceBusClientBuilder()).fullyQualifiedNamespace(String.format("%s.servicebus.windows.net", connection)).credential(credential).sender().queueName(queueName).buildAsyncClient();
        }

        this.objectMapper = objectMapperBuilder1.createXmlMapper(false).build();
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    @SneakyThrows
    public Mono<Boolean> sendNotification(NotificationPost post) {
        return this.serviceBusSenderClient.sendMessage(new ServiceBusMessage(this.objectMapper.writeValueAsString(post)))
                .thenReturn(true)
                .onErrorResume((Throwable thrown) -> {
                    log.error("Failed to send message {}, reason is: {}", post, thrown.getMessage());
                    return Mono.just(false);
                });
    }
}
