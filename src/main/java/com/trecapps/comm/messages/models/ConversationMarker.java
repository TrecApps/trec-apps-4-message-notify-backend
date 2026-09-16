package com.trecapps.comm.messages.models;

import lombok.Data;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ConversationMarker implements Comparable<ConversationMarker>{

    UUID messageId;
    Instant messageMade;
    int previousMessages;

    public int compareTo(ConversationMarker other){
        return messageMade.compareTo(other.messageMade);
    }
}
