package com.trecapps.comm.messages.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
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

    @JsonFormat(pattern="YYYY-MM-dd HH:mm:ss.SSSZ")
    @Field(targetType = FieldType.DATE_TIME)
    OffsetDateTime firstMade;

    SortedSet<MessageVersion> messageVersions = new TreeSet<>();

    int page;

    Map<String, Reaction> reactions = new HashMap<>();

    UUID conversationIdBranch;




}
