package com.trecapps.comm.messages.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory registry that tracks the active STOMP session IDs for each authenticated profile.
 *
 * <p>Thread-safety is achieved via:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for both the forward and reverse maps.</li>
 *   <li>{@link CopyOnWriteArraySet} as the per-profile session set, so iteration
 *       during fan-out is safe without additional locking.</li>
 *   <li>Atomic {@code computeIfAbsent} / {@code compute} operations to avoid
 *       lost-update races during concurrent register/deregister calls.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
public class SessionRegistry {

    /** profileId → set of active session IDs on this instance. */
    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<String>> profileSessions =
            new ConcurrentHashMap<>();

    /** sessionId → profileId (reverse index for O(1) deregister). */
    private final ConcurrentHashMap<String, UUID> sessionProfile = new ConcurrentHashMap<>();

    /**
     * Records a new STOMP session for the given profile.
     *
     * @param profileId the authenticated profile
     * @param sessionId the STOMP session ID assigned by Spring
     */
    public void register(UUID profileId, String sessionId) {
        profileSessions
                .computeIfAbsent(profileId, id -> new CopyOnWriteArraySet<>())
                .add(sessionId);
        sessionProfile.put(sessionId, profileId);
    }

    /**
     * Removes a STOMP session from the registry.
     *
     * <p>If the session is the last one for its profile, the profile entry is also removed.
     * This method is a no-op when {@code sessionId} is not present in the registry.
     *
     * @param sessionId the STOMP session ID to remove
     */
    public void deregister(String sessionId) {
        UUID profileId = sessionProfile.remove(sessionId);
        if (profileId == null) {
            // Unknown session — no-op as required.
            return;
        }

        profileSessions.compute(profileId, (id, sessions) -> {
            if (sessions == null) {
                return null; // already absent — nothing to do
            }
            sessions.remove(sessionId);
            // Return null to remove the key when the set is empty.
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /**
     * Returns the set of active session IDs for the given profile.
     *
     * @param profileId the profile to look up
     * @return an unmodifiable view of the session IDs, or an empty set if none exist
     */
    public Set<String> getSessionIds(UUID profileId) {
        CopyOnWriteArraySet<String> sessions = profileSessions.get(profileId);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(sessions);
    }
}
