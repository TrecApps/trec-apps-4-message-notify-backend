package com.trecapps.comm.messages.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry that tracks which conversations each STOMP session is
 * subscribed to.
 *
 * <p>Thread-safety is achieved via {@link ConcurrentHashMap} for the outer map
 * and a {@link ConcurrentHashMap#newKeySet() concurrent key-set} as the inner
 * collection. All mutating operations use atomic {@code computeIfAbsent} /
 * {@code compute} calls to avoid lost-update races under concurrent access.
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}; in HTTP-only mode the
 * entire WebSocket/Kafka subsystem is absent from the Spring context.
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
public class SubscriptionRegistry {

    /** sessionId → set of conversationIds the session is currently subscribed to. */
    private final ConcurrentHashMap<String, Set<UUID>> sessionSubscriptions =
            new ConcurrentHashMap<>();

    /**
     * Records that {@code sessionId} wants to receive events for
     * {@code conversationId}.
     *
     * <p>Calling this method when the subscription already exists is a no-op.
     *
     * @param sessionId      the STOMP session ID
     * @param conversationId the conversation to subscribe to
     */
    public void subscribe(String sessionId, UUID conversationId) {
        sessionSubscriptions
                .computeIfAbsent(sessionId, id -> ConcurrentHashMap.newKeySet())
                .add(conversationId);
    }

    /**
     * Removes the subscription for {@code conversationId} from
     * {@code sessionId}.
     *
     * <p>This method is a no-op when the session or the subscription does not
     * exist. The session entry is retained even if it becomes empty so that a
     * subsequent {@link #subscribe} does not need to re-create it.
     *
     * @param sessionId      the STOMP session ID
     * @param conversationId the conversation to unsubscribe from
     */
    public void unsubscribe(String sessionId, UUID conversationId) {
        sessionSubscriptions.computeIfPresent(sessionId, (id, conversations) -> {
            conversations.remove(conversationId);
            return conversations;
        });
    }

    /**
     * Removes all subscriptions associated with {@code sessionId}.
     *
     * <p>This is called when a STOMP session disconnects so that stale entries
     * do not accumulate in memory. The method is a no-op when the session is
     * not present.
     *
     * @param sessionId the STOMP session ID to remove
     */
    public void removeSession(String sessionId) {
        sessionSubscriptions.remove(sessionId);
    }

    /**
     * Returns {@code true} if {@code sessionId} is currently subscribed to
     * {@code conversationId}.
     *
     * @param sessionId      the STOMP session ID
     * @param conversationId the conversation to check
     * @return {@code true} if the subscription exists, {@code false} otherwise
     */
    public boolean isSubscribed(String sessionId, UUID conversationId) {
        Set<UUID> conversations = sessionSubscriptions.get(sessionId);
        return conversations != null && conversations.contains(conversationId);
    }

    /**
     * Returns an unmodifiable snapshot of the conversation IDs that
     * {@code sessionId} is currently subscribed to.
     *
     * <p>This is a convenience method used by the consumer routing logic.
     *
     * @param sessionId the STOMP session ID
     * @return an unmodifiable set of subscribed conversation IDs, or an empty
     *         set if the session has no subscriptions
     */
    public Set<UUID> getSubscriptions(String sessionId) {
        Set<UUID> conversations = sessionSubscriptions.get(sessionId);
        if (conversations == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(conversations);
    }
}
