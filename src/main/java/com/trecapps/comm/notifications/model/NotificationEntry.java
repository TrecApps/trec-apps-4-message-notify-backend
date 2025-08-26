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

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Table("notificationEntry")
public class NotificationEntry {

    @PrimaryKey
    UUID id;

    @Indexed
    String userId;

    @Enumerated(EnumType.STRING)
    ImageEndpointType type = ImageEndpointType.REGULAR;

    String imageId;

    String message;

    String category;

    OffsetDateTime createTime;
    OffsetDateTime updateTime;

    String appId;
    String brandId;

    String relevantId;

    @Enumerated(EnumType.STRING)
    NotificationStatus status = NotificationStatus.UNSEEN;

    public NotifyPost getNotifyPost(){
        NotifyPost notifyPost = new NotifyPost();
        notifyPost.setAppSpecific(appId != null);
        notifyPost.setCategory(category);
        notifyPost.setTime(updateTime);
        notifyPost.setType(type);
        notifyPost.setMessage(message);
        notifyPost.setImageId(imageId);
        notifyPost.setRelevantId(relevantId);
        return notifyPost;
    }

    public boolean isOwner(String userId, String brandId, String appId)
    {
        boolean byBrand = this.brandId == null ? (brandId == null) : brandId.equals(this.brandId);
        boolean byApp = this.appId == null || this.appId.equals(appId);
        boolean byUser = this.userId == null || this.userId.equals(userId);

        return (byUser || byBrand) && byApp;
    }
}
