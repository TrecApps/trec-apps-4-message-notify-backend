package com.trecapps.comm.messages.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A command frame sent by the client over the native WebSocket connection.
 *
 * <p>This replaces the STOMP {@code SUBSCRIBE}/{@code UNSUBSCRIBE} frames used by
 * the previous servlet-based implementation. The client sends a small JSON
 * object of the form:
 *
 * <pre>{@code
 * { "action": "SUBSCRIBE",   "conversationId": "..." }
 * { "action": "UNSUBSCRIBE", "conversationId": "..." }
 * }</pre>
 *
 * <p>The handler ({@code ConversationWebSocketHandler}) reads these frames on the
 * inbound side of the socket and updates the per-session subscription set.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientCommand {

    public enum Action {
        SUBSCRIBE,
        UNSUBSCRIBE
    }

    /** The action the client wishes to perform. */
    private Action action;

    /** The conversation the action applies to. */
    private UUID conversationId;
}
