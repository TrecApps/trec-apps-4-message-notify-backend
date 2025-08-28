package com.trecapps.comm.messages.models;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MessageVersion implements Comparable<MessageVersion>{
    String message;
    OffsetDateTime made;

    public int compareTo(MessageVersion other){
        return made.compareTo(other.made);
    }
}
