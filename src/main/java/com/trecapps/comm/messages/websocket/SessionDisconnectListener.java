package com.trecapps.comm.messages.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Cleans up in-memory state when a STOMP session disconnects.
 *
 * <p>On receiving a {@link SessionDisconnectEvent}, this listener:
 * <ol>
 *   <li>Calls {@link SessionRegistry#deregister(String)} to remove the session
 *       from the profile-to-sessions map (and the reverse index).</li>
 *   <li>Calls {@link SubscriptionRegistry#removeSession(String)} to drop all
 *       conversation subscriptions held by the session.</li>
 * </ol>
 *
 * <p>Both operations are idempotent — calling them for a session that is already
 * absent is a no-op, so duplicate disconnect events are handled safely.
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}; in HTTP-only mode the
 * entire WebSocket/Kafka subsystem is absent from the Spring context.
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
public class SessionDisconnectListener implements ApplicationListener<SessionDisconnectEvent> {

    private final SessionRegistry sessionRegistry;
    private final SubscriptionRegistry subscriptionRegistry;

    public SessionDisconnectListener(SessionRegistry sessionRegistry,
                                     SubscriptionRegistry subscriptionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
    }

    /**
     * Handles a STOMP session disconnect by removing the session from both
     * the {@link SessionRegistry} and the {@link SubscriptionRegistry}.
     *
     * @param event the disconnect event published by Spring's STOMP infrastructure
     */
    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        sessionRegistry.deregister(sessionId);
        subscriptionRegistry.removeSession(sessionId);
    }
}
