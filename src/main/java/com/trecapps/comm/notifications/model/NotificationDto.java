package com.trecapps.comm.notifications.model;

import com.trecapps.base.notify.models.Notification;
import com.trecapps.base.notify.models.NotificationStatus;
import lombok.Data;

@Data
public class NotificationDto {
    NotificationStatus status;
    String notificationId;
    String app;
    NotifyPostDto post;


    public static NotificationDto getInstance(Notification notification) {
        NotificationDto ret = new NotificationDto();
        ret.status = notification.getStatus();
        ret.notificationId = notification.getNotificationId();
        ret.app = notification.getApp();
        ret.post = NotifyPostDto.getInstance(notification.getPost());
        return ret;
    }
}
