package com.trecapps.comm.messages.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trecapps.comm.messages.repos.ConversationRepo;
import com.trecauth.common.model.AccountList;
import com.trecauth.webflux.repos.TrecAuthSecurityAsyncParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Native (reactive) Spring WebFlux {@link WebSocketHandler} that serves the
 * {@code /ws} endpoint on the Netty stack.
 *
 * <p>This class replaces the entire servlet-based STOMP stack (the old
 * {@code @EnableWebSocketMessageBroker} {@code WebSocketConfig},
 * {@code WebSocketHandshakeInterceptor}, {@code StompAuthChannelInterceptor},
 * {@code StompSubscriptionController}, {@code WebSocketLoggingInterceptor} and
 * {@code SessionDisconnectListener}). Because the app runs on Netty/WebFlux, the
 * MVC STOMP handler mapping was never registered — hence the 404. A reactive
 * {@code WebSocketHandler} is served by the reactive handler chain and works.
 *
 * <h3>Lifecycle of a connection</h3>
 * <ol>
 *   <li><b>Handshake auth.</b> On {@link #handle(WebSocketSession)} the
 *       {@code Authorization} header / auth cookie carried by the HTTP upgrade
 *       request is resolved via {@link TrecAuthSecurityAsyncParser}. A missing
 *       token or a missing {@code TREC_VERIFIED} authority closes the session.</li>
 *   <li><b>Register.</b> A {@link SessionRegistry.Session} is created for the
 *       resolved {@code profileId}.</li>
 *   <li><b>Inbound.</b> Client text frames are parsed as {@link ClientCommand}
 *       ({@code SUBSCRIBE}/{@code UNSUBSCRIBE}); {@code SUBSCRIBE} verifies the
 *       profile is a participant of the conversation before recording it.</li>
 *   <li><b>Outbound.</b> The session's sink is drained to the socket. The Kafka
 *       consumer emits {@code ConversationEvent} JSON into that sink.</li>
 *   <li><b>Cleanup.</b> When the socket terminates, the session is deregistered
 *       (its sink completed), replacing the old {@code SessionDisconnectListener}.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class ConversationWebSocketHandler implements WebSocketHandler {

    private static final String TREC_VERIFIED = "TREC_VERIFIED";

    private final TrecAuthSecurityAsyncParser authParser;
    private final ConversationRepo conversationRepo;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public ConversationWebSocketHandler(TrecAuthSecurityAsyncParser authParser,
                                        ConversationRepo conversationRepo,
                                        SessionRegistry sessionRegistry,
                                        ObjectMapper objectMapper) {
        this.authParser = authParser;
        this.conversationRepo = conversationRepo;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return resolveProfileId(session)
                .flatMap(profileId -> handleAuthenticated(session, profileId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("WebSocket handshake rejected: no verified account resolved (sessionId={})",
                            session.getId());
                    return session.close();
                }))
                .onErrorResume(e -> {
                    log.error("WebSocket handshake failed with unexpected error (sessionId={})",
                            session.getId(), e);
                    return session.close();
                });
    }

    /**
     * Authenticated connection: register the session, then run the inbound and
     * outbound pipelines concurrently until either terminates.
     */
    private Mono<Void> handleAuthenticated(WebSocketSession session, UUID profileId) {
        SessionRegistry.Session registered = sessionRegistry.register(profileId, session.getId());
        log.info("WebSocket connected: profileId={}, sessionId={}", profileId, session.getId());

        // Outbound: drain the per-session sink to the socket as text frames.
        Mono<Void> outbound = session.send(
                registered.getSink().asFlux().map(session::textMessage));

        // Inbound: parse each incoming text frame as a ClientCommand and act on it.
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> handleClientFrame(registered, payload))
                .then();

        return Mono.zip(inbound, outbound)
                .then()
                .doFinally(signal -> {
                    sessionRegistry.deregister(session.getId());
                    log.info("WebSocket disconnected: profileId={}, sessionId={}, signal={}",
                            profileId, session.getId(), signal);
                });
    }

    /**
     * Parses and dispatches a single inbound client frame. Parse failures and
     * per-frame errors are logged and swallowed so one bad frame never tears the
     * connection down.
     */
    private Mono<Void> handleClientFrame(SessionRegistry.Session session, String payload) {
        ClientCommand command;
        try {
            command = objectMapper.readValue(payload, ClientCommand.class);
        } catch (Exception e) {
            log.warn("Ignoring unparseable client frame (sessionId={}): {}", session.getSessionId(), payload);
            return Mono.empty();
        }

        if (command.getAction() == null || command.getConversationId() == null) {
            emit(session, ServerControlFrame.error(command == null ? null : command.getConversationId(),
                    "action and conversationId are required"));
            return Mono.empty();
        }

        return switch (command.getAction()) {
            case SUBSCRIBE -> handleSubscribe(session, command.getConversationId());
            case UNSUBSCRIBE -> {
                session.unsubscribe(command.getConversationId());
                emit(session, ServerControlFrame.unsubscribed(command.getConversationId()));
                yield Mono.empty();
            }
        };
    }

    /**
     * Verifies the session's owner is a participant of the conversation before
     * recording the subscription — the reactive equivalent of the old
     * {@code StompSubscriptionController.handleSubscription}.
     */
    private Mono<Void> handleSubscribe(SessionRegistry.Session session, UUID conversationId) {
        return conversationRepo.findById(conversationId)
                .map(conversation -> conversation.getProfiles().contains(session.getProfileId()))
                .defaultIfEmpty(false)
                .doOnNext(isParticipant -> {
                    if (Boolean.TRUE.equals(isParticipant)) {
                        session.subscribe(conversationId);
                        emit(session, ServerControlFrame.subscribed(conversationId));
                        log.debug("Subscription registered: profileId={}, conversationId={}",
                                session.getProfileId(), conversationId);
                    } else {
                        emit(session, ServerControlFrame.error(conversationId,
                                "You are not a participant of conversation " + conversationId));
                        log.warn("SUBSCRIBE rejected: profileId={} not a participant of conversationId={}",
                                session.getProfileId(), conversationId);
                    }
                })
                .then()
                .onErrorResume(e -> {
                    log.warn("SUBSCRIBE failed for conversationId={} (sessionId={})",
                            conversationId, session.getSessionId(), e);
                    emit(session, ServerControlFrame.error(conversationId, "Subscription failed"));
                    return Mono.empty();
                });
    }

    private void emit(SessionRegistry.Session session, ServerControlFrame frame) {
        try {
            session.emit(objectMapper.writeValueAsString(frame));
        } catch (Exception e) {
            log.warn("Failed to serialise control frame (sessionId={})", session.getSessionId(), e);
        }
    }

    /**
     * Resolves the authenticated {@code profileId} from the HTTP upgrade request.
     * Returns an empty {@link Mono} when no verified account can be resolved,
     * which the caller translates into closing the socket.
     */
    private Mono<UUID> resolveProfileId(WebSocketSession session) {
        ServerWebExchange exchange = buildExchange(session);
        return authParser.extractAccountInfo(exchange)
                .flatMap(accountListOpt -> {
                    if (accountListOpt == null || accountListOpt.isEmpty()) {
                        return Mono.empty();
                    }
                    AccountList accountList = accountListOpt.get();
                    boolean hasTrecVerified = accountList.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(TREC_VERIFIED::equals);
                    if (!hasTrecVerified || accountList.getMainAccount() == null) {
                        return Mono.empty();
                    }
                    return Mono.just(accountList.getMainAccount().getId());
                });
    }

    /**
     * Adapts the reactive WebSocket handshake into a {@link ServerWebExchange} so
     * {@link TrecAuthSecurityAsyncParser} — which reads from the exchange — can
     * pull the {@code Authorization} header and the auth cookie. The handshake
     * already exposes reactive headers/cookies/URI via {@link HandshakeInfo}, so
     * we build a minimal reactive request from those (no servlet bridging, no
     * blocking — the key improvement over the old interceptor).
     */
    private ServerWebExchange buildExchange(WebSocketSession session) {
        return new DefaultServerWebExchange(
                new HandshakeServerHttpRequest(session.getHandshakeInfo()),
                new MockServerHttpResponse(),
                new DefaultWebSessionManager(),
                ServerCodecConfigurer.create(),
                new AcceptHeaderLocaleContextResolver());
    }
}
