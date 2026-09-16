package com.trecapps.comm.messages.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trecapps.comm.messages.models.ConversationEvent;
import com.trecapps.comm.messages.repos.ConversationRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumes {@link ConversationEvent} records from the Azure Event Hubs Kafka topic
 * and forwards them as STOMP {@code MESSAGE} frames to locally-connected WebSocket
 * clients that are subscribed to the affected conversation.
 *
 * <h3>Routing logic</h3>
 * <ol>
 *   <li>Deserialise the raw JSON record into a {@link ConversationEvent}.</li>
 *   <li>Look up the {@link com.trecapps.comm.messages.models.Conversation} from
 *       MongoDB to obtain the full participant set.</li>
 *   <li>For each participant, query {@link SessionRegistry} for active session IDs
 *       on this instance.</li>
 *   <li>Filter those sessions to only those that are subscribed to the event's
 *       {@code conversationId} via {@link SubscriptionRegistry}.</li>
 *   <li>Send the event JSON to each matching session via
 *       {@link SimpMessagingTemplate#convertAndSendToUser}.</li>
 * </ol>
 *
 * <h3>Error handling</h3>
 * <ul>
 *   <li>Deserialisation failure: the raw record is logged at {@code ERROR} level
 *       and the record is skipped — no exception is rethrown.</li>
 *   <li>Delivery failure to a session: logged at {@code WARN} level; processing
 *       continues for remaining sessions and subsequent records.</li>
 *   <li>Conversation not found in MongoDB: treated as an empty participant set —
 *       the event is silently discarded (no sessions to notify).</li>
 * </ul>
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}; in HTTP-only mode the
 * entire WebSocket/Kafka subsystem is absent from the Spring context.
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.4</strong>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class KafkaConversationConsumer {

    /** STOMP destination template for per-conversation user queues. */
    private static final String CONVERSATION_DESTINATION = "/queue/conversations/";

    private final ObjectMapper objectMapper;
    private final ConversationRepo conversationRepo;
    private final SessionRegistry sessionRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public KafkaConversationConsumer(
            ObjectMapper objectMapper,
            ConversationRepo conversationRepo,
            SessionRegistry sessionRegistry,
            SubscriptionRegistry subscriptionRegistry,
            SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.conversationRepo = conversationRepo;
        this.sessionRegistry = sessionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.messagingTemplate = messagingTemplate;
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

        // Look up participants from MongoDB (blocking — Kafka listener runs on a
        // non-reactive thread managed by Spring Kafka).
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

        // Determine which sessions on this instance should receive the event.
        Set<String> targetSessions = resolveTargetSessions(
                event.getConversationId(), participants);

        if (targetSessions.isEmpty()) {
            log.info(
                    "No matching sessions on this instance for conversationId={} — discarding event",
                    event.getConversationId());
            return;
        }

        String destination = CONVERSATION_DESTINATION + event.getConversationId();

        for (String sessionId : targetSessions) {
            try {
                messagingTemplate.convertAndSendToUser(sessionId, destination, eventJson);
                log.debug(
                        "Delivered ConversationEvent to sessionId={}, conversationId={}, eventType={}",
                        sessionId, event.getConversationId(), event.getEventType());
            } catch (Exception e) {
                log.warn(
                        "Failed to deliver ConversationEvent to sessionId={}, conversationId={}, "
                                + "eventType={} — continuing",
                        sessionId, event.getConversationId(), event.getEventType(), e);
            }
        }
    }

    /**
     * Pure routing method: given a conversation's participant set and the target
     * {@code conversationId}, returns the set of STOMP session IDs on this instance
     * that should receive the event.
     *
     * <p>A session qualifies if and only if:
     * <ol>
     *   <li>Its owner ({@code profileId}) is in {@code participants}, AND</li>
     *   <li>The session is subscribed to {@code conversationId} in the
     *       {@link SubscriptionRegistry}.</li>
     * </ol>
     *
     * <p>This method is package-private to allow direct unit and property-based
     * testing without going through the full Kafka listener path.
     *
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3 (Property 8)</strong>
     *
     * @param conversationId the conversation whose event is being routed
     * @param participants   the set of profile UUIDs that are participants of the conversation
     * @return the set of session IDs that should receive the event; never {@code null}
     */
    Set<String> resolveTargetSessions(UUID conversationId, Set<UUID> participants) {
        return participants.stream()
                .flatMap(profileId -> sessionRegistry.getSessionIds(profileId).stream())
                .filter(sessionId -> subscriptionRegistry.isSubscribed(sessionId, conversationId))
                .collect(Collectors.toSet());
    }
}
