package com.trecapps.comm.notifications.services;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.trecapps.base.notify.models.*;
import com.trecapps.comm.notifications.model.NotificationPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Slf4j
public class AzureServiceBusNotificationConsumer implements INotificationConsumer{
    ServiceBusProcessorClient processorClient;
    INotificationMessageHandler handler;

    ObjectMapper objectMapper;

    AzureServiceBusNotificationConsumer(String queue, String connector, Jackson2ObjectMapperBuilder objectMapperBuilder1, boolean useConnectionString) {
        if (useConnectionString) {
            this.processorClient = (new ServiceBusClientBuilder())
                    .connectionString(connector)
                    .processor()
                    .queueName(queue)
                    .processMessage(this::processMessage).processError(this::processError).buildProcessorClient();
        } else {
            DefaultAzureCredential credential = (new DefaultAzureCredentialBuilder()).build();
            this.processorClient = (new ServiceBusClientBuilder())
                    .fullyQualifiedNamespace(String.format("%s.servicebus.windows.net", connector))
                    .credential(credential)
                    .processor()
                    .queueName(queue).processMessage(this::processMessage).processError(this::processError).buildProcessorClient();
        }

        this.objectMapper = objectMapperBuilder1.createXmlMapper(false).build();
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    }

    void processMessage(ServiceBusReceivedMessageContext context) {
        try {
            ServiceBusReceivedMessage message = context.getMessage();
            String messageStr = message.getBody().toString();
            NotificationPost messageObject = this.objectMapper.readValue(messageStr, NotificationPost.class);
            if(messageObject == null){
                log.error("Could not detect Message Type!");
                context.deadLetter();
            }
            handler.processNotification(messageObject).doOnNext((Boolean worked) -> {
                if (worked) {
                    context.complete();
                } else {
                    context.deadLetter();
                }
            }).subscribe();

        } catch (Throwable var5) {
            log.error("Error detected in Reading Message", var5);
            context.deadLetter();
        }
    }

    void processError(ServiceBusErrorContext context) {
    }

    public void initialize(INotificationMessageHandler handler) {
        this.handler = handler;
        if (handler != null) {
            this.processorClient.start();
        }

    }
}
