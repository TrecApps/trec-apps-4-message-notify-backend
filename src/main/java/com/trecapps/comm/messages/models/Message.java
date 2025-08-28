package com.trecapps.comm.messages.models;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.OffsetDateTime;
import java.util.*;

@Document("message")
@Data
public class Message {

    @MongoId
    UUID id;

    @Indexed
    UUID conversationId;

    String profile;

    OffsetDateTime firstMade;

    SortedSet<MessageVersion> messageVersions = new TreeSet<>();

    int page;

    Map<String, Reaction> reactions = new HashMap<>();

    UUID conversationIdBranch;




}
