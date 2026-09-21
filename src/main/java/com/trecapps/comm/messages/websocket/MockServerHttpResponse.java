package com.trecapps.comm.messages.websocket;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Mono;

/**
 * A no-op reactive server response used only to satisfy the
 * {@code DefaultServerWebExchange} constructor when authenticating a WebSocket
 * handshake. Authentication never writes a body or a status through this
 * response — the real handshake status is managed by the reactive WebSocket
 * upgrade infrastructure — so all write paths are empty.
 */
class MockServerHttpResponse
        extends org.springframework.http.server.reactive.AbstractServerHttpResponse {

    MockServerHttpResponse() {
        super(DefaultDataBufferFactory.sharedInstance);
    }

    @Override
    protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
        return Mono.empty();
    }

    @Override
    protected Mono<Void> writeAndFlushWithInternal(
            Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return Mono.empty();
    }

    @Override
    protected void applyStatusCode() {
        // no-op
    }

    @Override
    protected void applyHeaders() {
        // no-op
    }

    @Override
    protected void applyCookies() {
        // no-op
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getNativeResponse() {
        return (T) this;
    }
}
