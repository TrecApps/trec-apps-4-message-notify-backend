package com.trecapps.comm.messages.models;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Reaction {
    OffsetDateTime seen;
    String reaction;
}
