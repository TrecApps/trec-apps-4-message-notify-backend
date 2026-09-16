package com.trecapps.comm.messages.websocket;

import com.trecapps.comm.messages.repos.ConversationRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

/**
 * Handles STOMP {@code SUBSCRIBE} frames for the conversation update destination.
 *
 * <p>When a client subscribes to
 * {@code /user/queue/conversations/{conversationId}}, this controller:
 * <ol>
 *   <li>Reads the authenticated {@code profileId} from the STOMP session
 *       attributes (placed there by {@link WebSocketHandshakeInterceptor}).</li>
 *   <li>Queries {@link ConversationRepo} to verify that the profile is a
 *       participant of the requested conversation.</li>
 *   <li>On success: calls {@link SubscriptionRegistry#subscribe} to record the
 *       interest.</li>
 *   <li>On failure: sends a STOMP {@code ERROR} frame back to the session via
 *       {@link SimpMessagingTemplate} and does <em>not</em> register the
 *       subscription.</li>
 * </ol>
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}.
 *
 * <p><strong>Validates: Requirements 2.1, 2.3</strong>
 */
@Controller
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class StompSubscriptionController {

    /** STOMP destination prefix for per-user conversation queues. */
    static final String SUBSCRIBE_DESTINATION = "/user/queue/conversations/{conversationId}";

    /** Header key used to send STOMP ERROR frames back to the originating session. */
    private static final String SIMPUSER_HEADER = "simpUser";

    private final ConversationRepo conversationRepo;
    private final SubscriptionRegistry subscriptionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public StompSubscriptionController(
            ConversationRepo conversationRepo,
            SubscriptionRegistry subscriptionRegistry,
            SimpMessagingTemplate messagingTemplate) {
        this.conversationRepo = conversationRepo;
        this.subscriptionRegistry = subscriptionRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Processes a STOMP {@code SUBSCRIBE} frame for a specific conversation.
     *
     * <p>The method is intentionally {@code void} — no payload is returned to
     * the subscriber on success. The side-effect is the registration in
     * {@link SubscriptionRegistry}.
     *
     * @param conversationId the conversation UUID extracted from the destination path
     * @param sessionId      the STOMP session ID, injected from the
     *                       {@code simpSessionId} header
     * @param sessionAttributes the STOMP session attributes map, injected from
     *                          the {@code simpSessionAttributes} header; contains
     *                          the {@code profileId} set by the handshake interceptor
     */
    @SubscribeMapping("/ws/queue/conversations/{conversationId}")
    public void handleSubscription(
            @DestinationVariable("conversationId") UUID conversationId,
            @Header("simpSessionId") String sessionId,
            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {

        UUID profileId = (UUID) sessionAttributes.get(WebSocketHandshakeInterceptor.PROFILE_ID_ATTR);

        if (profileId == null) {
            log.warn("SUBSCRIBE rejected: profileId missing from session attributes (sessionId={})", sessionId);
            sendError(sessionId, "Subscription rejected: session is not authenticated.");
            return;
        }

        // Query ConversationRepo to verify the profile is a participant.
        // We use blockFirst() because @SubscribeMapping handlers run on a
        // non-reactive thread managed by Spring's STOMP infrastructure.
        boolean isParticipant = Boolean.TRUE.equals(
                conversationRepo.findById(conversationId)
                        .map(conversation -> conversation.getProfiles().contains(profileId))
                        .defaultIfEmpty(false)
                        .block()
        );

        if (!isParticipant) {
            log.warn(
                    "SUBSCRIBE rejected: profileId={} is not a participant of conversationId={} (sessionId={})",
                    profileId, conversationId, sessionId);
            sendError(sessionId,
                    "Subscription rejected: you are not a participant of conversation " + conversationId + ".");
            return;
        }

        subscriptionRegistry.subscribe(sessionId, conversationId);
        log.debug("Subscription registered: sessionId={}, conversationId={}, profileId={}",
                sessionId, conversationId, profileId);
    }

    /**
     * Sends a STOMP {@code ERROR} frame to the given session.
     *
     * <p>The error is delivered to the session-specific user queue so that only
     * the requesting client receives it. The session remains open — only the
     * subscription attempt is rejected.
     *
     * @param sessionId    the target STOMP session ID
     * @param errorMessage the human-readable error description
     */
    private void sendError(String sessionId, String errorMessage) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/errors",
                errorMessage,
                headerAccessor.getMessageHeaders());
    }
}
