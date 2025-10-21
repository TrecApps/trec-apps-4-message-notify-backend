package com.trecapps.comm.notifications.model;

import com.trecapps.base.notify.models.ImageEndpointType;
import com.trecapps.base.notify.models.NotificationStatus;
import com.trecapps.base.notify.models.NotifyPost;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Data
@Table("notification_entry")
public class NotificationEntry {

    @PrimaryKey
    NotificationEntryId id;

    @Enumerated(EnumType.STRING)
    ImageEndpointType type = ImageEndpointType.REGULAR;

    @Column("image_id")
    String imageId;


    String message;

    String category;

    @Column("update_time")
    Instant updateTime;



    String relevantId;
    String relevantIdSecond;

    @Enumerated(EnumType.STRING)
    NotificationStatus status = NotificationStatus.UNSEEN;

    public Instant getTime(){
        return updateTime == null ? id.createTime : updateTime;
    }

    public NotifyPost getNotifyPost(){
        NotifyPost notifyPost = new NotifyPost();
        notifyPost.setAppSpecific(id.getAppId() != null);
        notifyPost.setCategory(category);
        Instant time = getTime();
        notifyPost.setTime(time.atOffset(ZoneOffset.UTC));
        notifyPost.setType(type);
        notifyPost.setMessage(message);
        notifyPost.setImageId(imageId);
        notifyPost.setRelevantId(relevantId);
        return notifyPost;
    }

    public boolean isOwner(String userId, String brandId, String appId)
    {
        boolean byApp = this.id.appId == null || this.id.appId.contains(appId);
        boolean byProfile = false;
        if(id.profileId.startsWith("User-")){
            byProfile = id.profileId.substring(5).equals(userId);
        } else if(id.profileId.startsWith("Brand-")){
            byProfile = id.profileId.substring(6).equals(brandId);
        }

        return (byProfile) && byApp;
    }
}
