package com.trecapps.comm.messages.websocket;

import com.trecauth.common.model.AccountList;
import com.trecauth.webflux.repos.TrecAuthSecurityAsyncParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates the WebSocket HTTP upgrade request before the STOMP handshake
 * is completed.
 *
 * <p>During {@link #beforeHandshake}, the interceptor wraps the incoming
 * {@link ServerHttpRequest} and {@link ServerHttpResponse} into a
 * {@link DefaultServerWebExchange} so that
 * {@link TrecAuthSecurityAsyncParser#extractAccountInfo(ServerWebExchange)}
 * can be called. The parser automatically checks the {@code Authorization}
 * header first, then falls back to the configured request cookie — no
 * duplicate extraction logic is needed here.
 *
 * <p>On success the resolved {@code profileId} is stored in the WebSocket
 * session {@code attributes} map under the key {@code "profileId"} and
 * {@code true} is returned to allow the handshake to proceed.
 *
 * <p>On failure (missing/invalid token or missing {@code TREC_VERIFIED}
 * authority) the HTTP response status is set to {@code 401} and {@code false}
 * is returned to abort the handshake.
 *
 * <p>This bean is only created when
 * {@code trecapps.messaging.websocket.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    /** Authority string that must be present for a connection to be accepted. */
    private static final String TREC_VERIFIED = "TREC_VERIFIED";

    /** Session attribute key under which the resolved profileId is stored. */
    public static final String PROFILE_ID_ATTR = "profileId";

    private final TrecAuthSecurityAsyncParser authParser;

    @Autowired
    public WebSocketHandshakeInterceptor(TrecAuthSecurityAsyncParser authParser) {
        this.authParser = authParser;
    }

    /**
     * Authenticates the HTTP upgrade request.
     *
     * @param request    the incoming HTTP upgrade request
     * @param response   the HTTP response (used to set 401 on rejection)
     * @param wsHandler  the target WebSocket handler (unused here)
     * @param attributes the session attributes map shared with the STOMP session
     * @return {@code true} if authentication succeeded and the handshake should
     *         proceed; {@code false} to abort with a {@code 401} response
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        try {
            ServerWebExchange exchange = buildExchange(request, response);

            Optional<AccountList> accountListOpt = authParser
                    .extractAccountInfo(exchange)
                    .block();

            if (accountListOpt == null || accountListOpt.isEmpty()) {
                log.debug("WebSocket handshake rejected: no account resolved from token");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            AccountList accountList = accountListOpt.get();

            // TREC_VERIFIED is sourced from Account.permissions on the brand accounts
            // inside AccountList.getAuthorities() — no extra setup needed.
            boolean hasTrecVerified = accountList.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(TREC_VERIFIED::equals);

            if (!hasTrecVerified) {
                log.debug("WebSocket handshake rejected: TREC_VERIFIED authority absent for account {}",
                        accountList.getMainAccount() != null
                                ? accountList.getMainAccount().getId()
                                : "unknown");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            UUID profileId = accountList.getMainAccount().getId();
            attributes.put(PROFILE_ID_ATTR, profileId);
            log.debug("WebSocket handshake accepted for profileId={}", profileId);
            return true;

        } catch (Exception e) {
            log.error("WebSocket handshake failed with unexpected error", e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /**
     * No-op — nothing to clean up after the handshake.
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // intentionally empty
    }

    /**
     * Wraps a Spring MVC-style {@link ServerHttpRequest} / {@link ServerHttpResponse}
     * pair into a reactive {@link ServerWebExchange} that
     * {@link TrecAuthSecurityAsyncParser} can consume.
     *
     * <p>The {@code Authorization} header and cookies are forwarded as-is from
     * the original request; the parser reads them directly from the exchange.
     *
     * @param request  the blocking HTTP request from the handshake
     * @param response the blocking HTTP response from the handshake
     * @return a {@link ServerWebExchange} wrapping the request and response
     */
    ServerWebExchange buildExchange(ServerHttpRequest request, ServerHttpResponse response) {
        org.springframework.http.server.reactive.ServerHttpRequest reactiveRequest =
                new HandshakeRequestAdapter(request);

        org.springframework.http.server.reactive.ServerHttpResponse reactiveResponse =
                new HandshakeResponseAdapter(response);

        return new DefaultServerWebExchange(
                reactiveRequest,
                reactiveResponse,
                new DefaultWebSessionManager(),
                ServerCodecConfigurer.create(),
                new AcceptHeaderLocaleContextResolver()
        );
    }

    // -------------------------------------------------------------------------
    // Inner adapter: blocking ServerHttpRequest → reactive ServerHttpRequest
    // -------------------------------------------------------------------------

    /**
     * Minimal adapter that exposes the headers, cookies, URI, and method from a
     * blocking {@link ServerHttpRequest} through the reactive
     * {@link org.springframework.http.server.reactive.ServerHttpRequest} interface.
     *
     * <p>Only the fields that {@link TrecAuthSecurityAsyncParser} actually reads
     * ({@code Authorization} header, request cookies) are forwarded. The body is
     * always empty because authentication does not require it.
     */
    private static class HandshakeRequestAdapter
            extends ServerHttpRequestDecorator {

        private final ServerHttpRequest blockingRequest;

        HandshakeRequestAdapter(ServerHttpRequest blockingRequest) {
            // ServerHttpRequestDecorator requires a delegate; we supply a minimal
            // no-op implementation and override every method we need.
            super(new NoOpReactiveRequest(blockingRequest));
            this.blockingRequest = blockingRequest;
        }

        @Override
        public HttpHeaders getHeaders() {
            return blockingRequest.getHeaders();
        }

        @Override
        public MultiValueMap<String, HttpCookie> getCookies() {
            LinkedMultiValueMap<String, HttpCookie> map = new LinkedMultiValueMap<>();
            // Parse cookies from the raw Cookie header so the parser can find them.
            for (String cookieHeader : blockingRequest.getHeaders().getOrEmpty("Cookie")) {
                for (String part : cookieHeader.split(";")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2) {
                        map.add(kv[0].trim(), new HttpCookie(kv[0].trim(), kv[1].trim()));
                    }
                }
            }
            return map;
        }

        @Override
        public URI getURI() {
            return blockingRequest.getURI();
        }

        @Override
        public HttpMethod getMethod() {
            return blockingRequest.getMethod();
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return Flux.empty();
        }
    }

    /**
     * Minimal no-op reactive request used as the required delegate for
     * {@link ServerHttpRequestDecorator}. All meaningful data is provided by
     * {@link HandshakeRequestAdapter}'s overrides.
     */
    private static class NoOpReactiveRequest
            implements org.springframework.http.server.reactive.ServerHttpRequest {

        private final ServerHttpRequest source;

        NoOpReactiveRequest(ServerHttpRequest source) {
            this.source = source;
        }

        @Override public String getId() { return "ws-handshake"; }
        @Override public HttpMethod getMethod() { return source.getMethod(); }
        @Override public URI getURI() { return source.getURI(); }
        @Override public HttpHeaders getHeaders() { return source.getHeaders(); }
        @Override public MultiValueMap<String, HttpCookie> getCookies() { return new LinkedMultiValueMap<>(); }
        @Override public Flux<DataBuffer> getBody() { return Flux.empty(); }
        @Override public InetSocketAddress getRemoteAddress() { return source.getRemoteAddress(); }

        @Override
        public org.springframework.http.server.RequestPath getPath() {
            return org.springframework.http.server.RequestPath.parse(
                    source.getURI().getRawPath(), null);
        }

        @Override
        public MultiValueMap<String, String> getQueryParams() {
            return UriComponentsBuilder.fromUri(source.getURI()).build().getQueryParams();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    }

    // -------------------------------------------------------------------------
    // Inner adapter: blocking ServerHttpResponse → reactive ServerHttpResponse
    // -------------------------------------------------------------------------

    /**
     * Minimal adapter that forwards status-code writes back to the original
     * blocking {@link ServerHttpResponse}. The body write methods are no-ops
     * because authentication never writes a response body.
     */
    private static class HandshakeResponseAdapter extends AbstractServerHttpResponse {

        private final ServerHttpResponse blockingResponse;

        HandshakeResponseAdapter(ServerHttpResponse blockingResponse) {
            super(DefaultDataBufferFactory.sharedInstance, blockingResponse.getHeaders());
            this.blockingResponse = blockingResponse;
        }

        @Override
        protected Mono<Void> writeWithInternal(
                org.reactivestreams.Publisher<? extends DataBuffer> body) {
            return Mono.empty();
        }

        @Override
        protected Mono<Void> writeAndFlushWithInternal(
                org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
            return Mono.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getNativeResponse() {
            return (T) blockingResponse;
        }

        @Override
        protected void applyStatusCode() {
            org.springframework.http.HttpStatusCode status = getStatusCode();
            if (status != null) {
                blockingResponse.setStatusCode(status);
            }
        }

        @Override
        protected void applyHeaders() {
            // Headers are shared via the constructor — nothing extra needed.
        }

        @Override
        protected void applyCookies() {
            // Cookie writing is not needed for the handshake auth path.
        }
    }
}
