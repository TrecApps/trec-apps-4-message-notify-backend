package com.trecapps.comm.messages.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reactive (WebFlux/Netty) WebSocket wiring for the {@code /ws} endpoint.
 *
 * <p>This replaces the previous servlet {@code @EnableWebSocketMessageBroker}
 * STOMP configuration. That MVC-based broker never registered a handler on the
 * reactive Netty runtime, so every {@code /ws} request returned {@code 404}.
 * Here we register a native reactive {@link ConversationWebSocketHandler} through
 * a {@link SimpleUrlHandlerMapping}, which the reactive dispatcher serves
 * correctly on Netty.
 *
 * <p>Allowed origins for the WebSocket upgrade are enforced via CORS on the
 * handler mapping ({@code ws.allowed.origins}). In Spring Framework 7 the
 * reactive {@code HandshakeWebSocketService} no longer exposes
 * {@code setAllowedOrigins}; origin enforcement is done through the mapping's
 * {@link CorsConfiguration}.
 *
 * <p>Only active when {@code trecapps.messaging.websocket.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class WebSocketConfig {

    private final List<String> allowedOrigins;

    public WebSocketConfig(@Value("${ws.allowed.origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : Arrays.asList(allowedOrigins);
        log.info("Reactive WebSocket endpoint /ws configured with allowed origins: {}", this.allowedOrigins);
    }

    /**
     * Maps {@code /ws} (and {@code /ws/}) to the reactive conversation handler.
     * High precedence so the WebSocket upgrade is matched before generic routes.
     * CORS is configured on the mapping to restrict the handshake origin.
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping(ConversationWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of(
                "/ws", handler,
                "/ws/", handler));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);

        if (!allowedOrigins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(allowedOrigins);
            cors.addAllowedMethod("GET");
            cors.setAllowCredentials(true);
            mapping.setCorsConfigurations(Map.of(
                    "/ws", cors,
                    "/ws/", cors));
        }
        return mapping;
    }

    /**
     * The reactive {@link WebSocketService} performing the handshake/upgrade on
     * Netty.
     */
    @Bean
    public WebSocketService webSocketService() {
        return new HandshakeWebSocketService(new ReactorNettyRequestUpgradeStrategy());
    }

    /**
     * Adapter that lets the reactive dispatcher invoke the
     * {@link ConversationWebSocketHandler}, wired to the {@link #webSocketService()}.
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter(WebSocketService webSocketService) {
        return new WebSocketHandlerAdapter(webSocketService);
    }
}
