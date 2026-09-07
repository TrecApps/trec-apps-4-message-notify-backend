package com.trecapps.comm.messages.models;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Represents a single mutation event on a conversation, published to the
 * Kafka/Event Hubs topic and consumed by every application instance so that
 * locally-connected WebSocket clients can be notified in real time.
 *
 * <p>The {@code payload} field carries different concrete types depending on
 * {@code eventType}:
 * <ul>
 *   <li>{@link EventType#NEW_MESSAGE}      – full {@link Message} object</li>
 *   <li>{@link EventType#MESSAGE_SEEN}     – {@code List<UUID>} of updated message IDs</li>
 *   <li>{@link EventType#MESSAGE_REACTION} – updated {@link Message} object</li>
 *   <li>{@link EventType#MESSAGE_EDIT}     – updated {@link Message} object</li>
 * </ul>
 *
 * <p>{@code @JsonTypeInfo} on {@code payload} embeds a {@code @class} property
 * in the serialised JSON so that Jackson can reconstruct the correct concrete
 * type on deserialisation (required for the round-trip correctness property).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEvent {

    private EventType eventType;

    private UUID conversationId;

    private UUID actorProfileId;

    /**
     * The event-specific payload. Jackson embeds the concrete class name as a
     * {@code @class} discriminator so the type survives a JSON round-trip.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private Object payload;
}
