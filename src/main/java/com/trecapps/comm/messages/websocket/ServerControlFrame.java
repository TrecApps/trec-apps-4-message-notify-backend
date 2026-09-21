package com.trecapps.comm.messages.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A control frame the server sends back to a single client in response to a
 * {@link ClientCommand} (or to report an error).
 *
 * <p>Domain events ({@code ConversationEvent}) are sent as their own JSON on the
 * wire; control frames are distinguished by the presence of the {@code type}
 * field, which the client uses to tell them apart from domain events (a
 * {@code ConversationEvent} has an {@code eventType} field instead).
 *
 * <pre>{@code
 * { "type": "SUBSCRIBED",   "conversationId": "..." }
 * { "type": "UNSUBSCRIBED", "conversationId": "..." }
 * { "type": "ERROR",        "conversationId": "...", "message": "..." }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerControlFrame {

    public enum Type {
        SUBSCRIBED,
        UNSUBSCRIBED,
        ERROR
    }

    /** Discriminator that marks this JSON object as a control frame. */
    private Type type;

    /** The conversation the frame relates to, if any. */
    private UUID conversationId;

    /** Human-readable detail, populated for {@link Type#ERROR} frames. */
    private String message;

    public static ServerControlFrame subscribed(UUID conversationId) {
        return new ServerControlFrame(Type.SUBSCRIBED, conversationId, null);
    }

    public static ServerControlFrame unsubscribed(UUID conversationId) {
        return new ServerControlFrame(Type.UNSUBSCRIBED, conversationId, null);
    }

    public static ServerControlFrame error(UUID conversationId, String message) {
        return new ServerControlFrame(Type.ERROR, conversationId, message);
    }
}
