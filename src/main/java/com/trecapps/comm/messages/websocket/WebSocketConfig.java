package com.trecapps.comm.messages.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the STOMP-over-WebSocket endpoint and wires the authentication
 * and session-management components into the Spring WebSocket infrastructure.
 *
 * <p>This configuration is only active when
 * {@code trecapps.messaging.websocket.enabled=true}. When the flag is
 * {@code false} (the default), no STOMP endpoint, Kafka producer, or Kafka
 * consumer is initialised and the application runs in HTTP-only mode.
 *
 * <h3>What this class does</h3>
 * <ul>
 *   <li>Registers the STOMP WebSocket endpoint at {@code /ws} (no SockJS
 *       fallback — native WebSocket only).</li>
 *   <li>Attaches {@link WebSocketHandshakeInterceptor} to the {@code /ws}
 *       endpoint so that the HTTP upgrade request is authenticated before the
 *       STOMP handshake completes.</li>
 *   <li>Registers {@link StompAuthChannelInterceptor} on the inbound channel
 *       to enforce that a {@code profileId} was resolved and to record the
 *       new session in {@link SessionRegistry}.</li>
 *   <li>Configures a simple in-memory message broker for the
 *       {@code /user/queue} destination prefix used by the consumer to push
 *       events to individual sessions.</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 7.4</strong>
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Autowired
    public WebSocketConfig(WebSocketHandshakeInterceptor handshakeInterceptor,
                           StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    /**
     * Registers the STOMP endpoint at {@code /ws}.
     *
     * <p>SockJS fallback is intentionally omitted — clients must use native
     * WebSocket. The {@link WebSocketHandshakeInterceptor} is attached here so
     * that authentication runs during the HTTP upgrade before any STOMP frame
     * is processed.
     *
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(handshakeInterceptor);
        // No .withSockJS() — native WebSocket only, per design decision.
    }

    /**
     * Configures the in-memory message broker.
     *
     * <p>The {@code /user} destination prefix is required so that
     * {@code SimpMessagingTemplate.convertAndSendToUser(...)} routes messages
     * to the correct per-session queue ({@code /user/queue/conversations/{id}}).
     * The {@code /app} application-destination prefix is registered for any
     * future {@code @MessageMapping} handlers.
     *
     * @param registry the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable the simple in-memory broker for /user/queue destinations.
        registry.enableSimpleBroker("/user");
        // Prefix for @MessageMapping methods (e.g. StompSubscriptionController).
        registry.setApplicationDestinationPrefixes("/app");
        // Required for convertAndSendToUser to resolve /user/... destinations.
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Registers {@link StompAuthChannelInterceptor} on the inbound channel.
     *
     * <p>The interceptor guards {@code CONNECT} frames: it verifies that the
     * {@code profileId} attribute was set by the handshake interceptor and
     * registers the session in {@link SessionRegistry}.
     *
     * @param registration the inbound channel registration
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
