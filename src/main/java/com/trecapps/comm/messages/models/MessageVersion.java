package com.trecapps.comm.messages.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MessageVersion implements Comparable<MessageVersion>{
    String message;
    @JsonFormat(pattern="YYYY-MM-dd HH:mm:ss.SSSZ")
    OffsetDateTime made;

    public int compareTo(MessageVersion other){
        return made.compareTo(other.made);
    }
}
