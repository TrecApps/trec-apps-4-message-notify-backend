package com.trecapps.comm.messages.websocket;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.Getter;
import reactor.core.publisher.Sinks;

/**
 * In-memory, per-instance registry of the live reactive WebSocket sessions for
 * each authenticated profile.
 *
 * <p>This is the reactive replacement for the old STOMP {@code SessionRegistry}
 * plus {@code SubscriptionRegistry}. Instead of tracking opaque STOMP session
 * IDs and pushing through a {@code SimpMessagingTemplate}, each session owns a
 * {@link Sinks.Many} sink that the Kafka consumer emits JSON frames into; the
 * {@code ConversationWebSocketHandler} drains that sink to the socket.
 *
 * <h3>Routing model (unchanged from the STOMP version)</h3>
 * A session receives a {@code ConversationEvent} iff:
 * <ol>
 *   <li>its owner {@code profileId} is a participant of the conversation, AND</li>
 *   <li>the session has explicitly subscribed to that {@code conversationId}.</li>
 * </ol>
 *
 * <p>Thread-safety: the outer maps are {@link ConcurrentHashMap}; each session's
 * subscription set is a {@link CopyOnWriteArraySet} so fan-out iteration needs no
 * external locking.
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
public class SessionRegistry {

    /**
     * A single live WebSocket session: its owning profile, the set of
     * conversations it is subscribed to, and the sink used to push frames to it.
     */
    public static class Session {

        @Getter
        private final String sessionId;

        @Getter
        private final UUID profileId;

        /** Conversation IDs this session has subscribed to. */
        private final Set<UUID> subscriptions = new CopyOnWriteArraySet<>();

        /**
         * Multicast sink of outbound text frames (JSON). Backpressure buffer so a
         * momentarily slow client does not drop events; {@code onBackpressureBuffer}
         * keeps ordering and replays nothing to late subscribers.
         */
        @Getter
        private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        Session(String sessionId, UUID profileId) {
            this.sessionId = sessionId;
            this.profileId = profileId;
        }

        public void subscribe(UUID conversationId) {
            subscriptions.add(conversationId);
        }

        public void unsubscribe(UUID conversationId) {
            subscriptions.remove(conversationId);
        }

        public boolean isSubscribed(UUID conversationId) {
            return subscriptions.contains(conversationId);
        }

        /** Emits a frame to this session's client; failures are non-fatal. */
        public void emit(String frame) {
            sink.tryEmitNext(frame);
        }
    }

    /** profileId -> live sessions owned by that profile on this instance. */
    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<Session>> profileSessions =
            new ConcurrentHashMap<>();

    /** sessionId -> session (reverse index for O(1) lookup and removal). */
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new session for the given profile.
     *
     * @param profileId the authenticated profile that owns the session
     * @param sessionId the reactive WebSocket session ID
     * @return the newly registered {@link Session}
     */
    public Session register(UUID profileId, String sessionId) {
        Session session = new Session(sessionId, profileId);
        sessions.put(sessionId, session);
        profileSessions
                .computeIfAbsent(profileId, id -> new CopyOnWriteArraySet<>())
                .add(session);
        return session;
    }

    /**
     * Removes a session and completes its sink. Idempotent — a no-op if the
     * session was already removed.
     *
     * @param sessionId the reactive WebSocket session ID to remove
     */
    public void deregister(String sessionId) {
        Session session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.getSink().tryEmitComplete();
        profileSessions.compute(session.getProfileId(), (id, set) -> {
            if (set == null) {
                return null;
            }
            set.remove(session);
            return set.isEmpty() ? null : set;
        });
    }

    /**
     * Returns the live sessions owned by the given profile on this instance.
     *
     * @param profileId the profile to look up
     * @return an unmodifiable view of the sessions, empty if none exist
     */
    public Set<Session> getSessions(UUID profileId) {
        CopyOnWriteArraySet<Session> set = profileSessions.get(profileId);
        return set == null ? Set.of() : set;
    }
}
