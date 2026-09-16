package com.trecapps.comm.messages.models;

import lombok.Data;

import java.time.Instant;
import java.time.OffsetDateTime;

@Data
public class Reaction {
    Instant seen;
    String reaction;
}
