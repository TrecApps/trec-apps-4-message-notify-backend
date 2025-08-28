package com.trecapps.comm.messages.models;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.*;

@Data
@Document("conversation")
public class Conversation {
    @MongoId
    UUID id;

    Set<String> apps = new HashSet<>();

    @Indexed
    Set<String> profiles = new HashSet<>();

    int currentPage = 0;

    SortedSet<ConversationMarker> markers;

    int level = 0;

    UUID messageBase; // For replies
}
