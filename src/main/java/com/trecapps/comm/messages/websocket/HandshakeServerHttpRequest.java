package com.trecapps.comm.messages.websocket;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.HandshakeInfo;
import reactor.core.publisher.Flux;

/**
 * A minimal reactive {@link org.springframework.http.server.reactive.ServerHttpRequest}
 * backed by a WebSocket {@link HandshakeInfo}.
 *
 * <p>It exposes exactly what {@code TrecAuthSecurityAsyncParser} needs to resolve
 * the account — the request URI, the HTTP headers (for the {@code Authorization}
 * header) and the cookies (for the auth-cookie strategy that this deployment
 * uses). The body is always empty; authentication does not read it.
 *
 * <p>Extending {@link AbstractServerHttpRequest} keeps this small: the base class
 * derives the request path and query params from the URI supplied to the
 * constructor, so only cookies, SSL info, native request and body must be
 * provided here.
 */
class HandshakeServerHttpRequest extends AbstractServerHttpRequest {

    private final HandshakeInfo handshakeInfo;

    HandshakeServerHttpRequest(HandshakeInfo handshakeInfo) {
        super(HttpMethod.GET,
                handshakeInfo.getUri(),
                null,
                copyHeaders(handshakeInfo.getHeaders()));
        this.handshakeInfo = handshakeInfo;
    }

    private static HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(source);
        return headers;
    }

    @Override
    protected MultiValueMap<String, HttpCookie> initCookies() {
        MultiValueMap<String, HttpCookie> cookies = new LinkedMultiValueMap<>();
        handshakeInfo.getCookies().forEach((name, values) ->
                values.forEach(cookie -> cookies.add(name, cookie)));
        return cookies;
    }

    @Override
    protected SslInfo initSslInfo() {
        // No client SSL info is needed for token/cookie authentication.
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeRequest() {
        return (T) handshakeInfo;
    }

    @Override
    public Flux<DataBuffer> getBody() {
        return Flux.empty();
    }
}
