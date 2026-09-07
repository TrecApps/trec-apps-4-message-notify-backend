package com.trecapps.comm.messages.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trecapps.comm.messages.models.ConversationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link ConversationEvent} records to the configured Kafka/Event Hubs topic.
 *
 * <p>This component is only active when {@code trecapps.messaging.websocket.enabled=true}.
 * All exceptions (serialisation errors and Kafka send failures) are caught internally
 * so that the calling code (e.g. {@code MessageService}) is never affected by producer
 * failures.
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 5.1</strong>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class KafkaConversationProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${trecapps.messaging.kafka.topic}")
    private String topic;

    @Autowired
    public KafkaConversationProducer(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Serialises the given {@link ConversationEvent} to JSON and sends it to the
     * configured Kafka topic, keyed by {@code conversationId}.
     *
     * <p>All exceptions are caught internally. On failure the error is logged at
     * {@code ERROR} level (including {@code conversationId} and {@code eventType})
     * and the method returns normally — it never rethrows.
     *
     * @param event the conversation event to publish
     */
    public void publishEvent(ConversationEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String key = event.getConversationId().toString();
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.error(
                    "Failed to publish ConversationEvent: conversationId={}, eventType={}, error={}",
                    event.getConversationId(),
                    event.getEventType(),
                    e.getMessage(),
                    e
            );
        }
    }
}
