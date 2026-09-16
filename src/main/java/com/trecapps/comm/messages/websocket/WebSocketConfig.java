package com.trecapps.comm.messages.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

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

    @Slf4j
    static class WebSocketHandshakeLogger implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

            // This will print exactly what path your Gateway forwarded to Spring Boot
            log.info("Incoming WebSocket Handshake URI: {}", request.getURI());
            log.info("Handshake Headers: {}", request.getHeaders());

            return true; // Return true to let the handshake proceed
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            if (exception != null) {
                log.error("Handshake failed with exception: ", exception);
            }
        }
    }

    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    WebSocketLoggingInterceptor loggingInterceptor;

    String[] allowedOrigins;

    @Autowired
    public WebSocketConfig(WebSocketHandshakeInterceptor handshakeInterceptor,
                           StompAuthChannelInterceptor stompAuthChannelInterceptor,
                           WebSocketLoggingInterceptor loggingInterceptor,
                           @Value("${ws.allowed.origins}") String[] allowedOrigins1) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.allowedOrigins = allowedOrigins1;
        this.loggingInterceptor = loggingInterceptor;
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
        registry.addEndpoint("/ws/")

                .setAllowedOrigins("*")
                .addInterceptors(new WebSocketHandshakeLogger(), handshakeInterceptor);
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
        registration.interceptors(loggingInterceptor, stompAuthChannelInterceptor);

    }
}
