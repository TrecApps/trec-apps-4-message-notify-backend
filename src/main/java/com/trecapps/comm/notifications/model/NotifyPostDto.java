package com.trecapps.comm.notifications.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.trecapps.base.notify.models.ImageEndpointType;
import com.trecapps.base.notify.models.NotifyPost;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class NotifyPostDto {

    ImageEndpointType type;
    String imageId;
    String message;
    @JsonFormat(pattern="YYYY-MM-dd HH:mm:ss.SSSZ")
    OffsetDateTime time;
    String category;
    String relevantId;
    boolean appSpecific;


    public static NotifyPostDto getInstance(NotifyPost post){
        NotifyPostDto ret = new NotifyPostDto();
        ret.appSpecific = post.isAppSpecific();
        ret.category = post.getCategory();
        ret.imageId = post.getImageId();
        ret.time = post.getTime();
        ret.type = post.getType();
        ret.relevantId = post.getRelevantId();
        ret.message = post.getMessage();
        return ret;
    }
}
