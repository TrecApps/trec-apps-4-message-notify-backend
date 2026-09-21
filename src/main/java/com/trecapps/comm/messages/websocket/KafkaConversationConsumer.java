package com.trecapps.comm.messages.websocket;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trecapps.comm.messages.models.ConversationEvent;
import com.trecapps.comm.messages.repos.ConversationRepo;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@link ConversationEvent} records from the Azure Event Hubs Kafka topic
 * and pushes them to locally-connected reactive WebSocket clients that are
 * subscribed to the affected conversation.
 *
 * <h3>Routing logic</h3>
 * <ol>
 *   <li>Deserialise the raw JSON record into a {@link ConversationEvent}.</li>
 *   <li>Look up the {@link com.trecapps.comm.messages.models.Conversation} from
 *       MongoDB to obtain the full participant set.</li>
 *   <li>For each participant, query {@link SessionRegistry} for live sessions on
 *       this instance.</li>
 *   <li>Keep only sessions that are subscribed to the event's
 *       {@code conversationId}.</li>
 *   <li>Emit the event JSON into each matching session's sink; the
 *       {@link ConversationWebSocketHandler} drains the sink to the socket.</li>
 * </ol>
 *
 * <h3>Fan-out across instances</h3>
 * Each instance uses a unique consumer group (see {@code KafkaConfig}), so every
 * instance receives every event and filters to its own locally-connected,
 * subscribed sessions. No sticky sessions required.
 *
 * <h3>Error handling</h3>
 * <ul>
 *   <li>Deserialisation failure: raw record logged at {@code ERROR}, record skipped.</li>
 *   <li>Conversation not found: treated as an empty participant set — discarded.</li>
 *   <li>Per-session emit failure: logged at {@code WARN}; processing continues.</li>
 * </ul>
 *
 * <p>Only active when {@code trecapps.messaging.websocket.enabled=true}.
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.4</strong>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class KafkaConversationConsumer {

    private final ObjectMapper objectMapper;
    private final ConversationRepo conversationRepo;
    private final SessionRegistry sessionRegistry;

    @Autowired
    public KafkaConversationConsumer(
            ObjectMapper objectMapper,
            ConversationRepo conversationRepo,
            SessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.conversationRepo = conversationRepo;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Kafka listener method — invoked once per consumed record.
     *
     * <p>The {@code groupId} SpEL expression {@code "#{kafkaConsumerGroupId}"}
     * resolves to the unique group ID bean defined in {@link KafkaConfig}, ensuring
     * every application instance receives every event (Requirement 7.1).
     *
     * @param record the raw JSON string consumed from the Kafka topic
     */
    @KafkaListener(
            topics = "${trecapps.messaging.kafka.topic}",
            groupId = "#{kafkaConsumerGroupId}"
    )
    public void onMessage(String record) {
        ConversationEvent event;
        try {
            event = objectMapper.readValue(record, ConversationEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialise ConversationEvent — skipping record. raw={}", record, e);
            return;
        }

        String eventJson;
        try {
            eventJson = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error(
                    "Failed to re-serialise ConversationEvent for delivery — skipping. "
                            + "conversationId={}, eventType={}",
                    event.getConversationId(), event.getEventType(), e);
            return;
        }

        // Look up participants from MongoDB. The Kafka listener runs on a
        // non-reactive container thread, so we block for the single lookup.
        Set<UUID> participants = conversationRepo
                .findById(event.getConversationId())
                .map(conversation -> (Set<UUID>) conversation.getProfiles())
                .defaultIfEmpty(Collections.emptySet())
                .block();

        if (participants == null || participants.isEmpty()) {
            log.info(
                    "No participants found for conversationId={} — discarding event",
                    event.getConversationId());
            return;
        }

        Set<SessionRegistry.Session> targets = resolveTargetSessions(
                event.getConversationId(), participants);

        if (targets.isEmpty()) {
            log.info(
                    "No matching sessions on this instance for conversationId={} — discarding event",
                    event.getConversationId());
            return;
        }

        for (SessionRegistry.Session session : targets) {
            try {
                session.emit(eventJson);
                log.debug(
                        "Delivered ConversationEvent to sessionId={}, conversationId={}, eventType={}",
                        session.getSessionId(), event.getConversationId(), event.getEventType());
            } catch (Exception e) {
                log.warn(
                        "Failed to deliver ConversationEvent to sessionId={}, conversationId={}, "
                                + "eventType={} — continuing",
                        session.getSessionId(), event.getConversationId(), event.getEventType(), e);
            }
        }
    }

    /**
     * Pure routing method: given a conversation's participant set and the target
     * {@code conversationId}, returns the live sessions on this instance that
     * should receive the event.
     *
     * <p>A session qualifies iff its owner is a participant AND it is subscribed
     * to the conversation.
     *
     * <p>Package-private for direct unit / property-based testing.
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3 (Property 8)</strong>
     *
     * @param conversationId the conversation whose event is being routed
     * @param participants   the participant profile UUIDs of the conversation
     * @return the set of sessions that should receive the event; never {@code null}
     */
    Set<SessionRegistry.Session> resolveTargetSessions(UUID conversationId, Set<UUID> participants) {
        return participants.stream()
                .flatMap(profileId -> sessionRegistry.getSessions(profileId).stream())
                .filter(session -> session.isSubscribed(conversationId))
                .collect(Collectors.toSet());
    }
}
