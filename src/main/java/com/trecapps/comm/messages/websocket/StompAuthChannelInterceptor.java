package com.trecapps.comm.messages.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Lightweight STOMP channel interceptor that guards the inbound channel against
 * {@code CONNECT} frames that lack a resolved {@code profileId}.
 *
 * <p>Authentication itself is performed earlier, during the HTTP upgrade, by
 * {@link WebSocketHandshakeInterceptor}. That interceptor stores the resolved
 * {@code profileId} in the WebSocket session attributes under the key
 * {@code "profileId"}. This interceptor simply reads that value and, on
 * success, registers the new STOMP session in the {@link SessionRegistry}.
 *
 * <p>If the {@code profileId} attribute is absent — which can only happen if
 * the handshake interceptor was bypassed through misconfiguration — a
 * {@link MessagingException} is thrown. Spring's STOMP infrastructure
 * translates this into a STOMP {@code ERROR} frame sent to the client and
 * then closes the underlying WebSocket connection.
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}.
 *
 * <p><strong>Validates: Requirements 1.2, 1.3</strong>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final SessionRegistry sessionRegistry;

    @Autowired
    public StompAuthChannelInterceptor(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Intercepts inbound STOMP frames. For {@code CONNECT} frames, verifies
     * that a {@code profileId} was placed in the session attributes by the
     * HTTP handshake interceptor, then registers the session.
     *
     * @param message the inbound STOMP message
     * @param channel the channel the message is being sent to
     * @return the original message, unmodified, to allow processing to continue
     * @throws MessagingException if the frame is a {@code CONNECT} and the
     *                            {@code profileId} session attribute is absent
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

            if (sessionAttributes == null || !sessionAttributes.containsKey(WebSocketHandshakeInterceptor.PROFILE_ID_ATTR)) {
                log.warn("STOMP CONNECT rejected: 'profileId' missing from session attributes (sessionId={})",
                        accessor.getSessionId());
                throw new MessagingException(message,
                        "STOMP CONNECT rejected: profileId not present in session attributes. "
                                + "Ensure the WebSocket handshake interceptor is correctly configured.");
            }

            UUID profileId = (UUID) sessionAttributes.get(WebSocketHandshakeInterceptor.PROFILE_ID_ATTR);
            String sessionId = accessor.getSessionId();

            sessionRegistry.register(profileId, sessionId);
            log.debug("STOMP session registered: profileId={}, sessionId={}", profileId, sessionId);
        }

        return message;
    }
}
